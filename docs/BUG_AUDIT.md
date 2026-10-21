# MRM PG Manager - Obsessive Bug Hunt Audit v0.6.5+

Date: 2026-08-29
Scope: 48 Kotlin files, 10113 LOC, all screens, api, storage, utils, workers, widget
Method: Manual code review + grep for toIntOrNull, isDigit, limit constants, date parsing, backup/restore, caching, notification, UI

## Critical / High Severity

### 1. Persian digits still vulnerable in TemplatesScreen
- **File**: `app/src/main/java/com/mrm/pgmanager/ui/screens/TemplatesScreen.kt:492-494, 580, 591, 696, 724, 797`
- **Root cause**: `dataGb`, `days`, `hwid`, `onHoldDays` used `it.filter { c.isDigit() }` and `trim().toLongOrNull()` without `normalizePersianDigits`. `Char.isDigit()` returns true for Persian digits, but `toLongOrNull` returns null for Persian, causing unlimited.
- **Impact**: Same bug as previously fixed in UsersScreen - template creation with Persian keyboard yields unlimited data/time, silent data loss.
- **Fix**: Normalize before filter and before parsing. Done in commit.
```kotlin
val n = normalizePersianDigits(raw)
dataGb = n.filter { c.isDigit() }
val dataBytes = normalizePersianDigits(dataGb).trim().toLongOrNull()?.times(BYTES_PER_GB)
hwidLimit = normalizePersianDigits(hwid).trim().toIntOrNull()
```

### 2. BackupManager token exclusion vs warning mismatch - accounts not restored
- **File**: `utils/BackupManager.kt:183` `includeTokens = isEncrypted`
- **File**: `res/values/strings.xml:397` warning says plain backup contains tokens
- **Root cause**: Code excludes tokens when password blank, but UI warning says opposite. Restore skips accounts with blank token (`if (base.isBlank() || tok.isBlank()) continue`), so plain backup restores 0 accounts. User thinks backup works but loses all panels.
- **Impact**: Critical UX + security confusion. User creates plain backup expecting to restore accounts, gets empty list. Or if they rely on warning, they think tokens are included but they aren't.
- **Fix**: Changed to `includeTokens = true` always, keep warning. Now plain backup includes tokens and restores correctly, warning is accurate. Alternative would be to keep exclusion and fix warning, but that breaks restore expectation.
- **File**: `utils/BackupManager.kt:189` comment updated.

### 3. Groups pagination limit 200 - incomplete list for large panels
- **File**: `data/api/PanelApi.kt:620-660` `groups()` and `groupsDetailed()` and `admins()`
- **Root cause**: Hard limit 200, no pagination loop. Panels with >200 groups show incomplete group list, groupNames missing in users, filters broken, template group picker incomplete.
- **Impact**: High - data loss for large installations. `attachGroupNames` also uses `groups()` so user cards show no group names.
- **Fix**: Implemented pagination loop with offset/limit 200 until chunk < limit, guard offset >10_000. Applied to `groups()`, `groupsDetailed()`, `admins()`. Done.

### 4. BulkCreateUsersDialog canStart check without Persian normalization
- **File**: `ui/dialogs/BulkCreateUsersDialog.kt:84`
- **Root cause**: `limitGb.toDoubleOrNull() != null` without normalize. Persian digits -> null -> canStart false, user cannot start bulk creation, no error message, appears as disabled button.
- **Fix**: `normalizePersianDigits(limitGb).toDoubleOrNull()`

### 5. PanelApi online_at parsing fails for naive datetime
- **File**: `data/api/PanelApi.kt:380-410` `parseUser`
- **Root cause**: Only tries `Instant.parse` which requires `Z`, and timestamp. Panel may return `2024-01-15T10:30:00` without timezone or with space separator. Then onlineTime =0, isOnline false even if recently online.
- **Fix**: Added fallback to `LocalDateTime.parse(...).atZone(systemDefault).toInstant()`. Now handles ISO instant, naive LocalDateTime, and timestamp.

### 6. BackupManager inspect/restore JSON detection fragile
- **File**: `utils/BackupManager.kt:200-230`
- **Root cause**: Checks `trimStart().startsWith("{")` to detect plain JSON. Encrypted binary could theoretically start with 0x7B byte, misdetected as JSON and throw corrupt error instead of asking password. Low probability but possible.
- **Fix**: Try `JSONObject(s)` parse first, if succeeds treat as JSON, else encrypted. More robust.

## Medium Severity

### 7. userCountMetric uses maxOf instead of sum — RESOLVED 2026-10-21 (not a bug)
- **File**: `data/api/PanelApi.kt` `totals[time] = maxOf(totals[time] ?: 0L, v)`
- **Verification**: Checked against PasarGuard panel v5.4.1 source (`crud/user.py:get_user_count_metric_stats`). Without `group_by_node`/`node_id` the response contains exactly one aggregated key (`"-1"`) per time bucket, so `maxOf` and `sum` are equivalent in the real response shape. The app never sends group_by_node.
- **Future risk**: If grouped responses are ever consumed, neither max nor sum is correct for distinct users across nodes; the top-level `count_during_period` field must be used instead. This is now documented inline at the call site and locked by `PanelApiContractTest` (`minus-one key` / `total fallback` tests).
- **Current**: Kept as maxOf on purpose; ambiguity closed.

### 8. PdfInvoiceGenerator duration fallback misleading
- **File**: `utils/PdfInvoiceGenerator.kt:150-180`
- **Root cause**: When `createdAt` missing, `effectiveStartDate = LocalDate.now()`, duration = remaining days, not total. Invoice shows "Duration: 5 days" for old user whose total was 30 days but only 5 remaining. Misleading for billing.
- **Fix recommendation**: If createdAt null, show start as unknown or show remaining as duration with label "Remaining", or don't show duration. Current fallback shows today as start, which is wrong for old users.
- **Impact**: Medium - financial document inaccuracy.

