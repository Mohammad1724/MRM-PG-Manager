package com.mrm.pgmanager.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import com.mrm.pgmanager.data.model.DebtorInfo
import com.mrm.pgmanager.data.model.PanelUser
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*

object PdfInvoiceGenerator {

    private const val PAGE_WIDTH = 595
    private const val MARGIN = 50f

    private val darkColor = Color.rgb(0x20, 0x21, 0x24)
    private val grayColor = Color.rgb(0x74, 0x75, 0x7B)
    private val lightGray = Color.rgb(0xF8, 0xF8, 0xFA)
    private val borderGray = Color.rgb(0xE8, 0xE8, 0xEC)
    private val greenColor = Color.rgb(0x1A, 0x8C, 0x5B)
    private val redColor = Color.rgb(0xC9, 0x3B, 0x3B)
    private val primaryColor = Color.rgb(0xF4, 0xC9, 0x28)

    /**
     * تولید PDF مینیمال فاکتور
     * @param currentPrice قیمت این دوره (واردشده دستی توسط کاربر)
     * @param previousDebt بدهی قبلی (واردشده دستی)
     * اگر این دو null باشند، فاکتور "پرداخت شده" نمایش می‌دهد
     */
    fun generate(
        context: Context,
        user: PanelUser,
        debtorInfo: DebtorInfo? = null,
        currency: String = "تومان",
        currentPrice: Long? = null,
        previousDebt: Long? = null,
        paidAmount: Long? = null,
        notes: String = "",
        logoPath: String? = null,
        sellerName: String = "",
        isFullyPaid: Boolean = false,
        totalBilled: Long = 0L,
        remainingDebt: Long = 0L
    ): File {
        val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val file = File(dir, "invoice-${user.username}-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.pdf")

        val cp = currentPrice ?: 0L
        val pd = previousDebt ?: if (currentPrice == null && debtorInfo != null) debtorInfo.amount else 0L
        val paid = paidAmount ?: 0L
        val total = if (totalBilled > 0) totalBilled else cp + pd
        val remaining = if (paid > 0) (total - paid).coerceAtLeast(0L) else total
        val isPaid = isFullyPaid && total > 0L

        // محاسبه ارتفاع صفحه پویا
        val pageHeight = 720
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val persianTypeface = Typeface.create("sans-serif", Typeface.NORMAL)

        val brandPaint = TextPaint().apply {
            color = darkColor; textSize = 20f; isAntiAlias = true
            typeface = Typeface.create(persianTypeface, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        val headerPaint = TextPaint().apply {
            color = grayColor; textSize = 12f; isAntiAlias = true
            typeface = persianTypeface; textAlign = Paint.Align.CENTER
        }
        val labelPaint = TextPaint().apply {
            color = grayColor; textSize = 11f; isAntiAlias = true
            typeface = persianTypeface; textAlign = Paint.Align.RIGHT
        }
        val valPaint = TextPaint().apply {
            color = darkColor; textSize = 12f; isAntiAlias = true
            typeface = Typeface.create(persianTypeface, Typeface.BOLD); textAlign = Paint.Align.LEFT
        }
        val totalTextPaint = TextPaint().apply {
            isAntiAlias = true; textAlign = Paint.Align.LEFT
        }
        val smallPaint = TextPaint().apply {
            color = Color.rgb(0xA0, 0x9C, 0x94); textSize = 9f; isAntiAlias = true
            typeface = persianTypeface; textAlign = Paint.Align.CENTER
        }
        val thanksPaint = TextPaint().apply {
            color = grayColor; textSize = 11f; isAntiAlias = true
            typeface = persianTypeface; textAlign = Paint.Align.CENTER
        }

        val borderPaint = Paint().apply {
            color = borderGray; style = Paint.Style.STROKE; strokeWidth = 1.5f; isAntiAlias = true
        }
        val fillPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }

        var y = MARGIN

        // پس‌زمینه سفید کارت
        val cardRect = RectF(MARGIN - 10f, MARGIN - 10f, PAGE_WIDTH - MARGIN + 10f, pageHeight - MARGIN + 10f)
        fillPaint.color = Color.WHITE
        canvas.drawRoundRect(cardRect, 24f, 24f, fillPaint)
        borderPaint.color = borderGray
        canvas.drawRoundRect(cardRect, 24f, 24f, borderPaint)

        // ==== لوگو ====
        val logoBitmap: Bitmap? = if (!logoPath.isNullOrBlank()) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(logoPath, opts)
            }.getOrNull()
        } else null

