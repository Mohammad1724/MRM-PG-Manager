package com.mrm.pgmanager.utils

import com.mrm.pgmanager.data.model.SaleRecord
import java.time.LocalDate
import java.time.ZoneId

/**
 * منطقِ خالصِ گزارشِ درآمد — بدونِ وابستگی به اندروید تا قابلِ تستِ واحد باشد.
 *
 * نکتهٔ مهم: بازه‌ها **شمسی**‌اند. برای کاربرِ ایرانی «این ماه» یعنی ماهِ شمسی
 * (مثلاً از ۱ مرداد)، نه از ۱ آگوست. محاسبه با [JalaliCalendar] انجام می‌شود.
 */
object RevenueLogic {

    /** بازه‌های آمادهٔ گزارش. */
    enum class Range { TODAY, THIS_MONTH, LAST_MONTH, LAST_30_DAYS, THIS_YEAR, ALL }

    /** خلاصهٔ یک بازه. */
    data class Summary(
        val total: Long,
        val count: Int,
        val uniqueUsers: Int
    ) {
        /** میانگینِ هر فروش؛ برای بازهٔ خالی صفر. */
        val average: Long get() = if (count == 0) 0L else total / count
    }

    /** یک ردیف از نمودار/فهرستِ گروه‌بندی‌شده. */
    data class Bucket(val label: String, val total: Long, val count: Int)

    /**
     * تبدیلِ [Range] به بازهٔ تاریخِ میلادی (هر دو سر شامل).
     * [today] تزریق‌پذیر است تا تست به ساعتِ سیستم وابسته نباشد.
     */
    fun rangeBounds(range: Range, today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate>? {
        val j = JalaliCalendar.gregorianToJalali(today.year, today.monthValue, today.dayOfMonth)
        return when (range) {
            Range.ALL -> null
            Range.TODAY -> today to today
            Range.LAST_30_DAYS -> today.minusDays(29) to today
            Range.THIS_MONTH -> jalaliMonthStart(j.year, j.month) to today
            Range.LAST_MONTH -> {
                val (py, pm) = if (j.month == 1) (j.year - 1) to 12 else j.year to (j.month - 1)
                jalaliMonthStart(py, pm) to jalaliMonthStart(py, pm).let {
                    jalaliMonthStart(if (pm == 12) py + 1 else py, if (pm == 12) 1 else pm + 1).minusDays(1)
                }
            }
            Range.THIS_YEAR -> jalaliMonthStart(j.year, 1) to today
        }
    }

    /** اولین روزِ ماهِ شمسیِ داده‌شده، به میلادی. */
    fun jalaliMonthStart(jy: Int, jm: Int): LocalDate =
        LocalDate.parse(JalaliCalendar.jalaliToGregorian(jy, jm, 1))

    /** آیا این فروش داخلِ بازه است؟ */
    fun inRange(sale: SaleRecord, range: Range, today: LocalDate = LocalDate.now()): Boolean {
        val bounds = rangeBounds(range, today) ?: return true
        val d = epochToLocalDate(sale.soldAt)
        return !d.isBefore(bounds.first) && !d.isAfter(bounds.second)
    }

    /** فیلترِ فروش‌ها بر اساسِ پنل و بازه. */
    fun filter(
        sales: List<SaleRecord>,
        baseUrl: String?,
        range: Range,
        today: LocalDate = LocalDate.now()
    ): List<SaleRecord> = sales.filter { s ->
        (baseUrl == null || s.baseUrl == baseUrl) && inRange(s, range, today)
    }

    /** خلاصهٔ یک فهرستِ فروش. */
    fun summarize(sales: List<SaleRecord>): Summary = Summary(
        total = sales.sumOf { it.amount },
        count = sales.size,
        uniqueUsers = sales.map { it.username }.distinct().size
    )

    /**
     * گروه‌بندیِ روزانه برای نمودار — از قدیم به جدید، روزهای خالی هم صفر.
     * [days] تعدادِ روزِ گذشته شاملِ امروز.
     */
    fun dailyBuckets(
        sales: List<SaleRecord>,
        days: Int,
        today: LocalDate = LocalDate.now()
    ): List<Bucket> {
        if (days <= 0) return emptyList()
        val byDate = sales.groupBy { epochToLocalDate(it.soldAt) }
        return (days - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val items = byDate[d].orEmpty()
            val j = JalaliCalendar.gregorianToJalali(d.year, d.monthValue, d.dayOfMonth)
            Bucket("%02d/%02d".format(j.month, j.day), items.sumOf { it.amount }, items.size)
        }
    }

    /**
     * گروه‌بندیِ ماهانهٔ شمسی — [months] ماهِ گذشته شاملِ ماهِ جاری، از قدیم به جدید.
     */
    fun monthlyBuckets(
        sales: List<SaleRecord>,
        months: Int,
        today: LocalDate = LocalDate.now()
    ): List<Bucket> {
        if (months <= 0) return emptyList()
        val j = JalaliCalendar.gregorianToJalali(today.year, today.monthValue, today.dayOfMonth)
        return (months - 1 downTo 0).map { back ->
            var y = j.year
            var m = j.month - back
            while (m <= 0) { m += 12; y -= 1 }
            val start = jalaliMonthStart(y, m)
            val end = jalaliMonthStart(if (m == 12) y + 1 else y, if (m == 12) 1 else m + 1).minusDays(1)
            val items = sales.filter {
                val d = epochToLocalDate(it.soldAt)
                !d.isBefore(start) && !d.isAfter(end)
            }
            Bucket(JalaliCalendar.Date(y, m, 1).getMonthName(), items.sumOf { it.amount }, items.size)
        }
    }

    /** پرفروش‌ترین کاربران در یک فهرست. */
    fun topUsers(sales: List<SaleRecord>, limit: Int = 5): List<Bucket> =
        sales.groupBy { it.username }
            .map { (u, list) -> Bucket(u, list.sumOf { it.amount }, list.size) }
            .sortedWith(compareByDescending<Bucket> { it.total }.thenBy { it.label })
            .take(limit)

    /** مبلغ با جداکنندهٔ هزارگان. */
    fun formatAmount(amount: Long): String = "%,d".format(java.util.Locale.US, amount)

    /** مبلغِ فشرده برای جاهای تنگ: ۱۲۵٬۰۰۰ → «۱۲۵ هزار»، ۲٬۵۰۰٬۰۰۰ → «۲.۵ م». */
    fun formatAmountShort(amount: Long): String = when {
        amount >= 1_000_000_000L -> trimZero(amount / 1_000_000_000.0) + " میلیارد"
        amount >= 1_000_000L -> trimZero(amount / 1_000_000.0) + " م"
        amount >= 1_000L -> trimZero(amount / 1_000.0) + " هزار"
        else -> amount.toString()
    }

    private fun trimZero(v: Double): String =
        String.format(java.util.Locale.US, "%.1f", v).removeSuffix(".0")

    private fun epochToLocalDate(ts: Long): LocalDate =
        java.time.Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
}
