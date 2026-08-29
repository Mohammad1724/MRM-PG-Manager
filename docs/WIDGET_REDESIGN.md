# Widget Redesign — v0.7.0 — Material 3 / PasarGuard API Full Compliance

## Goals
- Fully match PasarGuard SystemStats API (`/api/system`)
- Android widget guidelines: Material You, resizing, previewLayout, offline handling, deep links
- Obsessive detail: every field visible when space allows, graceful degradation on small sizes

## API Coverage (SystemStats model)
```
uptimeSeconds, memTotal/memUsed, diskTotal/diskUsed, cpuCores/cpuUsage,
totalUsers, onlineUsers, activeUsers, expiredUsers, limitedUsers,
disabledUsers, onHoldUsers, incomingBandwidth, outgoingBandwidth
```
+ local debtors (SessionStore per baseUrl) + host extraction from baseUrl

## Layouts
- `widget_panel.xml` (legacy < S): 16dp radius, 12dp padding
- `widget_panel_material.xml` (Android 12+): 28dp radius per M3, 16dp padding, 1dp stroke #14FFFFFF
- `widget_panel_preview.xml`: static preview for widget picker (shows Online/Total/Active + Exp/Lim/Debtors)
- All layouts share same view IDs for single RemoteViews builder

### Tile System
- `widget_tile_bg`: #33FFFFFF 16dp
- `widget_tile_accent_bg`: #33F9D949 (PasarGuard yellow accent) for Online tile
- `widget_badge_offline_bg`: #E53935 pill
- `widget_progress_fill`: yellow #FFF9D949 over #33FFFFFF track
- `widget_divider`: #14FFFFFF 1dp

### Rows & Resizing (obsessive)
Root vertical LinearLayout with 5 rows, each with ID for visibility control:

1. **w_row_main** (always): Online / Total / Active — 3 tiles weight 1
2. **w_row_status2** (minHeight >=140dp): Expired / Limited / Disabled
3. **w_row_status3** ( >=170dp): OnHold / Debtors / Traffic total
4. **w_row_resources** ( >=210dp): CPU / RAM / Disk with ProgressBar (max 100)
5. **w_row_traffic** ( >=260dp): ↓ incoming ↑ outgoing + cores

`PanelWidgetProvider.onAppWidgetOptionsChanged` rebuilds on resize.
`applySizing()` reads OPTION_APPWIDGET_MIN_HEIGHT and sets GONE/VISIBLE.

Width handling: if minWidth <200dp hide cores text.

## Provider Logic (PanelWidgetProvider.kt)

### Host extraction
`extractHost(baseUrl)`:
- URI parsing, fallback to stripping https://, port, path
- Truncate >32 chars with ellipsis

### Formatting
- `formatBytes`: B/KB/MB/GB/TB with 0-1 decimal
- `formatUptime`: d h / h m / m / s from seconds
- `formatRelativeTime`: now / Xm ago / Xh ago / MM/dd HH:mm wrapped in `wg_updated`

### Offline detection
- No session -> host = Unknown, badge "No session", all "-"
- No cache -> badge "Offline", updated "No data"
- Cache age >30min (OFFLINE_THRESHOLD_MS) -> badge "Offline" visible, still shows last data
- Otherwise badge GONE

### Data binding
- Online/Total/Active/Expired/Limited/Disabled/OnHold from SystemStats
- Debtors from SessionStore.readDebtors filtered by baseUrl
- Traffic total = incoming+outgoing
- RAM % = memUsed*100/memTotal, Disk % similarly, CPU % = cpuUsage
- ProgressBars via `setProgressBar(id,100,pct,false)`
- Cores via `wg_cores` string

### PendingIntents (unique per widgetId)
base = widgetId*100
- w_root (1): open dashboard (DEST_DASHBOARD)
- w_tile_online (2), w_tile_total (3), w_tile_active (4): open users (DEST_USERS)
- w_tile_debtors (5): open users
- w_refresh (10): ACTION_REFRESH broadcast with EXTRA_APPWIDGET_ID
- If no session, refresh opens launch intent instead

All intents FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE, unique requestCode to avoid collision.

### Refresh
`onReceive ACTION_REFRESH`: goAsync + IO coroutine
- Reads session, calls PanelApi.systemStats, saves cache, then updateAll()
- On failure keeps old cache (offline badge will show)

### Update
`onUpdate`: per widgetId build with its own options -> supports different sizes on same home screen
`updateAll()`: used after refresh and by app (e.g. after dashboard fetch) — loops ids with fresh cache

## Widget Info (panel_widget_info.xml)
- minWidth 250dp (was 180), minHeight 110dp (spec)
- target 3x2, maxResize 400x380
- previewLayout = @layout/widget_panel_preview
- widgetFeatures = reconfigurable|configuration_optional
- updatePeriod 30min (1800000) + manual refresh

## Strings
New keys in values/values-fa:
- wg_active, wg_total_users, wg_cpu/ram/disk/traffic, wg_offline, wg_no_session, wg_uptime, wg_expired/limited/on_hold/disabled, wg_traffic_total, wg_host_unknown, wg_open_users/dashboard, wg_tap_refresh, widget_preview_desc

## Deep Links
Uses NotificationHelper.EXTRA_DEST (mrm_dest) with DEST_DASHBOARD / DEST_USERS
MainActivity.pendingDeepLink consumes it, selects tab, opens user detail if needed

## Testing checklist
- [ ] Add widget on Android 12+ -> 28dp corners, preview shows
- [ ] Resize to 3x2 min -> only Online/Total/Active + host + updated
- [ ] Resize to 3x3 -> Exp/Lim/Dis + Hold/Debtors/Traffic appear
- [ ] Resize to 3x4 -> CPU/RAM/Disk bars appear
- [ ] Resize to 4x4 -> bandwidth details + cores appear
- [ ] No session -> "Unknown panel" + "No session" badge
- [ ] Offline (airplane, old cache) -> red "Offline" badge, last data still visible
- [ ] Tap root -> dashboard, tap online/total/active/debtors -> users tab
- [ ] Tap refresh -> fetches /api/system, updates timestamp, hides offline badge if success
- [ ] Host extraction: https://panel.example.com:2096/dashboard -> panel.example.com

## Future improvements
- Light theme widget variant using ?android:attr/colorBackground
- Glance/Compose widget for Android 14+
- Config activity to pick which panel account to show (multi-panel)
