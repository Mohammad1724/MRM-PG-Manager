# CI سبز — چه چیزی اجرا می‌شود و چطور شکست را بخوانیم (نسخهٔ ۰٫۱۳٫۱)

> **مسئله:** تا نسخهٔ ۰٫۱۳٫۰ هیچ‌کدام از ۱۳ فایلِ تستِ `app/src/test` هرگز در CI
> اجرا نشده بود؛ ورک‌فلو فقط APK می‌ساخت. نتیجه: کدِ اصلی سالم بود ولی «تست‌ها»
> عملاً سندی بودند که کسی نمی‌خواند — و اولین اجرا ۱۳ شکست و ۳ خطای کامپایل داد.
> **راه‌حل:** ورک‌فلو روی شاخه‌های کاری هم اجرا می‌شود، تست‌ها می‌دوند، و هر شکست
> به **annotation** تبدیل می‌شود تا بدونِ باز کردنِ لاگ دیده شود.

---

## ۱. زنجیرهٔ اجرا

| گام | چه می‌کند | اگر سرخ شود |
|---|---|---|
| `Validate string resources` | `tools/check-resources.py` — arity و هم‌خوانیِ `strings.xml` فارسی/انگلیسی | خطِ همان فایل |
| `Validate Kotlin sources` | `kt-balance`، `kt-imports`، `kt-import-paths`، `kt-callcheck`، `kt-family`، `kt-modifier-order`، `check-contrast`، `kt-lambdaargs`، `check-widget-targets`، `kt-compose-api` | خطِ همان فایل |
| `Run unit tests` | `./gradlew testDebugUnitTest` (سورسِ اصلی + تست را کامپایل می‌کند) | annotationِ تست |
| `Annotate failures` | `tools/ci-annotate.py build.log app/build/test-results/testDebugUnitTest` | — (همیشه اجرا می‌شود وقتی قبلی سرخ بود) |
| `Build debug APK` / `Build signed release APK` | `assembleDebug` / `assembleRelease` | annotationِ بیلد |

تریگرها: `push` روی `main`، `liquid-glass-ui` و `arena/**`، هر `pull_request`، و
`workflow_dispatch`. `concurrency` هر شاخه را قفل می‌کند تا پوش‌های پشتِ‌سرِهم
اجراهای قدیمی را نیمه‌کاره رها نکنند.

## ۲. خواندنِ شکست بدونِ لاگ

لاگِ گام‌ها از API به `*.blob.core.windows.net` ریدایرکت می‌شود و از محیط‌هایی که به
آن دامنه دسترسی ندارند خوانده نمی‌شود. دو مسیرِ خوانا:

```bash
# فهرستِ اجراهای شاخه
gh run list --branch arena/546c9c18-mrm-pg-manager --limit 1

# annotationها (نامِ تست + پیام + file:line)
ID=$(gh api "repos/<owner>/<repo>/commits/$(git rev-parse HEAD)/check-runs" \
       -q '.check_runs[] | select(.name=="build") | .id')
gh api "repos/<owner>/<repo>/check-runs/$ID/annotations" \
   -q '.[] | select(.annotation_level=="failure") | "\(.path):\(.start_line) \(.message)"'
```

نکته‌های `ci-annotate.py`:

- **نامِ تست + file:line**: اولین فریمِ `com.mrm` از stack trace استخراج می‌شود، چون
  پیامِ `java.lang.AssertionError` یا `ComposeTimeoutException` به‌تنهایی هیچ
  اطلاعاتی ندارد.
- **سقفِ ۸ annotation** در GitHub: بقیه در یک annotationِ تجمیعی و در
  `$GITHUB_STEP_SUMMARY` می‌آید.
- `%` باید `%25` شود وگرنه دستورِ workflow شکسته می‌شود (تست‌شده).

## ۳. قاعده‌های تست که CI یاد داد

