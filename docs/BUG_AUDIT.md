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

- **#8 PdfInvoiceGenerator duration fallback** — still open by design. When `createdAt` is missing the invoice counts the service period from *today*, which understates an older user's real billing period. This needs a product decision (label as "Remaining" vs hide duration vs require createdAt), not a code-only fix.

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

## Testing Checklist

- [ ] Create template with Persian digits ۵۰ GB, ۳۰ days -> should parse as 50 and 30, not unlimited
- [ ] Bulk create with Persian ۵۰ -> canStart true, creates correctly
- [ ] Backup without password -> file contains tokens, inspect shows accountsCount >0, restore restores accounts
- [ ] Backup with password -> encrypted, inspect with wrong password fails with bk_encrypted_long
- [ ] Panel with >200 groups (mock) -> groups() returns all via pagination
- [ ] User online_at = "2024-01-15 10:30:00" without Z, within 5 min -> isOnline true
- [ ] Users page with group filter -> attachGroupNames works even when >200 groups