### 9. Export CSV newline handling — FIXED 2026-10-21
- **File**: `utils/ExportUsers.kt` `esc`
- **Root cause**: Note field may contain newline; CSV with newline inside a quoted field is RFC-valid but Excel/Sheets split the row and imports break.
- **Fix**: `esc` now also replaces `\r\n`/`\n`/`\r` with spaces. JSON export keeps raw newlines (org.json escapes them). Covered by `ExportUsersTest`.

### 10. SessionStore users cache always offline — FIXED in a later round
- **File**: `data/storage/SessionStore.kt` `readUsersCache`
- **Resolution**: `isOnline` is reconstructed from `onlineAt` against the panel's 2-minute window, using the cache timestamp (not current time) as baseline — users online at cache time stay online on restore.

### 11. MonitoringWorker deviceHasInternet uses VALIDATED capability
- **File**: `work/MonitoringWorker.kt:20-28`
- **Root cause**: `NET_CAPABILITY_VALIDATED` may be false on VPN or restricted networks, causing missed panel-offline alerts when device actually has internet via VPN.
- **Impact**: Medium - false negative for monitoring.

### 12. PanelCache no size limit / no eviction
- **File**: `data/cache/PanelCache.kt`
- **Root cause**: ConcurrentHashMap grows without bound, but keys are per baseUrl and limited types (stats, traffic, users, groups, etc), so max ~ 7 * numAccounts. Acceptable, but if user switches many accounts without clear, could accumulate.
- **Recommendation**: Keep as is, but ensure clear() called on logout/switch (already done).

### 13. DateLogic uses device LocalDate.now() - manipulable
- **File**: `utils/DateLogic.kt:45` `remainingDays` uses `LocalDate.now()`
- **Root cause**: If device clock manipulated, expiry calculations wrong. Should use server time? But panel's expire is absolute, device time is what user sees. Acceptable.
- **Impact**: Low.

## Low / Info

### 14. JalaliCalendar lastSeenShort not localized — FIXED 2026-10-21
- **File**: `utils/JalaliCalendar.kt` `lastSeenShort`
- **Root cause**: Under the fa locale the compact label still returned Latin digits with English suffixes ("4m"/"4h"), inconsistent with `lastSeenText`.
- **Fix**: fa branch now returns Persian digits with Persian units (دقیقه/ساعت/روز/هفته/ماه/سال); en keeps the compact `4m` form. Covered by new tests in `JalaliCalendarTest`.

### 15. SubscriptionCard cache pruning only 1 hour
- **File**: `utils/SubscriptionCard.kt:180` deletes files older than 1 hour
- **Root cause**: If user generates many cards within hour, they accumulate in cacheDir/shared, could fill storage.
- **Recommendation**: Keep 1 hour but also limit count (e.g., keep 20 most recent).

### 16. GroupsScreen duplicate check case-insensitive may be overly strict
- **File**: `ui/screens/GroupsScreen.kt:350` `equals(..., ignoreCase=true)`
- **Root cause**: Panel may allow "Test" vs "test" as different? Usually panel treats names case-insensitively? Overly strict prevents creation that panel would allow, but safer.
- **Impact**: Low.

### 17. Template summary shows integer GB via division, loses fractional — FIXED in a later round
- **File**: `ui/screens/TemplatesScreen.kt`
- **Resolution**: A `formatGb` helper now renders `bytes / BYTES_PER_GB.toDouble()` via `%.2f` with trailing zeros trimmed (e.g. 1.5 GB), replacing the integer division everywhere in the screen.

## Summary of Fixes Applied in this Audit

- TemplatesScreen.kt: Persian digits normalization for dataGb, days, hwid, onHoldDays (filter + parsing)
- BulkCreateUsersDialog.kt: canStart Persian normalization
- PanelApi.kt: groups() pagination, groupsDetailed() pagination, admins() pagination, online_at naive datetime parsing
- BackupManager.kt: includeTokens always true to match warning and enable restore, robust JSON detection for inspect/restore

## Remaining TODO (not yet fixed, needs decision)

- **#8 PdfInvoiceGenerator duration fallback** — **RESOLVED 2026-10-21 (Round 8)**. The invoice start date now resolves through a single pure chain — recorded renewal date → `createdAt` → today — shared by both the dialog and the PDF generator (`computeInvoicePeriod`). The "today" fallback only survives for users that have neither a renewal record nor a creation timestamp, where no better date exists.

## Round 2026-10-21 — deep re-audit findings and fixes

Manual re-audit of the whole codebase against panel v5.4.1 source. All changes keep the existing architecture and pass the nine repo validators.

### A. Action errors replaced the whole users screen with a full-screen error state (B4)
- **File**: `ui/screens/UsersUiState.kt` `runAction.onFailure`
- A failed single-user action (reset traffic, renew, toggle, …) wrote the page-level `error` field; `UsersScreen` then swapped the entire list for `MrmErrorState`. The snackbar/rollback path already reports the failure, so the screen-level assignment was removed — list state, selection and scroll position now survive action failures.

### B. One transient network failure permanently closed pagination
- **File**: `ui/screens/UsersUiState.kt` `loadMore.onFailure`
- `endReached = true` was set on any failure (timeout, 429, momentary disconnect), so the list could never load further pages until a full refresh. Removed; `endReached` is now derived only from real panel responses, and retry on scroll works after transient errors.

### C. TLS fallback client allowed cleartext token exchange
- **File**: `data/api/PanelApi.kt` `clientTls12`
- The last-resort login client listed `ConnectionSpec.CLEARTEXT`, which could let the admin token travel in plaintext. Replaced with `MODERN_TLS + COMPATIBLE_TLS` (TLS 1.3/1.2 only); no functional loss for self-hosted HTTPS panels.

### D. Widget refresh could outlive the goAsync() budget
- **File**: `widget/PanelWidgetProvider.kt`
- The manual refresh coroutine had no time cap while OkHttp timeouts (15–20 s) exceed the ~10 s BroadcastReceiver budget. Wrapped the stats call in `withTimeoutOrNull(9_000)`; on timeout the previous cache stays and the offline badge is shown.

### E. PDF invoice could clip content on tall invoices and leaked the document on write failure
- **File**: `utils/PdfInvoiceGenerator.kt`
- Page height was a fixed 720 pt; five price rows plus notes pushed the thank-you/date lines outside the page. Height is now pre-computed from the actual content (min 720). `PdfDocument` and the logo bitmap are also released in a `finally` when file writing throws.

