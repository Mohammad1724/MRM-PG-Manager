# سازگاری با PasarGuard 5.5.1 — نسخهٔ ۰٫۱۰٫۰

> **دامنه:** کل اپ — لایهٔ API، مدل‌ها، UI، ورکر پس‌زمینه، مستندات
> **پایه:** سورس `PasarGuard/panel` در تگ **v5.5.1** (تطبیقِ خط‌به‌خطِ روترها/مدل‌ها)
> **تصمیم کارفرما:** «اپ فقط از 5.5.1 به بالا پشتیبانی شود» + «دروازهٔ نسخه در UI باشد»

---

## ۱. بررسیِ diff پنل: 5.4.1 → 5.5.1

همهٔ تغییراتِ روترها/مدل‌هایی که اپ مصرف می‌کند با `git diff v5.4.1..v5.5.1` بررسی شد.
**هیچ endpoint یا فیلدی حذف/تغییرنام نداده است** — یعنی اپ از قبل با 5.5.1 «کار می‌کرد»؛
کاری که این نسخه انجام می‌دهد «استفاده از تازه‌ها + بستنِ لبه‌های سخت‌گیرانهٔ جدید» است:

| تغییرِ ۵٫۵ | اثر روی اپ | کاری که انجام شد |
|---|---|---|
| `hwid_count` در `UserListItem` | شمارشِ دستگاه بدونِ درخواستِ جداگانه | پارس + بجِ دستگاه روی کارت (گرید/فشرده) و آیکون+عدد در micro |
| `target=inactive` در `/api/users/expired` | پاک‌سازیِ کاربرانِ بی‌فعال | دیالوگِ «روزهای بی‌فعالیتی» + ارسالِ الزامیِ `expired_before` |
| `GET /api/users/simple` | جست‌وجوی سبک | ورکرِ بدهکاران از این مسیر استفاده می‌کند (به‌جای صفحهٔ کاملِ کاربران) |
| `PUT /api/nodes/order` + `sort_order` | ترتیبِ ماندگارِ نودها | دیالوگِ ترتیب با دکمه‌های بالا/پایین (مجوز `nodes.update`) |
| `on_hold_timeout_action` در `/api/settings/general` | رفتارِ پنل در پایانِ مهلتِ on_hold | متنِ راهنمای فرمِ کاربر همان اقدام را می‌گوید (فعال/غیرفعال/حذف) |
| `next_plan.expire` سقفِ `2_147_483_647` | ورودیِ بزرگ → ۴۲۲ خامِ pydantic | کلمپِ سمتِ اپ + همان سقف برای `on_hold_expire_duration` |
| RBAC سخت‌گیرانه‌تر (کلید API محدود به نقشِ ادمین، `require_scope_all` برای bulkها) | ۴۰۳های بیشتر | پیامِ ۴۰۳ حالا `detail` پنل را نشان می‌دهد، نه متنِ کلی |
| Passkey/WebAuthn، Cores/Hosts/Client templates، `/api/api_key`s | قابلیت‌های سطحِ ۵ | **در این مرحله پیاده نشدند** (فازِ محصولیِ جدا) |

## ۲. دروازهٔ نسخه — `PanelSupport`

`data/PanelSupport.kt` تنها منبعِ حقیقت برای «آیا این پنل قابلیتِ X را دارد؟»:

- نسخه از **همان پاسخِ `GET /api/system`** خوانده می‌شود (بدونِ درخواستِ اضافه) و با
  `PanelCache.statsKey(baseUrl)` در شروعِ سرد بازیابی می‌شود؛ با سوییچِ حساب ریست می‌شود.
- `PanelVersion` (توابعِ خالص، تست‌شده): `v5.5.1`, `5.5`, `5.5.1-rc.1`, `5.5.1+build` را
  می‌فهمد؛ نسخه‌های نامعلوم `null` می‌دهند.
- **سیاست «نامعلوم = مجاز»**: نقشِ بدونِ `system.read` نسخه را نمی‌بیند، پس نه بنر می‌گیرد و نه
  قابلیتی از دست می‌دهد؛ پنل خودش ۴۰۳/۴۲۲ را با پیامِ روشن برمی‌گرداند.
- **نسخهٔ قدیمی**: بنرِ کهربایی در داشبورد + ردیفِ «سازگاری با اپ» در تنظیمات؛ پاک‌سازیِ
  بی‌فعال‌ها و ترتیبِ نودها پنهان می‌شوند. کارهای روزمره (کاربران، گروه‌ها، قالب‌ها، آمار،
  مانیتورینگ) دست‌نخورده می‌مانند — تصمیمِ «قفلِ کامل» عمداً انتخاب نشد.

## ۳. تغییرهای API (`PanelApi`)

```kotlin
// v5.5 — شمارشِ دستگاه در پاسخِ لیست (null = عدد در دسترس نیست، 0 = مقدارِ واقعی)
parseUser(...).hwidCount

// v5.5 — پاک‌سازی بر اساسِ آخرین فعالیت؛ بدونِ مهلت، پیش از شبکه خطا می‌دهد
cleanupCandidates(session, target = "inactive", expiredBefore = "2026-09-01T00:00:00Z")
deleteCleanupCandidates(session, target = "inactive", expiredBefore = "…")

// v5.5 — فهرستِ سبک (id + username)
usersSimple(session, search = "ali", limit = 5)

// v5.5 — ترتیبِ ماندگارِ نودها (حداقل ۲ شناسه، بدون تکرار)
reorderNodes(session, listOf(3, 1, 2))

// v5.5 — اقدامِ پایانِ مهلتِ on_hold
generalSettings(session).onHoldTimeoutAction   // activate | disable | delete
```

ثابت‌های عمومی: `PanelApi.MAX_PLAN_SECONDS`, `PanelApi.CLEANUP_INACTIVE`, `PanelApi.CLEANUP_TARGETS`,
`PanelApi.ON_HOLD_ACTIONS`.

## ۴. تست‌های قرارداد

| فایل | چه چیزی را قفل می‌کند |
|---|---|
| `utils/PanelVersionTest` | پارس/مقایسهٔ نسخه، «نامعلوم ≠ قدیمی»، حداقلِ 5.5.1 |
| `data/api/Panel55CompatTest` | `hwid_count`، `inactive` + `expired_before` (encode شده)، `users/simple`، `PUT /api/nodes/order` + اعتبارسنجیِ محلی، `on_hold_timeout_action`، کلمپِ `next_plan`، دروازهٔ `PanelSupport` |

اجرای محلی: `./gradlew testDebugUnitTest` (نیازمند JDK 17 + Android SDK)؛ در CI همان گام.

## ۵. کارهایی که عمداً انجام نشدند

1. **صفحه‌های Hosts / Cores / Client templates / API Keys / Settings پنل** — قابلیت‌های سطحِ ۵،
   نه «هماهنگ‌سازی نسخه»؛ فازِ بعدی.
2. **ورود با Passkey** — روی موبایل نیازمندِ WebAuthn/Credential Manager است.
3. **`GET /api/users/sub_update/chart`، `bulk/proxy_settings`، لاگ/آپدیتِ نود** — استفاده نشده‌اند.
4. **حذفِ `settings-redesign.patch`** — فایلِ مرده در ریشهٔ مخزن است (با `git apply --check` هم
   اعمال نمی‌شود)؛ حذفش تصمیمِ جداگانه می‌خواهد.
