package com.mrm.pgmanager.ui.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.mrm.pgmanager.data.model.DebtorInfo
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.CompactGlassField
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.theme.glassBorder
import com.mrm.pgmanager.utils.JalaliCalendar
import com.mrm.pgmanager.utils.PdfInvoiceGenerator
import com.mrm.pgmanager.utils.formatBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
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

    // قیمت دستی کاربر
    var manualPrice by remember { mutableStateOf("") }
    var generatingPdf by remember { mutableStateOf(false) }
    var generatedPdfFile by remember { mutableStateOf<File?>(null) }

    // لوگو
    val logoPath = remember { store.readInvoiceLogoPath() }
    val sellerName = remember { store.readInvoiceSeller() }
    var logoBitmap by remember(logoPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(logoPath) {
        logoBitmap = if (!logoPath.isNullOrBlank()) {
            runCatching {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                android.graphics.BitmapFactory.decodeFile(logoPath, opts)
            }.getOrNull()
        } else null
    }

    // محاسبه روزها
    val startDateIso = user.createdAt ?: ""
    val endDateIso = user.expire ?: ""
    val startJalali = JalaliCalendar.isoToShamsi(startDateIso).ifBlank { "نامشخص" }
    val endJalali = JalaliCalendar.isoToShamsi(endDateIso).ifBlank { "نامحدود" }
    val startGregorian = try { startDateIso.take(10) } catch (_: Exception) { "نامشخص" }
    val endGregorian = try { endDateIso.take(10) } catch (_: Exception) { "نامحدود" }

    val durationDays = runCatching {
        val s = try { java.time.Instant.parse(user.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() } catch (_: Exception) { LocalDate.parse(user.createdAt?.take(10) ?: "") }
        val e = try { java.time.Instant.parse(user.expire).atZone(java.time.ZoneId.systemDefault()).toLocalDate() } catch (_: Exception) { LocalDate.parse(user.expire?.take(10) ?: "") }
        ChronoUnit.DAYS.between(s, e)
    }.getOrDefault(0)

    val durationText = when {
        durationDays <= 0 -> "نامحدود"
        durationDays == 1L -> "1 روزه"
        durationDays < 30 -> "$durationDays روزه"
        durationDays == 30L -> "1 ماهه"
        durationDays < 365 -> "${durationDays/30} ماهه (${durationDays} روز)"
        else -> "${durationDays/30} ماهه"
    }

    val dataLimitText = if (user.dataLimit == 0L) "نامحدود" else formatBytes(user.dataLimit)
    val usedText = formatBytes(user.usedTraffic)
    val remainingBytes = if (user.dataLimit > 0) (user.dataLimit - user.usedTraffic).coerceAtLeast(0) else 0L
    val remainingText = if (user.dataLimit == 0L) "نامحدود" else formatBytes(remainingBytes)

    val invoiceId = "INV-${user.id}-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}"
    val invoiceDate = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())
    val invoiceDateJalali = JalaliCalendar.todayJalali().toString()

    // محاسبه مبلغ نهایی
    val manualPriceLong = manualPrice.filter { it.isDigit() }.toLongOrNull()
    val effectivePrice = when {
        manualPriceLong != null && manualPriceLong > 0 -> manualPriceLong
        debtorInfo != null && debtorInfo.amount > 0 -> debtorInfo.amount
        else -> null
    }
    val priceText = when {
        manualPriceLong != null && manualPriceLong > 0 -> "${"%,d".format(Locale.US, manualPriceLong)} $currency"
        debtorInfo != null && debtorInfo.amount > 0 -> "${"%,d".format(Locale.US, debtorInfo.amount)} $currency"
        else -> "پرداخت شده ✅"
    }
    val priceColor = when {
        manualPriceLong != null && manualPriceLong > 0 -> theme.accentPrimary
        debtorInfo != null && debtorInfo.amount > 0 -> GlassRed
        else -> GlassGreen
    }

    // متن اشتراکی برای ارسال به کاربر
    val shareText = buildString {
        appendLine("🧾 فاکتور اشتراک VPN")
        appendLine("━━━━━━━━━━━━━━━━")
        if (sellerName.isNotBlank()) appendLine("🏢 $sellerName")
        appendLine("👤 نام کاربری: ${user.username}")
        appendLine("📦 حجم: $dataLimitText")
        appendLine("⏳ مدت: $durationText")
        appendLine("📅 شروع: $startJalali ($startGregorian)")
        appendLine("📅 پایان: $endJalali ($endGregorian)")
        appendLine("📊 مصرف شده: $usedText")
        appendLine("📊 باقی‌مانده: $remainingText")
        appendLine("🔗 وضعیت: ${when(user.status) { "active" -> "فعال" ; "disabled" -> "غیرفعال" ; "expired" -> "منقضی" ; "limited" -> "محدود" ; else -> user.status }}")
        appendLine("💰 مبلغ: $priceText")
        if (debtorInfo != null && debtorInfo.notes.isNotBlank()) appendLine("📝 توضیح: ${debtorInfo.notes}")
        if (!user.note.isNullOrBlank()) appendLine("📝 یادداشت: ${user.note}")
        appendLine("━━━━━━━━━━━━━━━━")
        appendLine("🆔 شماره فاکتور: $invoiceId")
        appendLine("📅 تاریخ فاکتور: $invoiceDateJalali")
        appendLine("")
        appendLine("با تشکر از شما! 🙏")
        if (user.subUrl.isNotBlank()) {
            appendLine("")
            appendLine("لینک اشتراک:")
            appendLine(user.subUrl.take(80) + if (user.subUrl.length > 80) "..." else "")
        }
    }

    fun shareTextInvoice() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "فاکتور ${user.username}")
        }
        context.startActivity(Intent.createChooser(intent, "اشتراک فاکتور"))
    }

    fun copyInvoice() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Invoice", shareText))
        android.widget.Toast.makeText(context, "فاکتور کپی شد", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun generateAndSharePdf() {
        if (generatingPdf) return
        generatingPdf = true
        scope.launch(Dispatchers.IO) {
            val file = runCatching {
                PdfInvoiceGenerator.generate(
                    context = context,
                    user = user,
                    debtorInfo = debtorInfo,
                    currency = currency,
                    priceOverride = effectivePrice,
                    logoPath = logoPath,
                    sellerName = sellerName
                )
            }.getOrNull()
            withContext(Dispatchers.Main) {
                generatingPdf = false
                if (file != null) {
                    generatedPdfFile = file
                    // Share PDF
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "اشتراک فاکتور PDF"))
                } else {
                    android.widget.Toast.makeText(context, "خطا در ساخت PDF", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // به‌روزرسانی خودکار وقتی قیمت دستی تغییر می‌کند
    fun regeneratePdfForPreview() {
        generatedPdfFile = null
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(theme.dialogBgColor)
                .border(BorderStroke(1.2.dp, theme.cardBorderBrush), RoundedCornerShape(22.dp))
                .padding(16.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // هدر فاکتور با لوگو
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.cardSurfaceColor)
                        .border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // لوگو در صورت وجود
                    if (logoBitmap != null) {
                        Image(
                            bitmap = logoBitmap!!.asImageBitmap(),
                            contentDescription = "Logo",
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.accentPrimary.copy(0.18f))
                                .border(BorderStroke(1.dp, theme.accentPrimary.copy(0.32f)), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            RoundedAppIcon(AppIcon.Receipt, tint = theme.inkColor, size = 24.dp)
                        }
                    }
                    if (sellerName.isNotBlank()) {
                        Text(sellerName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                    }
                    Text("فاکتور اشتراک", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    if (sellerName.isBlank()) Text("MRM PG Manager", fontSize = 10.sp, color = theme.mutedColor)
                    Divider(color = glassBorder(theme.isDark, theme.amoledDark), thickness = 1.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("شماره فاکتور", fontSize = 9.sp, color = theme.mutedColor)
                            Text(invoiceId, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("تاریخ", fontSize = 9.sp, color = theme.mutedColor)
                            Text(invoiceDateJalali, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                            Text(invoiceDate, fontSize = 8.sp, color = theme.mutedColor)
                        }
                    }
                }

                // اطلاعات کاربر
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (theme.isDark) Color.White.copy(0.06f) else Color(0xFFF8F8FA))
                        .border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("👤 اطلاعات کاربر", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    InvoiceRow("نام کاربری", user.username, theme.inkColor)
                    InvoiceRow("وضعیت", when(user.status) { "active" -> "فعال ✅" ; "disabled" -> "غیرفعال ❌" ; "expired" -> "منقضی ⌛" ; "limited" -> "محدود ⚠️" ; else -> user.status }, if (user.status=="active") GlassGreen else GlassRed)
                    if (!user.note.isNullOrBlank()) InvoiceRow("یادداشت", user.note!!, theme.mutedColor)
                }

                // جزئیات اشتراک
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (theme.isDark) Color.White.copy(0.06f) else Color(0xFFF8F8FA))
                        .border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📦 مشخصات اشتراک", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    InvoiceRow("حجم کل", dataLimitText, theme.inkColor, bold = true)
                    InvoiceRow("مصرف شده", usedText, GlassGreen)
                    InvoiceRow("باقی‌مانده", remainingText, theme.inkColor)
                    Divider(color = glassBorder(theme.isDark, theme.amoledDark).copy(alpha = 0.5f))
                    InvoiceRow("مدت اشتراک", durationText, theme.accentPrimary, bold = true)
                    InvoiceRow("تاریخ شروع (شمسی)", startJalali, theme.inkColor)
                    InvoiceRow("تاریخ شروع (میلادی)", startGregorian, theme.mutedColor)
                    InvoiceRow("تاریخ پایان (شمسی)", endJalali, GlassRed, bold = true)
                    InvoiceRow("تاریخ پایان (میلادی)", endGregorian, theme.mutedColor)
                }

                // ==== بخش قیمت دستی (جدید!) ====
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (theme.isDark) Color.White.copy(0.06f) else Color(0xFFF8F8FA))
                        .border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RoundedAppIcon(AppIcon.Money, tint = theme.accentPrimary, size = 16.dp)
                        Text("💰 قیمت فروش (دستی)", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    }
                    Text("اگر می‌خواهید قیمت دلخواه (غیر از بدهی) در فاکتور نمایش داده شود، اینجا وارد کنید:", fontSize = 9.sp, color = theme.mutedColor)
                    CompactGlassField(
                        value = manualPrice,
                        onValueChange = { v ->
                            manualPrice = v.filter { it.isDigit() }
                            regeneratePdfForPreview()
                        },
                        placeholder = "مثال 50000",
                        keyboardType = KeyboardType.Number,
                        leading = currency
                    )
                }

                // نمایش مبلغ نهایی
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(priceColor.copy(0.10f))
                        .border(BorderStroke(1.dp, priceColor.copy(0.32f)), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RoundedAppIcon(AppIcon.Money, tint = priceColor, size = 18.dp)
                        Text(if (effectivePrice != null) "مبلغ نهایی" else "وضعیت پرداخت", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = priceColor)
                    }
                    Text(priceText, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = priceColor)
                    if (manualPriceLong != null && manualPriceLong > 0) {
                        Text("✅ مبلغ دستی اعمال شد", fontSize = 9.sp, color = priceColor)
                    }
                }

                // بدهی (اگر قیمت دستی وارد نشده)
                if (debtorInfo != null && (manualPriceLong == null || manualPriceLong <= 0)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(GlassRed.copy(0.10f))
                            .border(BorderStroke(1.dp, GlassRed.copy(0.32f)), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RoundedAppIcon(AppIcon.Warning, tint = GlassRed, size = 18.dp)
                            Text("💳 وضعیت بدهی", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = GlassRed)
                        }
                        InvoiceRow("مبلغ بدهی", "${"%,d".format(Locale.US, debtorInfo.amount)} ${debtorInfo.currency}", GlassRed, bold = true)
                        InvoiceRow("تاریخ ثبت بدهی", SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(debtorInfo.markedAt)), theme.mutedColor)
                        if (debtorInfo.notes.isNotBlank()) InvoiceRow("توضیح بدهی", debtorInfo.notes, theme.inkColor)
                        if (debtorInfo.autoDisabled) {
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(GlassRed.copy(0.14f)).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RoundedAppIcon(AppIcon.Warning, tint = GlassRed, size = 14.dp)
                                Text("این کاربر به صورت خودکار به دلیل بدهی قطع شده است", fontSize = 9.sp, color = GlassRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // QR اگر موجود بود
                if (user.subUrl.isNotBlank()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(theme.cardSurfaceColor)
                            .border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("لینک اشتراک محدود به این فاکتور نیست و قابل استفاده است", fontSize = 9.sp, color = theme.mutedColor, textAlign = TextAlign.Center)
                        Text(user.subUrl.take(60) + if (user.subUrl.length > 60) "..." else "", fontSize = 8.sp, color = theme.mutedColor, maxLines = 2, textAlign = TextAlign.Center)
                    }
                }

                // فوتر
                Text("این فاکتور به صورت خودکار توسط MRM PG Manager تولید شده است - ${invoiceDateJalali}", fontSize = 8.sp, color = theme.mutedColor, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

                // اکشن‌ها در دو ردیف
                Text("💡 برای تنظیم لوگو و نام فروشنده به تنظیمات > فاکتور بروید", fontSize = 8.sp, color = theme.accentPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.searchBgColor)
                            .border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(12.dp))
                            .clickable { copyInvoice() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RoundedAppIcon(AppIcon.Copy, tint = theme.inkColor, size = 16.dp)
                            Text("کپی متن", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        }
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.accentPrimary.copy(0.18f))
                            .border(BorderStroke(1.dp, theme.accentPrimary.copy(0.4f)), RoundedCornerShape(12.dp))
                            .clickable { shareTextInvoice() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RoundedAppIcon(AppIcon.OpenNew, tint = theme.inkColor, size = 16.dp)
                            Text("اشتراک متن", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                        }
                    }
                }

                // دکمه PDF (پرایمری)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (generatingPdf) theme.accentPrimary.copy(0.5f) else theme.accentPrimary.copy(0.78f))
                        .clickable(enabled = !generatingPdf) { generateAndSharePdf() },
                    contentAlignment = Alignment.Center
                ) {
                    if (generatingPdf) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color(0xFF202124), strokeWidth = 2.dp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RoundedAppIcon(AppIcon.Pdf, tint = Color(0xFF202124), size = 18.dp)
                            Text("📄 ساخت و اشتراک‌گذاری PDF", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF202124))
                        }
                    }
                }

                com.mrm.pgmanager.ui.components.MutedCancelButton("بستن", onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(42.dp))
                Text("💡 نکته: برای ارسال به مشتری می‌توانی از متن کپی/اشتراک یا PDF استفاده کنی", fontSize = 9.sp, color = theme.mutedColor, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun InvoiceRow(label: String, value: String, valueColor: Color, bold: Boolean = false) {
    val theme = LocalThemeState.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, fontSize = 10.sp, color = theme.mutedColor, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text(value, fontSize = 11.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, color = valueColor, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}
