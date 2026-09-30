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

/** یک رویدادِ بازخوردِ سراسری: پیام + لحن. */
data class FeedbackEvent(val message: String, val tone: FeedbackTone)

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

    fun info(message: String) = offer(message, FeedbackTone.Info)
    fun success(message: String) = offer(message, FeedbackTone.Success)
    fun error(message: String) = offer(message, FeedbackTone.Error)
    fun show(message: String, tone: FeedbackTone) = offer(message, tone)

    private fun offer(message: String, tone: FeedbackTone) {
        if (message.isBlank()) return
        channel.trySend(FeedbackEvent(message, tone))
    }

    /** جریانِ رویدادها — فقط یک مصرف‌کننده (میزبانِ ریشه) دارد. */
    val events = channel.receiveAsFlow()
}
