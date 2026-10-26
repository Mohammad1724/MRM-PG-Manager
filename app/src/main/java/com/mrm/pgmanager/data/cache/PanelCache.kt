package com.mrm.pgmanager.data.cache

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * حافظهٔ کوتاه‌مدتِ درون‌برنامه‌ای برای پاسخ‌های پنل.
 *
 * ### چرا لازم شد
 * صفحه‌ها داخل یک `HorizontalPager` با `beyondViewportPageCount = 0` نشسته‌اند،
 * یعنی صفحهٔ بیرون از دید dispose می‌شود. نتیجه‌اش این بود که هر بار سوایپ
 * می‌کردی، صفحهٔ جدید از صفر ساخته می‌شد، `LaunchedEffect` شلیک می‌کرد و وسطِ
 * فریم‌های انیمیشنِ جابه‌جایی یک درخواستِ HTTP و پارسِ JSON راه می‌افتاد — دقیقاً
 * سنگین‌ترین کار در بدترین لحظهٔ ممکن. حسِ کاربر: «انیمیشنِ صفحه‌ها روان نیست».
 *
 * حالا آخرین پاسخ در حافظه می‌ماند؛ صفحه فوراً با همان داده ساخته می‌شود و فقط
 * وقتی داده کهنه شده باشد سراغِ شبکه می‌رود.
 *
 * ### چرا دیسک کافی نبود
 * `SessionStore` از قبل یک کشِ آفلاین روی دیسک دارد، ولی آن برای «پنل در دسترس
 * نیست» است: رمزگشاییِ EncryptedSharedPreferences به‌علاوهٔ پارسِ JSON، خودش چند
 * میلی‌ثانیه در نخِ اصلی می‌خورد. اینجا آبجکتِ آماده در حافظه نگه داشته می‌شود.
 *
 * ### طول عمر
 * تا وقتی پروسه زنده است. با خروج از حساب یا عوض‌کردنِ حساب باید [clear] شود،
 * وگرنه دادهٔ پنلِ قبلی یک لحظه روی صفحهٔ پنلِ جدید دیده می‌شود.
 *
 * زمان‌سنج `elapsedRealtime` است نه `currentTimeMillis`، تا دست‌کاریِ ساعتِ
 * گوشی کش را برای همیشه «تازه» یا «کهنه» نکند.
 */
object PanelCache {

    private class Entry(val value: Any?, val at: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    /**
     * پیش‌فرضِ تازگی. عمداً کوتاه است: هدف حذفِ درخواستِ تکراری در چند ثانیهٔ
     * سوایپ است، نه نگه‌داشتنِ دادهٔ قدیمی.
     */
    const val DefaultFreshMs = 20_000L

    /**
     * ساعتِ کش بر حسبِ میلی‌ثانیه.
     *
     * چرا جایگزین‌شدنی است: تست‌های JVM (`PanelCacheTest`) نه Robolectric اجرا
     * می‌کنند و نه `SystemClock` واقعی دارند، پس `SystemClock.elapsedRealtime()`
     * با `RuntimeException: Method elapsedRealtime … not mocked` کلِ تست را
     * می‌شکست. جدا از آن، سنجشِ «کهنه شدن» با ساعتِ واقعی غیرقطعی است؛ تست یک
     * شمارندهٔ دستی می‌دهد و سن را دقیق می‌سنجد. مقدارِ پیش‌فرض در تولید همان
     * `elapsedRealtime` است.
     */
    @Volatile
    internal var clock: () -> Long = { SystemClock.elapsedRealtime() }

    /** برگرداندنِ ساعت به حالتِ تولید — پایانِ هر تست. */
    internal fun resetClockForTests() {
        clock = { SystemClock.elapsedRealtime() }
    }

    fun put(key: String, value: Any?) {
        entries[key] = Entry(value, clock())
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = entries[key]?.value as? T

    /** سنِ دادهٔ ذخیره‌شده بر حسب میلی‌ثانیه، یا null اگر اصلاً ذخیره نشده. */
    fun ageMs(key: String): Long? = entries[key]?.let { clock() - it.at }

    fun isFresh(key: String, maxAgeMs: Long = DefaultFreshMs): Boolean =
        (ageMs(key) ?: Long.MAX_VALUE) <= maxAgeMs

    /** همهٔ کلیدها را دور می‌ریزد — موقعِ خروج از حساب. */
    fun clear() = entries.clear()

    // ── کلیدها؛ همه به آدرسِ پنل وصل‌اند تا با عوض‌شدنِ حساب قاطی نشوند.
    fun statsKey(baseUrl: String) = "stats:$baseUrl"
    /**
     * کلیدِ کشِ ترافیکِ داشبورد. بازهٔ انتخابی هم وارد کلید می‌شود (D5): قبلاً هر شش
     * بازه روی یک کلید می‌نوشتند و با عوض‌کردنِ بازه، کشِ بازهٔ قبلی بازنویسی و
     * هنگامِ ورودِ مجدد نقاطِ بازهٔ دیگری لحظه‌ای نمایش داده می‌شد.
     */
    fun trafficKey(baseUrl: String, range: String? = null) =
        if (range.isNullOrBlank()) "traffic:$baseUrl" else "traffic:$baseUrl:$range"
    fun statsTrafficKey(baseUrl: String) = "stats.traffic:$baseUrl"
    fun statsCountKey(baseUrl: String) = "stats.count:$baseUrl"
    fun statsNodeUsageKey(baseUrl: String) = "stats.nodeusage:$baseUrl"
    fun nodesKey(baseUrl: String) = "nodes:$baseUrl"
    fun usersKey(baseUrl: String) = "users:$baseUrl"
    fun groupsKey(baseUrl: String) = "groups:$baseUrl"
    fun inboundsKey(baseUrl: String) = "inbounds:$baseUrl"
    fun templatesKey(baseUrl: String) = "templates:$baseUrl"
    fun templateGroupsKey(baseUrl: String) = "templates.groups:$baseUrl"

    // ── زیرساختِ پنل (فازِ ۲): هاست‌ها / کورها / قالب‌های کلاینت.
    // این‌ها دادهٔ «سطحِ سرور» هستند و در یک نشست تقریباً ثابت‌اند، پس کشِ
    // کوتاه‌مدتِ همین حافظه برای برگشت از تب/صفحه کافی است و از درخواستِ
    // تکراری هنگامِ جابه‌جایی بین تب‌های مدیریتِ پنل جلوگیری می‌کند.
    fun hostsKey(baseUrl: String) = "hosts:$baseUrl"

    /**
     * تنظیماتِ پنل (`GET /api/settings`). مثلِ بقیهٔ داده‌های سطحِ سرور کش می‌شود
     * تا رفت‌وبرگشت بین چهار تب دوباره درخواست نفرستد؛ بعد از هر ذخیره، پاسخِ
     * تازه جای همین می‌نشیند.
     */
    fun panelSettingsKey(baseUrl: String) = "panelsettings:$baseUrl"

    /** فهرستِ کلیدهای API — همان قاعدهٔ کشِ کوتاه‌مدتِ داده‌های سطحِ سرور. */
    fun apiKeysKey(baseUrl: String) = "apikeys:$baseUrl"
    fun coresKey(baseUrl: String) = "cores:$baseUrl"
    fun clientTemplatesKey(baseUrl: String) = "clienttemplates:$baseUrl"
}