        val logoSize = 90f
        val cx = PAGE_WIDTH / 2f
        if (logoBitmap != null) {
            val dst = RectF(cx - logoSize/2, y, cx + logoSize/2, y + logoSize)
            canvas.drawBitmap(logoBitmap, null, dst, null)
        } else {
            val phRect = RectF(cx - logoSize/2, y, cx + logoSize/2, y + logoSize)
            fillPaint.color = Color.rgb(0xFF, 0xF8, 0xE1)
            canvas.drawRoundRect(phRect, 18f, 18f, fillPaint)
            borderPaint.color = Color.argb(100, 0xF4, 0xC9, 0x28)
            canvas.drawRoundRect(phRect, 18f, 18f, borderPaint)
            val phPaint = TextPaint().apply {
                color = Color.rgb(0xD4, 0xA8, 0x00); textSize = 28f
                typeface = Typeface.create(persianTypeface, Typeface.BOLD); textAlign = Paint.Align.CENTER; isAntiAlias = true
            }
            canvas.drawText(if (sellerName.isNotBlank()) sellerName.take(3) else "MRM", cx, y + logoSize/2 + 10f, phPaint)
        }
        y += logoSize + 14f

        // ==== نام برند ====
        if (sellerName.isNotBlank()) {
            canvas.drawText(sellerName, cx, y, brandPaint)
            y += 24f
        }
        canvas.drawText("فاکتور اشتراک VPN", cx, y, headerPaint)
        y += 28f

        // ==== خط طلایی ====
        fillPaint.color = primaryColor
        canvas.drawRect(cx - 100f, y, cx + 100f, y + 3f, fillPaint)
        y += 22f

        // ==== محاسبات اطلاعات ====
        val endJalali = JalaliCalendar.isoToShamsi(user.expire ?: "").ifBlank { "نامحدود" }
        val daysRemaining = runCatching {
            val e = try { Instant.parse(user.expire).atZone(ZoneId.systemDefault()).toLocalDate() }
            catch (_: Exception) { LocalDate.parse(user.expire?.take(10) ?: "") }
            ChronoUnit.DAYS.between(LocalDate.now(), e).coerceAtLeast(0)
        }.getOrDefault(0)
        val durationDays = daysRemaining.coerceAtLeast(0)
        val startJalali = JalaliCalendar.isoToShamsi(LocalDate.now().toString()).ifBlank { "-" }
        val durationText = when {
            durationDays <= 0 -> "نامحدود"
            durationDays == 1L -> "1 روزه"
            durationDays < 30 -> "$durationDays روزه"
            durationDays == 30L -> "1 ماهه"
            durationDays < 365 -> {
                val months = durationDays / 30
                val extraDays = durationDays % 30
                if (extraDays == 0) "${months} ماهه" else "${months} ماه و ${extraDays} روزه"
            }
            else -> "${durationDays/30} ماهه"
        }
        val dataLimitText = if (user.dataLimit == 0L) "نامحدود" else formatBytes(user.dataLimit)
        val invoiceDateJalali = JalaliCalendar.todayJalali().toString()

        // ==== کارت مشخصات ====
        val infoBoxH = 170f
        val infoRect = RectF(MARGIN + 10f, y, PAGE_WIDTH - MARGIN - 10f, y + infoBoxH)
        fillPaint.color = lightGray
        canvas.drawRoundRect(infoRect, 16f, 16f, fillPaint)
        borderPaint.color = borderGray
        canvas.drawRoundRect(infoRect, 16f, 16f, borderPaint)

        var ry = y + 24f
        val rowH = 28f
        val rightX = PAGE_WIDTH - MARGIN - 26f
        val leftX = MARGIN + 26f

        fun drawRow(label: String, value: String, color: Int = darkColor, bold: Boolean = true) {
            canvas.drawText(label, rightX, ry, labelPaint)
            val vp = TextPaint(valPaint).apply { this.color = color; this.typeface = if (bold) Typeface.create(persianTypeface, Typeface.BOLD) else persianTypeface }
            canvas.drawText(value, leftX, ry, vp)
            ry += rowH
        }

        drawRow("نام کاربری", user.username, bold = true)
        drawRow("حجم اشتراک", dataLimitText, bold = true)
        drawRow("مدت اشتراک", durationText, color = Color.rgb(0xD4, 0xA8, 0x00), bold = true)
        drawRow("تاریخ شروع", startJalali)
        drawRow("تاریخ پایان", endJalali, color = redColor, bold = true)

        y += infoBoxH + 18f

