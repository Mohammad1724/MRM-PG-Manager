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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun AppLogo(modifier: Modifier = Modifier, height: Dp = 24.dp) {
    val context = LocalContext.current
    val resId = remember(context) {
        var id = context.resources.getIdentifier("ic_launcher", "drawable", context.packageName)
        if (id == 0) id = context.resources.getIdentifier("logo_mrm", "drawable", context.packageName)
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
    Canvas(modifier = Modifier.size(20.dp).semantics { contentDescription = if (visible) "پنهان‌کردن رمز" else "نمایش رمز" }) {
        val w = size.width; val h = size.height
        drawOval(color = theme.inkColor, topLeft = Offset(1f, h * 0.22f), size = Size(w - 2f, h * 0.56f), style = Stroke(width = 2.2f))
        drawCircle(color = if (visible) theme.accentPrimary else theme.inkColor, radius = if (visible) w * 0.20f else w * 0.14f, center = Offset(w * 0.5f, h * 0.5f))
        if (!visible) drawLine(color = theme.accentPrimary, start = Offset(w * 0.10f, h * 0.90f), end = Offset(w * 0.90f, h * 0.10f), strokeWidth = 2.8f)
    }
}

// === MRM Premium UI Components (2026 Edition) ===

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: AppIcon? = null
) {
    MrmButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        icon = icon,
        style = MrmButtonStyle.Primary
    )
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: AppIcon? = null
) {
    MrmButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        icon = icon,
        style = MrmButtonStyle.Secondary
    )
}

@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: AppIcon? = null
) {
    MrmButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        icon = icon,
        style = MrmButtonStyle.Danger
    )
}

@Composable
fun ActionIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isRed: Boolean = false,
    size: Dp = 42.dp,
    contentDescription: String? = null
) {
    val theme = LocalThemeState.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed && enabled) 0.88f else 1.0f, label = "iconScale")
    
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer(scaleX = scale, scaleY = scale, alpha = if (enabled) 1f else 0.55f)
            .clip(RoundedCornerShape(size / 3f))
            .background(if (isRed) GlassRed.copy(0.12f) else theme.searchBgColor)
            .border(BorderStroke(1.dp, if (isRed) GlassRed.copy(0.35f) else glassBorder(theme.isDark, theme.amoledDark)), RoundedCornerShape(size / 3f))
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .clickable(interactionSource = interactionSource, indication = androidx.compose.material.ripple.rememberRipple(bounded = true), enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { icon() }
}

@Composable
fun PrimarySaveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    PrimaryButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled, loading = loading)
}

@Composable
fun MutedCancelButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SecondaryButton(text = text, onClick = onClick, modifier = modifier)
}

@Composable
fun MiniGlassButton(text: String, modifier: Modifier = Modifier, isRed: Boolean = false, onClick: () -> Unit) {
    SmallButton(text = text, onClick = onClick, modifier = modifier, isRed = isRed)
}

sealed class MrmButtonStyle {
    object Primary : MrmButtonStyle()
    object Secondary : MrmButtonStyle()
    object Danger : MrmButtonStyle()
    object Glass : MrmButtonStyle()
}

