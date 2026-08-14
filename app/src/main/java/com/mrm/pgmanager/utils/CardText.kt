package com.mrm.pgmanager.utils

/**
 * کمک‌تابع‌های متنیِ «کارتِ تصویریِ اشتراک».
 *
 * عمداً در فایلِ جدا و **بدونِ هیچ importی از اندروید** نگه داشته شده‌اند تا
 * بشود مستقیم روی JVM تستشان کرد (بقیهٔ `SubscriptionCard` به `android.graphics`
 * وابسته است و فقط در CI کامپایل می‌شود).
 */
object CardText {

    /**
     * تبدیلِ ارقامِ لاتین به فارسی.
     * روی خروجیِ [formatBytes] عمداً اعمال نمی‌شود چون واحدها (GB/MB) لاتین‌اند
     * و قاطی شدنِ ارقامِ فارسی با واحدِ لاتین بدنما می‌شود.
     */
    fun toPersianDigits(s: String): String =
        s.map { if (it in '0'..'9') ('۰' + (it - '0')) else it }.joinToString("")

    /**
     * کوتاه‌کردنِ متن تا در عرضِ داده‌شده جا شود، با «…» در انتها.
     *
     * تابعِ اندازه‌گیری از بیرون تزریق می‌شود تا این منطق به `Paint`ِ اندروید
     * وابسته نباشد. با جست‌وجوی دودویی بلندترین پیشوندی را پیدا می‌کند که
     * به‌همراهِ «…» هنوز جا می‌شود.
     */
    fun truncateToWidth(text: String, maxWidth: Float, measure: (String) -> Float): String {
        if (text.isEmpty() || measure(text) <= maxWidth) return text
        val ellipsis = "…"
        var lo = 0
        var hi = text.length
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (measure(text.take(mid) + ellipsis) <= maxWidth) lo = mid else hi = mid - 1
        }
        return if (lo == 0) ellipsis else text.take(lo) + ellipsis
    }
}
