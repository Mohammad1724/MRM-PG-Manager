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

- The mobile editor now uses a full-height, scrollable dialog with a fixed header and
  actions, editable basics and priority, separate host enablement and user-status
  filters, a searchable inbound selector, and a searchable Xray subscription-template
  selector. The selectors use the actual `/api/inbounds` (`system.read`) and
  `/api/client_templates/simple` (`client_templates.read_simple`) responses; failures
  remain visible and do not clear existing values. Creating a host requires an inbound;
  manual tag entry is available if the list is empty or cannot be loaded.
- The expandable editors cover network, TLS certificate-verification inheritance,
  ECH, parts of XHTTP/gRPC/WebSocket, Xray and sing-box fragmentation, selected mux
  settings, VLESS routing and WireGuard overrides. This is **not full parity** with
  the web editor: HTTP headers, noise, final masks, additional transport/mux controls,
  and other unsupported fields are not editable here. Untouched values are carried
  forward from the fresh response, subject to the receiving server's behavior.
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

### Verification and limits

- 46 standalone JUnit tests passed using the actual API, models, OkHttp, Okio and
  MockWebServer, including the state/draft/merge suite. An upstream Pydantic model accepted a
  Kotlin-generated request with 19 field assertions, with some dependencies stubbed.
  These are not a real server, device test, or an Android Gradle build.
- Four UI source files were compiled with a temporary Compose compiler setup and
  project dependency stubs; this checks compilation against those approximated
  dependencies, not layout, runtime behavior or the release APK. Compose behavior
  tests were added but have not been run in this environment.
- The full Android build remains unverified here because the Gradle distribution
  download failed during TLS negotiation; no local SDK/emulator was available. Prior
  CI results belong to earlier commits and do not validate this uncommitted editor.
