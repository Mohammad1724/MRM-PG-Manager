# MRM PG Manager

Android client (Kotlin + Jetpack Compose) for administrating a **PasarGuard** panel from your phone.

Targets PasarGuard panel **v5.x** (tested against the v5.4.1 API). The app relies on v5‑only routes
(`/api/user/by-id/…`, `/api/users/counts/…`, `PUT …/disabled`, `GET /api/admin` RBAC), so older
panels (v3/v4) are not supported.

## Features

- **Sign in** with the dashboard URL and either
  - admin username + password (panel JWT; expires after `JWT_ACCESS_TOKEN_EXPIRE_MINUTES`, default 24 h), or
  - an **API key** (`pg_key_…`, Settings → API Keys in the panel) sent as `X-Api-Key` — never expires, so
    background monitoring keeps running.
- Multiple panels / accounts; when a session expires the account is kept and the login form is prefilled.
- **Dashboard**: system resources, user counters, nodes, admins (when permitted), health alerts.
- **Users**: server‑side search / status / online / group filters, sorting and paging; create (single, bulk,
  from template), edit, disable, reset usage, revoke subscription, next plan, HWID devices, QR / copy
  subscription, invoices, debtor tracking with optional auto‑disable.
- **Bulk actions** on selected users (delete, reset, enable/disable, revoke, add days/data, groups, apply template).
- **Groups** and **User templates** management.
- **Statistics**: traffic charts, per‑metric counters, node realtime stats and reconnect.
- **RBAC‑aware UI**: the admin's role (`GET /api/admin`) hides actions the panel would reject with 403.
- Background **monitoring worker** (limited / expired / near‑limit / near‑expiry users, node offline,
  CPU/RAM/disk, capacity) with an offline cache and a home‑screen widget.
- Encrypted local storage (tokens only — the password is never stored), optional biometric app lock,
  encrypted backup / restore, Persian & English UI, Jalali dates.

## Panel API usage

All requests are HTTPS. Main endpoints: `POST /api/admin/token`, `GET /api/admin`, `GET /api/system`,
`GET /api/users` (+ `search/status/online/group/sort/offset/limit`), `/api/user/by-id/{id}` (get / modify /
delete / disabled / reset / revoke_sub / active_next / usage), `/api/users/bulk/*`, `/api/user/from_template`,
`/api/users/expired`, `/api/user/{id}/hwids`, `/api/groups`, `/api/user_templates`, `/api/nodes`,
`/api/nodes/realtime_stats`, `/api/inbounds`, `/api/admins`.

"Online" uses the panel's own 2‑minute window (`online_at`), and `online=true` is applied server‑side.

## Build

APKs are built by GitHub Actions: open **Actions → Build Android APK** and download the
`MRM-PG-Manager-debug-apk` artifact. The workflow validates resources and Kotlin sources
(`tools/*.py`), runs the unit tests (`app/src/test`, MockWebServer contract tests against the panel API
shapes) and then assembles the debug APK.

Local build: Android Studio (compileSdk 35, minSdk 26) or `./gradlew assembleDebug`.

## Notes

- Use an HTTPS panel address; cleartext HTTP is rejected.
- For background monitoring prefer an API key over a password login.
- Reseller/operator roles see only the actions their permissions allow; the panel remains the authority and
  its error `detail` is shown when a request is rejected.
