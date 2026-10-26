package com.mrm.pgmanager.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mrm.pgmanager.data.model.SystemStats
import com.mrm.pgmanager.utils.PanelVersion

/**
 * «وضعیتِ پشتیبانیِ پنل» — یک منبعِ حقیقتِ واحد برای مقایسهٔ نسخهٔ پنل با حداقلِ
 * نسخهٔ پشتیبانی‌شده ([PanelVersion.MIN_SUPPORTED] = 5.5.1).
 *
 * ### چرا
 * اپ از این نسخه فقط PasarGuard **5.5.1+** را پشتیبانی می‌کند، ولی «قفل‌کردنِ کاملِ
 * اپ روی پنلِ قدیمی» بدترین رفتار است: کاربری که هنوز آپدیت نکرده، نه می‌فهمد چرا
 * اپ باز نمی‌شود و نه راهی برای دیدنِ پنلش دارد. اینجا سیاستِ «نرم» پیاده شده است:
 *
 *  - نسخه از همان پاسخِ `GET /api/system` خوانده می‌شود که اپ به‌هرحال برای داشبورد
 *    می‌گیرد؛ هیچ درخواستِ اضافه‌ای در کار نیست.
 *  - **نسخهٔ قدیمی** → یک بنرِ روشن («پنل را به 5.5.1 به‌روزرسانی کنید») + پنهان‌شدنِ
 *    قابلیت‌های تازهٔ ۵.۵ (پاک‌سازیِ بی‌فعال‌ها، ترتیبِ نودها).
 *  - **نسخهٔ نامعلوم** (نقشِ بدونِ مجوزِ `system.read`، یا اولین اجرا پیش از پاسخ)
 *    → نه بنر، نه پنهان‌سازی؛ پنل خودش ۴۰۳/۴۲۲ را با پیامِ روشن برمی‌گرداند.
 *
 * `version` یک State کامپوز است، پس هر کامپوزبلی که [isOutdated] یا یکی از
 * `supports*` را بخواند با رسیدنِ پاسخِ پنل خودکار دوباره ترسیم می‌شود.
 */
object PanelSupport {

    /** نسخهٔ خامِ پنل (همان `version` در `/api/system`)، یا null اگر هنوز ندانیم. */
    var version: String? by mutableStateOf(null)
        private set

    /** نسخه از پاسخِ `/api/system` (یا کشِ آن در شروعِ سرد) تازه می‌شود. */
    fun updateFrom(stats: SystemStats?) {
        val raw = stats?.version?.trim().orEmpty()
        if (raw.isNotEmpty() && raw != "null") update(raw)
    }

    fun update(raw: String?) {
        val clean = raw?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
        if (clean != null) version = clean
    }

    /** با تغییرِ حساب پاک می‌شود تا نسخهٔ پنلِ قبلی روی پنلِ جدید نماند. */
    fun reset() {
        version = null
    }

    /** نسخه خوانده شده است (نه اینکه حتماً پشتیبانی شود). */
    val isKnown: Boolean get() = PanelVersion.parts(version) != null

    /** نسخهٔ خوانده‌شده قدیمی‌تر از حداقلِ پشتیبانی است (اطمینان‌بخش‌ترین حالتِ «هشدار»). */
    val isOutdated: Boolean get() = PanelVersion.isAtLeast(version) == false

    /**
     * آیا می‌شود روی این پنل قابلیت‌های ۵.۵ را نشان داد؟
     * نامعلوم = بله (مدارا) — جزئیات در [PanelVersion.supports].
     */
    val supportsCurrentFeatures: Boolean get() = PanelVersion.supports(version)

    /** پاک‌سازیِ «کاربرانِ بی‌فعال» — `target=inactive` (v5.5). */
    val supportsInactiveCleanup: Boolean get() = supportsCurrentFeatures

    /** ذخیرهٔ ترتیبِ نودها — `PUT /api/nodes/order` (v5.5). */
    val supportsNodeOrder: Boolean get() = supportsCurrentFeatures

    /** نمایشِ تعداد دستگاه روی کارتِ کاربر از `hwid_count` پاسخِ لیست (v5.5). */
    val supportsHwidCount: Boolean get() = supportsCurrentFeatures

    /** متنِ «اقدامِ پایانِ مهلتِ on_hold» از تنظیماتِ عمومی (v5.5). */
    val supportsOnHoldAction: Boolean get() = supportsCurrentFeatures

    /** نسخهٔ نمایشی (مثل `5.5.1`) یا رشتهٔ خالی. */
    val label: String get() = PanelVersion.label(version)

    /** حداقلِ نسخهٔ پشتیبانی‌شده — برای متن‌های UI که نباید شماره را هاردکد کنند. */
    val minSupported: String get() = PanelVersion.MIN_SUPPORTED
}