### F. Backup could report success while leaving a 0-byte file
- **File**: `ui/screens/SettingsSectionsAdvanced.kt` manual backup
- A null SAF output stream was silently ignored, then success was shown. Now throws the same "cannot create" error as the worker path.

### G. Subscription card leaked the decoded logo bitmap
- **File**: `utils/SubscriptionCard.kt`
- The optional logo was decoded per card generation and never recycled (native memory leak on repeated sharing). Recycled immediately after drawing.

### H. Legacy BUG_AUDIT items closed
- #7 verified against panel source (not a bug; documented + contract tests), #9 CSV newlines fixed (+`ExportUsersTest`), #14 `lastSeenShort` fully localized (+tests); #10 and #17 confirmed already fixed in earlier rounds.

## Round 2026-10-21 (2) — Login screen deep audit

Focused re-audit of `LoginScreen.kt`, the `login`/`loginWithApiKey`/`baseUrl` paths in `PanelApi.kt`, session restore and diagnostics. All items below were user-approved before implementation; no string resources changed (fa/en parity untouched).

### L1. Revoked API key showed a *format* error instead of an *invalid key* error
- **File**: `ui/screens/LoginScreen.kt` failure mapping
- A well-formed key rejected with 401 produces `Login failed: 401 invalid API key`. The `contains("Invalid API key")` branch ran before `isUnauthorized`, so users saw "Enter a valid API key (starts with pg_key_)" even though the format was fine. The correct `login_err_api_key` message was effectively dead.
- **Fix**: `isUnauthorized` is now evaluated first; the format branch only covers the defensive `require()` path. Locked by `LoginScreenTest.rejectedWellFormedApiKey_showsInvalidKeyMessage_notFormatError`.

### L2. Loopback http passed UI validation but was rejected by the network layer
- **File**: `ui/screens/LoginScreen.kt` local validation
- `http://localhost`/`http://127.0.0.1` were exempt in the UI while the manifest (`usesCleartextTraffic=false`) and `baseUrl()` reject all cleartext — users passed local validation, watched the loading spinner, then got the https error anyway.
- **Fix**: all http is rejected locally with the actionable https field error. The only remaining loopback opening is behind the existing test-only `allowCleartextLoopbackForTests` flag (false in release), so Robolectric/MockWebServer tests can still exercise the full flow.

### L3. API-key login lacked the TLS fallback that password login has
- **File**: `data/api/PanelApi.kt`
- Password login tries default client → retry → TLS 1.2 client; `loginWithApiKey` probed `/api/admin` only once with the default client, so on self-hosted panels with flaky TLS 1.3 handshakes password login could succeed while key login failed.
- **Fix**: extracted `fetchAdminWithTlsFallback()` mirroring the login() attempt pattern (only `SSLException` retried; 401 still fails immediately). Contract test asserts a successful key login uses exactly one request.

### L4. Username was sent untrimmed
- **File**: `ui/screens/LoginScreen.kt` submit
- Leading/trailing whitespace from paste/autofill caused a misleading 401. Now trimmed before send; password is intentionally left untouched (spaces are legal in passwords).

### B1–B3. Small UX improvements
- **B1**: IME **Done** on the password/API-key field now submits the form (previously only hid the keyboard); submit logic extracted into a shared `submitLogin` lambda used by both button and keyboard.
- **B2**: panel address field uses `KeyboardType.Uri` (`/` and `.com` keys reachable).
- **B3**: address and username use `rememberSaveable` so they survive rotation/process death; password and API key stay in-memory only.

### C1. Autofill / password-manager support
- `PGField` gained an `autofillType`; username exposes `ContentType.Username`, password and API key expose `ContentType.Password` via semantics, so Google Password Manager and other autofill services can offer to fill credentials. Locked by `LoginScreenTest.credentialFields_exposeAutofillContentType`.

### Tests added
- `ui/screens/LoginScreenTest.kt` (Robolectric Compose): empty-submit inline errors, IME Done submission, loopback http local rejection, autofill semantics, and the end-to-end 401 API-key mapping through a MockWebServer.
- `PanelApiContractTest`: single-request guarantee for successful API-key login (no wasted retries).

### Deliberately not changed
- The biometric info card stays (static guidance; the actual lock is configured post-login) and no saved-account picker was added to the login screen — both were considered and not selected this round.

## Round 2026-10-21 (3) — Dashboard deep audit

Focused re-audit of `DashboardScreen.kt` plus the stats/traffic/admins/nodes endpoints, `PanelCache` and `NotificationHelper`. All fixes user-approved; two new strings added in both fa/en (parity kept).

### D1. Trending indicator compared two single noisy buckets and produced absurd percentages
- Traffic points are per-bucket usage, not cumulative. The old code compared only the first vs last bucket: the last bucket is the in-progress partial period (systematic false "down"), and `coerceAtLeast(1.0)` on a zero first bucket produced percentages in the millions.
- **Fix**: pure, unit-tested `trafficTrendPercent()` drops the in-progress last bucket and compares the averages of two halves; returns null ("بدون روند") with fewer than 4 points or a zero baseline. Covered by `DashboardTrendTest` (7 cases).

### D2. Duplicate identical traffic request on every screen entry
- `load()` and `LaunchedEffect(chartRange)` both called `trafficUsage` with identical parameters concurrently.
- **Fix**: single `fetchTraffic(range)` entry point with an in-flight range guard; stale-range responses never overwrite the selected range's state, and each range writes its own cache key.

### D3. Transient failure permanently hid the admins section
- `onFailure { admins = emptyList() }` conflated 403 (intentional hide) with transient network errors, replacing a good list with an empty one.
- **Fix**: only `FORBIDDEN` empties the section; other failures keep the previously loaded list.

