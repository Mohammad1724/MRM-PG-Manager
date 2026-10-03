package com.mrm.pgmanager.ui.dialogs

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.dialogs.paneTitle
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.*
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.designsystem.*
import com.mrm.pgmanager.ui.theme.*
import com.mrm.pgmanager.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun SubscriptionQrDialog(user: PanelUser, onDismiss: () -> Unit) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val isFa = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val store = remember { SessionStore(context) }
    var busy by remember { mutableStateOf(false) }

    val qrBitmap = remember(user.subUrl) { QrGenerator.encode(user.subUrl) }

    /** اشتراک‌گذاریِ یک فایل با نوعِ مشخص، به‌همراهِ لینکِ متنی به‌عنوان fallback. */
    fun shareFile(file: java.io.File, mime: String, title: String) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            // اگر اپِ مقصد عکس را پشتیبانی نکرد، دستِ‌کم لینک می‌رود.
            putExtra(Intent.EXTRA_TEXT, user.subUrl)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    // به اشتراک‌گذاری عکس QR + لینک متنی از طریق FileProvider.
    fun shareQr() {
        val bitmap = qrBitmap ?: run {
            com.mrm.pgmanager.ui.feedback.AppFeedback.error(context.getString(R.string.qr_failed))
            // Fallback: فقط لینک
            val fallback = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, user.subUrl) }
            context.startActivity(Intent.createChooser(fallback, context.getString(R.string.qr_subscription)))
            return
        }
        runCatching {
            val shareDir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
            // پاک‌کردن فایل‌های قدیمی برای انباشته‌نشدن کش
            shareDir.listFiles()?.forEach {
                if (it.lastModified() < System.currentTimeMillis() - 3_600_000L) it.delete()
            }
            val file = java.io.File(shareDir, "qr-${user.username}.png")
            java.io.FileOutputStream(file).use { out ->
                // برای خوانایی بهتر در تلگرام/واتساپ پس‌زمینهٔ سفید با حاشیه ذخیره می‌کنیم.
                val pad = 32
                val bmp = android.graphics.Bitmap.createBitmap(bitmap.width + pad * 2, bitmap.height + pad * 2, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                canvas.drawColor(android.graphics.Color.WHITE)
                canvas.drawBitmap(bitmap, pad.toFloat(), pad.toFloat(), null)
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                if (bmp !== bitmap) bmp.recycle()
            }
            shareFile(file, "image/png", context.getString(R.string.qr_share_qr))
        }.onFailure { e ->
            com.mrm.pgmanager.ui.feedback.AppFeedback.error(
                context.getString(R.string.qr_share_error, com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, e, com.mrm.pgmanager.utils.ErrorOrigin.LOCAL)),
                detail = com.mrm.pgmanager.utils.ApiErrorMapper.technical(e)
            )
        }
    }

    /**
     * ساخت و ارسالِ «کارتِ تصویری» — QR به‌همراهِ نام، حجم، اعتبار و برندِ فروشنده.
     * رندر روی رشتهٔ پس‌زمینه انجام می‌شود چون کشیدنِ یک بیت‌مپِ ۱۰۰۰×۱۵۰۰ روی
     * رشتهٔ اصلی باعث پرشِ رابط می‌شود.
     */
    fun shareCard() {
        if (busy) return
        busy = true
        scope.launch {
            val file = withContext(Dispatchers.Default) {
                SubscriptionCard.generate(
                    context = context,
                    user = user,
                    qr = qrBitmap,
                    sellerName = store.readInvoiceSeller(),
                    logoPath = store.readInvoiceLogoPath(),
                    isFa = isFa
                )
            }
            busy = false
            if (file == null) {
                com.mrm.pgmanager.ui.feedback.AppFeedback.error(context.getString(R.string.qr_card_failed))
            } else {
                runCatching { shareFile(file, "image/png", context.getString(R.string.qr_send_card)) }
                    .onFailure { e ->
                        com.mrm.pgmanager.ui.feedback.AppFeedback.error(
                context.getString(R.string.qr_share_error, com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, e, com.mrm.pgmanager.utils.ErrorOrigin.LOCAL)),
                detail = com.mrm.pgmanager.utils.ApiErrorMapper.technical(e)
            )
                    }
            }
        }
    }

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = theme.dialogBgColor, contentColor = theme.inkColor, tonalElevation = 0.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { Box(Modifier.fillMaxWidth().padding(top = DsSpacing.Mid, bottom = DsSpacing.Sm), contentAlignment = Alignment.Center) { Box(Modifier.width(36.dp).height(4.dp).clip(DsRadius.Full).background(theme.borderColor)) } }
    ) {
        LiquidGlassTheme(themeState = theme, drawBackground = false) {
            // این ورقه عمداً لینکِ اشتراک (توکن) را نشان می‌دهد؛ تا وقتی باز است،
            // پنجره‌اش از عکس‌برداری/ضبط محافظت می‌شود. نکته: FLAG_SECURE روی پنجرهٔ
            // Activity شاملِ پنجرهٔ ورقه نمی‌شود، پس محافظت اینجا و روی همین پنجره گذاشته
            // می‌شود (بازبینیِ فاز ۸؛ پیش‌تر این سطح در عمل محافظت‌نشده بود).
            com.mrm.pgmanager.ui.designsystem.SecureContent {
                Box(Modifier.fillMaxWidth().paneTitle("QR ${user.username}").navigationBarsPadding().padding(DsSpacing.Xxl)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DsSpacing.Screen)) {
                        MrmText("QR ${user.username}", fontWeight = FontWeight.Bold, color = theme.inkColor, isTechnical = true)
                        // فاز ۷.۵ — لینکِ اشتراک یک «کلید» است، نه یک آدرسِ ساده؛
                        // کاربر باید همین‌جا (نه در صفحهٔ راهنما) بداند که سهمی است.
                        Text(
                            stringResource(R.string.set_link_copy_warning),
                            fontSize = 11.sp, color = theme.tertiaryColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Box(Modifier.size(220.dp).clip(DsRadius.Xxl).background(Color.White).padding(DsSpacing.Mid), contentAlignment = Alignment.Center) {
                            if (qrBitmap != null) Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            else Text(stringResource(R.string.qr_error), fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.Mid), modifier = Modifier.fillMaxWidth()) {
                            PGSecondaryButton(stringResource(R.string.qr_copy), onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub", user.subUrl))
                                com.mrm.pgmanager.ui.feedback.AppFeedback.success(context.getString(R.string.qr_copied))
                            }, modifier = Modifier.weight(1f))
                            PGSecondaryButton(stringResource(R.string.qr_only), onClick = ::shareQr, modifier = Modifier.weight(1f))
                        }
                        // گزینهٔ اصلی: کارتِ کامل با نام، حجم، اعتبار و برند
                        PGPrimaryButton(
                            stringResource(R.string.qr_send_card),
                            icon = null,
                            onClick = ::shareCard,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                            loading = busy
                        )
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.qr_close), color = theme.mutedColor) }
                    }
                }
            }
        }
    }
}