@Composable
fun MrmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: AppIcon? = null,
    style: MrmButtonStyle = MrmButtonStyle.Primary,
    compact: Boolean = false
) {
    val theme = LocalThemeState.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !loading) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "btnScale"
    )

    val contentAlpha by animateFloatAsState(targetValue = if (enabled && !loading) 1f else 0.5f, label = "btnAlpha")
    
    val (backgroundColor, contentColor, borderStroke) = when (style) {
        MrmButtonStyle.Primary -> {
            Triple(
                Brush.linearGradient(listOf(theme.accentPrimary, theme.accentPrimary.copy(alpha = 0.85f))),
                Color(0xFF1A1A1A),
                null
            )
        }
        MrmButtonStyle.Secondary -> {
            Triple(
                Brush.linearGradient(listOf(theme.searchBgColor, theme.searchBgColor)),
                theme.inkColor,
                BorderStroke(1.dp, glassBorder(theme.isDark, theme.amoledDark))
            )
        }
        MrmButtonStyle.Danger -> {
            Triple(
                Brush.linearGradient(listOf(GlassRed.copy(0.12f), GlassRed.copy(0.08f))),
                GlassRed,
                BorderStroke(1.dp, GlassRed.copy(0.35f))
            )
        }
        MrmButtonStyle.Glass -> {
            Triple(
                Brush.linearGradient(listOf(Color.White.copy(0.08f), Color.White.copy(0.02f))),
                theme.inkColor,
                BorderStroke(1.dp, Color.White.copy(0.15f))
            )
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .height(if (compact) 36.dp else 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .let { 
                if (style == MrmButtonStyle.Primary && enabled && !loading) {
                    it.graphicsLayer(shadowElevation = 8f) // Soft shadow for primary
                } else it
            }
            .background(backgroundColor)
            .let { if (borderStroke != null) it.border(borderStroke, RoundedCornerShape(16.dp)) else it }
            .clickable(interactionSource = interactionSource, indication = androidx.compose.material.ripple.rememberRipple(color = contentColor.copy(0.2f)), enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Inner Glow / Illumination effect for Primary
        if (style == MrmButtonStyle.Primary && enabled && !loading) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, 16.dp.toPx(), 16.dp.toPx()))
                }
                drawContext.canvas.save()
                drawContext.canvas.clipPath(path)
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color.White.copy(0.25f), Color.Transparent), startY = 0f, endY = size.height * 0.4f),
                    size = size
                )
                drawContext.canvas.restore()
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = if (compact) 12.dp else 20.dp).graphicsLayer(alpha = contentAlpha),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (loading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = contentColor,
                    strokeWidth = 2.dp
                )
            } else {
                if (icon != null) {
                    RoundedAppIcon(icon, tint = contentColor, size = if (compact) 16.dp else 20.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    color = contentColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 11.sp else 14.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// === Text handling for RTL/LTR consistency ===

@Composable
fun MrmText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    isTechnical: Boolean = false // Usernames, URLs, Tokens, etc.
) {
    val theme = LocalThemeState.current
    val finalColor = if (color == Color.Unspecified) theme.inkColor else color
    
    val direction = if (isTechnical) androidx.compose.ui.text.style.TextDirection.Ltr else androidx.compose.ui.text.style.TextDirection.Content
    
    Text(
        text = text,
        modifier = modifier,
        color = finalColor,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
        style = androidx.compose.ui.text.TextStyle(
            textDirection = direction
        )
    )
}

@Composable
fun TechnicalContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
    ) {
        Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
            content()
        }
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
    modifier: Modifier = Modifier,
    isTechnical: Boolean = true // Most fields are URLs, usernames, etc.
) {
    val theme = LocalThemeState.current
    var isFocused by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor.copy(0.9f), modifier = Modifier.padding(start = 6.dp))
        
        val interactionSource = remember { MutableInteractionSource() }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (isFocused) {
                        if (theme.isDark) theme.cardSurfaceColor.copy(0.95f) else Color.White
                    } else {
                        if (theme.isDark) theme.cardSurfaceColor.copy(0.7f) else theme.searchBgColor.copy(0.5f)
                    }
                )
                .border(
                    BorderStroke(
                        width = if (isFocused) 2.dp else 1.2.dp,
                        color = if (isFocused) theme.accentPrimary else if (theme.isDark) Color.White.copy(0.12f) else Color(0xFFDCDDE1)
                    ),
                    RoundedCornerShape(18.dp)
                )
                .let {
                    if (isFocused) it.graphicsLayer(shadowElevation = 4f) else it
                }
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon wrapper
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (isFocused) theme.accentPrimary.copy(0.12f) else if (theme.isDark) Color.White.copy(0.06f) else Color.Black.copy(0.04f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (leadingAppIcon != null) RoundedAppIcon(leadingAppIcon, tint = if (isFocused) theme.accentPrimary else theme.mutedColor, size = 20.dp)
                    else if (leadingIcon.isNotEmpty()) Text(leadingIcon, fontSize = 18.sp)
                }

                // Input area
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            color = theme.mutedColor.copy(0.45f),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.let { if (isTechnical) it.graphicsLayer(alpha = 0.8f) else it },
                            style = TextStyle(textDirection = if (isTechnical) androidx.compose.ui.text.style.TextDirection.Ltr else androidx.compose.ui.text.style.TextDirection.Content)
                        )
                    }
                    
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        interactionSource = interactionSource,
                        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                        textStyle = TextStyle(
                            color = theme.inkColor,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            textDirection = if (isTechnical) androidx.compose.ui.text.style.TextDirection.Ltr else androidx.compose.ui.text.style.TextDirection.Content
                        ),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused }
                    )
                }

                if (isPassword) {
                    ActionIconButton(
                        icon = { PasswordEyeIcon(visible = passwordVisible) },
                        onClick = { passwordVisible = !passwordVisible },
                        size = 38.dp
                    )
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
