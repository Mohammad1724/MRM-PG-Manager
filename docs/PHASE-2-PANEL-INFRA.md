# فاز ۲ — مدیریتِ زیرساختِ پنل (نسخهٔ ۰٫۱۱٫۰)

> **پایه:** سورس `PasarGuard/panel` تگ **v5.5.1**
> **راهِ رسیدن:** تنظیمات → تبِ «اتصال و حساب‌ها» → کارتِ «زیرساختِ پنل»
> **تصمیمِ کلان:** این سه بخش **تب**های یک صفحه‌اند، نه سه صفحهٔ مستقل و نه سه
> آیتمِ نوارِ پایین؛ چون کارِ روزمره نیستند و به هم مربوط‌اند.

---

## ۱. چه چیزی اضافه شد

| بخش | مسیرهای پنل | کارهای ممکن در اپ |
|---|---|---|
| **هاست‌ها** | `GET /api/hosts`، `POST /api/host/`، `PUT /api/host/{id}`، `DELETE /api/host/{id}`، `POST /api/hosts/bulk/{enable,disable}` | فهرست + جست‌وجو، ساخت، ویرایش، فعال/غیرفعال (تکی و گروهی از سمتِ API)، حذف |
| **کورها** | `GET /api/cores`، `PUT /api/cores/{id}?restart_nodes=…`، `POST /api/cores/{id}/restart`، `DELETE /api/cores/{id}?restart_nodes=…` | فهرست + جست‌وجو، ویرایشِ نام/نوع/تگ‌های استثنا و فالبک، ری‌استارتِ نودها، حذف |
| **قالب‌های کلاینت** | `GET /api/client_templates`، `POST /api/client_templates`، `PUT /api/client_templates/{id}`، `DELETE /api/client_templates/{id}` | فهرست + جست‌وجو + فیلترِ نوع، ساخت (با انتخابِ نوع)، ویرایشِ نام/محتوا/پیش‌فرض، حذف |

مجوزها از خودِ پنل خوانده می‌شوند (`hosts.*`، `cores.*`، `client_templates.*`) و
دکمه‌های ساخت/ویرایش/حذف بر همان اساس پنهان می‌شوند؛ ولی تصمیمِ نهایی همیشه با
پنل است — ۴۰۳ با `detail` خودش نشان داده می‌شود.

## ۲. سه تصمیمِ طراحی که بقیهٔ کد از آن‌ها پیروی می‌کند

### ۲.۱ ویرایش با merge روی بدنهٔ خام (هاست و کور)

> **Host editor update, 2026-10-28:** The example below describes the retained legacy
> `modifyHost` API. The current host editor instead reads `GET /api/host/{id}` immediately
> before saving and calls `modifyHostSafely`; it merges only deliberate field-path edits
> into that fresh response and refuses to PUT on a read failure or same-field conflict.
> The core editor has not changed. The host GET and PUT are not atomic; another writer
> can still update the host between those requests, and preservation of unrecognized
> properties also depends on the server accepting and storing them.

پنل برای `PUT` **کلِ** بدنه را می‌خواهد. هاست می‌تواند `transport_settings`،
`mux_settings`، `fragment_settings`، `noise_settings`، `ech`، `final_mask_settings`،
`wireguard_overrides` و… داشته باشد؛ کور می‌تواند صدها کیلوبایت کانفیگِ Xray باشد.
اگر فرمِ موبایل فقط فیلدهای خودش را بفرستد، بقیه به پیش‌فرض برمی‌گردند و شیئی که
در پنلِ وب ساخته شده **بی‌سروصدا** خراب می‌شود.

پس:

```kotlin
data class PanelHost(..., val raw: String)   // بدنهٔ کاملِ همان ردیف
data class PanelCore(..., val config: String) // کانفیگِ خام

PanelApi.modifyHost(session, host, mapOf("remark" to …, "port" to JSONObject.NULL))
// → JSONObject(host.raw) + تغییرها؛ مقدارِ null یعنی «کلید را بردار»
```

تستِ `modify host merges into the raw body and keeps unknown fields` دقیقاً همین
را قفل می‌کند: بعد از ویرایش، `transport_settings` و `mux_settings` سرِ جایشان‌اند.

