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

### 7. userCountMetric uses maxOf instead of sum
- **File**: `data/api/PanelApi.kt:200` `totals[time] = maxOf(totals[time] ?: 0L, v)`
- **Root cause**: For trafficUsage sum is used, but for count metric max is used. If panel returns per-node counts, sum is needed for total online. If panel returns aggregated total per key, max avoids double counting. Behavior ambiguous, may underreport online chart on multi-node setups.
- **Recommendation**: Verify panel's `/api/users/counts/{metric}` response shape. If stats keys are node_ids, should sum. If keys are shards of same aggregated value, max is correct. Add comment and consider making it sum with dedup, or keep max but document.
- **Current**: Left as max with comment, flagged for further verification against PasarGuard panel source (app/routers/users.py counts endpoint).

### 8. PdfInvoiceGenerator duration fallback misleading
- **File**: `utils/PdfInvoiceGenerator.kt:150-180`
- **Root cause**: When `createdAt` missing, `effectiveStartDate = LocalDate.now()`, duration = remaining days, not total. Invoice shows "Duration: 5 days" for old user whose total was 30 days but only 5 remaining. Misleading for billing.
- **Fix recommendation**: If createdAt null, show start as unknown or show remaining as duration with label "Remaining", or don't show duration. Current fallback shows today as start, which is wrong for old users.
- **Impact**: Medium - financial document inaccuracy.

### 9. Export CSV newline handling
- **File**: `utils/ExportUsers.kt:8` `esc` only escapes quotes, not newlines
- **Root cause**: Note field may contain newline, CSV with newline inside quoted field is valid but Excel may break, and our `appendLine` will create extra lines.
- **Fix recommendation**: Replace newline with space in esc: `s.replace("\"", "\"\"").replace("\n"," ").replace("\r"," ")`

### 10. SessionStore users cache always offline
- **File**: `data/storage/SessionStore.kt:180` `isOnline = false` on restore
- **Root cause**: Offline cache restores all users as offline, losing last online state. `onlineAt` preserved, but `isOnline` false, so `lastSeenText` will show "X minutes ago" even if user was online at cache time.
- **Impact**: Low-medium, offline banner shown, but online indicators lost.
- **Recommendation**: Store isOnline flag in cache JSON as well, or compute isOnline from onlineAt timestamp at restore time (if within 5 min, mark online).

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

### 14. JalaliCalendar faNum unused, lastSeenShort not localized
- **File**: `utils/JalaliCalendar.kt:130-140`
- **Root cause**: `faNum` converts to Persian digits but `lastSeenShort` returns English short format even in Persian locale ("4h" not "۴h"). Inconsistent with `lastSeenText` which does Persian.
- **Fix**: Make short also Persian when locale fa.

### 15. SubscriptionCard cache pruning only 1 hour
- **File**: `utils/SubscriptionCard.kt:180` deletes files older than 1 hour
- **Root cause**: If user generates many cards within hour, they accumulate in cacheDir/shared, could fill storage.
- **Recommendation**: Keep 1 hour but also limit count (e.g., keep 20 most recent).

### 16. GroupsScreen duplicate check case-insensitive may be overly strict
- **File**: `ui/screens/GroupsScreen.kt:350` `equals(..., ignoreCase=true)`
- **Root cause**: Panel may allow "Test" vs "test" as different? Usually panel treats names case-insensitively? Overly strict prevents creation that panel would allow, but safer.
- **Impact**: Low.

### 17. Template summary shows integer GB via division, loses fractional
- **File**: `ui/screens/TemplatesScreen.kt:330` `${it / BYTES_PER_GB} GB`
- **Root cause**: If dataLimit is 1.5 GB (1610612736 bytes), integer division shows 1 GB.
- **Fix**: Use formatBytes or `%.1f` GB.

## Summary of Fixes Applied in this Audit

- TemplatesScreen.kt: Persian digits normalization for dataGb, days, hwid, onHoldDays (filter + parsing)
- BulkCreateUsersDialog.kt: canStart Persian normalization
- PanelApi.kt: groups() pagination, groupsDetailed() pagination, admins() pagination, online_at naive datetime parsing
- BackupManager.kt: includeTokens always true to match warning and enable restore, robust JSON detection for inspect/restore

## Remaining TODO (not yet fixed, needs decision)

- PdfInvoiceGenerator duration fallback
- Export CSV newline
- userCountMetric max vs sum verification against real panel
- SessionStore cache isOnline flag
- JalaliCalendar short localization
- Template summary fractional GB

## Testing Checklist

- [ ] Create template with Persian digits ۵۰ GB, ۳۰ days -> should parse as 50 and 30, not unlimited
- [ ] Bulk create with Persian ۵۰ -> canStart true, creates correctly
- [ ] Backup without password -> file contains tokens, inspect shows accountsCount >0, restore restores accounts
- [ ] Backup with password -> encrypted, inspect with wrong password fails with bk_encrypted_long
- [ ] Panel with >200 groups (mock) -> groups() returns all via pagination
- [ ] User online_at = "2024-01-15 10:30:00" without Z, within 5 min -> isOnline true
- [ ] Users page with group filter -> attachGroupNames works even when >200 groups
