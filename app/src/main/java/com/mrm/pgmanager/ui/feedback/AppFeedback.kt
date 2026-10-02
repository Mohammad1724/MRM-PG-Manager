package com.mrm.pgmanager.ui.feedback

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * لحنِ بازخورد — رنگِ نقطه و مدتِ نمایش را تعیین می‌کند.
 *
 * Info    → خنثی (اکسنتِ طلایی)، ۳ ثانیه
 * Success → سبز، ۳ ثانیه
 * Error   → قرمز، ۵ ثانیه
 */
enum class FeedbackTone { Info, Success, Error }

/**
 * یک رویدادِ بازخوردِ سراسری: پیام + لحن (+ اکشنِ اختیاری).
 *
 * [actionLabel] و [onAction] برای «بازگرداندن» (Undo) و «تلاش دوباره» است:
 * پیام تا وقتی اکشن دارد ۶.۵ ثانیه روی صفحه می‌ماند تا کاربر فرصتِ زدنش را
 * داشته باشد؛ بعد از آن اکشن منقضی می‌شود (خودِ عملیات ولی انجام شده است).
 */
data class FeedbackEvent(
    val message: String,
    val tone: FeedbackTone,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

/**
 * اتوبوسِ بازخوردِ سراسریِ اپ — جایگزینِ Toastهای پراکنده.
 *
 * چرا به‌جای Snackbar مستقیم؟ چون باید در **ریشهٔ UI** (بالایِ لاگین/قفل/اصلی)
 * نمایش داده شود تا با عوض‌شدنِ شاخه (مثلاً انقضای نشست ← صفحهٔ ورود) پیام
 * گم نشود. صفحه‌ها فقط `AppFeedback.success(…)` صدا می‌زنند؛ میزبانِ نمایش
 * در `AppFeedbackHost` (ریشهٔ MRMApp) نشسته است.
 *
 * `trySend` بدونِ block از هر thread امن است؛ بافرِ Channel از دست‌رفتنِ
 * پیامِ هم‌زمان جلوگیری می‌کند (آخرین پیام جایگزینِ قبلی می‌شود).
 */
object AppFeedback {
    private val channel = Channel<FeedbackEvent>(Channel.BUFFERED)

    fun info(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) =
        offer(FeedbackEvent(message, FeedbackTone.Info, actionLabel, onAction))

    fun success(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) =
        offer(FeedbackEvent(message, FeedbackTone.Success, actionLabel, onAction))

    fun error(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) =
        offer(FeedbackEvent(message, FeedbackTone.Error, actionLabel, onAction))
    fun show(message: String, tone: FeedbackTone) = offer(message, tone)

    private fun offer(message: String, tone: FeedbackTone) {
        if (message.isBlank()) return
        channel.trySend(FeedbackEvent(message, tone))
    }

    /** جریانِ رویدادها — فقط یک مصرف‌کننده (میزبانِ ریشه) دارد. */
    val events = channel.receiveAsFlow()
}
