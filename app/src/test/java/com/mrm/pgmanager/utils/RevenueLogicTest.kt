package com.mrm.pgmanager.utils

import com.mrm.pgmanager.data.model.SaleRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RevenueLogicTest {

    // 2026-08-14 میلادی = 1405/05/23 شمسی (۲۳ مرداد)
    private val today: LocalDate = LocalDate.of(2026, 8, 14)

    private fun epochOf(d: LocalDate): Long =
        d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun sale(
        user: String,
        amount: Long,
        date: LocalDate,
        base: String = "https://p1",
        days: Int = 30
    ) = SaleRecord(
        id = "$user-${date}", username = user, baseUrl = base,
        amount = amount, days = days, soldAt = epochOf(date)
    )

    // ---------- بازه‌های شمسی ----------

    @Test
    fun thisMonthStartsAtJalaliMonthNotGregorian() {
        val (start, end) = RevenueLogic.rangeBounds(RevenueLogic.Range.THIS_MONTH, today)!!
        // ۱ مرداد ۱۴۰۵ = 2026-07-23 — نه 2026-08-01
        assertEquals(LocalDate.of(2026, 7, 23), start)
        assertEquals(today, end)
    }

    @Test
    fun lastMonthIsFullJalaliMonth() {
        val (start, end) = RevenueLogic.rangeBounds(RevenueLogic.Range.LAST_MONTH, today)!!
        // تیر ۱۴۰۵: از ۱ تیر تا ۳۱ تیر
        assertEquals(LocalDate.of(2026, 6, 22), start)
        assertEquals(LocalDate.of(2026, 7, 22), end)
        // پایانِ ماهِ قبل باید دقیقاً یک روز پیش از شروعِ ماهِ جاری باشد
        val thisStart = RevenueLogic.rangeBounds(RevenueLogic.Range.THIS_MONTH, today)!!.first
        assertEquals(thisStart.minusDays(1), end)
    }

    @Test
    fun lastMonthWrapsAcrossJalaliNewYear() {
        // ۵ فروردین ۱۴۰۵ = 2026-03-25 → ماهِ قبل باید اسفندِ ۱۴۰۴ باشد
        val farvardin = LocalDate.of(2026, 3, 25)
        val (start, end) = RevenueLogic.rangeBounds(RevenueLogic.Range.LAST_MONTH, farvardin)!!
        assertEquals(RevenueLogic.jalaliMonthStart(1404, 12), start)
        assertEquals(RevenueLogic.jalaliMonthStart(1405, 1).minusDays(1), end)
    }

    @Test
    fun thisYearStartsAtNowruz() {
        val (start, _) = RevenueLogic.rangeBounds(RevenueLogic.Range.THIS_YEAR, today)!!
        assertEquals(RevenueLogic.jalaliMonthStart(1405, 1), start)
    }

    @Test
    fun last30DaysIsInclusiveWindow() {
        val (start, end) = RevenueLogic.rangeBounds(RevenueLogic.Range.LAST_30_DAYS, today)!!
        assertEquals(today.minusDays(29), start)
        assertEquals(today, end)
    }

    @Test
    fun todayRangeIsSingleDay() {
        val (start, end) = RevenueLogic.rangeBounds(RevenueLogic.Range.TODAY, today)!!
        assertEquals(today, start)
        assertEquals(today, end)
    }

    @Test
    fun allRangeHasNoBounds() {
        assertNull(RevenueLogic.rangeBounds(RevenueLogic.Range.ALL, today))
    }

    // ---------- inRange ----------

    @Test
    fun inRange_boundariesAreInclusive() {
        val (start, end) = RevenueLogic.rangeBounds(RevenueLogic.Range.THIS_MONTH, today)!!
        assertTrue(RevenueLogic.inRange(sale("a", 1, start), RevenueLogic.Range.THIS_MONTH, today))
        assertTrue(RevenueLogic.inRange(sale("a", 1, end), RevenueLogic.Range.THIS_MONTH, today))
        assertFalse(
            RevenueLogic.inRange(sale("a", 1, start.minusDays(1)), RevenueLogic.Range.THIS_MONTH, today)
        )
    }

    @Test
    fun inRange_allAlwaysTrue() {
        val old = sale("a", 1, LocalDate.of(2001, 1, 1))
        assertTrue(RevenueLogic.inRange(old, RevenueLogic.Range.ALL, today))
    }

    // ---------- filter ----------

    @Test
    fun filter_separatesPanels() {
        val sales = listOf(
            sale("a", 100, today, base = "https://p1"),
            sale("b", 200, today, base = "https://p2")
        )
        val p1 = RevenueLogic.filter(sales, "https://p1", RevenueLogic.Range.ALL, today)
        assertEquals(1, p1.size)
        assertEquals(100L, p1[0].amount)
        // baseUrl = null یعنی همهٔ پنل‌ها
        assertEquals(2, RevenueLogic.filter(sales, null, RevenueLogic.Range.ALL, today).size)
    }

    @Test
    fun filter_appliesBothPanelAndRange() {
        val sales = listOf(
            sale("a", 100, today, base = "https://p1"),
            sale("a", 100, today.minusDays(300), base = "https://p1"),
            sale("b", 100, today, base = "https://p2")
        )
        val r = RevenueLogic.filter(sales, "https://p1", RevenueLogic.Range.THIS_MONTH, today)
        assertEquals(1, r.size)
    }

    // ---------- summarize ----------

    @Test
    fun summarize_totalsCountsAndUniqueUsers() {
        val sales = listOf(
            sale("ali", 100_000, today),
            sale("ali", 50_000, today),
            sale("sara", 25_000, today)
        )
        val s = RevenueLogic.summarize(sales)
        assertEquals(175_000L, s.total)
        assertEquals(3, s.count)
        assertEquals(2, s.uniqueUsers)
        assertEquals(58_333L, s.average)
    }

    @Test
    fun summarize_emptyIsZeroAndDoesNotDivideByZero() {
        val s = RevenueLogic.summarize(emptyList())
        assertEquals(0L, s.total)
        assertEquals(0, s.count)
        assertEquals(0, s.uniqueUsers)
        assertEquals(0L, s.average)
    }

    @Test
    fun summarize_freeRenewalsCountButAddNothing() {
        val s = RevenueLogic.summarize(listOf(sale("a", 0, today), sale("b", 100, today)))
        assertEquals(100L, s.total)
        assertEquals(2, s.count)
    }

    // ---------- dailyBuckets ----------

    @Test
    fun dailyBuckets_fillsEmptyDaysWithZero() {
        val sales = listOf(sale("a", 500, today), sale("b", 300, today.minusDays(2)))
        val b = RevenueLogic.dailyBuckets(sales, 3, today)
        assertEquals(3, b.size)
        assertEquals(listOf(300L, 0L, 500L), b.map { it.total })   // قدیم → جدید
        assertEquals(listOf(1, 0, 1), b.map { it.count })
    }

    @Test
    fun dailyBuckets_labelsAreJalali() {
        val b = RevenueLogic.dailyBuckets(emptyList(), 1, today)
        assertEquals("05/23", b[0].label)  // ۲۳ مرداد
    }

    @Test
    fun dailyBuckets_nonPositiveIsEmpty() {
        assertTrue(RevenueLogic.dailyBuckets(emptyList(), 0, today).isEmpty())
    }

    // ---------- monthlyBuckets ----------

    @Test
    fun monthlyBuckets_groupsByJalaliMonth() {
        val sales = listOf(
            sale("a", 100, today),                              // مرداد
            sale("b", 200, LocalDate.of(2026, 7, 1)),           // تیر
            sale("c", 400, LocalDate.of(2026, 7, 23))           // ۱ مرداد
        )
        val b = RevenueLogic.monthlyBuckets(sales, 2, today)
        assertEquals(listOf("تیر", "مرداد"), b.map { it.label })
        assertEquals(listOf(200L, 500L), b.map { it.total })
    }

    @Test
    fun monthlyBuckets_wrapsToPreviousJalaliYear() {
        val farvardin = LocalDate.of(2026, 3, 25)   // ۵ فروردین ۱۴۰۵
        val b = RevenueLogic.monthlyBuckets(emptyList(), 3, farvardin)
        assertEquals(listOf("بهمن", "اسفند", "فروردین"), b.map { it.label })
    }

    // ---------- topUsers ----------

    @Test
    fun topUsers_sortedByTotalDescending() {
        val sales = listOf(
            sale("ali", 100, today), sale("ali", 100, today),
            sale("sara", 500, today),
            sale("reza", 50, today)
        )
        val top = RevenueLogic.topUsers(sales, 2)
        assertEquals(listOf("sara", "ali"), top.map { it.label })
        assertEquals(listOf(500L, 200L), top.map { it.total })
        assertEquals(2, top[1].count)
    }

    @Test
    fun topUsers_tiesBreakAlphabeticallyForStableOrder() {
        val sales = listOf(sale("zed", 100, today), sale("amir", 100, today))
        assertEquals(listOf("amir", "zed"), RevenueLogic.topUsers(sales).map { it.label })
    }

    // ---------- قالب‌بندی ----------

    @Test
    fun formatAmount_groupsThousands() {
        assertEquals("1,250,000", RevenueLogic.formatAmount(1_250_000L))
        assertEquals("0", RevenueLogic.formatAmount(0L))
    }

    @Test
    fun formatAmountShort_isCompact() {
        assertEquals("125 هزار", RevenueLogic.formatAmountShort(125_000L))
        assertEquals("2.5 م", RevenueLogic.formatAmountShort(2_500_000L))
        assertEquals("1.2 میلیارد", RevenueLogic.formatAmountShort(1_200_000_000L))
        assertEquals("999", RevenueLogic.formatAmountShort(999L))
    }
}
