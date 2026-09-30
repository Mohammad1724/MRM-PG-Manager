package com.mrm.pgmanager.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * بازخوردِ لمسی (Haptics) — یکپارچه و سبک.
 *
 * - [tick]    لمسِ سبک: تغییر وضعیت/انتخاب (چک‌باکس، فیلتر)
 * - [confirm] تأییدِ میانه: پایانِ موفقِ عملیات (عملیات گروهی، ذخیره)
 * - [warn]    هشدار: پیش از عملِ مخرب (حذف، باطل‌سازی)
 *
 * همه فراخوانی‌ها بی‌صدا و بدونِ exception به پایان می‌رسند؛ گوشیِ بدون
 * ویبراتور یا دستگاهِ در حالِ بی‌صدا نباید کرش کند.
 */
object Haptics {

    private fun vibrate(context: Context, ms: Long, amplitude: Int) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
        } catch (_: Exception) {
            // بازخوردِ لمسی، سرویسِ حیاتی نیست.
        }
    }

    fun tick(context: Context) = vibrate(context, 12, 64)

    fun confirm(context: Context) = vibrate(context, 28, 120)

    fun warn(context: Context) = vibrate(context, 60, 180)
}
