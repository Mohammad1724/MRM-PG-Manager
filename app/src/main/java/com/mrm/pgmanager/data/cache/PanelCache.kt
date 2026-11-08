package com.mrm.pgmanager.data.cache

import android.os.SystemClock
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.storage.offlineCacheOwner
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

    /** Invalidate one resource without discarding unrelated panel data. */
    fun invalidate(key: String) {
        entries.remove(key)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = entries[key]?.value as? T

    /** سنِ دادهٔ ذخیره‌شده بر حسب میلی‌ثانیه، یا null اگر اصلاً ذخیره نشده. */
    fun ageMs(key: String): Long? = entries[key]?.let { clock() - it.at }

    fun isFresh(key: String, maxAgeMs: Long = DefaultFreshMs): Boolean =
        (ageMs(key) ?: Long.MAX_VALUE) <= maxAgeMs

    /** همهٔ کلیدها را دور می‌ریزد — موقعِ خروج از حساب. */
    fun clear() = entries.clear()

    // Scope every resource to the exact account and credential. Clearing on a switch is
    // not enough: a response from the old account can arrive after clear() has run.
    // Never embed the credential itself in a key.
    private fun sessionKey(resource: String, session: Session) =
        "$resource:session:${offlineCacheOwner(session)}"

    fun statsKey(session: Session) = sessionKey("stats", session)
    /** Each traffic range has its own entry within the current session. */
    fun trafficKey(session: Session, range: String? = null): String {
        val key = sessionKey("traffic", session)
        return if (range.isNullOrBlank()) key else "$key:$range"
    }
    fun statsTrafficKey(session: Session) = sessionKey("stats.traffic", session)
    fun statsCountKey(session: Session) = sessionKey("stats.count", session)
    fun statsNodeUsageKey(session: Session) = sessionKey("stats.nodeusage", session)
    fun nodesKey(session: Session) = sessionKey("nodes", session)
    fun usersKey(session: Session) = sessionKey("users", session)
    fun groupsKey(session: Session) = sessionKey("groups", session)
    fun inboundsKey(session: Session) = sessionKey("inbounds", session)
    fun templatesKey(session: Session) = sessionKey("templates", session)
    fun templateGroupsKey(session: Session) = sessionKey("templates.groups", session)
    fun hostsKey(session: Session) = sessionKey("hosts", session)
    fun panelSettingsKey(session: Session) = sessionKey("panelsettings", session)
    fun apiKeysKey(session: Session) = sessionKey("apikeys", session)
    fun adminsKey(session: Session) = sessionKey("admins", session)
    fun adminRolesKey(session: Session) = sessionKey("adminroles", session)
    fun coresKey(session: Session) = sessionKey("cores", session)
    fun clientTemplatesKey(session: Session) = sessionKey("clienttemplates", session)
}