### ۲.۲ «ساختِ کور» در اپ نیست — و دلیلش گفته می‌شود
پنل برای `POST /api/cores` یک `config` کاملِ JSON لازم دارد (inbounds/outbounds/
routing/…). ادیتورِ JSON روی صفحهٔ گوشی، برای کاری که ادمین یک‌بار انجام می‌دهد،
ارزشش را ندارد. در حالتِ خالی، جملهٔ راهنما همین را می‌گوید (نه یک دکمهٔ بی‌اثر).

### ۲.۳ نوعِ قالبِ کلاینت پس از ساخت قفل است
`ClientTemplateModify` در پنل `template_type` ندارد. اپ هم در ویرایش فقط نام/
محتوا/پیش‌فرض را می‌فرستد و به‌جای چیپِ غیرفعال، بجِ نوع + دلیل نشان می‌دهد.

## ۳. نکاتِ قراردادی که اگر از قلم بیفتند همیشه ۴۲۲ می‌دهیم

| مورد | جزئیات |
|---|---|
| `restart_nodes` | در `PUT /api/cores/{id}` و `DELETE /api/cores/{id}` کوئریِ **الزامی** است (بدونِ پیش‌فرض) — همیشه فرستاده می‌شود. |
| `POST /api/host/` | اسلشِ پایانی دارد؛ `/api/host` بدونِ اسلش ریدایرکت می‌شود. |
| `Priority` هاست | در `BaseHost` فیلدِ الزامی است؛ در ساخت مقدارِ `0` فرستاده می‌شود. |
| شکلِ پاسخ | `/api/hosts` آرایهٔ خالص، `/api/cores` پوشیده در `{count, cores}`, `/api/client_templates` در `{count, templates}` — سه شکلِ متفاوت در یک گروه. |
| `address`/`sni`/`host` | در پنل `set[str]` هستند؛ JSON آرایه می‌گیرد و ترتیب تضمینی ندارد. |
| حذفِ قالبِ سیستمی | پنل ۴۰۳ می‌دهد؛ دکمه برای `is_system` اصلاً نمایش داده نمی‌شود. |
| `CoreType` | فقط `xray` / `wg` / `mtproto` / `singbox` (نه `mihomo` و نه `sing-box` با خط تیره). |
| `ProxyHostSecurity` | فقط `inbound_default` / `none` / `tls` — **`reality` روی هاست وجود ندارد**؛ Reality در کانفیگِ inbound/core تعریف می‌شود. |
| `ClientTemplateType` | `clash_subscription` / `xray_subscription` / `singbox_subscription` / `user_agent` / `grpc_user_agent`. |

این چهار enum عمداً در یک `object` در `Models.kt` نشسته‌اند و تستِ
`InfraValidationTest` مقادیرشان را قفل می‌کند، چون یک رشتهٔ اشتباه (مثلاً
`sing-box` به‌جای `singbox`) همیشه ۴۲۲ خامِ pydantic می‌داد.

## ۴. تست‌ها

| فایل | چه چیزی را قفل می‌کند |
|---|---|
| `data/api/PanelInfraApiTest` | مسیر/متد/بدنهٔ هر ۱۲ فراخوانی + حفظِ فیلدهای ناشناخته + `JSONObject.NULL` برای پاک‌کردنِ `port`/`inbound_tag` + شمارشِ `count` در bulk |
| `data/model/InfraValidationTest` | قواعدِ فرمِ هاست (نام/آدرس/پورت) و قالب (نام ≤۶۴، محتوای غیرخالی) + تطابقِ مقادیرِ enum با پنل |

## ۵. آنچه در فازهای بعد می‌ماند

1. ~~**API Keys** (`/api/api_key`): ساخت/ابطال~~ → انجام‌شده در فاز ۶ (`docs/PHASE-6-PANEL-OPS.md` §۳).
2. ~~**Settings پنل** (`/api/settings/*`): فعلاً فقط `general` خوانده می‌شود~~ → در فاز ۵.۱/۶ چهار تبِ `general`/`subscription`/`hwid`/`notification_settings` شد (`docs/PHASE-6-PANEL-OPS.md` §۲).
3. **Admin Roles** (`/api/admin_role`) و مدیریتِ ادمین‌ها.
4. **ورود با Passkey** — WebAuthn روی موبایل، کارِ مستقل.
5. **ساختِ کور** با ادیتورِ JSON (اگر واقعاً لازم شد).
6. ترتیبِ کورها (`PUT /api/cores/order`) — همان دیالوگِ ترتیبِ نودها قابلِ استفادهٔ مجدد است.

