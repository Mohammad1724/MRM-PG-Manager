package com.mrm.pgmanager.utils

import java.time.LocalDate

/**
 * منطقِ خالصِ «تمدید اشتراک» — بدونِ وابستگی به اندروید تا قابلِ تستِ واحد باشد.
 *
 * تفاوتِ مهم با «ریستِ زمان» که از قبل در اپ بود:
 * ریست همیشه انقضا را از **امروز** حساب می‌کند، ولی تمدید باید روی زمانِ باقی‌مانده
 * **اضافه** شود. اگر کاربری ۱۰ روز اعتبار دارد و ۳۰ روز تمدیدش کنید باید ۴۰ روز
 * بشود نه ۳۰ — وگرنه هر بار ۱۰ روز از حقِ مشتری خورده می‌شود.
 */
object RenewalLogic {

    /** شیوهٔ محاسبهٔ تاریخِ جدید. */
    enum class Mode {
        /** افزودن به انقضای فعلی (اگر منقضی شده باشد، از امروز). پیش‌فرض و منصفانه. */
        EXTEND,

        /** محاسبه از امروز، صرف‌نظر از باقی‌مانده. همان رفتارِ «ریستِ زمان». */
        FROM_TODAY
    }

    /** جایگاهِ کاربر در فهرستِ تمدیدها. */
    enum class Bucket {
        /** انقضا گذشته یا حجم تمام شده. */
        EXPIRED,

        /** تا N روزِ آینده منقضی می‌شود. */
        SOON,

        /** فعلاً کاری ندارد. */
        OK
    }

    /**
     * تاریخِ جدیدِ انقضا پس از تمدید.
     *
     * @param currentExpire مقدارِ فعلیِ `expire` (ISO یا خالی/۰ برای نامحدود)
     * @param days تعدادِ روزِ تمدید (باید مثبت باشد)
     * @param today امروز — تزریق‌شدنی تا تست به تاریخِ سیستم وابسته نباشد
     * @return تاریخِ جدید، یا `null` اگر ورودی نامعتبر باشد
     */
    fun newExpiryDate(
        currentExpire: String?,
        days: Int,
        mode: Mode = Mode.EXTEND,
        today: LocalDate = LocalDate.now()
    ): LocalDate? {
        if (days <= 0) return null
        if (mode == Mode.FROM_TODAY) return today.plusDays(days.toLong())

        val remaining = DateLogic.remainingDays(currentExpire, today)
        // نامحدود یا نامشخص → مبنا امروز است
        // منقضی (باقی‌مانده منفی) → از امروز، نه از تاریخِ گذشته
        val base = if (remaining == null || remaining < 0L) today else today.plusDays(remaining)
        return base.plusDays(days.toLong())
    }

    /**
     * همان [newExpiryDate] ولی به‌صورت رشتهٔ سادهٔ `yyyy-MM-dd`.
     *
     * این همان چیزی است که باید به `PanelApi.modifyUser` داده شود: آن تابع خودش
     * `DateLogic.expireValue` را صدا می‌زند که ابتدا `take(10)` می‌گیرد و سپس
     * پایانِ روزِ محلی را به UTC تبدیل می‌کند. اگر اینجا مستقیماً ISO‌ی UTC بدهیم،
     * در منطقه‌های زمانیِ با اختلافِ منفی (مثل آمریکا) آن `take(10)` می‌تواند
     * روزِ بعد را بردارد و انقضا یک روز جلو بیفتد.
     */
    fun newExpiryDateString(
        currentExpire: String?,
        days: Int,
        mode: Mode = Mode.EXTEND,
        today: LocalDate = LocalDate.now()
    ): String? = newExpiryDate(currentExpire, days, mode, today)?.toString()

    /**
     * دسته‌بندیِ یک اشتراک.
     *
     * حجمِ تمام‌شده هم «منقضی» حساب می‌شود، چون از نظرِ مشتری فرقی ندارد:
     * در هر دو حالت اتصال قطع است و باید تمدید شود.
     */
    fun bucket(
        expire: String?,
        usedTraffic: Long,
        dataLimit: Long,
        soonDays: Int,
        today: LocalDate = LocalDate.now()
    ): Bucket {
        val limitReached = dataLimit > 0L && usedTraffic >= dataLimit
        val remaining = DateLogic.remainingDays(expire, today)
        return when {
            limitReached -> Bucket.EXPIRED
            remaining != null && remaining < 0L -> Bucket.EXPIRED
            remaining != null && remaining <= soonDays.toLong() -> Bucket.SOON
            else -> Bucket.OK
        }
    }

    /**
     * ترتیبِ نمایش: فوری‌ترین اول.
     * منقضی‌ها بالا، بعد نزدیک‌ترین انقضا. نامحدودها آخر.
     */
    fun urgencyKey(expire: String?, today: LocalDate = LocalDate.now()): Long =
        DateLogic.remainingDays(expire, today) ?: Long.MAX_VALUE
}
