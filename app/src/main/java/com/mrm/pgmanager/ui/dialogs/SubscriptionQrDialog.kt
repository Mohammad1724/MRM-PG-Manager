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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import com.mrm.pgmanager.R
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
            android.widget.Toast.makeText(context, "ساخت QR ممکن نشد", android.widget.Toast.LENGTH_SHORT).show()
            // Fallback: فقط لینک
            val fallback = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, user.subUrl) }
            context.startActivity(Intent.createChooser(fallback, "اشتراک"))
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
            shareFile(file, "image/png", "اشتراک QR")
        }.onFailure { e ->
            android.widget.Toast.makeText(context, "خطا در اشتراک‌گذاری: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
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
                android.widget.Toast.makeText(context, if (isFa) "ساخت کارت ممکن نشد" else "Could not create card", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                runCatching { shareFile(file, "image/png", if (isFa) "ارسال کارت اشتراک" else "Share card") }
                    .onFailure { e ->
                        android.widget.Toast.makeText(context, "خطا در اشتراک‌گذاری: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        LiquidGlassTheme(themeState = theme, drawBackground = false) {
            Box(Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(theme.dialogBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl).padding(20.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    MrmText("QR ${user.username}", fontWeight = FontWeight.Bold, color = theme.inkColor, isTechnical = true)
                    Box(Modifier.size(220.dp).clip(DsRadius.Xxl).background(Color.White).padding(10.dp), contentAlignment = Alignment.Center) {
                        if (qrBitmap != null) Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        else Text("QR خطا", fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        SecondaryButton("کپی", onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub", user.subUrl))
                            android.widget.Toast.makeText(context, "لینک اشتراک کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.weight(1f))
                        SecondaryButton(if (isFa) "فقط QR" else "QR only", onClick = ::shareQr, modifier = Modifier.weight(1f))
                    }
                    // گزینهٔ اصلی: کارتِ کامل با نام، حجم، اعتبار و برند
                    PrimaryButton(
                        if (isFa) "ارسال کارت اشتراک" else "Share card",
                        onClick = ::shareCard,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        loading = busy
                    )
                    TextButton(onClick = onDismiss) { Text("بستن", color = theme.mutedColor) }
                }
            }
        }
    }
}