| قاعده | چرا |
|---|---|
| تابعِ `@Test` باید `void` باشد → بدنهٔ `{ … }` بنویسید، نه `= runBlocking { … }` | `= runBlocking {}` مقدارِ آخرین عبارت را برمی‌گرداند و JUnit **کلِ کلاس** را با `InvalidTestClassError` رد می‌کند؛ هیچ تستی از آن کلاس اجرا نمی‌شود |
| `assertExists` / `assertDoesNotExist` را **import نکنید** | در Compose UI ۱.۷.۶ این‌ها متدِ عضوِ `SemanticsNodeInteraction`‌اند؛ import کردنشان `Unresolved reference` می‌دهد. بقیهٔ assert‌ها (`assertCountEquals`، `assertIsDisplayed`، `assertHeightIsAtLeast` …) هنوز تابعِ سطحِ فایل‌اند و import می‌خواهند. `tools/kt-compose-api.py` پاسِ سومی دارد که importِ این دو را رد می‌کند |
| روی کانالِ درست انتظار بکشید | `selectRange` شرحِ دسترس‌پذیری می‌دهد نه متن؛ `waitUntil` روی متن همیشه timeout می‌دهد (`گره با متن=0، گره با شرح=1`) |
| `csv.lines()` خطِ خالیِ آخر را هم می‌شمارد | CSV متعارف با newline تمام می‌شود؛ در شمارش `trimEnd('\n', '\r')` لازم است |
| انتظارهای `waitUntil` را در `awaitCondition` بپیچید | پیامِ پیش‌فرض «Condition still not satisfied after 15000 ms» است؛ نسخهٔ محلی تعدادِ واقعیِ گره‌ها را می‌گوید |
| صفحه را با `CircularProgressIndicator` روی صحنه **مونتاژ نکنید** | انیمیشنِ بی‌پایان هیچ‌وقت idle نمی‌شود؛ `setContent`/`performClick` تا ۶۰ ثانیه بلوکه می‌شود (`AppNotIdleException`) و چون لوپرِ Main در دستِ تست است، ادامهٔ کوروتینِ شبکه هم جلو نمی‌آید. نتیجه فقط تستِ خودشان نیست: **قفلِ sandbox** تمامِ کلاس‌های Composeِ بعد از آن را سرخ می‌کند. راه‌حل: `PanelCache` را پیشِ `setContent` پر کن تا `loading` از فریمِ اول false باشد (همان کاری که در تولید می‌کند) |
| از درونِ شرطِ `waitUntil` گرهٔ semantics **نخوان** | `fetchSemanticsNodes()` خودش `runOnIdle` می‌کند؛ داخلِ شرط یعنی `waitForIdle` تودرتو و همان قفل. شرط فقط شمارندهٔ سرور باشد (`waitUntil { server.requestCount >= 1 }`) و متن‌ها بعد از آن بی‌واسطه سنجیده شوند |
| محتوای hoist‌شدهٔ دیالوگ را با والدِ اندازه‌دار رندر کن و «دیده‌شدن» را با `boundsInRoot` بسنج | بدنۀ دیالوگ `Column(Modifier.fillMaxWidth())` است و ارتفاعش را از محتوا می‌گیرد. اگر فضای بلندی نپیشنهاد شود، `Column` فرزندی را که در آن فضای باقی‌مانده نمی‌گنجد **اندازه هم نمی‌گیرد**: نه placed می‌شود نه گرهٔ semantics می‌سازد. دو پیامی که CI از یک تست داد همین را می‌گفت — «گره هست ولی displayed نیست» برای دو `Text` اول (ارتفاعِ صفر) و «هیچ گره‌ای پیدا نشد» برای دکمهٔ چهارم (اصلاً نچیده). `Box(Modifier.fillMaxSize())` هم درمان نکرد؛ `Box(Modifier.size(400.dp, 700.dp))` بله. و `assertIsDisplayed` را در این خانه به کار نبرید: ناحیۀ دیدنی با کرانۀ *پنجره* برش می‌خورد و پنجرۀ Robolectric ارتفاع اتکاپذیر ندارد — `assertExists` + `boundsInRoot.height > 0` همان ادعا با عدد است (`ApiKeyRevealContentTest`) |
| اگر اسپینرِ بی‌پایان **نتوانست** ساکن شود، اکشنِ `busy`/`saving` را لمس نکن | مسیر «بساز/ذخیره/چرخش» را در تستِ لایهٔ داده (`*ApiTest`) قفل کن. از ۰٫۱۶٫۰ این قاعده نرم‌تر شده: `MrmBusyIndicator` با `MotionPrefs.reduceMotion = true` (که `setUp` همهٔ تست‌های صفحه می‌گذارد) ساکن است، پس لمسِ «ذخیره» و سنجیدنِ `PUT`ِ ارسال‌شده از UI ممکن است — نمونه‌اش `PanelSettingsScreenTest.save_sends_one_put_for_the_dirty_section_only` |
| نشانگرِ مشغولی فقط از `MrmBusyIndicator` می‌سازد، نه `CircularProgressIndicator` خام | دو هزینهٔ یک‌جا: کاربرِ «کاهش انیمیشن» و آزمونِ idle. با یک نقطه، هیچ صفحه‌ای دیگر در تست روی اسپینر نمی‌خوابد و cold-mount هم ممکن می‌شود |
| صفحهٔ جریان‌محور (SSE/WebSocket) با جریانِ باز آزموده نمی‌شود | جریان هیچ‌وقت idle نمی‌شود؛ **محتوا** را از صفحه جدا کن و با دادهٔ ثابت رندر کن (`NodeLogsContent` + `NodeLogsContentTest`) و رفتارِ اتصال را در لایهٔ داده قفل کن (`NodeLogsApiTest`) |
| پنجرهٔ `Dialog` در تستِ Compose **باز نشود** | در Robolectric پنجرهٔ دیالوگ هرگز idle گزارش نمی‌شود؛ همین `performClick` روی دکمهٔ بازکردنش را ۶۰ ثانیه می‌خواباند (دو اجرای CI). محتوای دیالوگ را اگر لازم بود جدا کنید و بدونِ `Dialog` رندر کنید — الگوی `UserDetailsSectionsScreenshotTest` |
| `ApiKeysScreen` (و هر صفحهٔ تازهٔ سطح‌سرور) با کشِ تازه درخواست نمی‌فرستد | `LaunchedEffect(session) { if (!PanelCache.isFresh(key)) load() }` — هم رفتاریِ درست است (برگشتن از دیالوگ داده را دوباره از شبکه نمی‌گیرد)، هم تست را از حالتِ in-flight/اسپینر نجات می‌دهد |

