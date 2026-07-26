package com.mrm.pgmanager.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassAmber
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.theme.glassBorder
import androidx.compose.ui.focus.onFocusChanged

@Composable
fun AppLogo(modifier: Modifier = Modifier, height: Dp = 24.dp) {
    val context = LocalContext.current
    val resId = remember(context) {
        var id = context.resources.getIdentifier("ic_launcher", "drawable", context.packageName)
        if (id == 0) id = context.resources.getIdentifier("logo_mrm", "drawable", context.packageName)
        if (id == 0) id = context.resources.getIdentifier("file_000000003f2481f8aa2cab3dfb1ff5a1", "drawable", context.packageName)
        id
    }
    if (resId != 0) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = "MRM Logo",
            contentScale = ContentScale.Fit,
            modifier = modifier.height(height).widthIn(max = height * 3.2f)
        )
    } else {
        val theme = LocalThemeState.current
        Box(
            modifier = modifier.height(height).widthIn(max = height * 2.8f)
                .clip(RoundedCornerShape(height / 3.2f))
                .background(Brush.linearGradient(listOf(theme.accentPrimary, theme.accentLight)))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.85f)), RoundedCornerShape(height / 3.2f))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("MRM", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (height.value * 0.45f).sp)
        }
    }
}

@Composable
fun PasswordEyeIcon(visible: Boolean) {
    val theme = LocalThemeState.current
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width; val h = size.height
        drawOval(color = theme.inkColor, topLeft = Offset(1f, h * 0.22f), size = Size(w - 2f, h * 0.56f), style = Stroke(width = 2.2f))
        drawCircle(color = if (visible) theme.accentPrimary else theme.inkColor, radius = if (visible) w * 0.20f else w * 0.14f, center = Offset(w * 0.5f, h * 0.5f))
        if (!visible) drawLine(color = theme.accentPrimary, start = Offset(w * 0.10f, h * 0.90f), end = Offset(w * 0.90f, h * 0.10f), strokeWidth = 2.8f)
    }
}

@Composable
fun ExitIcon() {
    Canvas(modifier = Modifier.size(16.dp)) {
        val w = size.width; val h = size.height
        drawRect(color = GlassRed, topLeft = Offset(0f, 1f), size = Size(w * 0.45f, h - 2f), style = Stroke(width = 2f))
        drawLine(color = GlassRed, start = Offset(w * 0.25f, h * 0.5f), end = Offset(w, h * 0.5f), strokeWidth = 2.2f)
        drawLine(color = GlassRed, start = Offset(w * 0.68f, h * 0.22f), end = Offset(w, h * 0.5f), strokeWidth = 2.2f)
        drawLine(color = GlassRed, start = Offset(w * 0.68f, h * 0.78f), end = Offset(w, h * 0.5f), strokeWidth = 2.2f)
    }
}

// === دکمه‌های یکدست برنامه — همان زبان بصری پنجرهٔ تنظیمات ===
// سه حالت: primary (کپسول توپُر اکسنت ۷۸٪ + متن تیره)، neutral (کاشی خاکستری searchBg + مرز ظریف)،
// danger (کاشی قرمز کم‌رنگ + مرز و متن قرمز). انیمیشن فشاری در همه حفظ شده است.

