package com.mrm.pgmanager.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.mrm.pgmanager.data.model.DebtorInfo
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.theme.glassBorder
import com.mrm.pgmanager.utils.JalaliCalendar
import com.mrm.pgmanager.utils.PdfInvoiceGenerator
import com.mrm.pgmanager.utils.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

@Composable
fun InvoiceDialog(
    user: PanelUser,
    debtorInfo: DebtorInfo? = null,
    currency: String = "تومان",
    onDismiss: () -> Unit
) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SessionStore(context) }

    // ==== ورودی‌ها ====
    var currentPriceText by remember { mutableStateOf("") }
    var previousDebtText by remember { mutableStateOf(
        if (debtorInfo != null && debtorInfo.amount > 0) debtorInfo.amount.toString() else ""
    ) }
    var paidAmountText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var generatingPdf by remember { mutableStateOf(false) }
    var previewMode by remember { mutableStateOf(false) }
    var textShareMode by remember { mutableStateOf(false) }

    // ==== اطلاعات برند/لوگو ====
    val logoPath = remember { store.readInvoiceLogoPath() }
    val sellerName = remember { store.readInvoiceSeller() }
    var logoBitmap by remember(logoPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(logoPath) {
        logoBitmap = if (!logoPath.isNullOrBlank()) {
            runCatching {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(logoPath, opts)
            }.getOrNull()
        } else null
    }

    // ==== محاسبات تاریخ ====
    val startDateIso = user.createdAt ?: ""
    val endDateIso = user.expire ?: ""
    val startJalali = JalaliCalendar.isoToShamsi(startDateIso).ifBlank { "-" }
    val endJalali = JalaliCalendar.isoToShamsi(endDateIso).ifBlank { "نامحدود" }

    val durationDays = runCatching {
        val s = try { java.time.Instant.parse(user.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
        catch (_: Exception) { LocalDate.parse(user.createdAt?.take(10) ?: "") }
        val e = try { java.time.Instant.parse(user.expire).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
        catch (_: Exception) { LocalDate.parse(user.expire?.take(10) ?: "") }
        ChronoUnit.DAYS.between(s, e)
    }.getOrDefault(0)

    val durationText = when {
        durationDays <= 0 -> "نامحدود"
        durationDays == 1L -> "1 روزه"
        durationDays < 30 -> "$durationDays روزه"
        durationDays == 30L -> "1 ماهه"
        durationDays < 365 -> "${durationDays/30} ماهه"
        else -> "${durationDays/30} ماهه"
    }

    val dataLimitText = if (user.dataLimit == 0L) "نامحدود" else formatBytes(user.dataLimit)
    val invoiceDateJalali = JalaliCalendar.todayJalali().toString()

    // ==== محاسبه مبالغ ====
    val currentPrice = currentPriceText.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val previousDebt = previousDebtText.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val paidAmount = paidAmountText.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val totalBilled = currentPrice + previousDebt
    val remainingDebt = (totalBilled - paidAmount).coerceAtLeast(0L)
    val isFullyPaid = totalBilled > 0L && paidAmount >= totalBilled
    val statusText = when {
        totalBilled == 0L && previousDebt == 0L -> "پرداخت شده ✅"
        isFullyPaid -> "پرداخت شده ✅"
        paidAmount > 0L && remainingDebt > 0L -> "پرداخت جزئی - مانده: %,d %s".format(Locale.US, remainingDebt, currency)
        else -> "مبلغ قابل پرداخت"
    }
    val statusColor = when {
        isFullyPaid || totalBilled == 0L -> GlassGreen
        paidAmount > 0L -> theme.accentPrimary
        else -> GlassRed
    }

    // ==== ساخت متن فاکتور ====
    fun buildTextInvoice(): String {
        val lines = mutableListOf<String>()
        if (sellerName.isNotBlank()) lines.add(sellerName)
        lines.add("📄 فاکتور اشتراک VPN")
        lines.add("─────────────────")
        lines.add("👤 کاربر: ${user.username}")
        lines.add("📦 حجم: $dataLimitText")
        lines.add("⏱ مدت: $durationText")
        lines.add("📅 شروع: $startJalali")
        lines.add("📅 پایان: $endJalali")
        lines.add("─────────────────")
        if (currentPrice > 0L) lines.add("💵 مبلغ این دوره: %,d %s".format(Locale.US, currentPrice, currency))
        if (previousDebt > 0L) lines.add("💳 بدهی قبلی: %,d %s".format(Locale.US, previousDebt, currency))
        if (paidAmount > 0L) {
            lines.add("✅ پرداختی: %,d %s".format(Locale.US, paidAmount, currency))
            if (remainingDebt > 0L) lines.add("⚠️ مانده بدهی: %,d %s".format(Locale.US, remainingDebt, currency))
        }
        if (totalBilled > 0L) {
            val total = if (isFullyPaid) totalBilled else remainingDebt
            lines.add("─────────────────")
            lines.add("${if (isFullyPaid) "✅ تسویه کامل" else "💰 مبلغ قابل پرداخت"}: %,d %s".format(Locale.US, total, currency))
        } else {
            lines.add("✅ پرداخت شده")
        }
        if (notesText.isNotBlank()) {
            lines.add("─────────────────")
            lines.add("📝 یادداشت: $notesText")
        }
        lines.add("─────────────────")
        lines.add("📅 تاریخ: $invoiceDateJalali")
        lines.add("با تشکر از انتخاب شما 🙏")
        return lines.joinToString("\n")
    }

    // ==== حالت نمایش متن فاکتور ====
    if (textShareMode) {
        val invoiceText = buildTextInvoice()
        Dialog(onDismissRequest = { textShareMode = false }) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(theme.dialogBgColor)
                    .border(BorderStroke(1.2.dp, theme.cardBorderBrush), RoundedCornerShape(24.dp)).padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("فاکتور متنی", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    val scroll = rememberScrollState()
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (theme.isDark) Color.White.copy(0.04f) else Color(0xFFF8F8FA))
                            .border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(14.dp))
                            .verticalScroll(scroll).padding(14.dp)
                    ) {
                        Text(invoiceText, fontSize = 12.sp, color = theme.inkColor, lineHeight = 22.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                                .background(theme.accentPrimary.copy(0.78f)).clickable {
                                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clip.setPrimaryClip(ClipData.newPlainText("Invoice", invoiceText))
                                    android.widget.Toast.makeText(context, "در کلیپ‌بورد کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RoundedAppIcon(AppIcon.Copy, tint = Color(0xFF202124), size = 16.dp)
                                Text("کپی متن", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF202124))
                            }
                        }
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                                .background(theme.searchBgColor).border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(12.dp)).clickable {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, invoiceText)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری فاکتور"))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RoundedAppIcon(AppIcon.OpenNew, tint = theme.inkColor, size = 16.dp)
                                Text("اشتراک‌گذاری", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                            }
                        }
                    }
                    com.mrm.pgmanager.ui.components.MutedCancelButton("بستن", onClick = { textShareMode = false }, modifier = Modifier.fillMaxWidth().height(44.dp))
                }
            }
        }
        return
    }

    // ==== حالت پیش‌نمایش (تمام صفحه برای اسکرین‌شات) ====
    if (previewMode) {
        InvoicePreviewCard(
            logoBitmap = logoBitmap,
            sellerName = sellerName,
            username = user.username,
            volume = dataLimitText,
            duration = durationText,
            startDate = startJalali,
            endDate = endJalali,
            currentPrice = currentPrice,
            previousDebt = previousDebt,
            paidAmount = paidAmount,
            remainingDebt = remainingDebt,
            isFullyPaid = isFullyPaid,
            totalBilled = totalBilled,
            currency = currency,
            invoiceDate = invoiceDateJalali,
            notes = notesText,
            statusText = statusText,
            statusColor = statusColor,
            onClose = { previewMode = false }
        )
        return
    }

    // ==== دیالوگ ویرایش ====
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(theme.dialogBgColor)
                .border(BorderStroke(1.2.dp, theme.cardBorderBrush), RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // هدر
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                            .background(theme.accentPrimary.copy(0.18f))
                            .border(BorderStroke(1.dp, theme.accentPrimary.copy(0.32f)), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        RoundedAppIcon(AppIcon.Receipt, tint = theme.accentPrimary, size = 20.dp)
                    }
                    Column {
                        Text("فاکتور اشتراک", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                        Text(user.username, fontSize = 10.sp, color = theme.mutedColor)
                    }
                }

                Divider(color = glassBorder(theme.isDark, theme.amoledDark), thickness = 1.dp)

                // اطلاعات ثابت کاربر (فقط نمایشی)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (theme.isDark) Color.White.copy(0.04f) else Color(0xFFF8F8FA))
                        .border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InfoRow("نام کاربری", user.username, theme)
                    InfoRow("حجم اشتراک", dataLimitText, theme, bold = true)
                    InfoRow("مدت اشتراک", durationText, theme, bold = true, color = theme.accentPrimary)
                    InfoRow("تاریخ شروع", startJalali, theme)
                    InfoRow("تاریخ پایان", endJalali, theme, color = GlassRed, bold = true)
                }

                // فیلدهای مبلغ
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (theme.isDark) Color.White.copy(0.04f) else Color(0xFFF8F8FA))
                        .border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("💰 مبالغ", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)

                    // قیمت فعلی
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("قیمت این دوره", fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                        CompactGlassField(
                            value = currentPriceText,
                            onValueChange = { v -> currentPriceText = v.filter { it.isDigit() } },
                            placeholder = "مثال: ۵۰,۰۰۰",
                            keyboardType = KeyboardType.Number,
                            leading = currency
                        )
                    }

                    // بدهی قبلی
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("بدهی قبلی", fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                        CompactGlassField(
                            value = previousDebtText,
                            onValueChange = { v -> previousDebtText = v.filter { it.isDigit() } },
                            placeholder = "0",
                            keyboardType = KeyboardType.Number,
                            leading = currency
                        )
                    }

                    // مبلغ پرداختی
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("مبلغ واریزی (اختیاری)", fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                        CompactGlassField(
                            value = paidAmountText,
                            onValueChange = { v -> paidAmountText = v.filter { it.isDigit() } },
                            placeholder = "اگر بخشی/کل را پرداخت کرده",
                            keyboardType = KeyboardType.Number,
                            leading = currency
                        )
                    }

                    Divider(color = glassBorder(theme.isDark, theme.amoledDark).copy(alpha = 0.5f))

                    // مجموع / وضعیت
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (totalBilled > 0) "جمع کل" else "وضعیت", fontSize = 11.sp, color = theme.mutedColor)
                            val totalColor = if (totalBilled == 0L) GlassGreen else theme.inkColor
                            val totalText = if (totalBilled > 0) "%,d %s".format(Locale.US, totalBilled, currency) else "بدون مبلغ"
                            Text(totalText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = totalColor)
                        }
                        if (paidAmount > 0L) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("پرداخت شده", fontSize = 11.sp, color = GlassGreen)
                                Text("%,d %s".format(Locale.US, paidAmount, currency), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GlassGreen)
                            }
                        }
                        if (paidAmount > 0L && remainingDebt > 0L) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("مانده بدهی", fontSize = 11.sp, color = GlassRed)
                                Text("%,d %s".format(Locale.US, remainingDebt, currency), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = GlassRed)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (paidAmount > 0) "وضعیت نهایی" else "مبلغ قابل پرداخت", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                            Text(
                                if (isFullyPaid) "✅ پرداخت شده"
                                else if (totalBilled == 0L) "✅ پرداخت شده"
                                else if (remainingDebt > 0 && paidAmount > 0) "%,d %s مانده".format(Locale.US, remainingDebt, currency)
                                else "%,d %s".format(Locale.US, remainingDebt, currency),
                                fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = statusColor
                            )
                        }
                    }
                }

                // توضیحات یادداشت
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📝 یادداشت (اختیاری)", fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                    CompactGlassField(
                        value = notesText,
                        onValueChange = { v -> notesText = v.take(200) },
                        placeholder = "مثلاً بابت اشتراک یک ماهه، تا تاریخ...",
                        leading = "",
                        keyboardType = KeyboardType.Text
                    )
                }

                // دکمه‌ها
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // پیش‌نمایش
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(theme.accentPrimary.copy(0.15f))
                            .border(BorderStroke(1.2.dp, theme.accentPrimary.copy(0.5f)), RoundedCornerShape(14.dp))
                            .clickable { previewMode = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RoundedAppIcon(AppIcon.Qr, tint = theme.accentPrimary, size = 18.dp)
                            Text("📸 پیش‌نمایش فاکتور (اسکرین‌شات)", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = theme.accentPrimary)
                        }
                    }

                    // ردیف دکمه‌های فاکتور (متن + PDF)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        // متن فاکتور
                        Box(
                            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp))
                                .background(theme.searchBgColor)
                                .border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(14.dp))
                                .clickable { textShareMode = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RoundedAppIcon(AppIcon.Note, tint = theme.inkColor, size = 17.dp)
                                Text("📄 فاکتور متنی", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                            }
                        }
                        // PDF
                        Box(
                            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (generatingPdf) theme.accentPrimary.copy(0.5f) else theme.accentPrimary.copy(0.78f))
                                .clickable(enabled = !generatingPdf) {
                                    if (generatingPdf) return@clickable
                                    generatingPdf = true
                                    scope.launch(Dispatchers.IO) {
                                        val file = runCatching {
                                            PdfInvoiceGenerator.generate(
                                                context = context,
                                                user = user,
                                                debtorInfo = debtorInfo,
                                                currency = currency,
                                                currentPrice = currentPrice.takeIf { currentPrice > 0L || totalBilled > 0L },
                                                previousDebt = previousDebt.takeIf { it > 0L },
                                                paidAmount = paidAmount.takeIf { it > 0L },
                                                notes = notesText,
                                                logoPath = logoPath,
                                                sellerName = sellerName,
                                                isFullyPaid = isFullyPaid || totalBilled == 0L,
                                                totalBilled = totalBilled,
                                                remainingDebt = remainingDebt
                                            )
                                        }.getOrNull()
                                        withContext(Dispatchers.Main) {
                                            generatingPdf = false
                                            if (file != null) {
                                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/pdf"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(intent, "اشتراک PDF"))
                                            } else {
                                                android.widget.Toast.makeText(context, "خطا در ساخت PDF", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                    ) {
                            if (generatingPdf) {
                                CircularProgressIndicator(Modifier.size(20.dp), color = Color(0xFF202124), strokeWidth = 2.dp)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RoundedAppIcon(AppIcon.Pdf, tint = Color(0xFF202124), size = 18.dp)
                                    Text("📄 PDF", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF202124))
                                }
                            }
                        }
                    }

                    // بستن
                    com.mrm.pgmanager.ui.components.MutedCancelButton(
                        "بستن",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, theme: com.mrm.pgmanager.ui.theme.ThemeState, bold: Boolean = false, color: Color = theme.inkColor) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 10.sp, color = theme.mutedColor)
        Text(value, fontSize = 12.sp, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium, color = color)
    }
}