        // ==== کارت مبالغ ====
        var priceRows = 0
        if (cp > 0) priceRows++
        if (pd > 0) priceRows++
        if (total > 0) priceRows++
        if (paid > 0) priceRows++
        if (paid > 0 && remaining > 0) priceRows++
        val priceBoxH = when {
            priceRows <= 2 -> 100f
            priceRows <= 4 -> 150f
            else -> 180f
        }
        val priceRect = RectF(MARGIN + 10f, y, PAGE_WIDTH - MARGIN - 10f, y + priceBoxH)
        fillPaint.color = lightGray
        canvas.drawRoundRect(priceRect, 16f, 16f, fillPaint)
        borderPaint.color = borderGray
        canvas.drawRoundRect(priceRect, 16f, 16f, borderPaint)

        ry = y + 22f

        if (cp > 0) {
            canvas.drawText("قیمت این دوره", rightX, ry, labelPaint)
            canvas.drawText("%,d %s".format(Locale.US, cp, currency), leftX, ry, valPaint)
            ry += rowH
        }
        if (pd > 0) {
            canvas.drawText("بدهی قبلی", rightX, ry, labelPaint)
            val pdP = TextPaint(valPaint).apply { color = redColor }
            canvas.drawText("%,d %s".format(Locale.US, pd, currency), leftX, ry, pdP)
            ry += rowH
        }
        if (total > 0) {
            fillPaint.color = borderGray
            canvas.drawRect(MARGIN + 30f, ry - 4f, PAGE_WIDTH - MARGIN - 30f, ry - 3f, fillPaint)
            ry += 14f
            canvas.drawText("جمع کل", rightX, ry, TextPaint(valPaint).apply { textAlign = Paint.Align.RIGHT })
            canvas.drawText("%,d %s".format(Locale.US, total, currency), leftX, ry, valPaint)
            ry += rowH
        }
        if (paid > 0) {
            val paidP = TextPaint(valPaint).apply { color = greenColor }
            canvas.drawText("پرداخت شده", rightX, ry, labelPaint)
            canvas.drawText("%,d %s".format(Locale.US, paid, currency), leftX, ry, paidP)
            ry += rowH
        }
        if (paid > 0 && remaining > 0) {
            val remP = TextPaint(valPaint).apply { color = redColor }
            canvas.drawText("مانده بدهی", rightX, ry, labelPaint)
            canvas.drawText("%,d %s".format(Locale.US, remaining, currency), leftX, ry, remP)
            ry += rowH
        }

        fillPaint.color = borderGray
        canvas.drawRect(MARGIN + 30f, ry - 4f, PAGE_WIDTH - MARGIN - 30f, ry - 3f, fillPaint)
        ry += 16f

        val hasAnyAmount = cp > 0L || pd > 0L || paid > 0L
        // مجموع نهایی
        val finalColor = when {
            isPaid && total > 0L -> greenColor
            !hasAnyAmount -> grayColor
            else -> redColor
        }
        val finalLabel = when {
            isPaid && total > 0L -> "پرداخت شده"
            !hasAnyAmount -> "وضعیت"
            paid > 0 && remaining > 0 -> "مانده قابل پرداخت"
            else -> "مبلغ قابل پرداخت"
        }
        val finalTextStr = when {
            isPaid && total > 0L -> "تسویه کامل"
            !hasAnyAmount -> "بدون مبلغ"
            else -> "%,d %s".format(Locale.US, remaining, currency)
        }
        canvas.drawText(finalLabel, rightX, ry, TextPaint(valPaint).apply { textAlign = Paint.Align.RIGHT })
        totalTextPaint.color = finalColor
        totalTextPaint.textSize = 16f
        totalTextPaint.typeface = Typeface.create(persianTypeface, Typeface.BOLD)
        canvas.drawText(finalTextStr, leftX, ry, totalTextPaint)

        y += priceBoxH + 12f

        // ==== یادداشت ====
        if (notes.isNotBlank()) {
            val noteH = 60f
            val noteRect = RectF(MARGIN + 10f, y, PAGE_WIDTH - MARGIN - 10f, y + noteH)
            fillPaint.color = lightGray
            canvas.drawRoundRect(noteRect, 12f, 12f, fillPaint)
            val noteLabelPaint = TextPaint(labelPaint).apply { color = grayColor; textSize = 10f }
            canvas.drawText("یادداشت", rightX, y + 20f, noteLabelPaint)
            val notePaint = TextPaint(valPaint).apply { textSize = 11f; color = darkColor }
            val truncated = if (notes.length > 80) notes.take(80) + "..." else notes
            canvas.drawText(truncated, MARGIN + 26f, y + 44f, notePaint)
            y += noteH + 18f
        }

        // ==== تشکر و تاریخ ====
        canvas.drawText("با تشکر از انتخاب شما", cx, y, thanksPaint)
        y += 20f
        canvas.drawText("تاریخ صدور: $invoiceDateJalali", cx, y, smallPaint)

        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()

        logoBitmap?.recycle()
        return file
    }
}
