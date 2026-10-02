package com.mrm.pgmanager.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.PanelUser

/*
 *  پلِ بینِ منطق و زبان.
 *
 *  توابعِ محاسباتی (DateLogic، formatBytes، JalaliCalendar) عمداً هیچ متنی
 *  تولید نمی‌کنند؛ آن‌ها «معنا» برمی‌گردانند و ترجمه‌اش اینجا انجام می‌شود.
 *  دلیلش دو چیز است:
 *
 *   ۱. آن لایه‌ها روی JVM و در تست‌ها هم اجرا می‌شوند، جایی که Context وجود
 *      ندارد؛ اگر رشته می‌ساختند یا باید فارسی هاردکد می‌ماندند (که در حالتِ
 *      انگلیسی غلط بود) یا تست‌ها می‌شکستند.
 *   ۲. تستِ روی نوعِ ساخت‌یافته دقیق‌تر از تستِ روی رشته است: تغییرِ یک واژه
 *      دیگر تست را قرمز نمی‌کند.
 */

// ── زمانِ باقی‌مانده ────────────────────────────────────────────────────────

@Composable
fun daysLeftText(expire: String?): String = when (val d = DateLogic.daysLeft(expire)) {
    DateLogic.DaysLeft.Unlimited -> stringResource(R.string.dl_unlimited)
    DateLogic.DaysLeft.Expired -> stringResource(R.string.dl_expired)
    DateLogic.DaysLeft.Today -> stringResource(R.string.dl_today)
    is DateLogic.DaysLeft.Days ->
        if (d.count == 1) stringResource(R.string.dl_one_day)
        else stringResource(R.string.dl_days, d.count)
}

/**
 * زمانِ باقی‌ماندهٔ کاربر با درنظرگرفتنِ on_hold: برای کاربرِ در انتظار، «N روز پس از اولین اتصال»
 * نشان می‌دهد (چون expire ندارد)؛ برای بقیه همان [daysLeftText].
 */
@Composable
fun remainingText(user: PanelUser): String {
    val onHoldDays = user.onHoldDays
    return if (onHoldDays != null) stringResource(R.string.dl_on_hold_days, onHoldDays) else daysLeftText(user.expire)
}

/**
 * نسخهٔ فشرده‌ی [remainingText] برای ردیفِ micro: «19d» به‌جای «19 days»، «∞» به‌جای «Unlimited».
 * برای on_hold همان تعدادِ روز (نشانِ on_hold را بج می‌دهد).
 */
@Composable
fun remainingTextShort(user: PanelUser): String {
    user.onHoldDays?.let { return stringResource(R.string.dl_days_short, it) }
    return when (val d = DateLogic.daysLeft(user.expire)) {
        DateLogic.DaysLeft.Unlimited -> "∞"
        DateLogic.DaysLeft.Expired -> stringResource(R.string.dl_expired)
        DateLogic.DaysLeft.Today -> stringResource(R.string.dl_today)
        is DateLogic.DaysLeft.Days -> stringResource(R.string.dl_days_short, d.count)
    }
}

/** نسخهٔ غیرکامپوزبل برای جاهایی مثل رندرِ کارتِ اشتراک روی بوم. */
fun Context.daysLeftText(expire: String?): String = when (val d = DateLogic.daysLeft(expire)) {
    DateLogic.DaysLeft.Unlimited -> getString(R.string.dl_unlimited)
    DateLogic.DaysLeft.Expired -> getString(R.string.dl_expired)
    DateLogic.DaysLeft.Today -> getString(R.string.dl_today)
    is DateLogic.DaysLeft.Days ->
        if (d.count == 1) getString(R.string.dl_one_day) else getString(R.string.dl_days, d.count)
}

// ── مدتِ روشن‌بودن ─────────────────────────────────────────────────────────

@Composable
fun uptimeText(seconds: Long): String = when (val u = uptimeOf(seconds)) {
    Uptime.None -> "—"
    is Uptime.DaysHours -> stringResource(R.string.up_days_hours, u.days, u.hours)
    is Uptime.HoursMinutes -> stringResource(R.string.up_hours_minutes, u.hours, u.minutes)
    is Uptime.Minutes -> stringResource(R.string.up_minutes, u.minutes)
    is Uptime.Seconds -> stringResource(R.string.up_seconds, u.seconds)
}

// ── نامِ ماهِ شمسی ──────────────────────────────────────────────────────────

@Composable
fun jalaliMonthName(date: JalaliCalendar.Date): String =
    stringArrayResource(R.array.jalali_months)[date.monthIndex()]
