package com.mrm.pgmanager.utils

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * بازه‌ی صدور فاکتور: شروع/پایان دوره و تعداد روزهای آن.
 */
data class InvoicePeriod(
    val start: LocalDate,
    val end: LocalDate?,
    val durationDays: Long
)

/**
 * محاسبه‌ی بازه‌ی فاکتور با ترتیب اولویتِ تاریخ شروع:
 *
 *  1. تاریخ آخرین **تمدیدِ ثبت‌شده** — وقتی فروشنده expire را تمدید می‌کند،
 *     `created_at` کاربر در پنل دست‌نخورده می‌ماند و دوره‌ی جدید عملاً از روز
 *     تمدید شروع می‌شود (اپ هنگام تمدید expire = امروز + N روز می‌گذارد).
 *  2. `created_at` واقعی کاربر — برای کاربرِ تازه‌ساخته که هرگز تمدید نشده.
 *  3. امروز — وقتی هیچ‌کدام در دسترس نیست (همان fallback قدیمی، با این تفاوت
 *     که حالا آخرین حلقه‌ی زنجیره است نه تنها انتخاب).
 *
 * پایان نامحدود (expire خالی/صفر) → [InvoicePeriod.durationDays] صفر و لایه‌ی UI
 * آن را «نامحدود» نمایش می‌دهد. تابع خالص است تا در تست JVM بدون اندروید سنجیده شود.
 */
fun computeInvoicePeriod(
    createdAtIso: String?,
    expireIso: String?,
    renewalIso: String?,
    today: LocalDate = LocalDate.now()
): InvoicePeriod {
    val end = DateLogic.expiryDate(expireIso)
    val start = DateLogic.expiryDate(renewalIso)
        ?: DateLogic.expiryDate(createdAtIso)
        ?: today
    val days = if (end != null) ChronoUnit.DAYS.between(start, end).coerceAtLeast(0L) else 0L
    return InvoicePeriod(start = start, end = end, durationDays = days)
}
