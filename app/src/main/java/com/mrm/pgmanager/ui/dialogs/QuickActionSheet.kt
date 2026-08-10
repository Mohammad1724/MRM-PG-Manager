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
    val isFa = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    Dialog(onDismissRequest = onDismiss) {
        LiquidGlassTheme(themeState = theme) {
            Box(Modifier.fillMaxWidth().clip(DsRadius.Xxl).background(theme.dialogBgColor).border(BorderStroke(1.dp, theme.borderColor), DsRadius.Xxl).padding(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(DsRadius.Xxl).background(if (user.isOnline) GlassGreen.copy(.14f) else Color.Gray.copy(.12f)), contentAlignment = Alignment.Center) { Box(Modifier.size(12.dp).clip(DsRadius.Xs).background(if (user.isOnline) GlassGreen else Color.Gray)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { 
                            MrmText(user.username, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, isTechnical = true)
                            MrmText(lastSeenText(user.onlineAt, user.isOnline), fontSize = 10.sp, color = if (user.isOnline) GlassGreen else theme.mutedColor, isTechnical = true)
                        }
                        Box(Modifier.clip(DsRadius.Md).background(theme.accentPrimary.copy(.14f)).padding(horizontal = 10.dp, vertical = 6.dp)) { Text(if (user.status == "disabled") (if (isFa) "غیرفعال" else "Disabled") else (if (isFa) "فعال" else "Active"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor) }
                    }
                    
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickActionRow(AppIcon.Template, if (isFa) "تمپلت" else "Template", theme.accentPrimary, Modifier.weight(1f)) { onUseTemplate(); onDismiss() }
                            QuickActionRow(AppIcon.Edit, if (isFa) "ویرایش" else "Edit", theme.inkColor, Modifier.weight(1f)) { onEdit(); onDismiss() }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickActionRow(AppIcon.Reset, if (isFa) "ریست حجم" else "Reset Data", theme.accentPrimary, Modifier.weight(1f)) { onResetUsage(); onDismiss() }
                            QuickActionRow(AppIcon.Calendar, if (isFa) "ریست زمان" else "Reset Time", theme.accentPrimary, Modifier.weight(1f)) { onResetExpiry(); onDismiss() }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickActionRow(AppIcon.Copy, if (isFa) "کپی لینک" else "Copy Link", theme.inkColor, Modifier.weight(1f)) { onCopySub(); onDismiss() }
                            QuickActionRow(AppIcon.Qr, if (isFa) "نمایش QR" else "Show QR", theme.inkColor, Modifier.weight(1f)) { onQr(); onDismiss() }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickActionRow(AppIcon.Note, if (isFa) "فاکتور 🧾" else "Invoice 🧾", theme.accentPrimary, Modifier.weight(1f)) {
                                if (onInvoice != null) { onInvoice(); onDismiss() } else { onDismiss() }
                            }
                            QuickActionRow(if (isDebtor) AppIcon.CheckCircle else AppIcon.Warning, if (isDebtor) (if (isFa) "تسویه بدهی" else "Clear Debt") else (if (isFa) "بدهکار" else "Debtor"), GlassRed, Modifier.weight(1f)) {
                                if (onDebtor != null) { onDebtor(); onDismiss() } else { onDismiss() }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickActionRow(AppIcon.User, if (user.status == "disabled") (if (isFa) "فعال‌سازی" else "Enable") else (if (isFa) "غیرفعال‌سازی" else "Disable"), theme.inkColor, Modifier.weight(1f)) { onToggle(); onDismiss() }
                            QuickActionRow(AppIcon.Delete, if (isFa) "حذف کاربر" else "Delete User", GlassRed, Modifier.weight(1f)) { onDelete(); onDismiss() }
                        }
                    }
                    
                    SecondaryButton(if (isFa) "بستن" else "Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
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