## ۴. سه دریچهٔ تست در کدِ تولید (و چرا)

تست‌های JVM نه کی‌استور دارند نه `SystemClock`؛ بدونِ این‌ها هر تستی که صفحه‌ای را
رندر کند می‌شکست — بدونِ اینکه تست غلط باشد:

| دریچه | پیش‌فرضِ تولید | چه چیزی را ممکن می‌کند |
|---|---|---|
| `PanelCache.clock` | `SystemClock.elapsedRealtime()` | تستِ «کهنه شدن» با شمارندهٔ دستی؛ `SystemClock` در JVM `not mocked` است |
| `SessionStore.prefsFactory` | `EncryptedSharedPreferences` | Robolectric `AndroidKeyStore` ندارد → `KeyStoreException`؛ تست یک `SharedPreferences`ِ ساده می‌دهد |
| `Autofill?.requestAutofillSafely` در `LoginScreen` | همان `requestAutofillForNode`، داخلِ `runCatching` | Robolectric: `requestAutofill called before onChildPositioned()`؛ در تولید هم مدیرِ رمزِ خراب نباید ورود را بشکند |

هر سه `internal` هستند و در `reset…ForTests` به حالتِ تولید برمی‌گردند؛ مسیرِ تولید
دست‌نخورده است.

## ۵. باگِ واقعی که همین تست‌ها پیدا کردند

`PanelApi.modifyHost` تنها `value == null` را از بدنه حذف می‌کرد. فرمِ هاست برای
«این فیلد را پاک کن» مقدارِ `JSONObject.NULL` می‌فرستد (چون `Map<String, Any?>` است)،
پس بدنه `"port": null` می‌رفت و کلید **باقی می‌ماند**: پنل آن را «صریحاً خالی»
می‌فهمید، نه «پیش‌فرضِ inbound». حالا هر دو شکل یعنی «کلید را بردار» و تستِ
`PanelInfraApiTest` همین را قفل می‌کند.

## ۶. آنچه ابزارها **نمی‌گیرند** (صادقانه)

- `kt-callcheck.py` فقط وجودِ **آرگومانِ نام‌دار** را می‌سنجد؛ نبودِ پارامترِ الزامی را
  نمی‌بیند (`RoundedAppIcon(tint = …)` یک اجرای CI را همین‌طور سوزاند).
- `check-resources` و بیشترِ `kt-*` فقط `app/src/main/java` و `res` را می‌گردند؛
  `kt-compose-api` و `kt-balance` استثنا‌اند و `app/src/test` را هم می‌بینند
  (`kt-balance` از این فاز: یک ` */ */` در KDocِ تست‌ها فقط در
  `compileDebugUnitTestKotlin` می‌سوخت، آن هم بعد از پنج دقیقه).
- `kt-balance` پارسر نیست؛ توازنِ آکولاد و `*/` بی‌شروع را می‌گیرد. رشته‌های
  `${…}`، شناسه‌های بک‌تیک‌دار و کامنتِ تودرتو را می‌شناسد — هر سه‌تایشان یک‌بار
  «مثبتِ کاذب» روی فایل‌هایِ سالم ساختند.
- امضا (نوعِ آرگومان‌ها) را فقط کامپایلر می‌گیرد؛ همین دلیلِ وجودِ گامِ تست است.