## Host editor update (2026-10-28)

- The mobile editor uses a bottom sheet with a scrollable body, fixed header and
  actions, editable basics and priority, separate host enablement and a multiselect
  user-status dialog, a searchable inbound selector, and a searchable Xray
  subscription-template selector. The selectors use the actual `/api/inbounds` (`system.read`) and
  `/api/client_templates/simple` (`client_templates.read_simple`) responses; failures
  remain visible and do not clear existing values. Creating a host requires an inbound;
  manual tag entry is available if the list is empty or cannot be loaded.
- The expandable editors cover network (including HTTP header name/value pairs), TLS
  certificate-verification inheritance, ECH, parts of XHTTP/gRPC/WebSocket, Xray and
  sing-box fragmentation, selected mux settings, VLESS routing and WireGuard overrides.
  This is **not full parity** with the web editor: noise, final masks, additional
  transport/mux controls, and other unsupported fields are not editable here. Untouched
  values are carried forward from the fresh response, subject to the receiving server's
  behavior.
- HTTP headers use the panel's `dict[str, str] | null` contract. Edits validate token
  names, case-insensitive duplicates and control characters; removing every entry sends
  null. The entire header map is a conflict unit so a concurrent addition or deletion
  prevents a guarded PUT instead of being overwritten. Unsupported stored formats are
  read-only and preserved. Header values remain in the in-memory draft and are redacted
  from the new header object's string representation; they are not saved in a Bundle.
- Dismissing a dirty bottom sheet by close, cancel, back, scrim or drag is intended to
  request confirmation, and dismissal is blocked while saving. The visible close surface
  is 32dp inside a 48dp touch target. The searchable status dialog applies selections
  immediately and preserves unknown values until explicitly removed or cleared; no
  selection means all user statuses.
- `allowinsecure` is tri-state: null inherits from the inbound, false verifies
  certificates, and true disables verification. Priority defaults to zero on creation;
  smaller numbers normally come first, but randomized ordering may override that.
- An in-memory Activity-scoped ViewModel is intended to retain the target, draft, and
  in-flight save across Activity recreation and prevent duplicate submissions. State
  transitions have unit coverage, but Activity recreation has not been tested on a
  device. The draft is not saved to a Bundle and will not survive process death. A
  response from an older editor or another account cannot update the current state.
- Xray mux output uses `xudpConcurrency` and `xudpProxyUDP443` while this upstream
  request model expects `xudp_concurrency` and `xudp_proxy_udp_443`. The guarded PUT
  includes compatible aliases and rejects conflicting pairs rather than guessing.

### UI consistency follow-up (2026-10-28)

- Host and user form fields now share the search-field surface and focus treatment;
  technical host values and template content are displayed left-to-right. Host status,
  advanced host choices, and user-editor status/next-plan choices use the same
  searchable dialog shell as inbound selection. Toolbar and row action menus remain
  anchored menus, with a shared theme-matched surface. Existing custom or unknown next
  plans remain unchanged unless the administrator explicitly chooses a replacement.
- Compact infrastructure tabs retain a 48dp target while the selected surface is
  inset; the More and Refresh icons use the same visual and touch dimensions.
- Backup and restore password fields now mask their contents. Backup settings also
  keep the complete entered password in both the form and persistent preferences,
  avoiding different passwords for manual and scheduled backups after a long paste.
  This does not change the existing backup format or add authentication to AES-CBC.
- For this follow-up, 55 existing standalone draft/state/API tests and four new pure
  choice-filter and password-transformation tests passed in the temporary JVM harness.
  Resource, contrast, widget and Kotlin static validators passed. In Android CI, the
  focused editor tests passed, and the full suite at `a88c8f9` had one remaining
  failure at the outer corner of a compact tab touch target. The tab
  container now clips drawing rather than pointer hit testing; the PR checks provide
  the result for the current commit.