### D4. Alert flags and node states survived account switches
- `lastNodeStates` and five health-alert flags were plain `remember`, so after switching accounts, node states from account A were compared against account B — immediately posting false node online/offline notifications (the `isNotEmpty()` guard passed with A's data). `offlineAt` had the same cross-account leak.
- **Fix**: all keyed with `remember(session)`.

### D5. Traffic cache key ignored the selected range
- One `traffic:<base>` key for all six ranges; switching ranges overwrote the cache and re-entry could briefly render another range's points.
- **Fix**: `PanelCache.trafficKey(base, range)`; legacy key kept via default parameter. Covered by `PanelCacheTest`.

### D6. Three guaranteed-failing requests after session expiry
- After `onSessionExpired()`, `load()` continued to traffic/admins/nodes, all returning 401.
- **Fix**: early return on the expired flag.

### D7. Notification delivery exception poisoned the stats success path
- `NotificationHelper.post()` had no guard; a `notify()` failure (OEM restriction, dead service) propagated through `evaluateHealth` into the load `runCatching`, turning a successful stats fetch into a displayed failure (and potentially a false offline notification).
- **Fix**: delivery wrapped in `runCatching` at the single shared choke point (protects workers too).

### D8. Pull-to-refresh spinner could stick forever
- `manualRefreshing` was reset without `finally`.
- **Fix**: extracted `refreshManually()` with try/finally, used by both the pull gesture and the header button.

### D9. Node notification ID bands could collide on large panels
- Offline `4100+id` vs online `4200+id` collide when two node ids differ by 100, one notification replacing the other.
- **Fix**: online band moved to `4300+id`.

### Improvements (user-approved)
- **I1**: chart range selector now exposes an accessible description (`chart_range_selector`, new fa+en string) instead of TalkBack reading only "▾".
- **I2**: the four system ring cards (CPU/RAM/disk/traffic) are tappable and deep-link to the Statistics tab via a new `onOpenStatistics` callback wired in `MainActivity`; description `db_open_statistics` (new fa+en string).
- **I3**: the centered spinner on first load is replaced by a skeleton column matching the real card layout (reuses `SkeletonCard`).

### Tests added
- `DashboardTrendTest` — pure trend math (flat, rising, falling, zero baseline, partial last bucket, split behavior).
- `PanelCacheTest` — range-specific traffic keys and isolation between ranges.
- `DashboardScreenTest` (Robolectric + MockWebServer dispatcher) — ring-card tap opens statistics, range selector a11y description, empty traffic shows "no trend".

## Round 2026-10-21 (4) — Users screen deep audit

Full read-through of the users feature: `UsersUiState`, `UsersScreen`, `UsersScreenControls`, `UsersListItems`, `UsersScreenDialogs`, `UserEditorDialog`, `UserDetailsDialog`, `BulkCreateUsersDialog`, `BulkApplyTemplateDialog`, `ConfirmActionDialog`, `ResetExpiryDurationDialog`, `QuickActionSheet`, `SubscriptionQrDialog`, `InvoiceDialog`, `CommonComponents.BulkActionsBar`, the users/groups/bulk endpoints in `PanelApi`, and the parallel debtor path in `MonitoringWorker`. All fixes user-approved (U1–U6 + I1). One new string added in both fa/en (parity kept).

### U1. Success feedback shown before the server responded (two call sites)
- In `UsersScreenDialogs`, the expired-users cleanup confirm and the bulk group add/remove picker launched `runAction(notification = null) { … }` and then called `AppFeedback.success(...)` immediately. The action runs async, so on failure the user saw "done" followed by an error.
- **Fix**: both now pass `successMessage` into `runAction`, which shows the snack only in the `onSuccess` path (and still runs the existing silent refresh). No new strings; the same `us_cleanup_done` / `us_bulk_group_added|removed` messages are used.

### U2. Group-name cache not invalidated after bulk group membership changes
- `PanelApi.bulkGroupMembership` (`/api/groups/bulk/add|remove`) skipped `invalidateGroupNames`, unlike create/modify/delete group. The group badge on user cards could stay stale/missing for the full 60 s cache TTL, and the silent refresh re-read the same stale map.
- **Fix**: invalidate the per-panel name cache after a successful bulk membership call.

### U3. Debtor auto-disable effect fired blind offline and never patched the row
- The `LaunchedEffect(ui.users, …)` at the end of `UsersScreen` calls `PanelApi.setDisabled` directly with no connectivity guard (unlike `runAction`): offline, every list change fired one doomed request per overdue debtor. On success it also never updated the row, so the card stayed "active" until the next silent refresh.
- **Fix**: early return when `NetworkStatus.isOnline` is false (the effect re-runs on the next list change/refresh after reconnect); on success the row is now patched locally to `disabled`. Debtors outside the loaded 60-row page remain covered by `MonitoringWorker`'s 15-minute individual fetch — unchanged.

### U4. Offline fallback in `loadAll` reset the online counter to zero
- When a local filter (debtor / near-limit) was active and the network failed but a cached list existed, `onlineCount` was hard-set to 0 even though the cached users may include online ones. The paged `loadPage` path did not do this.
- **Fix**: count online users from the cached list, matching the success path.

### U5. Numeric bulk days/data dialog reused the search bar
- The bulk amount dialog rendered `GlassSearchBar`, which carries a magnifier icon, a hardcoded "Search" hint, and a text keyboard inside a field that accepts signed numbers / decimals.
- **Fix**: swapped to the existing `CompactGlassField` with the correct `us_bulk_amount_days|gb` hint. The keyboard stays `Text` deliberately so the leading minus (documented "minus to take away") and the data decimal remain typeable. The filtering rules were extracted verbatim into the pure `UsersUiState.sanitizeBulkAmount(raw, kind)` — no behavior change, now JVM-testable.

### U6. CI gate `kt-compose-api` was red: committed Compose symbol index was incomplete
- Four standard Compose 1.7.x symbols were missing from `tools/compose-api-index.txt` (`assertExists`, `assertDoesNotExist`, `onNode`, `androidx.compose.ui.semantics.SemanticsMatcher`), failing the gate on `LoginScreenTest` and `DashboardScreenTest`. The index generator needs AAR downloads (unavailable offline), so the four entries were added manually in sorted position with this audit entry as rationale.
- Adding the missing symbols exposed a genuine latent compile error: `RealButtonsTapTest` called `assertExists()` without importing it — import added.
- **Gate now green**: 1352 Compose references across 101 files.

### Improvement (user-approved)
- **I1**: in server-paginated mode, "select all" only selects the loaded rows (≤ PAGE_SIZE). Tapping it when `totalMatches` exceeds the loaded count now shows an info snack explaining exactly how many were selected vs how many match (`us_select_all_limited`, new fa+en string).

### Tests added
- `UsersBulkAmountTest` — pure JUnit cases for `sanitizeBulkAmount`: Latin/Persian/Arabic digits, leading-minus preservation, inner-minus stripping, decimal dot only for the data kind, letter/space stripping, empty input.

### Deliberately not changed
- String-`hashCode` notification IDs: this is the app-wide convention (UiState, worker, `runAction`); bands used elsewhere are small numeric constants and practical collision is negligible. The two debtor auto-disable paths use different ID prefixes but share the persisted `autoDisabled` flag, so double notifications are effectively prevented. Documented as theoretical risk only.
- The auto-disable effect's `ui.users` restart key: restarts are cheap thanks to the existing per-debtor guards; reworking the key was judged riskier than the benefit.

## Round 2026-10-21 (5) — Settings screen deep audit

Full read-through of the settings feature: `SettingsScreen`, `SettingsSectionsAdvanced` (connection, users, invoice, backup), `RestoreBackupDialog`, `BackupManager`, `BackupWorker`, `MonitoringWorker`, `MonitoringSettings`, shared settings components, and the `MainActivity` wiring. All fixes user-approved (S1–S9 + the S4 product decision: implement the promised debtor notification). Two new strings added in both fa/en (parity kept).

### S1. Invoice logo picker decoded full-resolution images (OOM crash)
- `InvoiceSection` ran `BitmapFactory.decodeStream(input)` with no sampling, while the preview path and `InvoiceDialog` already decode with `inSampleSize` 4/2. A typical camera photo (e.g. 4000×3000, ~48 MB as ARGB) could exhaust the heap on low-RAM devices.
- **Fix**: two-pass decode — bounds-only first pass, then sampled decode via the new pure `BitmapUtils.calculateInSampleSize(w, h, 400, 400)` (power-of-two, both dimensions must cover the target). The stream is reopened from the URI for the second pass. Covered by `BitmapUtilsTest` (5 JVM cases).

### S2. BackupWorker reported success when the output stream was null
- `openOutputStream(newDocUri)?.use { … }` silently skipped writing when null, then proceeded to prune and `saveLastBackupSuccess(true)`, leaving a zero-byte `.mbak` while the UI showed a successful last backup. The manual UI path already guarded this.
- **Fix**: null stream deletes the empty document and returns `Result.retry()`; success is only recorded after a real write.

### S3. In-app restore did not refresh live app state
- Theme, monitoring settings, UI prefs and app lock are loaded once into `remember` state in `MainActivity`. A restore overwrote the prefs but the running app kept the old values until a full process restart.
- **Fix**: new `onRestored`/`onDataRestored` callback chain `RestoreBackupDialog → AdvancedSection → SettingsScreen → MainActivity`; on success the five in-memory states are re-read from the store and `PanelCache.clear()` prevents stale cross-account data. No new strings.

### S4. "Debtor registered" notification switch was dead while the UI promised a notification
- `MonitoringSettings.notifyDebtor` was persisted, restored from backup and shown as a switch, but no code ever posted on debtor registration — even though `set_debtor_desc` explicitly promises a notification "when a user is marked as a debtor".
- **Fix (user-approved option A)**: marking a **new** debtor now posts an events-channel notification (`us_n_debtor` / `us_n_debtor_body`, new fa+en strings), gated on `notificationsEnabled && notifyDebtor`. Editing an existing debt deliberately does not re-notify.

### S5. Unguarded persistable URI permission could crash the backup folder picker
- Some SAF providers throw `SecurityException` from `takePersistableUriPermission`; the uncaught exception closed the app.
- **Fix**: wrapped in `runCatching`; the folder is still kept (usable until the next restart) and an error snack explains the permission limitation via the existing `set_bk_error` / `set_bk_cant_create` strings.

### S6. Debtor count re-parsed JSON on every recomposition
- `UsersSettingsSection` called `store.readDebtors()` directly in composition, re-parsing the prefs JSON on every stepper tap/keystroke.
- **Fix**: moved into `remember(store, baseUrl)`.

### S7. Conditional `remember` inside an elvis expression for the panel version
- The disk-cache fallback called `remember` only when the memory cache missed — a composable call whose presence changed between recompositions. Functionally tolerated but fragile and non-idiomatic.
- **Fix**: the disk read is now an unconditional `remember(session.baseUrl)`; the memory cache is still checked cheaply per recomposition.

### S8. Corrupt JSON backups were mislabeled as encrypted
- `BackupManager.inspect` mapped any failure without a password to `bk_encrypted_long`, including valid-looking JSON that simply wasn't a backup (missing `_meta`/`data`).
- **Fix**: track whether the content parsed as JSON; the "encrypted" hint is only used for binary/non-JSON payloads, otherwise the real invalid/corrupt error propagates.

### S9. Failed manual backup left an empty/partial document behind
- `createDocument` runs before the write; a later failure kept the zero-byte file in the chosen folder.
- **Fix**: the created URI is tracked and deleted in the failure branch (best-effort `runCatching`).

### Tests added
- `BitmapUtilsTest` — pure `calculateInSampleSize` cases: invalid inputs, already-small images, a 4000×3000 camera photo (expect 4), power-of-two invariant, and a very wide image where the short edge bounds the sample.

### Deliberately not changed
- Per-keystroke prefs writes (seller name, backup password, username pattern) follow an existing app-wide convention; the values are tiny and encrypted-prefs writes are cheap here.
- Backup password stays in encrypted prefs; the plaintext-backup warning already explains the token exposure.
- Restored accounts do not hot-swap the running session (the current token stays valid); `PanelCache.clear()` removes stale visuals and the restored active account applies on next launch.

## Round 2026-10-21 (6) — Statistics/Nodes screen deep audit

Scope approved by the user: N1–N4, N6, N7 plus widget localization (W1) and the
initial-loading skeleton (I-stat). N5 (targeted refetch instead of reloading all
six requests on every filter change) was explicitly deferred as regression-prone.
Panel facts were re-verified against `PasarGuard/panel` `app/routers/node.py`
(v5.4.1): reconnect requires `nodes.reconnect`; realtime stats and node usage
require `nodes.stats`; the node list requires `nodes.read`.

### N1. Reconnect button shown to roles without `nodes.reconnect`, and its failures were silent
- The button rendered for every `error`/`connecting` node although `POST /api/node/{id}/reconnect` is gated by `nodes.reconnect`; a tap by an unauthorized role always 403'd, the error was swallowed by `runCatching`, and `load(true)` ran unconditionally — even after failure.
- **Fix**: the button is now gated with `AdminAccess.can("nodes", "reconnect")`; failures surface `AppFeedback.error(...)` with a friendly message plus technical detail, and the full reload only runs `onSuccess`. The `reconnecting` per-node state is preserved. No new strings.

### N2. Two concurrent `GET /api/nodes` requests on first entry
- A standalone `LaunchedEffect(session)` fetched the node list while `load()` fetched the same endpoint again on entry.
- **Fix**: the standalone effect was removed; `load()` is the single path and writes both the node cache and dependent stats together.

### N3. "No live data" label shown while the first realtime response was still in flight
- `live == null` rendered `st_node_unreachable` immediately on initial load, before any `realtime_stats` response had arrived.
- **Fix**: a `nodeStatsLoaded` flag distinguishes "not answered yet" (small neutral spinner) from "answered but this node's key is absent/null" (the unreachable label). The node's own `message` still takes precedence when present.

### N4. Zero-traffic ring rendered as 100% upload
- `downShare` defaulted to 0 when total traffic was 0, so the second segment `1f - downShare` filled the whole ring and the upload card showed 100%.
- **Fix**: new pure top-level helper `trafficShares(incoming, outgoing): Pair<Float, Float>` in `RingChart.kt` returns `(0f, 0f)` when total ≤ 0 and proportional shares otherwise; both ring segments and both stat cards now consume it. Covered by a pure JVM test (`TrafficSharesTest`: zero, download-only, upload-only, 75/25 split, shares-sum-to-one).

### N6. Guaranteed 403 realtime-stats request for roles lacking `nodes.stats`
- `nodeRealtimeStats()` was called unconditionally although the endpoint requires `nodes.stats`.
- **Fix**: the call is gated by `canNodeStats`; without permission the map stays empty and the row shows the node's own `message` (or nothing) instead of a misleading "no live data" label. `nodeUsage` already had this gate.
- **Follow-up (not changed, needs a separate decision)**: `DashboardScreen` (and the background `MonitoringWorker`) call `nodeOnlineStates()`, which hits the same permission-gated endpoint; failures there are already swallowed by `runCatching`, so the impact is only a wasted 403 per refresh — documented here rather than silently expanding the closed dashboard round.

### N7. Transient node-list failures looked like "this panel has no nodes"
- `PanelApi.nodes()` wrapped everything in `runCatching` and returned `emptyList()` for *any* failure, so a network hiccup rendered `st_node_none`.
- **Fix**: `nodes()` now mirrors the dashboard D3 pattern — HTTP 403 maps to an empty list (a role without `nodes.read` genuinely sees none), while every other failure throws. The screen keeps its cached list on failure and only shows an error line (separate `nodesError` state, danger color) when it has no list to show; the "no nodes" string is reserved for a genuinely empty successful response.

### W1. English hard-coded relative time inside the Persian widget layout
- `PanelWidgetProvider.formatRelativeTime` built `"now"` / `"Nm ago"` / `"Nh ago"` literals even inside the localized `wg_updated` template.
- **Fix**: three new strings added to both locales in lockstep — `wg_rel_now`, `wg_rel_minutes` (`%1$d`), `wg_rel_hours` (`%1$d`). The daily `MM/dd HH:mm` fallback and the Latin uptime formatter are untouched.

### I-stat. Centered spinner on first load caused layout jump
- The initial-load branch showed one `CircularProgressIndicator` in a 160 dp box, which was replaced by the full card stack on arrival.
- **Fix**: it now renders three `SkeletonCard`s (the existing internal shimmer component, same package; honors reduce-motion) matching the real card rhythm, reserving the layout and removing the jump. No strings added.

### Deliberately not changed
- N5: every filter change intentionally reruns the full `load()`; splitting requests per filter needs its own regression pass and was excluded by the user.
- Dashboard/worker `nodeOnlineStates` permission gate (see N6 follow-up).
- `formatUptime` Latin digits and the 9-second `goAsync` widget budget — both audited healthy.

## Round 2026-10-21 (7) — Notifications subsystem deep audit

Scope approved by the user: F1–F5 all fixed; cross-engine dedup via unified IDs
**and** a shared `SessionStore` baseline; a new pure `NotificationIds` object with
a JVM test; the mute Toast kept (F6, no in-app feedback channel exists from a
background receiver). F7 (cancel worker on logout) and F8 (explicit goAsync
deadline) were not selected and remain unchanged. No new strings were needed —
all 66 notification keys already exist in both locales.

### New shared component: `NotificationIds`
- Pure (no Android dependency) object in `utils/NotificationIds.kt`, the single source of truth for every notification ID: fixed health/lifecycle constants (5101–5106), hash-typed node IDs (`node_offline_<id>` / `node_online_<id>`), per-user event IDs, user-action IDs, and debtor IDs.
- Node IDs abandon numeric bands entirely: the D9 band scheme (4100/4300, then 6100/6200) could still collide when node ids were spaced by the band gap; typed hashes have no such failure mode regardless of id magnitude.
- Covered by `NotificationIdsTest` (pure JVM): constant stability, shared string-scheme parity, per-kind/per-user uniqueness, node collision cases including large spaced ids, debtor parity, deterministic action IDs.

### F1. Capacity alert was silently gated by the system-health switch
- Both engines evaluated the online-capacity alert inside the `notifySystemHealth` branch, although Settings exposes `notifyCapacity` as an independent switch with its own stepper. Turning health off killed capacity alerts too.
- **Fix**: the capacity check now runs in its own `notificationsEnabled && notifyCapacity` block in both `MonitoringWorker` and `DashboardScreen.evaluateHealth`, keeping its own latch/flag (`capacity`).

### F2. Foreground user alerts lacked the mute action and used a mismatched near-expiry ID
- `UsersUiState.notify()` posted without `kind`, so foreground-originated alerts had no mute action while identical worker-originated alerts did; its near-expiry key was `"near_expire"` vs the worker's `"near_expiry"`, producing two shade entries for one event.
- **Fix**: every foreground call passes the matching `MUTE_TARGETS` kind and all IDs come from `NotificationIds.userEvent(kind, userId)`; the key is now `near_expiry` everywhere.

### F3. Node online/offline could notify twice (dashboard vs worker)
- Two baselines (in-memory `lastNodeStates` vs persisted `node_states`) and disjoint ID bands meant a transition observed while the app was open could alert from both engines.
- **Fix (full unification)**: both engines now use `NotificationIds.nodeOffline/nodeOnline`; the dashboard keeps its D4 per-session silent-first-load memory but writes every fetched state through to `SessionStore.saveNodeStates`, so the next worker tick reads the same baseline and cannot re-alert. The dashboard call is additionally gated by `AdminAccess.can("nodes","stats")`, closing the N6 follow-up documented in Round (6).

### F4. Overdue-debtor auto-disable used two different notification IDs
- Foreground sweep used `debtor_overdue_<name>`, the worker `debtor_<name>`; a near-simultaneous race could post two notifications.
- **Fix**: both call `NotificationIds.debtorOverdue(username)`; the new-debtor event also centralizes on `NotificationIds.debtorRegistered`. The shared `autoDisabled` flag already prevented double actions.

### F5. Panel-offline and system-health alerts could duplicate across engines
- Dashboard used IDs 3101–3105 and an in-memory panel-offline latch; the worker uses 5101–5106 and a persisted latch. Same event, two shade entries.
- **Fix**: all IDs converge on `NotificationIds` constants (same channel + same ID means the second post replaces, never stacks). The panel-offline latch is now the shared persisted `alert_panel_offline` flag (dashboard resets it on successful fetch and on account switch via a `LaunchedEffect(session.baseUrl)`, preserving the D4 cross-account guarantee). Health latches stay per-session in memory: unified IDs already collapse duplicates, and keeping them out of the global prefs avoids cross-account latch contamination.

### Deliberately not changed
- F6: `NotificationActionReceiver` Toast — from a background receiver there is no composition/AppFeedback host; behavior and wording stay as-is.
- F7: periodic work still no-ops after logout (`read() ?: success`); cancellation on logout was not approved.
- F8: receiver `goAsync` relies on the system deadline; EncryptedSharedPreferences I/O is fast and no explicit timeout was added.
- `MonitoringWorker` cannot use `AdminAccess` (its process may have no in-memory RBAC state); it keeps the `runCatching` swallow for a possible 403 while the UI engine is now permission-gated.

## Round 2026-10-21 (8) — Invoice subscription start date after renewal

User-reported bug: after renewing a user, the invoice "Starts" row still showed the
account's original creation date, and "Duration" spanned creation→expiry instead of
the newly purchased period (e.g. a renewed account showed "4 months 17 days" for a
56-day renewal). Approved scope: local renewal-date tracking, all three in-app
expiry-change paths record it, no backup inclusion, pure helper with JVM tests.

### Root cause
- The app's own renewal flows set `expire = today + N days`, so the new billing period starts on the renewal day, but `InvoiceDialog` and `PdfInvoiceGenerator` both derived the start from `user.createdAt`. Marzban's user model has no renewal-date field and `created_at` never changes on renewal, so the panel cannot provide this date; it has to be recorded locally.

### INV1. New pure period calculator
- `utils/InvoicePeriod.kt`: `computeInvoicePeriod(createdAtIso, expireIso, renewalIso, today)` returns start/end/duration with start priority **renewal → created_at → today**; unlimited expiry yields null end and zero duration (UI renders "unlimited"). Both invoice engines now call it, deleting the duplicated inline date math.
- Pure JVM `InvoicePeriodTest` (7 cases): renewed user uses renewal date with exact purchased duration, fresh user falls back to created_at, both-missing falls back to today, null/`"0"` expiry is unlimited, negative spans clamp to zero, full ISO timestamps reduce to the local calendar day.

### INV2. Local renewal registry
- `SessionStore.readRenewalDate/saveRenewalDate` keep a JSON map (`renewal_dates`) keyed `baseUrl|userId` → ISO date, following the existing node-states JSON pattern. Keyed with the panel URL so identical user ids across panels never collide. Per the approved decision it is treated as transient state and is not serialized into backups (restores simply fall back to `createdAt`).

### INV3. Recording on every in-app expiry change
- Both dedicated renewal flows (`ResetExpiryDurationDialog` confirm and the user-details quick action, both `expire = today + N`) record `today` only **after** `modifyUser` succeeds, so a failed API call never poisons the date.
- The full editor records today too when it actually sends a new expiry (`iso != null`); "keep current expiry" saves write nothing.
- Not recorded: `bulkAddDays` (extends the existing period rather than restarting it) and panel/web-side renewals (no data available) — both fall back to `createdAt`, i.e. the previous behavior.

### INV4. Invoice engines consume the registry
- `InvoiceDialog` receives `baseUrl`, reads the recorded date via `remember(user.id, baseUrl)`, and passes it to `PdfInvoiceGenerator.generate(renewalIso = …)`; preview card, text invoice and PDF therefore show identical dates/durations. Unused `LocalDate`/`ChronoUnit`/`DateLogic` imports removed from both engines.
- Also closes the long-open audit item #8: the misleading "today" fallback is now the last resort of an explicit, tested chain instead of the only option when `createdAt` is missing.

### Known limitation
- Renewals performed outside this app (panel web UI, another device/install) are not in the local registry; those invoices keep using `createdAt`. No server-side field exists to close this gap on Marzban.
- No new strings: the existing "Starts"/`inv_start` label keeps its meaning (start of the billed period).

## Testing Checklist

- [ ] Create template with Persian digits ۵۰ GB, ۳۰ days -> should parse as 50 and 30, not unlimited
- [ ] Bulk create with Persian ۵۰ -> canStart true, creates correctly
- [ ] Backup without password -> file contains tokens, inspect shows accountsCount >0, restore restores accounts
- [ ] Backup with password -> encrypted, inspect with wrong password fails with bk_encrypted_long
- [ ] Panel with >200 groups (mock) -> groups() returns all via pagination
- [ ] User online_at = "2024-01-15 10:30:00" without Z, within 5 min -> isOnline true
- [ ] Users page with group filter -> attachGroupNames works even when >200 groups
- [ ] Bulk add/remove group -> success snack appears only after the server confirms; group badge updates without waiting 60 s (U1/U2)
- [ ] Expired cleanup confirm -> snack fires only on success; airplane-mode failure shows the error snack, not "deleted" (U1)
- [ ] Airplane mode with overdue debtors on the users list -> no disable requests fire; after reconnect the row flips to disabled immediately (U3)
- [ ] Local filter (debtor/near-limit) offline with cached list -> online stat card shows cached online count, not 0 (U4)
- [ ] Bulk add days/data dialog -> field shows the days/GB hint (not "Search"), no magnifier, accepts minus and Persian digits (U5)
- [ ] Paginated list with totalMatches > loaded -> select-all shows the limited-selection info snack once (I1)
- [ ] Pick a 12MP+ gallery image as invoice logo -> no crash/OOM, saved logo is 400px (S1)
- [ ] Simulate SAF permission denial on backup folder -> error snack, app stays open and folder still works until restart (S5)
- [ ] Mark a new debtor with notifyDebtor on -> events notification posted; editing the same debtor -> no new notification (S4)
- [ ] Restore a backup inside the app -> theme/monitoring/app-lock/UI prefs update immediately without restart (S3)
- [ ] Corrupt JSON file selected for restore inspection -> "invalid/corrupt" error, not "encrypted file" (S8)
- [ ] Kill the backup worker's output stream (mock) -> zero-byte doc removed, last-backup state not marked success, work retries (S2)
- [ ] Fail a manual backup after document creation -> partial/empty file is removed from the SAF folder (S9)
- [ ] Role without nodes.reconnect -> no reconnect button on error/connecting nodes; authorized role sees it, failure shows error snack and no reload, success reloads (N1)
- [ ] Cold open of Statistics -> exactly one GET /api/nodes, no duplicate request (N2)
- [ ] Slow network on first entry -> node rows show a small spinner, never st_node_unreachable before the first realtime response; unreachable label appears only after a response lacking that node (N3)
- [ ] Fresh panel with zero traffic -> traffic ring shows only the background track and upload card shows 0%, not 100% (N4)
- [ ] Role without nodes.stats -> no realtime_stats/usage requests fire; rows show the node's own message rather than "no live data" (N6)
- [ ] Airplane-mode refresh with an empty node list -> danger-colored error line, not st_node_none; with a cached list -> cached list stays (N7)
- [ ] 403 on GET /api/nodes -> empty list and st_node_none, no error line (N7)
- [ ] Widget in Persian locale -> "همین حالا" / "N دقیقه پیش" / "N ساعت پیش"; English locale -> "just now" / "N min ago" / "N h ago"; older than 24 h still MM/dd HH:mm (W1)
- [ ] First entry -> three shimmer skeleton cards instead of a centered spinner, no layout jump when data lands (I-stat)
- [ ] Unit: TrafficSharesTest green in CI (zero, download-only, upload-only, 75/25, sum-to-one) (N4)
- [ ] System health OFF + capacity ON, cross the online-user limit -> capacity notification still arrives on both foreground refresh and worker tick (F1)
- [ ] Foreground-triggered limited/expired/near-limit/near-expiry notification -> shows the Mute action like worker ones; muting flips the matching Settings switch (F2)
- [ ] Near-expiry transition while app is open -> exactly one notification in the shade, never two ("near_expire" vs "near_expiry" regression) (F2)
- [ ] Node goes offline while dashboard is open and a worker tick overlaps -> single notification; subsequent worker tick does not re-alert (shared node_states baseline) (F3)
- [ ] Role without nodes.stats on dashboard -> no realtime_stats request is fired from the dashboard (F3/N6)
- [ ] Overdue debtor auto-disabled from foreground sweep while worker is due -> one notification with one ID; both code paths use NotificationIds.debtorOverdue (F4)
- [ ] Panel unreachable with app foreground then background worker failure -> one panel-offline notification (ID 5104), mute disables notifyPanelOffline; success clears the latch (F5)
- [ ] Switch accounts while panel A is offline -> no suppressed/missing panel-offline alert for account B (latch reset on baseUrl change) (F5/D4)
- [ ] Unit: NotificationIdsTest green in CI (constants, per-kind uniqueness, node hash collisions, debtor parity) (F2-F5)
- [ ] Renew an existing user for 30 days then open the invoice -> Starts = renewal day (not original createdAt), Duration = 30 days, Ends = renewal+30; identical in sheet, preview card, text invoice and PDF (INV1/INV4)
- [ ] Brand-new user, no renewal -> invoice Starts falls back to createdAt exactly like before (INV1)
- [ ] Renewal API call fails (airplane mode) -> no renewal date recorded; invoice keeps the previous start; error snack shown (INV3)
- [ ] Edit user with "keep current expiry" -> renewal record untouched; edit with an explicit new expiry -> today recorded as renewal (INV3)
- [ ] Same user id on two different panels -> renewal records do not cross-contaminate (baseUrl-scoped key) (INV2)
- [ ] Unlimited-expiry user invoice -> duration renders the unlimited label, no negative day counts (INV1)
- [ ] Unit: InvoicePeriodTest green in CI (renewal priority, createdAt fallback, today fallback, unlimited, clamp, timestamp parsing) (INV1)