/**
 * کارت تمام‌صفحه فاکتور برای اسکرین‌شات - طراحی ساده و شیک
 */
@Composable
private fun InvoicePreviewCard(
    logoBitmap: android.graphics.Bitmap?,
    sellerName: String,
    username: String,
    volume: String,
    duration: String,
    startDate: String,
    endDate: String,
    currentPrice: Long,
    previousDebt: Long,
    paidAmount: Long,
    remainingDebt: Long,
    isFullyPaid: Boolean,
    totalBilled: Long,
    currency: String,
    invoiceDate: String,
    notes: String,
    statusText: String,
    statusColor: Color,
    onClose: () -> Unit
) {
    val theme = LocalThemeState.current

    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // کارت فاکتور - پس‌زمینه سفید/روشن برای خوانایی در اسکرین‌شات (بدون دکمه بستن)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .border(BorderStroke(1.5.dp, Color(0xFFE8E8EC)), RoundedCornerShape(24.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // لوگو/برند
                if (logoBitmap != null) {
                    Image(
                        bitmap = logoBitmap.asImageBitmap(),
                        contentDescription = "Logo",
                        modifier = Modifier.size(90.dp).clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        Modifier.size(90.dp).clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFFFFF8E1))
                            .border(BorderStroke(1.dp, Color(0xFFF4C928).copy(alpha = 0.4f)), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (sellerName.isNotBlank()) sellerName.take(3) else "MRM", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFD4A800))
                    }
                }

                // نام برند
                if (sellerName.isNotBlank()) {
                    Text(sellerName, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF202124))
                }
                Text(
                    "فاکتور اشتراک VPN",
                    fontSize = 13.sp,
                    color = Color(0xFF74757B)
                )

                // خط جداکننده طلایی
                Box(
                    Modifier.fillMaxWidth(0.8f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFF4C928))
                )

                Spacer(Modifier.height(4.dp))

                // مشخصات کاربر
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF8F8FA))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PreviewRow("نام کاربری", username, bold = true)
                    PreviewRow("حجم اشتراک", volume, bold = true, color = Color(0xFF202124))
                    PreviewRow("مدت اشتراک", duration, color = Color(0xFFD4A800), bold = true)
                    PreviewRow("تاریخ شروع", startDate)
                    PreviewRow("تاریخ پایان", endDate, color = Color(0xFFC93B3B), bold = true)
                }

                // مبالغ
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF8F8FA))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentPrice > 0) PreviewRow("قیمت این دوره", "%,d %s".format(Locale.US, currentPrice, currency))
                    if (previousDebt > 0) PreviewRow("بدهی قبلی", "%,d %s".format(Locale.US, previousDebt, currency), color = Color(0xFFC93B3B))
                    if (totalBilled > 0) {
                        Divider(color = Color(0xFFE8E8EC))
                        PreviewRow("جمع کل", "%,d %s".format(Locale.US, totalBilled, currency))
                    }
                    if (paidAmount > 0) PreviewRow("پرداخت شده", "%,d %s".format(Locale.US, paidAmount, currency), color = Color(0xFF1A8C5B))
                    if (paidAmount > 0 && remainingDebt > 0) PreviewRow("مانده بدهی", "%,d %s".format(Locale.US, remainingDebt, currency), color = Color(0xFFC93B3B))
                    Divider(color = Color(0xFFE8E8EC))
                    val finalColor = if (isFullyPaid || totalBilled == 0L) Color(0xFF1A8C5B) else Color(0xFFC93B3B)
                    val finalLabel = if (isFullyPaid || totalBilled == 0L) "✅ پرداخت شده" else if (paidAmount > 0 && remainingDebt > 0) "مانده قابل پرداخت" else "مبلغ قابل پرداخت"
                    val finalText = if (isFullyPaid || totalBilled == 0L) "تسویه کامل" else "%,d %s".format(Locale.US, remainingDebt, currency)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(finalLabel, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF202124))
                        Text(finalText, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = finalColor)
                    }
                }

                if (notes.isNotBlank()) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF8F8FA)).padding(12.dp)) {
                        Text("📝 یادداشت", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF74757B))
                        Text(notes, fontSize = 11.sp, color = Color(0xFF202124))
                    }
                }

                Spacer(Modifier.height(4.dp))

                // تاریخ و تشکر
                Text("با تشکر از انتخاب شما 🙏", fontSize = 11.sp, color = Color(0xFF74757B))
                Text(
                    "تاریخ صدور: $invoiceDate",
                    fontSize = 9.sp,
                    color = Color(0xFFA09C94)
                )
            }
            // دکمه بستن در پایین (بیرون کارت سفید تا در اسکرین‌شات دیده نشود)
            com.mrm.pgmanager.ui.components.MutedCancelButton(
                "بستن",
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().height(44.dp)
            )
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String, bold: Boolean = false, color: Color = Color(0xFF202124)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = Color(0xFF74757B))
        Text(value, fontSize = 12.sp, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium, color = color)
    }
}