- Robolectric could not reliably idle a native choice dialog window, including with a
  paused test clock and explicit drawing. Host tests therefore render the same dialog
  content through a test window host, and the debtor test renders its extracted content.
  They exercise selection and save behavior but do not verify actual platform-window
  back, focus or keyboard behavior. Production still uses the Android dialog.
- Four SHA-256-verified screenshots from `c4398c7` were inspected: English and large
  Persian RTL infrastructure tabs, the English Network section, and the dark Persian
  status selector at 320 × 480 dp and 1.8× text. The clear label wraps on the small
  screen. The Network screenshot predates the subsequent input touch-bounds fix. These
  synthetic captures do not replace real-device, IME, TalkBack, real-panel, or broader
  language and theme verification.

### Verification and limits for the preceding host-sheet work

- The bottom-sheet and HTTP-header changes have 55 passing standalone JVM tests across
  draft, state, merge, API and infrastructure, using the actual API implementation and
  MockWebServer with a temporary compiler harness. Kotlin-generated HTTP-header map
  and null-deletion payloads were also accepted by the upstream `CreateHost` Pydantic
  model with some dependencies stubbed. The Compose UI sources also compile locally
  in the harness with dependency stubs; those checks are not a full Android build.
- [PR CI run 40645788030](https://github.com/Mohammad1724/MRM-PG-Manager/actions/runs/40645788030)
  and [push CI run 40645780918](https://github.com/Mohammad1724/MRM-PG-Manager/actions/runs/40645780918)
  passed the complete Android unit suite, including the new Compose/Robolectric sheet,
  dropdown, draft-protection and header tests, and built debug and signed release APKs
  at `663965d`. The [signed release APK](https://github.com/Mohammad1724/MRM-PG-Manager/actions/runs/40645788030/artifacts/12797722101)
  is available for device testing.
- [PR CI run 40648098243](https://github.com/Mohammad1724/MRM-PG-Manager/actions/runs/40648098243)
  and [push CI run 40648093044](https://github.com/Mohammad1724/MRM-PG-Manager/actions/runs/40648093044)
  also passed at `b895100`, including a new check that the captured images are not blank.
  The [Robolectric screenshots](https://github.com/Mohammad1724/MRM-PG-Manager/actions/runs/40648098243/artifacts/12798521844)
  could not be downloaded directly in this sandbox. A temporary CI diagnostic enabled
  inspection of the full Persian RTL, dark-theme, 320 × 480 dp image with 1.8× text and
  a long error: both actions and the close icon were visible. Only the upper 767 of 800
  pixels of the English image could be recovered because a diagnostic annotation was
  masked; its footer was not fully inspected visually. These synthetic screenshots are
  not a substitute for observing the real app or its keyboard.
- The first sheet CI runs failed on an invalid test-only `onNode` import. The import
  validator was corrected and regression-tested while preserving the UI assertions;
  a superseded push run was cancelled by the workflow's concurrency policy.
- Before the bottom-sheet change, 46 standalone JUnit tests passed using the actual API, models, OkHttp, Okio and
  MockWebServer, including the state/draft/merge suite. An upstream Pydantic model accepted a
  Kotlin-generated request with 19 field assertions, with some dependencies stubbed.
  These are not a real server, device test, or an Android Gradle build.
- [PR CI run 40636160519](https://github.com/Mohammad1724/MRM-PG-Manager/actions/runs/40636160519)
  and [push CI run 40636153723](https://github.com/Mohammad1724/MRM-PG-Manager/actions/runs/40636153723)
  passed `testDebugUnitTest`, including the new Robolectric/Compose editor tests and
  existing infrastructure tests, and built debug and signed release APKs at `1d097cf`.
  The [signed release APK](https://github.com/Mohammad1724/MRM-PG-Manager/actions/runs/40636160519/artifacts/12794026158)
  is available for device testing.
- Initial CI failures exposed a Compose test compilation mistake, missing test-only
  preferences setup, and a regression in the shared inbound-cache refresh. Those were
  corrected without removing the existing assertions. Local Gradle download still
  fails during TLS negotiation, and no local SDK/emulator is available.
- The Compose checks exercise selected controls in Robolectric; they are not visual
  sign-off or real-device validation. Activity recreation, keyboard/insets, TalkBack,
  real-panel behavior, the non-atomic GET/PUT race and `lintDebug` remain unverified.
