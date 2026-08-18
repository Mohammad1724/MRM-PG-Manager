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
import androidx.compose.foundation.layout.imePadding
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
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.theme.glassBorder
import androidx.compose.ui.res.stringResource
import com.mrm.pgmanager.R
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
    currency: String = stringResource(R.string.inv_currency),
    onDismiss: () -> Unit
) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SessionStore(context) }

    // ==== ورودی‌ها ====
    var currentPriceText by remember { mutableStateOf("") }
    var previousDebtText by remember { mutableStateOf(
        if (debtorInfo != null && debtorInfo.amount > 0L) debtorInfo.amount.toString() else ""
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
    val unlimitedLabel = stringResource(R.string.inv_unlimited)
    val endJalali = JalaliCalendar.isoToShamsi(user.expire ?: "").ifBlank { unlimitedLabel }
    val daysRemaining = runCatching {
        val e = try { java.time.Instant.parse(user.expire).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
        catch (_: Exception) { LocalDate.parse(user.expire?.take(10) ?: "") }
        ChronoUnit.DAYS.between(LocalDate.now(), e).coerceAtLeast(0L)
    }.getOrDefault(0L)
    val durationDays = daysRemaining.coerceAtLeast(0L)
    val startLocalDate = LocalDate.now()
    val startJalali = JalaliCalendar.isoToShamsi(startLocalDate.toString()).ifBlank { "-" }

    val durationText = when {
        durationDays <= 0L -> stringResource(R.string.inv_unlimited)
        durationDays == 1L -> stringResource(R.string.inv_one_day)
        durationDays < 30L -> stringResource(R.string.inv_days, durationDays.toInt())
        durationDays == 30L -> stringResource(R.string.inv_one_month)
        durationDays < 365L -> {
            val months = (durationDays / 30L).toInt()
            val extraDays = (durationDays % 30L).toInt()
            if (extraDays == 0) stringResource(R.string.inv_months, months)
            else stringResource(R.string.inv_months_days, months, extraDays)
        }
        else -> stringResource(R.string.inv_months, (durationDays / 30L).toInt())
    }

    val dataLimitText = if (user.dataLimit == 0L) stringResource(R.string.inv_unlimited) else formatBytes(user.dataLimit)
    val invoiceDateJalali = JalaliCalendar.todayJalali().toString()

    // ==== محاسبه مبالغ ====
    val currentPrice = currentPriceText.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val previousDebt = previousDebtText.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val paidAmount = paidAmountText.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val totalBilled = currentPrice + previousDebt
    val remainingDebt = (totalBilled - paidAmount).coerceAtLeast(0L)
    val isFullyPaid = totalBilled > 0L && paidAmount >= totalBilled
    val hasAnyAmount = currentPrice > 0L || previousDebt > 0L || paidAmount > 0L
    val moneyFmt: (Long) -> String = { "%,d".format(Locale.US, it) }
    val statusText = when {
        isFullyPaid && hasAnyAmount -> stringResource(R.string.inv_paid)
        !hasAnyAmount -> stringResource(R.string.inv_no_amount)
        paidAmount > 0L && remainingDebt > 0L -> stringResource(R.string.inv_partial, moneyFmt(remainingDebt), currency)
        else -> stringResource(R.string.inv_payable)
    }
    val statusColor = when {
        isFullyPaid && hasAnyAmount -> GlassGreen
        !hasAnyAmount -> theme.mutedColor
        paidAmount > 0L -> theme.accentPrimary
        else -> GlassRed
    }

    // ==== ساخت متن فاکتور ====
    // متن‌های فاکتورِ متنی از پیش خوانده می‌شوند: داخلِ تابعِ معمولی نمی‌شود
    // stringResource صدا زد.
    val txtTitle = stringResource(R.string.inv_line_title)
    val txtUser = stringResource(R.string.inv_line_user, user.username)
    val txtData = stringResource(R.string.inv_line_data, dataLimitText)
    val txtDuration = stringResource(R.string.inv_line_duration, durationText)
    val txtStart = stringResource(R.string.inv_line_start, startJalali)
    val txtEnd = stringResource(R.string.inv_line_end, endJalali)
    val txtPrice = stringResource(R.string.inv_line_price, moneyFmt(currentPrice), currency)
    val txtPrev = stringResource(R.string.inv_line_prev, moneyFmt(previousDebt), currency)
    val txtPaid = stringResource(R.string.inv_line_paid, moneyFmt(paidAmount), currency)
    val txtLeft = stringResource(R.string.inv_line_left, moneyFmt(remainingDebt), currency)
    val txtSettledLabel = stringResource(R.string.inv_settled)
    val txtPayableLabel = stringResource(R.string.inv_payable)
    val txtPaidOnly = stringResource(R.string.inv_paid)
    val txtNoteLine = stringResource(R.string.inv_line_note, notesText)
    val txtDateLine = stringResource(R.string.inv_line_date, invoiceDateJalali)
    val txtThanks = stringResource(R.string.inv_thanks)
    val txtTotalSettled = stringResource(R.string.inv_line_total, txtSettledLabel, moneyFmt(totalBilled), currency)
    val txtTotalPayable = stringResource(R.string.inv_line_total, txtPayableLabel, moneyFmt(remainingDebt), currency)

    fun buildTextInvoice(): String {
        val lines = mutableListOf<String>()
        if (sellerName.isNotBlank()) lines.add(sellerName)
        lines.add("📄 $txtTitle")
        lines.add("─────────────────")
        lines.add("👤 $txtUser")
        lines.add("📦 $txtData")
        lines.add("⏱ $txtDuration")
        lines.add("📅 $txtStart")
        lines.add("📅 $txtEnd")
        lines.add("─────────────────")
        if (currentPrice > 0L) lines.add("💵 $txtPrice")
        if (previousDebt > 0L) lines.add("💳 $txtPrev")
        if (paidAmount > 0L) {
            lines.add("✅ $txtPaid")
            if (remainingDebt > 0L) lines.add("⚠️ $txtLeft")
        }
        if (totalBilled > 0L) {
            lines.add("─────────────────")
            lines.add(if (isFullyPaid) "✅ $txtTotalSettled" else "💰 $txtTotalPayable")
        } else {
            lines.add("✅ $txtPaidOnly")
        }
        if (notesText.isNotBlank()) {
            lines.add("─────────────────")
            lines.add("📝 $txtNoteLine")
        }
        lines.add("─────────────────")
        lines.add("📅 $txtDateLine")
        lines.add("$txtThanks 🙏")
        return lines.joinToString("\n")
    }

    // ==== حالت نمایش متن فاکتور ====
    if (textShareMode) {
        val invoiceText = buildTextInvoice()
        Dialog(onDismissRequest = { textShareMode = false }) {
            Box(
                Modifier.fillMaxWidth().imePadding().clip(DsRadius.Xxl).background(theme.dialogBgColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl).padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.inv_text_invoice), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    val scroll = rememberScrollState()
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(DsRadius.Xl)
                            .background(if (theme.isDark) Color.White.copy(0.04f) else Color(0xFFF8F8FA))
                            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xl)
                            .verticalScroll(scroll).padding(14.dp)
                    ) {
                        Text(invoiceText, fontSize = 12.sp, color = theme.inkColor, lineHeight = 22.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PrimaryButton(
                            text = stringResource(R.string.inv_copy_text),
                            onClick = {
                                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clip.setPrimaryClip(ClipData.newPlainText("Invoice", invoiceText))
                                android.widget.Toast.makeText(context, context.getString(R.string.inv_copied), android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            icon = AppIcon.Copy
                        )
                        SecondaryButton(
                            text = stringResource(R.string.inv_share),
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, invoiceText)
                                }
                                context.startActivity(Intent.createChooser(intent, context.getString(R.string.inv_share_invoice)))
                            },
                            modifier = Modifier.weight(1f),
                            icon = AppIcon.OpenNew
                        )
                    }
                    SecondaryButton(stringResource(R.string.inv_close), onClick = { textShareMode = false }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        return
    }

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
            hasAnyAmount = hasAnyAmount,
            currency = currency,
            invoiceDate = invoiceDateJalali,
            notes = notesText,
            statusText = statusText,
            statusColor = statusColor,
            onClose = { previewMode = false }
        )
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .clip(DsRadius.Xxl)
                .background(theme.dialogBgColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
                .padding(18.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(38.dp).clip(DsRadius.Lg)
                            .background(theme.accentPrimary.copy(0.18f))
                            .border(BorderStroke(DsBorder.Hairline, theme.accentPrimary.copy(0.32f)), DsRadius.Lg),
                        contentAlignment = Alignment.Center
                    ) {
                        RoundedAppIcon(AppIcon.Receipt, tint = theme.accentPrimary, size = 20.dp)
                    }
                    Column {
                        Text(stringResource(R.string.inv_title), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                        com.mrm.pgmanager.ui.components.MrmText(user.username, fontSize = 10.sp, color = theme.mutedColor, isTechnical = true)
                    }
                }

                Divider(color = theme.borderColor, thickness = 1.dp)

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(DsRadius.Xl)
                        .background(if (theme.isDark) Color.White.copy(0.04f) else Color(0xFFF8F8FA))
                        .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xl)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InfoRow(stringResource(R.string.inv_username), user.username, theme)
                    InfoRow(stringResource(R.string.inv_data), dataLimitText, theme, bold = true)
                    InfoRow(stringResource(R.string.inv_duration), durationText, theme, bold = true, color = theme.accentPrimary)
                    InfoRow(stringResource(R.string.inv_start), startJalali, theme)
                    InfoRow(stringResource(R.string.inv_end), endJalali, theme, color = GlassRed, bold = true)
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(DsRadius.Xl)
                        .background(if (theme.isDark) Color.White.copy(0.04f) else Color(0xFFF8F8FA))
                        .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xl)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("💰 " + stringResource(R.string.inv_amounts), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.inv_price), fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                        CompactGlassField(
                            value = currentPriceText,
                            onValueChange = { v -> currentPriceText = v.filter { it.isDigit() } },
                            placeholder = stringResource(R.string.inv_price_hint),
                            keyboardType = KeyboardType.Number,
                            leading = currency
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.inv_previous_debt), fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                        CompactGlassField(
                            value = previousDebtText,
                            onValueChange = { v -> previousDebtText = v.filter { it.isDigit() } },
                            placeholder = "0",
                            keyboardType = KeyboardType.Number,
                            leading = currency
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.inv_paid_amount), fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                        CompactGlassField(
                            value = paidAmountText,
                            onValueChange = { v -> paidAmountText = v.filter { it.isDigit() } },
                            placeholder = stringResource(R.string.inv_paid_hint),
                            keyboardType = KeyboardType.Number,
                            leading = currency
                        )
                    }

                    Divider(color = theme.borderColor.copy(alpha = 0.5f))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (totalBilled > 0L) stringResource(R.string.inv_total) else stringResource(R.string.inv_status), fontSize = 11.sp, color = theme.mutedColor)
                            val totalColor = if (totalBilled == 0L) GlassGreen else theme.inkColor
                            val totalText = if (totalBilled > 0L) "%,d %s".format(Locale.US, totalBilled, currency) else stringResource(R.string.inv_no_amount)
                            Text(totalText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = totalColor)
                        }
                        if (paidAmount > 0L) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.inv_paid), fontSize = 11.sp, color = GlassGreen)
                                Text("%,d %s".format(Locale.US, paidAmount, currency), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GlassGreen)
                            }
                        }
                        if (paidAmount > 0L && remainingDebt > 0L) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.inv_remaining), fontSize = 11.sp, color = GlassRed)
                                Text("%,d %s".format(Locale.US, remainingDebt, currency), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = GlassRed)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (paidAmount > 0L) stringResource(R.string.inv_final_status) else stringResource(R.string.inv_payable), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                            Text(
                                if (isFullyPaid) "✅ " + stringResource(R.string.inv_paid)
                                else if (totalBilled == 0L) "✅ " + stringResource(R.string.inv_paid)
                                else if (remainingDebt > 0L && paidAmount > 0L) stringResource(R.string.inv_remaining_fmt, moneyFmt(remainingDebt), currency)
                                else "%,d %s".format(Locale.US, remainingDebt, currency),
                                fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = statusColor
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📝 " + stringResource(R.string.inv_note_optional), fontSize = 10.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                    CompactGlassField(
                        value = notesText,
                        onValueChange = { v -> notesText = v.take(200) },
                        placeholder = stringResource(R.string.inv_note_hint),
                        leading = "",
                        keyboardType = KeyboardType.Text
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "📸 " + stringResource(R.string.inv_preview),
                        onClick = { previewMode = true },
                        modifier = Modifier.fillMaxWidth(),
                        icon = AppIcon.Qr
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        SecondaryButton(
                            text = "📄 " + stringResource(R.string.inv_text_invoice),
                            onClick = { textShareMode = true },
                            modifier = Modifier.weight(1f)
                        )
                        PrimaryButton(
                            text = "📄 PDF",
                            onClick = {
                                if (generatingPdf) return@PrimaryButton
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
                                            totalBilled = totalBilled
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
                                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.inv_share_pdf)))
                                        } else {
                                            android.widget.Toast.makeText(context, context.getString(R.string.inv_pdf_failed), android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            loading = generatingPdf,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    SecondaryButton(
                        stringResource(R.string.inv_close),
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
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
    hasAnyAmount: Boolean,
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(DsRadius.Xxl)
                    .background(Color.White)
                    .border(BorderStroke(1.5.dp, Color(0xFFE8E8EC)), DsRadius.Xxl)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (logoBitmap != null) {
                    Image(
                        bitmap = logoBitmap.asImageBitmap(),
                        contentDescription = "Logo",
                        modifier = Modifier.size(90.dp).clip(DsRadius.Xxl),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        Modifier.size(90.dp).clip(DsRadius.Xxl)
                            .background(Color(0xFFFFF8E1))
                            .border(BorderStroke(DsBorder.Hairline, Color(0xFFF4C928).copy(alpha = 0.4f)), DsRadius.Xxl),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (sellerName.isNotBlank()) sellerName.take(3) else "MRM", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFD4A800))
                    }
                }

                if (sellerName.isNotBlank()) {
                    Text(sellerName, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF202124))
                }
                Text(
                    stringResource(R.string.inv_line_title),
                    fontSize = 13.sp,
                    color = Color(0xFF74757B)
                )

                Box(
                    Modifier.fillMaxWidth(0.8f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFF4C928))
                )

                Spacer(Modifier.height(4.dp))

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(DsRadius.Xxl)
                        .background(Color(0xFFF8F8FA))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PreviewRow(stringResource(R.string.inv_username), username, bold = true)
                    PreviewRow(stringResource(R.string.inv_data), volume, bold = true, color = Color(0xFF202124))
                    PreviewRow(stringResource(R.string.inv_duration), duration, color = Color(0xFFD4A800), bold = true)
                    PreviewRow(stringResource(R.string.inv_start), startDate)
                    PreviewRow(stringResource(R.string.inv_end), endDate, color = Color(0xFFC93B3B), bold = true)
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(DsRadius.Xxl)
                        .background(Color(0xFFF8F8FA))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentPrice > 0L) PreviewRow(stringResource(R.string.inv_price), "%,d %s".format(Locale.US, currentPrice, currency))
                    if (previousDebt > 0L) PreviewRow(stringResource(R.string.inv_previous_debt), "%,d %s".format(Locale.US, previousDebt, currency), color = Color(0xFFC93B3B))
                    if (totalBilled > 0L) {
                        Divider(color = Color(0xFFE8E8EC))
                        PreviewRow(stringResource(R.string.inv_total), "%,d %s".format(Locale.US, totalBilled, currency))
                    }
                    if (paidAmount > 0L) PreviewRow(stringResource(R.string.inv_paid), "%,d %s".format(Locale.US, paidAmount, currency), color = Color(0xFF1A8C5B))
                    if (paidAmount > 0L && remainingDebt > 0L) PreviewRow(stringResource(R.string.inv_remaining), "%,d %s".format(Locale.US, remainingDebt, currency), color = Color(0xFFC93B3B))
                    Divider(color = Color(0xFFE8E8EC))
                    val finalColor = if (isFullyPaid && totalBilled > 0L) Color(0xFF1A8C5B) else if (!hasAnyAmount) Color(0xFF74757B) else if (paidAmount > 0L) Color(0xFFC93B3B) else Color(0xFFC93B3B)
                    val finalLabel = when {
                        isFullyPaid && totalBilled > 0L -> "✅ " + stringResource(R.string.inv_paid)
                        !hasAnyAmount -> stringResource(R.string.inv_status)
                        paidAmount > 0L && remainingDebt > 0L -> stringResource(R.string.inv_payable_remaining)
                        else -> stringResource(R.string.inv_payable)
                    }
                    val finalText = when {
                        isFullyPaid && totalBilled > 0L -> stringResource(R.string.inv_settled)
                        !hasAnyAmount -> "—"
                        else -> "%,d %s".format(Locale.US, remainingDebt, currency)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(finalLabel, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF202124))
                        Text(finalText, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = finalColor)
                    }
                }

                if (notes.isNotBlank()) {
                    Column(Modifier.fillMaxWidth().clip(DsRadius.Lg).background(Color(0xFFF8F8FA)).padding(12.dp)) {
                        Text("📝 " + stringResource(R.string.inv_note), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF74757B))
                        Text(notes, fontSize = 11.sp, color = Color(0xFF202124))
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(stringResource(R.string.inv_thanks) + " 🙏", fontSize = 11.sp, color = Color(0xFF74757B))
                Text(
                    stringResource(R.string.inv_issued_on, invoiceDate),
                    fontSize = 10.sp,
                    color = Color(0xFFA09C94)
                )
            }
            SecondaryButton(
                stringResource(R.string.inv_close),
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
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
