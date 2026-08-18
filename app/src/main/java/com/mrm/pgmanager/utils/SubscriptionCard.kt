package com.mrm.pgmanager.utils

import com.mrm.pgmanager.R

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import com.mrm.pgmanager.data.model.PanelUser
import java.io.File
import java.io.FileOutputStream

/**
 * ساختِ «کارتِ تصویریِ اشتراک» — یک عکسِ آمادهٔ ارسال برای مشتری که
 * QR، نامِ کاربری، حجم، تاریخِ انقضا و نامِ برندِ فروشنده را یک‌جا دارد.
 *
 * چرا عکس و نه فقط لینک: مشتری‌ها لینکِ خام را گم می‌کنند، ولی عکس در گالریِ
 * تلگرام می‌ماند و همه‌چیزِ لازم را یک‌جا دارد.
 *
 * این فایل عمداً **هیچ وابستگی‌ای به Compose ندارد** تا بشود مستقل تستش کرد؛
 * فقط از Canvas و Paintِ خودِ اندروید استفاده می‌کند.
 */
object SubscriptionCard {

    private const val W = 1000
    private const val PAD = 60f

    // ارتفاعِ هر بخش — برای محاسبهٔ ارتفاعِ نهاییِ کارت پیش از رسم
    private const val TOP_OFFSET = 40f
    private const val LOGO_BLOCK = 120f + 24f
    private const val SELLER_BLOCK = 76f
    private const val USERNAME_BLOCK = 92f
    private const val QR_BOX = 520f
    private const val QR_BLOCK = QR_BOX + 56f
    private const val INFO_BLOCK = 150f + 40f
    private const val LINK_BLOCK = 52f
    private const val DATE_GAP = 70f

    // پالت — مستقل از تمِ اپ، چون خروجی باید روی هر پس‌زمینه‌ای در تلگرام خوانا باشد
    private const val INK = 0xFF111827.toInt()
    private const val MUTED = 0xFF6B7280.toInt()
    private const val BORDER = 0xFFE5E7EB.toInt()
    private const val GREEN = 0xFF22C55E.toInt()
    private const val AMBER = 0xFFF59E0B.toInt()
    private const val RED = 0xFFEF4444.toInt()

    /**
     * تبدیلِ ارقامِ لاتین به فارسی. روی خروجیِ [formatBytes] عمداً اعمال **نمی‌شود**
     * چون واحدها (GB/MB) لاتین‌اند و قاطی شدنشان بدنما می‌شود.
     */
    private fun fa(s: String): String = CardText.toPersianDigits(s)

