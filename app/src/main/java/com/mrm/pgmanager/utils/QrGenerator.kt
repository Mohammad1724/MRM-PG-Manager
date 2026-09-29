package com.mrm.pgmanager.utils

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * ساختِ بیت‌مپِ QR از روی یک متن.
 *
 * نسخهٔ قبلی zxing را با reflection صدا می‌زد — آن هم برای *تک‌تکِ پیکسل‌ها*
 * (`get(x, y)` × ۵۱۲×۵۱۲ ≈ ۲۶۰ هزار فراخوانیِ بازتابی با boxing)؛ یعنی بازشدنِ
 * دیالوگِ QR نیم‌ثانیه تا یک ثانیه نخِ اصلی را می‌گرفت. zxing وابستگیِ مستقیمِ
 * اپ است، پس مستقیم صدا زده می‌شود و کلِ کار چند میلی‌ثانیه طول می‌کشد.
 * خطاهای zxing (متنِ خیلی بلند و …) همچنان به `null` تبدیل می‌شوند.
 */
object QrGenerator {

    fun encode(text: String, size: Int = 512, margin: Int = 1): Bitmap? {
        if (text.isBlank()) return null
        return runCatching {
            val hints = mapOf(EncodeHintType.MARGIN to margin)
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val w = matrix.width
            val h = matrix.height
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    pixels[row + x] = if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                }
            }
            Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }
}
