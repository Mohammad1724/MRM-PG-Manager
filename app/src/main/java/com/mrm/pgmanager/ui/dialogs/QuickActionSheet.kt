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

/** منوی اکشنِ سریع (long-press روی کارت): تمدید سریع + عملیات‌های پرتکرار بدون دیالوگِ کامل. */
@Composable
fun QuickActionSheet(
    user: PanelUser,
    onDismiss: () -> Unit,
    onUseTemplate: () -> Unit,
    onToggle: () -> Unit,
    onCopySub: () -> Unit,
    onQr: () -> Unit,
    onEdit: () -> Unit,
    onResetUsage: () -> Unit,
    onResetExpiry: () -> Unit,
    onDelete: () -> Unit,
    onDebtor: (() -> Unit)? = null,
    isDebtor: Boolean = false,
    onInvoice: (() -> Unit)? = null
) {
    val theme = LocalThemeState.current
    Dialog(onDismissRequest = onDismiss) {
        LiquidGlassTheme(themeState = theme, drawBackground = false) {
            Box(Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(theme.dialogBgColor).border(BorderStroke(1.dp, theme.borderColor), DsRadius.Xxl).padding(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(DsRadius.Xxl).background(if (user.isOnline) GlassGreen.copy(.14f) else Color.Gray.copy(.12f)), contentAlignment = Alignment.Center) { Box(Modifier.size(12.dp).clip(DsRadius.Xs).background(if (user.isOnline) GlassGreen else Color.Gray)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { 
                            MrmText(user.username, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
                            MrmText(lastSeenText(user.onlineAt, user.isOnline), fontSize = 10.sp, color = if (user.isOnline) GlassGreen else theme.mutedColor, isTechnical = true)
                        }
                        run {
                            val (c, label) = when (user.status) {
                                "active" -> GlassGreen to stringResource(R.string.active)
                                "expired" -> GlassRed to stringResource(R.string.expired)
                                "limited" -> GlassAmber to stringResource(R.string.limited)
                                "disabled" -> Color(0xFF8A8A8A) to stringResource(R.string.disabled)
                                "on_hold" -> DsSemantic.Violet to stringResource(R.string.on_hold)
                                else -> theme.mutedColor to user.status
                            }
                            Box(Modifier.clip(DsRadius.Md).background(c.copy(.14f)).padding(horizontal = 10.dp, vertical = 6.dp)) { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c) }
                        }
                    }
                    
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickActionRow(AppIcon.Template, stringResource(R.string.qa_template), theme.accentPrimary, Modifier.weight(1f)) { onUseTemplate(); onDismiss() }
                            QuickActionRow(AppIcon.Edit, stringResource(R.string.qa_edit), theme.inkColor, Modifier.weight(1f)) { onEdit(); onDismiss() }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickActionRow(AppIcon.Reset, stringResource(R.string.qa_reset_data), theme.accentPrimary, Modifier.weight(1f)) { onResetUsage(); onDismiss() }
                            QuickActionRow(AppIcon.Calendar, stringResource(R.string.qa_reset_time), theme.accentPrimary, Modifier.weight(1f)) { onResetExpiry(); onDismiss() }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickActionRow(AppIcon.Copy, stringResource(R.string.qa_copy_link), theme.inkColor, Modifier.weight(1f)) { onCopySub(); onDismiss() }
                            QuickActionRow(AppIcon.Qr, stringResource(R.string.qa_show_qr), theme.inkColor, Modifier.weight(1f)) { onQr(); onDismiss() }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickActionRow(AppIcon.Note, stringResource(R.string.qa_invoice) + " 🧾", theme.accentPrimary, Modifier.weight(1f)) {
                                if (onInvoice != null) { onInvoice(); onDismiss() } else { onDismiss() }
                            }
                            QuickActionRow(if (isDebtor) AppIcon.CheckCircle else AppIcon.Warning, stringResource(if (isDebtor) R.string.qa_settle_debt else R.string.qa_debtor), GlassRed, Modifier.weight(1f)) {
                                if (onDebtor != null) { onDebtor(); onDismiss() } else { onDismiss() }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickActionRow(AppIcon.User, stringResource(if (user.status == "disabled") R.string.qa_enable else R.string.qa_disable), theme.inkColor, Modifier.weight(1f)) { onToggle(); onDismiss() }
                            QuickActionRow(AppIcon.Delete, stringResource(R.string.qa_delete), GlassRed, Modifier.weight(1f)) { onDelete(); onDismiss() }
                        }
                    }
                    
                    SecondaryButton(stringResource(R.string.qa_close), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}


@Composable
private fun QuickActionRow(icon: AppIcon, label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // چیپ رنگی کم‌رنگ؛ همان زبان ردیف‌های اکشنِ تنظیمات (مرز یک‌چهارم رنگ).
    Box(modifier.height(38.dp).clip(DsRadius.Md).background(color.copy(.10f)).border(BorderStroke(1.dp, color.copy(.26f)), DsRadius.Md).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RoundedAppIcon(icon, tint = color, size = 16.dp)
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }
}
