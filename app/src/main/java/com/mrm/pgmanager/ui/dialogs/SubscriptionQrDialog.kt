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

fun SubscriptionQrDialog(user: PanelUser, onDismiss: () -> Unit) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val qrBitmap = remember(user.subUrl) {
        runCatching {
            val writerClass = Class.forName("com.google.zxing.qrcode.QRCodeWriter")
            val formatClass = Class.forName("com.google.zxing.BarcodeFormat")
            val hintClass = Class.forName("com.google.zxing.EncodeHintType")
            val qrCodeFormat = formatClass.getField("QR_CODE").get(null)
            val marginHint = hintClass.getField("MARGIN").get(null)
            val writer = writerClass.getDeclaredConstructor().newInstance()
            val encodeMethod = writerClass.getMethod("encode", String::class.java, formatClass, Int::class.java, Int::class.java, Map::class.java)
            val bitMatrix = encodeMethod.invoke(writer, user.subUrl, qrCodeFormat, 512, 512, mapOf(marginHint to 1))
            val matrixClass = bitMatrix!!.javaClass
            val getMethod = matrixClass.getMethod("get", Int::class.java, Int::class.java)
            val getWidthMethod = matrixClass.getMethod("getWidth")
            val getHeightMethod = matrixClass.getMethod("getHeight")
            val w = getWidthMethod.invoke(bitMatrix) as Int
            val h = getHeightMethod.invoke(bitMatrix) as Int
            val pixels = IntArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val isBlack = getMethod.invoke(bitMatrix, x, y) as Boolean
                pixels[y * w + x] = if (isBlack) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
            android.graphics.Bitmap.createBitmap(pixels, w, h, android.graphics.Bitmap.Config.ARGB_8888)
        }.getOrNull()
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
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                // لینک متنی هم در متن قرار می‌گیرد تا اگر اپلیکیشن مقصد عکس را پشتیبانی نکرد، لینک برود.
                putExtra(Intent.EXTRA_TEXT, user.subUrl)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "اشتراک QR"))
        }.onFailure { e ->
            android.widget.Toast.makeText(context, "خطا در اشتراک‌گذاری: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        LiquidGlassTheme(themeState = theme) {
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
                        PrimaryButton("اشتراک", onClick = ::shareQr, modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = onDismiss) { Text("بستن", color = theme.mutedColor) }
                }
            }
        }
    }
}
