package com.mrm.pgmanager.utils

import android.content.Context
import com.mrm.pgmanager.R
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
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
     * اگر این دو null باشند، فاکتور «پرداخت شده» نمایش می‌دهد
     */
    fun generate(
        context: Context,
        user: PanelUser,
        debtorInfo: DebtorInfo? = null,
        currency: String = context.getString(R.string.inv_currency),
        currentPrice: Long? = null,
        previousDebt: Long? = null,
        paidAmount: Long? = null,
        notes: String = "",
        logoPath: String? = null,
        sellerName: String = "",
        isFullyPaid: Boolean = false,
        totalBilled: Long = 0L
    ): File {
        val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val file = File(dir, "invoice-${user.username}-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.pdf")

        val cp = currentPrice ?: 0L
        val pd = previousDebt ?: if (currentPrice == null && debtorInfo != null) debtorInfo.amount else 0L
        val paid = paidAmount ?: 0L
        val total = if (totalBilled > 0L) totalBilled else cp + pd
        val remaining = if (paid > 0L) (total - paid).coerceAtLeast(0L) else total
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
            drawFaCentered(canvas, if (sellerName.isNotBlank()) sellerName.take(3) else "MRM", phPaint, y + logoSize/2 + 10f)
        }
        y += logoSize + 14f

        // ==== نام برند ====
        if (sellerName.isNotBlank()) {
            drawFaCentered(canvas, sellerName, brandPaint, y)
            y += 24f
        }
        drawFaCentered(canvas, context.getString(R.string.inv_line_title), headerPaint, y)
        y += 28f

        // ==== خط طلایی ====
        fillPaint.color = primaryColor
        canvas.drawRect(cx - 100f, y, cx + 100f, y + 3f, fillPaint)
        y += 22f

        // ==== محاسبات اطلاعات ====
        // تاریخ پایان
        val endJalali = JalaliCalendar.isoToShamsi(user.expire ?: "").ifBlank { context.getString(R.string.inv_unlimited) }
        val endDate: LocalDate? = runCatching {
            try { Instant.parse(user.expire).atZone(ZoneId.systemDefault()).toLocalDate() }
            catch (_: Exception) { LocalDate.parse(user.expire?.take(10) ?: "") }
        }.getOrNull()

        // تاریخ شروع: از created_at واقعی کاربر، نه امروز!
        // اگر created_at موجود نباشد، از امروز منهای مدت باقی‌مانده حساب نمی‌کنیم، بلکه امروز را به عنوان fallback می‌گذاریم
        // اما مدت کل باید از شروع تا پایان باشد، نه فقط باقی‌مانده
        val startDate: LocalDate? = runCatching {
            val created = user.createdAt
            if (created.isNullOrBlank() || created == "0" || created == "null") null
            else try { Instant.parse(created).atZone(ZoneId.systemDefault()).toLocalDate() }
            catch (_: Exception) { LocalDate.parse(created.take(10)) }
        }.getOrNull()

        val effectiveStartDate = startDate ?: run {
            // اگر createdAt نداریم، شروع را از روی مدت باقی‌مانده تخمین نزن، فقط امروز را بگذار
            // اما اگر endDate داریم، مدت را از امروز تا پایان حساب نکن، بلکه اگر createdAt نیست،
            // شروع = امروز و مدت = باقی‌مانده است (برای کاربر تازه ساخته شده منطقی است)
            // برای کاربر قدیمی که createdAt ندارد، حداقل شروع را امروز نگذار که گمراه کننده است - از 30 روز قبل تخمین بزن؟
            // بهترین: اگر createdAt نداریم، شروع را امروز بگذار ولی مدت را از امروز تا پایان حساب کن
            LocalDate.now()
        }

        val durationDays = if (endDate != null) {
            val s = startDate ?: effectiveStartDate
            ChronoUnit.DAYS.between(s, endDate).coerceAtLeast(0L)
        } else 0L

        val startJalali = if (startDate != null) {
            JalaliCalendar.isoToShamsi(startDate.toString()).ifBlank { "-" }
        } else {
            // اگر createdAt نداریم، سعی کن از روی مدت باقی‌مانده، شروع واقعی را حدس بزنی؟
            // برای فاکتور، بهتر است تاریخ ساخت واقعی را نشان دهیم، اگر نداریم امروز را نشان بده ولی با برچسب «شروع دوره»
            JalaliCalendar.isoToShamsi(effectiveStartDate.toString()).ifBlank { "-" }
        }
        val durationText = when {
            durationDays <= 0L -> context.getString(R.string.inv_unlimited)
            durationDays == 1L -> context.getString(R.string.inv_one_day)
            durationDays < 30L -> context.getString(R.string.inv_days, durationDays.toInt())
            durationDays == 30L -> context.getString(R.string.inv_one_month)
            durationDays < 365L -> {
                val months = durationDays / 30L
                val extraDays = durationDays % 30L
                if (extraDays == 0L) context.getString(R.string.inv_months, months.toInt()) else context.getString(R.string.inv_months_days, months.toInt(), extraDays.toInt())
            }
            else -> context.getString(R.string.inv_months, (durationDays / 30L).toInt())
        }
        val dataLimitText = if (user.dataLimit == 0L) context.getString(R.string.inv_unlimited) else formatBytes(user.dataLimit)
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
            drawFaRight(canvas, label, labelPaint, leftX, rightX, ry)
            val vp = TextPaint(valPaint).apply { this.color = color; this.typeface = if (bold) Typeface.create(persianTypeface, Typeface.BOLD) else persianTypeface }
            drawFaLeft(canvas, value, vp, leftX, rightX, ry)
            ry += rowH
        }

        drawRow(context.getString(R.string.inv_username), user.username, bold = true)
        drawRow(context.getString(R.string.inv_data), dataLimitText, bold = true)
        drawRow(context.getString(R.string.inv_duration), durationText, color = Color.rgb(0xD4, 0xA8, 0x00), bold = true)
        drawRow(context.getString(R.string.inv_start), startJalali)
        drawRow(context.getString(R.string.inv_end), endJalali, color = redColor, bold = true)

        y += infoBoxH + 18f

        // ==== کارت مبالغ ====
        var priceRows = 0
        if (cp > 0L) priceRows++
        if (pd > 0L) priceRows++
        if (total > 0L) priceRows++
        if (paid > 0L) priceRows++
        if (paid > 0L && remaining > 0L) priceRows++
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

        if (cp > 0L) {
            drawFaRight(canvas, context.getString(R.string.inv_price), labelPaint, leftX, rightX, ry)
            drawFaLeft(canvas, "%,d %s".format(Locale.US, cp, currency), valPaint, leftX, rightX, ry)
            ry += rowH
        }
        if (pd > 0L) {
            drawFaRight(canvas, context.getString(R.string.inv_previous_debt), labelPaint, leftX, rightX, ry)
            val pdP = TextPaint(valPaint).apply { color = redColor }
            drawFaLeft(canvas, "%,d %s".format(Locale.US, pd, currency), pdP, leftX, rightX, ry)
            ry += rowH
        }
        if (total > 0L) {
            fillPaint.color = borderGray
            canvas.drawRect(MARGIN + 30f, ry - 4f, PAGE_WIDTH - MARGIN - 30f, ry - 3f, fillPaint)
            ry += 14f
            drawFaRight(canvas, context.getString(R.string.inv_total), TextPaint(valPaint).apply { textAlign = Paint.Align.RIGHT }, leftX, rightX, ry)
            drawFaLeft(canvas, "%,d %s".format(Locale.US, total, currency), valPaint, leftX, rightX, ry)
            ry += rowH
        }
        if (paid > 0L) {
            val paidP = TextPaint(valPaint).apply { color = greenColor }
            drawFaRight(canvas, context.getString(R.string.inv_paid), labelPaint, leftX, rightX, ry)
            drawFaLeft(canvas, "%,d %s".format(Locale.US, paid, currency), paidP, leftX, rightX, ry)
            ry += rowH
        }
        if (paid > 0L && remaining > 0L) {
            val remP = TextPaint(valPaint).apply { color = redColor }
            drawFaRight(canvas, context.getString(R.string.inv_remaining), labelPaint, leftX, rightX, ry)
            drawFaLeft(canvas, "%,d %s".format(Locale.US, remaining, currency), remP, leftX, rightX, ry)
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
            isPaid && total > 0L -> context.getString(R.string.inv_paid)
            !hasAnyAmount -> context.getString(R.string.inv_status)
            paid > 0L && remaining > 0L -> context.getString(R.string.inv_payable_remaining)
            else -> context.getString(R.string.inv_payable)
        }
        val finalTextStr = when {
            isPaid && total > 0L -> context.getString(R.string.inv_settled)
            !hasAnyAmount -> context.getString(R.string.inv_no_amount)
            else -> "%,d %s".format(Locale.US, remaining, currency)
        }
        drawFaRight(canvas, finalLabel, TextPaint(valPaint).apply { textAlign = Paint.Align.RIGHT }, leftX, rightX, ry)
        totalTextPaint.color = finalColor
        totalTextPaint.textSize = 16f
        totalTextPaint.typeface = Typeface.create(persianTypeface, Typeface.BOLD)
        drawFaLeft(canvas, finalTextStr, totalTextPaint, leftX, rightX, ry)

        y += priceBoxH + 12f

        // ==== یادداشت ====
        if (notes.isNotBlank()) {
            val noteH = 60f
            val noteRect = RectF(MARGIN + 10f, y, PAGE_WIDTH - MARGIN - 10f, y + noteH)
            fillPaint.color = lightGray
            canvas.drawRoundRect(noteRect, 12f, 12f, fillPaint)
            val noteLabelPaint = TextPaint(labelPaint).apply { color = grayColor; textSize = 10f }
            drawFaRight(canvas, context.getString(R.string.inv_note), noteLabelPaint, MARGIN + 26f, rightX, y + 20f)
            val notePaint = TextPaint(valPaint).apply { textSize = 11f; color = darkColor }
            val truncated = if (notes.length > 80) notes.take(80) + "..." else notes
            drawFaLeft(canvas, truncated, notePaint, MARGIN + 26f, rightX, y + 44f)
            y += noteH + 18f
        }

        // ==== تشکر و تاریخ ====
        drawFaCentered(canvas, context.getString(R.string.inv_thanks), thanksPaint, y)
        y += 20f
        drawFaCentered(canvas, context.getString(R.string.inv_issued_on, invoiceDateJalali), smallPaint, y)

        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()

        logoBitmap?.recycle()
        return file
    }

    // ================= رسم متن فارسی با شکل‌دهی درست =================
    // drawText اندروید متن فارسی را shape نمی‌کند (حروف جدا از هم/معکوس در PDF).
    // StaticLayout متن را با shaping و bidi صحیح رسم می‌کند.

    private fun drawFaCentered(canvas: android.graphics.Canvas, text: String, paint: TextPaint, baselineY: Float) {
        val tp = TextPaint(paint).apply { textAlign = Paint.Align.LEFT }
        val top = baselineY - tp.fontMetrics.ascent
        val width = (PAGE_WIDTH - 2 * MARGIN).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, width)
            .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(MARGIN, top)
        layout.draw(canvas)
        canvas.restore()
    }

    /** متن راست‌چین (برچسب‌ها): انتهای متن روی [rightX] می‌نشیند. */
    private fun drawFaRight(canvas: android.graphics.Canvas, text: String, paint: TextPaint, leftBound: Float, rightX: Float, baselineY: Float) {
        val tp = TextPaint(paint).apply { textAlign = Paint.Align.LEFT }
        val top = baselineY - tp.fontMetrics.ascent
        val width = (rightX - leftBound).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, width)
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL) // متن RTL داخل همین عرض راست‌چین می‌شود
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(leftBound, top)
        layout.draw(canvas)
        canvas.restore()
    }

    /** متن چپ‌چین (مقادیر عددی): ابتدای متن روی [leftX] می‌نشیند. */
    private fun drawFaLeft(canvas: android.graphics.Canvas, text: String, paint: TextPaint, leftX: Float, rightBound: Float, baselineY: Float) {
        val tp = TextPaint(paint).apply { textAlign = Paint.Align.LEFT }
        val top = baselineY - tp.fontMetrics.ascent
        val width = (rightBound - leftX).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, width)
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL) // ارقام LTR داخل همین عرض چپ‌چین می‌شوند
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(leftX, top)
        layout.draw(canvas)
        canvas.restore()
    }
}