@Composable
fun ActionIconButton(icon: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, isRed: Boolean = false, size: Dp = 42.dp) {
    val theme = LocalThemeState.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed && enabled) 0.88f else 1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "iconScale")
    Box(
        modifier = modifier.size(size).graphicsLayer(scaleX = scale, scaleY = scale, alpha = if (enabled) 1f else 0.55f)
            .clip(RoundedCornerShape(size / 3f))
            .background(if (isRed) GlassRed.copy(0.10f) else theme.searchBgColor)
            .border(BorderStroke(1.dp, if (isRed) GlassRed.copy(0.30f) else glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(size / 3f))
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { icon() }
}

@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, isRed: Boolean = false) {
    val theme = LocalThemeState.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val boxScale by animateFloatAsState(targetValue = if (isPressed && enabled) 0.93f else 1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "btnScale")
    Box(
        modifier = modifier.height(46.dp).graphicsLayer(scaleX = boxScale, scaleY = boxScale, alpha = if (enabled) 1f else 0.55f)
            .clip(RoundedCornerShape(13.dp))
            .background(if (isRed) GlassRed.copy(0.10f) else theme.searchBgColor)
            .border(BorderStroke(1.dp, if (isRed) GlassRed.copy(0.30f) else glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(13.dp))
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = if (isRed) GlassRed else theme.inkColor, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MiniGlassButton(text: String, modifier: Modifier = Modifier, isRed: Boolean = false, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val boxScale by animateFloatAsState(targetValue = if (isPressed) 0.91f else 1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "miniScale")
    Box(
        modifier = modifier.height(26.dp).graphicsLayer(scaleX = boxScale, scaleY = boxScale)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isRed) GlassRed.copy(0.10f) else theme.searchBgColor)
            .border(BorderStroke(1.dp, if (isRed) GlassRed.copy(0.30f) else glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = if (isRed) GlassRed else theme.inkColor, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 7.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PrimarySaveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val theme = LocalThemeState.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed && enabled) 0.94f else 1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "saveScale")
    // دکمهٔ اصلی: دقیقاً همان کپسول توپُرِ آیتمِ انتخاب‌شدهٔ تنظیمات (بدون مرز).
    Box(
        modifier = modifier.height(48.dp).graphicsLayer(scaleX = scale, scaleY = scale, alpha = if (enabled) 1f else 0.55f)
            .clip(RoundedCornerShape(13.dp))
            .background(theme.accentPrimary.copy(alpha = .78f))
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color(0xFF202124), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

@Composable
fun MutedCancelButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.94f else 1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "cancelScale")
    // دکمهٔ خنثی: همان کاشی خاکستریِ آیتم‌های غیرفعالِ سگمنت تنظیمات.
    Box(
        modifier = modifier.height(48.dp).graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(13.dp))
            .background(theme.searchBgColor)
            .border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(13.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = theme.mutedColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

// === ULTRA PREMIUM FIELD - EXACTLY LIKE THE PRETTY IMAGE ===
@Composable
fun UltraPremiumField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: String = "",
    leadingAppIcon: AppIcon? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    modifier: Modifier = Modifier
) {
    val theme = LocalThemeState.current
    var isFocused by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor, modifier = Modifier.padding(start = 4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (theme.isDark) theme.cardSurfaceColor.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.88f))
                .border(
                    BorderStroke(
                        width = if (isFocused) 1.8.dp else 1.1.dp,
                        color = if (isFocused) theme.accentPrimary else if (theme.isDark) Color.White.copy(0.14f) else Color(0xFFD1D3D9)
                    ),
                    RoundedCornerShape(18.dp)
                )
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (isFocused) theme.accentPrimary.copy(0.16f) else if (theme.isDark) Color.White.copy(0.08f) else Color.Black.copy(0.04f))
                        .border(BorderStroke(1.dp, if (isFocused) theme.accentPrimary.copy(0.22f) else Color.Transparent), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) { if (leadingAppIcon != null) RoundedAppIcon(leadingAppIcon, tint = theme.mutedColor, size = 18.dp) else if (leadingIcon.isNotEmpty()) Text(leadingIcon, fontSize = 17.sp) }

                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = theme.mutedColor.copy(0.58f), fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                        textStyle = TextStyle(color = theme.inkColor, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused }
                    )
                }

                if (isPassword) {
                    // دکمهٔ «چشم»: مخفی/نمایش رمز عبور فعال باقی می‌ماند؛ ظاهرش کاشی خاکستریِ خنثیِ design system است.
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                            .background(theme.searchBgColor)
                            .clickable { passwordVisible = !passwordVisible },
                        contentAlignment = Alignment.Center
                    ) { PasswordEyeIcon(visible = passwordVisible) }
                } else if (value.isNotEmpty()) {
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(14.dp))
                            .background(theme.searchBgColor)
                            .clickable { onValueChange("") },
                        contentAlignment = Alignment.Center
                    ) { Text("×", color = theme.mutedColor, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// Jelly for backward compat
@Composable
fun JellyGlassActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    PrimarySaveButton(text = text, onClick = onClick, modifier = modifier.height(58.dp), enabled = enabled)
}

@Composable
fun JellyGlassInputField(value: String, onValueChange: (String) -> Unit, label: String, leadingIcon: String, modifier: Modifier = Modifier, password: Boolean = false, keyboardType: KeyboardType = KeyboardType.Text) {
    UltraPremiumField(value = value, onValueChange = onValueChange, label = label, placeholder = label, leadingIcon = leadingIcon, isPassword = password, keyboardType = keyboardType, modifier = modifier)
}

@Composable
fun BulkActionsBar(
    selectedCount: Int,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onResetUsage: () -> Unit,
    onDisable: () -> Unit,
    onEnable: () -> Unit,
    onApplyTemplate: () -> Unit,
    onSelectAll: () -> Unit = {},
    onExport: () -> Unit = {}
) {
    val theme = LocalThemeState.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (theme.isDark) theme.dialogBgColor.copy(alpha = 0.96f) else theme.cardSurfaceColor.copy(alpha = 0.96f))
            .border(BorderStroke(1.2.dp, theme.cardBorderBrush), RoundedCornerShape(22.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { RoundedAppIcon(AppIcon.Users, tint = theme.inkColor, size = 17.dp); Text("عملیات گروهی روی $selectedCount کاربر", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(8.dp)).clickable { onSelectAll() }.padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("انتخاب همه", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                    }
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(GlassRed.copy(0.10f)).border(BorderStroke(1.dp, GlassRed.copy(0.30f)), RoundedCornerShape(8.dp)).clickable { onClear() }.padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("× لغو", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlassRed)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BulkActionChip("فعال‌سازی", AppIcon.Check, GlassGreen) { onEnable() }
                BulkActionChip("غیرفعال‌سازی", AppIcon.User, Color(0xFF7A7886)) { onDisable() }
                BulkActionChip("ریست حجم", AppIcon.Reset, theme.accentPrimary) { onResetUsage() }
                BulkActionChip("اعمال تمپلت", AppIcon.Template, Color(0xFF8B5CF6)) { onApplyTemplate() }
                BulkActionChip("خروجی", AppIcon.Download, GlassGreen) { onExport() }
                BulkActionChip("حذف همه", AppIcon.Delete, GlassRed) { onDelete() }
            }
        }
    }
}

@Composable
private fun BulkActionChip(label: String, icon: AppIcon, color: Color, onClick: () -> Unit) {
    // چیپ رنگی کم‌رنگ؛ همان زبان ردیف‌های اکشنِ تنظیمات (bg یک دهم + مرز یک چهارم).
    Box(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.10f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.26f)), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { RoundedAppIcon(icon, tint = color, size = 15.dp); Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = color) }
    }
}
