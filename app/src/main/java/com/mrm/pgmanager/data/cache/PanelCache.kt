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

    fun put(key: String, value: Any?) {
        entries[key] = Entry(value, SystemClock.elapsedRealtime())
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = entries[key]?.value as? T

    /** سنِ دادهٔ ذخیره‌شده بر حسب میلی‌ثانیه، یا null اگر اصلاً ذخیره نشده. */
    fun ageMs(key: String): Long? = entries[key]?.let { SystemClock.elapsedRealtime() - it.at }

    fun isFresh(key: String, maxAgeMs: Long = DefaultFreshMs): Boolean =
        (ageMs(key) ?: Long.MAX_VALUE) <= maxAgeMs

    /** همهٔ کلیدها را دور می‌ریزد — موقعِ خروج از حساب. */
    fun clear() = entries.clear()

    // ── کلیدها؛ همه به آدرسِ پنل وصل‌اند تا با عوض‌شدنِ حساب قاطی نشوند.
    fun statsKey(baseUrl: String) = "stats:$baseUrl"
    fun trafficKey(baseUrl: String) = "traffic:$baseUrl"
    fun statsTrafficKey(baseUrl: String) = "stats.traffic:$baseUrl"
    fun statsCountKey(baseUrl: String) = "stats.count:$baseUrl"
    fun nodesKey(baseUrl: String) = "nodes:$baseUrl"
    fun usersKey(baseUrl: String) = "users:$baseUrl"
    fun groupsKey(baseUrl: String) = "groups:$baseUrl"
    fun inboundsKey(baseUrl: String) = "inbounds:$baseUrl"
    fun templatesKey(baseUrl: String) = "templates:$baseUrl"
    fun templateGroupsKey(baseUrl: String) = "templates.groups:$baseUrl"
}
