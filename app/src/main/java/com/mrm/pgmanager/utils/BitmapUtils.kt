package com.mrm.pgmanager.utils

/**
 * کمک‌توابع بیت‌مپ.
 */
object BitmapUtils {

    /**
     * ضریب کوچک‌سازیِ [android.graphics.BitmapFactory.Options.inSampleSize] برای
     * رساندن یک تصویرِ ابعادِ [outWidth]×[outHeight] به نزدیک‌ترین اندازه‌ای که
     * هنوز از [reqWidth]×[reqHeight] کوچک‌تر نشود.
     *
     * قاعده همان الگوی استانداردِ مستندات اندروید است (توان‌های دو، شروع از ۱) و
     * خالص است تا در تستِ JVM بدونِ چارچوب اندروید قابلِ سنجش باشد.
     */
    fun calculateInSampleSize(outWidth: Int, outHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        if (outWidth <= 0 || outHeight <= 0 || reqWidth <= 0 || reqHeight <= 0) return 1
        var inSampleSize = 1
        var halfW = outWidth / 2
        var halfH = outHeight / 2
        while (halfW / inSampleSize >= reqWidth && halfH / inSampleSize >= reqHeight) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}