    private fun paint(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.CENTER) =
        TextPaint().apply {
            isAntiAlias = true
            textSize = size
            this.color = color
            textAlign = align
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    /**
     * کارت را می‌سازد و به‌صورت PNG در `cacheDir/shared` ذخیره می‌کند.
     * مسیرِ `shared/` از قبل در `file_paths.xml` برای FileProvider تعریف شده است.
     *
     * @param qr تصویرِ QR که بیرون ساخته می‌شود (تا این کلاس به zxing وابسته نشود)
     * @return فایلِ ساخته‌شده، یا `null` اگر چیزی خطا داد
     */
    fun generate(
        context: Context,
        user: PanelUser,
        qr: Bitmap?,
        sellerName: String = "",
        logoPath: String? = null,
        isFa: Boolean = true
    ): File? = runCatching {
        // ── لوگو (اختیاری) — پیش از محاسبهٔ ارتفاع بارگذاری می‌شود چون روی ارتفاع اثر دارد
        val logo = if (!logoPath.isNullOrBlank()) {
            runCatching {
                BitmapFactory.decodeFile(logoPath, BitmapFactory.Options().apply { inSampleSize = 2 })
            }.getOrNull()
        } else null

        // ارتفاعِ کارت از روی محتوای واقعی حساب می‌شود، نه یک عددِ ثابت.
        // وگرنه اگر لوگو یا نامِ برند نداشته باشیم، پایینِ کارت فضای مردهٔ بزرگی می‌ماند.
        val contentEnd = PAD + TOP_OFFSET +
            (if (logo != null) LOGO_BLOCK else 0f) +
            (if (sellerName.isNotBlank()) SELLER_BLOCK else 0f) +
            USERNAME_BLOCK + QR_BLOCK + INFO_BLOCK +
            (if (user.subUrl.isNotBlank()) LINK_BLOCK else 0f)
        val h = (contentEnd + DATE_GAP + 8f + PAD).toInt()

        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)

        val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        val stroke = Paint().apply {
            isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 2f; color = BORDER
        }

        // قابِ بیرونی
        val card = RectF(PAD / 2, PAD / 2, W - PAD / 2, h - PAD / 2)
        fill.color = Color.WHITE
        c.drawRoundRect(card, 40f, 40f, fill)
        c.drawRoundRect(card, 40f, 40f, stroke)

        var y = PAD + TOP_OFFSET
        val cx = W / 2f

        if (logo != null) {
            val size = 120f
            val dst = RectF(cx - size / 2, y, cx + size / 2, y + size)
            c.drawBitmap(logo, null, dst, Paint().apply { isAntiAlias = true; isFilterBitmap = true })
            y += LOGO_BLOCK
        }

        // ── نامِ برند
        if (sellerName.isNotBlank()) {
            c.drawText(sellerName, cx, y + 40f, paint(46f, INK, bold = true))
            y += SELLER_BLOCK
        }

        // ── نامِ کاربری (لاتین است، پس ارقامش فارسی نمی‌شود)
        c.drawText(user.username, cx, y + 44f, paint(52f, INK, bold = true))
        y += USERNAME_BLOCK

        // ── QR در کادرِ سفید با حاشیه
        val qrRect = RectF(cx - QR_BOX / 2, y, cx + QR_BOX / 2, y + QR_BOX)
        fill.color = Color.WHITE
        c.drawRoundRect(qrRect, 28f, 28f, fill)
        c.drawRoundRect(qrRect, 28f, 28f, stroke)
        if (qr != null) {
            val inset = 24f
            c.drawBitmap(
                qr, null,
                RectF(qrRect.left + inset, qrRect.top + inset, qrRect.right - inset, qrRect.bottom - inset),
                Paint().apply { isAntiAlias = true; isFilterBitmap = true }
            )
        } else {
            c.drawText(
                if (isFa) context.getString(R.string.sc_qr_unavailable) else "QR unavailable",
                cx, qrRect.centerY(), paint(28f, MUTED)
            )
        }
        y += QR_BLOCK

        // ── ردیفِ اطلاعات: حجم و انقضا
        val used = formatBytes(user.usedTraffic)
        val total = if (user.dataLimit == 0L) (if (isFa) context.getString(R.string.sc_unlimited) else "Unlimited") else formatBytes(user.dataLimit)
        val trafficText = if (user.dataLimit == 0L) total else "$used / $total"
        val daysText = context.daysLeftText(user.expire)

        fun infoBox(left: Float, right: Float, label: String, value: String, valueColor: Int) {
            val r = RectF(left, y, right, y + 150f)
            fill.color = 0xFFF9FAFB.toInt()
            c.drawRoundRect(r, 24f, 24f, fill)
            c.drawRoundRect(r, 24f, 24f, stroke)
            val mid = (left + right) / 2
            c.drawText(label, mid, y + 56f, paint(28f, MUTED))
            c.drawText(value, mid, y + 108f, paint(36f, valueColor, bold = true))
        }

        val gap = 20f
        val half = (W - PAD * 2 - gap) / 2
        // رنگِ انقضا با میزانِ فوریت هماهنگ می‌شود
        val remaining = DateLogic.remainingDays(user.expire)
        val expiryColor = when {
            remaining == null -> INK
            remaining <= 0L -> RED
            remaining <= 3L -> AMBER
            else -> GREEN
        }
        infoBox(PAD, PAD + half, if (isFa) context.getString(R.string.sc_data) else "Traffic", trafficText, INK)
        infoBox(PAD + half + gap, W - PAD, if (isFa) context.getString(R.string.sc_validity) else "Validity", daysText, expiryColor)
        y += INFO_BLOCK

        // ── لینکِ اشتراک (کوچک، برای وقتی که QR اسکن نمی‌شود)
        if (user.subUrl.isNotBlank()) {
            val linkPaint = paint(22f, MUTED)
            val link = CardText.truncateToWidth(user.subUrl, W - PAD * 2) { linkPaint.measureText(it) }
            c.drawText(link, cx, y + 24f, linkPaint)
            y += LINK_BLOCK
        }

        // ── تاریخِ ساخت
        val today = if (isFa) fa(JalaliCalendar.todayJalali().toString()) else
            java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.US).format(java.util.Date())
        c.drawText(today, cx, h - PAD - 8f, paint(24f, MUTED))

        // ── ذخیره
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        // پاک‌کردنِ فایل‌های قدیمی‌تر از یک ساعت تا کش انباشته نشود
        dir.listFiles()?.forEach {
            if (it.lastModified() < System.currentTimeMillis() - 3_600_000L) it.delete()
        }
        val out = File(dir, "card-${user.username}.png")
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        out
    }.getOrNull()
}
