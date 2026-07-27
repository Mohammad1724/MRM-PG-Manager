package com.mrm.pgmanager.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import com.mrm.pgmanager.data.model.DebtorInfo
import com.mrm.pgmanager.data.model.PanelUser
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

private data class InvoiceRow(val label: String, val value: String, val bold: Boolean = false, val color: Int)

object PdfInvoiceGenerator {

    private const val PAGE_WIDTH = 595   // A4 in points
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    private val darkColor = Color.rgb(0x20, 0x21, 0x24)
    private val grayColor = Color.rgb(0x74, 0x75, 0x7B)
    private val lightGray = Color.rgb(0xF2, 0xF2, 0xF4)
    private val borderGray = Color.rgb(0xD7, 0xD8, 0xDD)
    private val greenColor = Color.rgb(0x1A, 0x8C, 0x5B)
    private val redColor = Color.rgb(0xC9, 0x3B, 0x3B)
    private val amberColor = Color.rgb(0xD9, 0x82, 0x2B)
    private val primaryColor = Color.rgb(0xF4, 0xC9, 0x28)

    /**
     * تولید PDF فاکتور با PdfDocument اندروید (پشتیبانی کامل فارسی)
     */
    fun generate(
        context: Context,
        user: PanelUser,
        debtorInfo: DebtorInfo? = null,
        currency: String = "تومان",
        priceOverride: Long? = null,
        logoPath: String? = null,
        sellerName: String = ""
    ): File {
        val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val file = File(dir, "invoice-${user.username}-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.pdf")

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val persianTypeface = Typeface.create("sans-serif", Typeface.NORMAL)

        val titlePaint = TextPaint().apply {
            color = darkColor
            textSize = 22f
            isAntiAlias = true
            typeface = Typeface.create(persianTypeface, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val h2Paint = TextPaint().apply {
            color = darkColor
            textSize = 14f
            isAntiAlias = true
            typeface = Typeface.create(persianTypeface, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val boldPaint = TextPaint().apply {
            color = darkColor
            textSize = 11f
            isAntiAlias = true
            typeface = Typeface.create(persianTypeface, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val normalPaint = TextPaint().apply {
            color = darkColor
            textSize = 10f
            isAntiAlias = true
            typeface = persianTypeface
            textAlign = Paint.Align.RIGHT
        }
        val smallPaint = TextPaint().apply {
            color = grayColor
            textSize = 8.5f
            isAntiAlias = true
            typeface = persianTypeface
            textAlign = Paint.Align.RIGHT
        }
        val centerPaint = TextPaint().apply {
            color = darkColor
            textSize = 11f
            isAntiAlias = true
            typeface = Typeface.create(persianTypeface, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val pricePaint = TextPaint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val borderPaint = Paint().apply {
            color = borderGray
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }
        val fillPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        var y = MARGIN

        // ===== هدر: لوگو سمت چپ، عنوان سمت راست =====
        val logoBitmap: Bitmap? = if (!logoPath.isNullOrBlank()) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(logoPath, opts)
            }.getOrNull()
        } else null

        val logoSize = 72f
        if (logoBitmap != null) {
            val dst = RectF(MARGIN, y, MARGIN + logoSize, y + logoSize)
            canvas.drawBitmap(logoBitmap, null, dst, null)
        } else {
            val phRect = RectF(MARGIN, y, MARGIN + logoSize, y + logoSize)
            fillPaint.color = Color.argb(40, 0xF4, 0xC9, 0x28)
            canvas.drawRoundRect(phRect, 14f, 14f, fillPaint)
            borderPaint.color = primaryColor
            canvas.drawRoundRect(phRect, 14f, 14f, borderPaint)
            val mrmPaint = TextPaint().apply {
                color = darkColor
                textSize = 20f
                typeface = Typeface.create(persianTypeface, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText(if (sellerName.isNotBlank()) sellerName.take(3) else "MRM", MARGIN + logoSize / 2f, y + logoSize / 2f + 7f, mrmPaint)
        }

        // عنوان (سمت راست)
        canvas.drawText("فاکتور اشتراک", PAGE_WIDTH - MARGIN, y + 24f, titlePaint)
        val seller = if (sellerName.isNotBlank()) sellerName else "MRM PG Manager"
        canvas.drawText(seller, PAGE_WIDTH - MARGIN, y + 44f, h2Paint)
        val subPaint = TextPaint(smallPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Subscription Invoice / Voucher", PAGE_WIDTH - MARGIN, y + 60f, subPaint)

        y += logoSize + 14f

        // خط جداکننده
        fillPaint.color = primaryColor
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 3f, fillPaint)
        y += 16f

        // ===== اطلاعات فاکتور =====
        val invoiceId = "INV-${user.id}-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}"
        val invoiceDate = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())
        val invoiceDateJalali = JalaliCalendar.todayJalali().toString()

        canvas.drawText("شماره فاکتور / Invoice No.", PAGE_WIDTH - MARGIN, y, smallPaint)
        canvas.drawText(invoiceId, PAGE_WIDTH - MARGIN, y + 16f, boldPaint)

        canvas.drawText("تاریخ / Date", MARGIN + 200f, y, TextPaint(smallPaint).apply { textAlign = Paint.Align.LEFT })
        canvas.drawText(invoiceDateJalali, MARGIN + 200f, y + 16f, TextPaint(boldPaint).apply { textAlign = Paint.Align.LEFT })

        y += 36f
        canvas.drawText("ساعت صدور / Issue Time", PAGE_WIDTH - MARGIN, y, smallPaint)
        canvas.drawText(invoiceDate, PAGE_WIDTH - MARGIN, y + 16f, normalPaint)

        canvas.drawText("نام کاربری / Username", MARGIN + 200f, y, TextPaint(smallPaint).apply { textAlign = Paint.Align.LEFT })
        canvas.drawText(user.username, MARGIN + 200f, y + 16f, TextPaint(boldPaint).apply { textAlign = Paint.Align.LEFT })

        y += 38f

        // ===== نوار وضعیت =====
        val statusText = when (user.status) {
            "active" -> "فعال / Active"
            "disabled" -> "غیرفعال / Disabled"
            "expired" -> "منقضی / Expired"
            "limited" -> "محدود / Limited"
            else -> user.status
        }
        val statusColor = when (user.status) {
            "active" -> greenColor
            "disabled" -> redColor
            "expired" -> redColor
            "limited" -> amberColor
            else -> grayColor
        }
        val statusRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 34f)
        fillPaint.color = lightGray
        canvas.drawRoundRect(statusRect, 8f, 8f, fillPaint)
        val statusPaint = TextPaint(centerPaint).apply { color = statusColor }
        canvas.drawText("وضعیت: $statusText", PAGE_WIDTH / 2f, y + 22f, statusPaint)
        y += 46f

        // ===== جدول مشخصات اشتراک =====
        canvas.drawText("جزئیات اشتراک / Subscription Details", PAGE_WIDTH - MARGIN, y, h2Paint)
        y += 20f

        val dataLimitText = if (user.dataLimit == 0L) "نامحدود / Unlimited" else formatBytes(user.dataLimit)
        val usedText = formatBytes(user.usedTraffic)
        val remainingBytes = if (user.dataLimit > 0) (user.dataLimit - user.usedTraffic).coerceAtLeast(0) else 0L
        val remainingText = if (user.dataLimit == 0L) "نامحدود / Unlimited" else formatBytes(remainingBytes)

        val startDateIso = user.createdAt ?: ""
        val endDateIso = user.expire ?: ""
        val startJalali = JalaliCalendar.isoToShamsi(startDateIso).ifBlank { "نامشخص" }
        val endJalali = JalaliCalendar.isoToShamsi(endDateIso).ifBlank { "نامحدود" }
        val startGregorian = try { startDateIso.take(10) } catch (_: Exception) { "-" }
        val endGregorian = try { endDateIso.take(10) } catch (_: Exception) { "-" }

        val durationDays = runCatching {
            val s = try { java.time.Instant.parse(user.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
            catch (_: Exception) { java.time.LocalDate.parse(user.createdAt?.take(10) ?: "") }
            val e = try { java.time.Instant.parse(user.expire).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
            catch (_: Exception) { java.time.LocalDate.parse(user.expire?.take(10) ?: "") }
            java.time.temporal.ChronoUnit.DAYS.between(s, e)
        }.getOrDefault(0)
        val durationText = when {
            durationDays <= 0 -> "نامحدود / Unlimited"
            durationDays == 1L -> "1 روز / 1 day"
            durationDays < 30 -> "$durationDays روز / $durationDays days"
            durationDays == 30L -> "1 ماه / 1 month"
            durationDays < 365 -> "${durationDays/30} ماه (${durationDays} روز)"
            else -> "${durationDays/30} ماه"
        }

        val rows = listOf(
            InvoiceRow("حجم کل / Total Volume", dataLimitText, bold = true, color = darkColor),
            InvoiceRow("مصرف شده / Used", usedText, color = greenColor),
            InvoiceRow("باقی‌مانده / Remaining", remainingText, color = darkColor),
            InvoiceRow("مدت اشتراک / Duration", durationText, bold = true, color = primaryColor),
            InvoiceRow("شروع شمسی / Start (Shamsi)", startJalali, color = darkColor),
            InvoiceRow("پایان شمسی / End (Shamsi)", endJalali, bold = true, color = redColor),
            InvoiceRow("شروع میلادی / Start (Gregorian)", startGregorian, color = grayColor),
            InvoiceRow("پایان میلادی / End (Gregorian)", endGregorian, color = grayColor)
        )

        val rowH = 28f
        val labelW = (PAGE_WIDTH - 2 * MARGIN) * 0.55f
        for (r in rows) {
            val top = y
            val bgColor = if ((rows.indexOf(r) % 2) == 0) Color.WHITE else lightGray
            fillPaint.color = bgColor
            canvas.drawRect(MARGIN, top, PAGE_WIDTH - MARGIN, top + rowH, fillPaint)
            borderPaint.color = borderGray
            canvas.drawRect(MARGIN, top, PAGE_WIDTH - MARGIN, top + rowH, borderPaint)

            val paint = if (r.bold) TextPaint(boldPaint).apply { color = r.color; textAlign = Paint.Align.RIGHT }
            else TextPaint(normalPaint).apply { color = r.color; textAlign = Paint.Align.RIGHT }
            canvas.drawText(r.value, PAGE_WIDTH - MARGIN - 10f, top + 18f, paint)

            val lblPaint = TextPaint(smallPaint).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText(r.label, MARGIN + labelW - 10f, top + 18f, lblPaint)
            y += rowH
        }
        y += 18f

        // ===== بخش قیمت / مبلغ =====
        val effectivePrice = when {
            priceOverride != null && priceOverride > 0 -> priceOverride
            debtorInfo != null && debtorInfo.amount > 0 -> debtorInfo.amount
            else -> null
        }

        val priceBoxH = 80f
        val priceRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + priceBoxH)

        val priceTitle: String
        val priceVal: String
        val priceColor: Int

        if (effectivePrice != null && effectivePrice > 0) {
            val isManual = priceOverride != null && priceOverride > 0
            fillPaint.color = if (isManual) Color.argb(25, 0x1A, 0x8C, 0x5B) else Color.argb(25, 0xC9, 0x3B, 0x3B)
            canvas.drawRoundRect(priceRect, 10f, 10f, fillPaint)
            borderPaint.color = if (isManual) greenColor else redColor
            borderPaint.strokeWidth = 2f
            canvas.drawRoundRect(priceRect, 10f, 10f, borderPaint)
            borderPaint.strokeWidth = 1f

            priceTitle = if (isManual) "مبلغ قابل پرداخت / Price" else "مبلغ بدهی / Outstanding Debt"
            priceVal = "%,d %s".format(Locale.US, effectivePrice, if (isManual) currency else (debtorInfo?.currency ?: currency))
            priceColor = if (isManual) greenColor else redColor
        } else {
            fillPaint.color = Color.argb(25, 0x1A, 0x8C, 0x5B)
            canvas.drawRoundRect(priceRect, 10f, 10f, fillPaint)
            borderPaint.color = greenColor
            borderPaint.strokeWidth = 2f
            canvas.drawRoundRect(priceRect, 10f, 10f, borderPaint)
            borderPaint.strokeWidth = 1f
            priceTitle = "وضعیت پرداخت / Payment Status"
            priceVal = "پرداخت شده / Paid"
            priceColor = greenColor
        }

        val titlePaint2 = TextPaint(smallPaint).apply { color = grayColor; textAlign = Paint.Align.CENTER }
        canvas.drawText(priceTitle, PAGE_WIDTH / 2f, y + 26f, titlePaint2)

        pricePaint.color = priceColor
        pricePaint.textSize = 20f
        pricePaint.typeface = Typeface.create(persianTypeface, Typeface.BOLD)
        canvas.drawText(priceVal, PAGE_WIDTH / 2f, y + 55f, pricePaint)
        y += priceBoxH + 14f

        // یادداشت بدهی
        if (debtorInfo != null && debtorInfo.notes.isNotBlank()) {
            val noteH = 44f
            val noteRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + noteH)
            fillPaint.color = Color.argb(25, 0xC9, 0x3B, 0x3B)
            canvas.drawRoundRect(noteRect, 8f, 8f, fillPaint)
            borderPaint.color = redColor
            canvas.drawRoundRect(noteRect, 8f, 8f, borderPaint)

            val notePaint = TextPaint(normalPaint).apply { color = darkColor; textAlign = Paint.Align.RIGHT }
            canvas.drawText("توضیح / Note: ${debtorInfo.notes}", PAGE_WIDTH - MARGIN - 10f, y + 26f, notePaint)
            y += noteH + 12f
        } else if (debtorInfo?.autoDisabled == true) {
            val noteH = 34f
            val noteRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + noteH)
            fillPaint.color = Color.argb(40, 0xC9, 0x3B, 0x3B)
            canvas.drawRoundRect(noteRect, 8f, 8f, fillPaint)
            val notePaint = TextPaint(boldPaint).apply { color = redColor; textAlign = Paint.Align.CENTER; textSize = 10f }
            canvas.drawText("به صورت خودکار به دلیل بدهی قطع شده است", PAGE_WIDTH / 2f, y + 22f, notePaint)
            y += noteH + 12f
        }

        // یادداشت کاربر
        if (!user.note.isNullOrBlank()) {
            val noteH = 44f
            val noteRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + noteH)
            fillPaint.color = lightGray
            canvas.drawRoundRect(noteRect, 8f, 8f, fillPaint)
            borderPaint.color = borderGray
            canvas.drawRoundRect(noteRect, 8f, 8f, borderPaint)
            val notePaint = TextPaint(normalPaint).apply { color = darkColor; textAlign = Paint.Align.RIGHT }
            canvas.drawText("یادداشت / Note: ${user.note}", PAGE_WIDTH - MARGIN - 10f, y + 26f, notePaint)
            y += noteH + 12f
        }

        // لینک اشتراک
        if (user.subUrl.isNotBlank()) {
            val subH = 44f
            val subRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + subH)
            fillPaint.color = Color.WHITE
            canvas.drawRoundRect(subRect, 8f, 8f, fillPaint)
            borderPaint.color = borderGray
            canvas.drawRoundRect(subRect, 8f, 8f, borderPaint)
            val subLbl = TextPaint(smallPaint).apply { color = grayColor; textAlign = Paint.Align.CENTER }
            canvas.drawText("لینک اشتراک / Subscription Link", PAGE_WIDTH / 2f, y + 18f, subLbl)
            val urlPaint = TextPaint(smallPaint).apply {
                color = grayColor; textAlign = Paint.Align.CENTER; textSize = 7f
            }
            val shortUrl = user.subUrl.take(90) + if (user.subUrl.length > 90) "..." else ""
            canvas.drawText(shortUrl, PAGE_WIDTH / 2f, y + 34f, urlPaint)
            y += subH + 20f
        }

        // فوتر
        val footerPaint = TextPaint(smallPaint).apply { textAlign = Paint.Align.CENTER; color = grayColor }
        val footerText = "این فاکتور به صورت خودکار توسط MRM PG Manager تولید شده است - $invoiceDateJalali\n" +
                "Generated automatically by MRM PG Manager on $invoiceDate"
        // خروجی چند خطی ساده
        val lines = footerText.split("\n")
        var fy = y + 10f
        for (line in lines) {
            canvas.drawText(line, PAGE_WIDTH / 2f, fy, footerPaint)
            fy += 12f
        }

        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()

        logoBitmap?.recycle()

        return file
    }
}
