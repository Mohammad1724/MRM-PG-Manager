package com.mrm.pgmanager.utils

import android.graphics.Bitmap

/**
 * ساختِ بیت‌مپِ QR از روی یک متن.
 *
 * zxing با reflection صدا زده می‌شود (همان روشی که در `SubscriptionQrDialog` بود)
 * تا اگر کتابخانه در دسترس نبود اپ کرش نکند و فقط `null` برگردد.
 *
 * این منطق قبلاً داخلِ دیالوگ بود؛ بیرون کشیده شد تا «کارتِ تصویری» هم بتواند
 * از همان استفاده کند و کد دو جا تکرار نشود.
 */
object QrGenerator {

    fun encode(text: String, size: Int = 512, margin: Int = 1): Bitmap? {
        if (text.isBlank()) return null
        return runCatching {
            val writerClass = Class.forName("com.google.zxing.qrcode.QRCodeWriter")
            val formatClass = Class.forName("com.google.zxing.BarcodeFormat")
            val hintClass = Class.forName("com.google.zxing.EncodeHintType")
            val qrCodeFormat = formatClass.getField("QR_CODE").get(null)
            val marginHint = hintClass.getField("MARGIN").get(null)
            val writer = writerClass.getDeclaredConstructor().newInstance()
            val encodeMethod = writerClass.getMethod(
                "encode", String::class.java, formatClass, Int::class.java, Int::class.java, Map::class.java
            )
            val bitMatrix = encodeMethod.invoke(writer, text, qrCodeFormat, size, size, mapOf(marginHint to margin))
            val matrixClass = bitMatrix!!.javaClass
            val getMethod = matrixClass.getMethod("get", Int::class.java, Int::class.java)
            val w = matrixClass.getMethod("getWidth").invoke(bitMatrix) as Int
            val h = matrixClass.getMethod("getHeight").invoke(bitMatrix) as Int
            val pixels = IntArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val isBlack = getMethod.invoke(bitMatrix, x, y) as Boolean
                pixels[y * w + x] = if (isBlack) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
            Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }
}
