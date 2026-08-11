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

/** دیالوگ کوچکِ تأییدِ عملیات (مثل ریست حجم/زمان و عملیات گروهی). */
@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String = "تایید",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    /** عملیات مخرب (مثل حذف) → قرمز؛ بقیه → اکسنت برنامه. */
    danger: Boolean = false
) {
    val theme = LocalThemeState.current
    Dialog(onDismissRequest = onDismiss) {
        LiquidGlassTheme(themeState = theme, drawBackground = false) {
            Box(Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(theme.dialogBgColor).border(BorderStroke(1.dp, theme.borderColor), DsRadius.Xxl).padding(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = theme.inkColor)
                    Text(message, fontSize = 13.5.sp, color = theme.mutedColor, lineHeight = 20.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SecondaryButton("انصراف", onClick = onDismiss, modifier = Modifier.weight(1f))
                        if (danger) {
                            DangerButton(confirmLabel, onClick = onConfirm, modifier = Modifier.weight(1f))
                        } else {
                            PrimaryButton(confirmLabel, onClick = onConfirm, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
