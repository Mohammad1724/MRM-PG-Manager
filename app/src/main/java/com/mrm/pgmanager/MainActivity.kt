package com.mrm.pgmanager

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.text.DecimalFormat
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MRMApp(this) }
    }
}

// ==========================================
// 1. JALALI (SHAMSI / PERSIAN) CALENDAR CORE
// ==========================================
object JalaliCalendar {
    data class Date(val year: Int, val month: Int, val day: Int) {
        override fun toString(): String = "%04d/%02d/%02d".format(year, month, day)
        fun getMonthName(): String {
            val names = arrayOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
            return if (month in 1..12) names[month - 1] else "$month"
        }
    }
    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Date {
        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        val gy2 = gy - 1600; val gm2 = gm - 1; val gd2 = gd - 1
        var gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
        for (i in 0 until gm2) gDayNo += gDaysInMonth[i]
        if (gm2 > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) gDayNo++
        gDayNo += gd2
        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053; jDayNo %= 12053
        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461
        if (jDayNo >= 366) { jy += (jDayNo - 1) / 365; jDayNo = (jDayNo - 1) % 365 }
        var i = 0; while (i < 11 && jDayNo >= jDaysInMonth[i]) { jDayNo -= jDaysInMonth[i]; i++ }
        return Date(jy, i + 1, jDayNo + 1)
    }
    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): String {
        val jy2 = jy - 979; val jm2 = jm - 1; val jd2 = jd - 1
        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        var jDayNo = 365 * jy2 + (jy2 / 33) * 8 + (jy2 % 33 + 3) / 4
        for (i in 0 until jm2) jDayNo += jDaysInMonth[i]
        jDayNo += jd2
        var gDayNo = jDayNo + 79
        var gy = 1600 + 400 * (gDayNo / 146097); gDayNo %= 146097
        var leap = true
        if (gDayNo >= 36525) {
            gDayNo--; gy += 100 * (gDayNo / 36524); gDayNo %= 36524
            if (gDayNo >= 365) gDayNo++ else leap = false
        }
        gy += 4 * (gDayNo / 1461); gDayNo %= 1461
        if (gDayNo >= 366) { leap = false; gDayNo--; gy += gDayNo / 365; gDayNo %= 365 }
        val gDaysInMonth = intArrayOf(31, if (leap && ((gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var i = 0; while (i < 12 && gDayNo >= gDaysInMonth[i]) { gDayNo -= gDaysInMonth[i]; i++ }
        return "%04d-%02d-%02d".format(gy, i + 1, gDayNo + 1)
    }
    fun isoToShamsi(iso: String?): String {
        if (iso.isNullOrBlank() || iso == "0" || iso == "null") return ""
        val parts = iso.take(10).split("-")
        if (parts.size != 3) return iso.take(10)
        val gy = parts[0].toIntOrNull() ?: return iso.take(10)
        val gm = parts[1].toIntOrNull() ?: return iso.take(10)
        val gd = parts[2].toIntOrNull() ?: return iso.take(10)
        return gregorianToJalali(gy, gm, gd).toString()
    }
    fun shamsiToIso(shamsi: String): String {
        if (shamsi.isBlank()) return ""
        val clean = shamsi.replace("-", "/").split("/")
        if (clean.size != 3) return shamsi
        val jy = clean[0].toIntOrNull() ?: return shamsi
        val jm = clean[1].toIntOrNull() ?: return shamsi
        val jd = clean[2].toIntOrNull() ?: return shamsi
        return jalaliToGregorian(jy, jm, jd)
    }
    fun todayJalali(): Date {
        val today = LocalDate.now()
        return gregorianToJalali(today.year, today.monthValue, today.dayOfMonth)
    }
    fun addDaysToIso(iso: String?, daysToAdd: Int): String {
        val baseDate = if (iso.isNullOrBlank() || iso == "0" || iso == "null") LocalDate.now()
        else runCatching { LocalDate.parse(iso.take(10)) }.getOrDefault(LocalDate.now())
        return baseDate.plusDays(daysToAdd.toLong()).toString()
    }
}

// ==========================================
// 2. THEME & PALETTE SYSTEM - LUXURY UPGRADE
// ==========================================
enum class LampColor(
    val label: String,
    val labelFa: String,
    val primary: Color,
    val light: Color,
    val spotHigh: Color,
    val spotLow: Color,
    val emoji: String
) {
    GOLD("Champagne Gold", "طلایی شامپاین", Color(0xFFC59B27), Color(0xFFF3E5AB), Color(0xBBF5D061), Color(0x44E5B84B), "✨"),
    MAGENTA("Royal Magenta", "سرخابی سلطنتی", Color(0xFFC8327E), Color(0xFFFAD1E6), Color(0xBBC8327E), Color(0x44E86FA8), "💖"),
    TURQUOISE("Neon Turquoise", "فیروزه‌ای نئون", Color(0xFF0EA89B), Color(0xFFB5F2EC), Color(0xBB2AD4C5), Color(0x4414A094), "🌊"),
    SKY_BLUE("Sky Blue", "آبی آسمانی", Color(0xFF1982C4), Color(0xFFBAE1FF), Color(0xBB3BA3EC), Color(0x441982C4), "💎"),
    VIOLET("Cyber Violet", "بنفش سایبری", Color(0xFF7A42D4), Color(0xFFE2D1FC), Color(0xBB9862F5), Color(0x447A42D4), "🔮"),
    EMERALD("Emerald Glow", "زمرد درخشان", Color(0xFF1A8C5B), Color(0xFFC2F2DC), Color(0xBB2EC486), Color(0x441A8C5B), "🍀")
}

data class ThemeState(
    val lamp: LampColor = LampColor.SKY_BLUE,
    val isDark: Boolean = false
) {
    val inkColor: Color get() = if (isDark) Color(0xFFF4F4F6) else Color(0xFF1C1B18)
    val mutedColor: Color get() = if (isDark) Color(0xFFA09C94) else Color(0xFF6A655B)
    val cardBgColor: Color get() = if (isDark) Color(0xFF222226).copy(alpha = 0.68f) else Color.White.copy(alpha = 0.52f)
    val cardBorderBrush: Brush get() = if (isDark) Brush.linearGradient(listOf(Color.White.copy(0.38f), Color.White.copy(0.08f))) else Brush.linearGradient(listOf(Color.White.copy(0.95f), Color.White.copy(0.22f)))
    val dialogBgColor: Color get() = if (isDark) Color(0xFF18181C).copy(alpha = 0.96f) else Color(0xFFFFFDF8).copy(alpha = 0.94f)
    val searchBgColor: Color get() = if (isDark) Color(0xFF2C2C32).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.68f)
    val bgDeep: Color get() = if (isDark) Color(0xFF0D0D0F) else Color(0xFFFFFBF1)
}

val LocalThemeState = compositionLocalOf { ThemeState() }
private val GlassGreen = Color(0xFF1A8C5B)
private val GlassAmber = Color(0xFFD9822B)
private val GlassRed = Color(0xFFC93B3B)
private val GlassShape = RoundedCornerShape(24.dp)

@Composable
private fun LiquidGlassTheme(themeState: ThemeState, content: @Composable () -> Unit) {
    val colors = if (themeState.isDark) {
        darkColorScheme(primary = themeState.lamp.primary, onPrimary = Color.White, secondary = themeState.lamp.light, background = Color(0xFF101012), surface = Color(0xFF1E1E22).copy(0.70f), onSurface = themeState.inkColor, onBackground = themeState.inkColor, error = GlassRed)
    } else {
        lightColorScheme(primary = themeState.lamp.primary, onPrimary = Color.White, secondary = themeState.lamp.light, background = Color(0xFFFAF6EE), surface = Color.White.copy(0.60f), onSurface = themeState.inkColor, onBackground = themeState.inkColor, error = GlassRed)
    }
    val bgGradient = if (themeState.isDark) Brush.verticalGradient(listOf(Color(0xFF15151A), Color(0xFF0E0E12), Color(0xFF08080A))) else Brush.verticalGradient(listOf(Color(0xFFFFFDF9), Color(0xFFFFF7E6), Color(0xFFFFF4DC)))

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !themeState.isDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !themeState.isDark
            }
        }
    }

    CompositionLocalProvider(LocalThemeState provides themeState) {
        MaterialTheme(colorScheme = colors) {
            Box(modifier = Modifier.fillMaxSize().background(bgGradient)) {
                // === NEW LUXURY BACKGROUND: 4 layered orbs ===
                Box(Modifier.size(580.dp).align(Alignment.TopStart).offset(x = (-160).dp, y = (-80).dp).background(Brush.radialGradient(listOf(themeState.lamp.spotHigh, themeState.lamp.spotLow, Color.Transparent)), RoundedCornerShape(300.dp)).blur(18.dp))
                Box(Modifier.size(420.dp).align(Alignment.TopEnd).offset(x = 120.dp, y = (-60).dp).background(Brush.radialGradient(listOf(themeState.lamp.light.copy(alpha = 0.35f), Color.Transparent)), RoundedCornerShape(300.dp)).blur(22.dp))
                Box(Modifier.size(500.dp).align(Alignment.BottomStart).offset(x = (-150).dp, y = 120.dp).background(Brush.radialGradient(listOf(themeState.lamp.spotHigh.copy(alpha = 0.35f), Color.Transparent)), RoundedCornerShape(300.dp)).blur(28.dp))
                Box(Modifier.size(380.dp).align(Alignment.Center).offset(x = 80.dp, y = (-20).dp).background(Brush.radialGradient(listOf(Color.White.copy(alpha = if (themeState.isDark) 0.06f else 0.22f), Color.Transparent)), RoundedCornerShape(300.dp)))
                content()
            }
        }
    }
}

// ==========================================
// 3. LOGO, GLASS BUTTONS & VECTOR ICONS - POLISHED
// ==========================================
@Composable
private fun AppLogo(modifier: Modifier = Modifier, height: Dp = 24.dp) {
    val context = LocalContext.current
    val resId = remember(context) {
        var id = context.resources.getIdentifier("logo_mrm", "drawable", context.packageName)
        if (id == 0) id = context.resources.getIdentifier("file_000000003f2481f8aa2cab3dfb1ff5a1", "drawable", context.packageName)
        id
    }
    if (resId != 0) {
        Image(painter = painterResource(id = resId), contentDescription = "MRM Logo", contentScale = ContentScale.Fit, modifier = modifier.height(height).widthIn(max = height * 3.2f))
    } else {
        val theme = LocalThemeState.current
        Box(modifier = modifier.height(height).widthIn(max = height * 2.8f).clip(RoundedCornerShape(height / 3.2f)).background(Brush.linearGradient(listOf(theme.lamp.primary, theme.lamp.light))).border(BorderStroke(1.dp, Color.White.copy(alpha = 0.85f)), RoundedCornerShape(height / 3.2f)).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text("MRM", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (height.value * 0.45f).sp, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
private fun PasswordEyeIcon(visible: Boolean) {
    val theme = LocalThemeState.current
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width; val h = size.height
        drawOval(color = theme.inkColor, topLeft = Offset(1f, h * 0.22f), size = Size(w - 2f, h * 0.56f), style = Stroke(width = 2.2f))
        drawCircle(color = if (visible) theme.lamp.primary else theme.inkColor, radius = if (visible) w * 0.20f else w * 0.14f, center = Offset(w * 0.5f, h * 0.5f))
        if (!visible) drawLine(color = theme.lamp.primary, start = Offset(w * 0.10f, h * 0.90f), end = Offset(w * 0.90f, h * 0.10f), strokeWidth = 2.8f)
    }
}

@Composable
private fun ExitIcon() {
    Canvas(modifier = Modifier.size(16.dp)) {
        val w = size.width; val h = size.height
        drawRect(color = GlassRed, topLeft = Offset(0f, 1f), size = Size(w * 0.45f, h - 2f), style = Stroke(width = 2f))
        drawLine(color = GlassRed, start = Offset(w * 0.25f, h * 0.5f), end = Offset(w, h * 0.5f), strokeWidth = 2.2f)
        drawLine(color = GlassRed, start = Offset(w * 0.68f, h * 0.22f), end = Offset(w, h * 0.5f), strokeWidth = 2.2f)
        drawLine(color = GlassRed, start = Offset(w * 0.68f, h * 0.78f), end = Offset(w, h * 0.5f), strokeWidth = 2.2f)
    }
}

@Composable
private fun ActionIconButton(icon: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, isRed: Boolean = false) {
    val theme = LocalThemeState.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed && enabled) 0.88f else 1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "iconScale")
    Box(modifier = modifier.size(42.dp).graphicsLayer(scaleX = scale, scaleY = scale).clip(RoundedCornerShape(14.dp)).background(if (isRed) Color(0xFFFFF2F2).copy(alpha = if (theme.isDark) 0.18f else 0.85f) else if (theme.isDark) Color.White.copy(0.12f) else Color.White.copy(alpha = 0.72f)).border(BorderStroke(if (isPressed) 1.6.dp else 1.dp, if (isRed) Color(0xFFF2BABA) else if (theme.isDark) Color.White.copy(0.26f) else Color.White.copy(0.9f)), RoundedCornerShape(14.dp)).clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) { icon() }
}

@Composable
private fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, isRed: Boolean = false) {
    val theme = LocalThemeState.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val glowAlpha by animateFloatAsState(targetValue = if (isPressed && enabled) 0.65f else 0.18f, animationSpec = tween(140), label = "btnGlow")
    val boxScale by animateFloatAsState(targetValue = if (isPressed && enabled) 0.93f else 1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "btnScale")
    val baseBg = if (isRed) { if (theme.isDark) Color(0xFF3D1E1E).copy(0.88f) else Color(0xFFFFF0F0).copy(0.92f) } else { if (theme.isDark) Color(0xFF2A2A32).copy(0.88f) else Color.White.copy(0.86f) }
    val activeColor = if (isRed) GlassRed else theme.lamp.primary
    val borderColor = if (isPressed && enabled) SolidColor(activeColor) else if (isRed) SolidColor(GlassRed.copy(0.65f)) else Brush.linearGradient(listOf(Color.White.copy(0.95f), theme.lamp.primary.copy(0.45f), Color.White.copy(0.35f)))
    Box(modifier = modifier.height(46.dp).graphicsLayer(scaleX = boxScale, scaleY = boxScale).clip(RoundedCornerShape(16.dp)).background(baseBg).border(BorderStroke(if (isPressed && enabled) 1.6.dp else 1.2.dp, borderColor), RoundedCornerShape(16.dp)).clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(activeColor.copy(alpha = glowAlpha), activeColor.copy(alpha = glowAlpha * 0.35f), Color.Transparent))))
        Text(text = text, color = if (isRed) GlassRed else theme.inkColor, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MiniGlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, isRed: Boolean = false) {
    val theme = LocalThemeState.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val glowAlpha by animateFloatAsState(targetValue = if (isPressed) 0.65f else 0.18f, animationSpec = tween(140), label = "miniGlow")
    val boxScale by animateFloatAsState(targetValue = if (isPressed) 0.91f else 1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "miniScale")
    val baseBg = if (isRed) { if (theme.isDark) Color(0xFF3D1E1E).copy(0.88f) else Color(0xFFFFF0F0).copy(0.92f) } else { if (theme.isDark) Color(0xFF2A2A32).copy(0.88f) else Color.White.copy(0.86f) }
    val activeColor = if (isRed) GlassRed else theme.lamp.primary
    val borderColor = if (isPressed) SolidColor(activeColor) else if (isRed) SolidColor(GlassRed.copy(0.65f)) else Brush.linearGradient(listOf(Color.White.copy(0.95f), theme.lamp.primary.copy(0.45f), Color.White.copy(0.35f)))
    Box(modifier = modifier.height(34.dp).graphicsLayer(scaleX = boxScale, scaleY = boxScale).clip(RoundedCornerShape(12.dp)).background(baseBg).border(BorderStroke(if (isPressed) 1.5.dp else 1.dp, borderColor), RoundedCornerShape(12.dp)).clickable(interactionSource = interactionSource, indication = null, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(activeColor.copy(alpha = glowAlpha), activeColor.copy(alpha = glowAlpha * 0.3f), Color.Transparent))))
        Text(text = text, color = if (isRed) GlassRed else theme.inkColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PrimarySaveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val theme = LocalThemeState.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed && enabled) 0.94f else 1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "saveScale")
    Box(modifier = modifier.height(48.dp).graphicsLayer(scaleX = scale, scaleY = scale).clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(if (isPressed) listOf(theme.lamp.primary, Color(0xFFF5D061)) else listOf(theme.lamp.primary, theme.lamp.primary.copy(0.82f)))).border(BorderStroke(if (isPressed) 1.8.dp else 1.2.dp, Color.White.copy(0.85f)), RoundedCornerShape(16.dp)).clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(text = text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

@Composable
private fun MutedCancelButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.94f else 1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "cancelScale")
    Box(modifier = modifier.height(48.dp).graphicsLayer(scaleX = scale, scaleY = scale).clip(RoundedCornerShape(16.dp)).background(if (theme.isDark) Color.White.copy(0.06f) else Color.Black.copy(0.06f)).border(BorderStroke(1.dp, if (theme.isDark) Color.White.copy(0.18f) else Color.Black.copy(0.12f)), RoundedCornerShape(16.dp)).clickable(interactionSource = interactionSource, indication = null, onClick = onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(text = text, color = theme.mutedColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

// New Luxury Stat Card Component
@Composable
private fun StatGlassCard(icon: String, label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(theme.cardBgColor)
            .border(BorderStroke(1.dp, theme.cardBorderBrush), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.18f)).border(BorderStroke(1.dp, accent.copy(0.25f)), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                    Text(icon, fontSize = 14.sp)
                }
                Text(label, fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(accent.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxWidth(0.62f).fillMaxHeight().background(accent, RoundedCornerShape(2.dp)))
            }
        }
    }
}

@Composable
private fun SkeletonCard(modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    val infinite = rememberInfiniteTransition(label = "shimmer")
    val alpha by infinite.animateFloat(initialValue = 0.35f, targetValue = 0.75f, animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Box(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(theme.cardBgColor.copy(alpha = alpha)).border(BorderStroke(1.dp, Color.White.copy(0.15f)), RoundedCornerShape(20.dp)).height(110.dp))
}

data class Session(val baseUrl: String, val token: String, val username: String)
data class PanelUser(val id: Long, val username: String, val status: String, val usedTraffic: Long, val dataLimit: Long, val expire: String?, val createdAt: String?, val subUrl: String = "", val onlineAt: String? = null, val isOnline: Boolean = false)
enum class UserFilter { ALL, ACTIVE, NEAR_LIMIT, EXPIRED, DISABLED }
enum class UserSort { NAME, USAGE, EXPIRY, CREATED }
enum class ViewMode { GRID, COMPACT_LIST, MICRO_LIST }

@Composable
private fun MRMApp(context: Context) {
    val store = remember { SessionStore(context) }
    var session by remember { mutableStateOf(store.read()) }
    var themeState by remember { mutableStateOf(store.readTheme()) }
    LiquidGlassTheme(themeState = themeState) {
        if (session == null) {
            LoginScreen(onLoggedIn = { v -> store.save(v); session = v }, themeState = themeState, onThemeChange = { nt -> themeState = nt; store.saveTheme(nt) })
        } else {
            UsersScreen(session = session!!, onLogout = { store.clear(); session = null }, themeState = themeState, onThemeChange = { nt -> themeState = nt; store.saveTheme(nt) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, password: Boolean = false, keyboardType: KeyboardType = KeyboardType.Text) {
    val theme = LocalThemeState.current
    var passwordVisible by remember { mutableStateOf(false) }
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label, fontSize = 13.sp) }, singleLine = true, modifier = modifier.fillMaxWidth(), visualTransformation = if (password && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None, keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = if (password) { { Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable { passwordVisible = !passwordVisible }, contentAlignment = Alignment.Center) { PasswordEyeIcon(visible = passwordVisible) } } } else null,
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = if (theme.isDark) Color.White.copy(0.12f) else Color.White.copy(0.55f), unfocusedContainerColor = if (theme.isDark) Color.White.copy(0.06f) else Color.White.copy(0.35f), focusedBorderColor = theme.lamp.primary, unfocusedBorderColor = if (theme.isDark) Color.White.copy(0.30f) else Color.White.copy(0.85f), focusedLabelColor = theme.lamp.primary, unfocusedLabelColor = theme.mutedColor, cursorColor = theme.lamp.primary), textStyle = TextStyle(color = theme.inkColor, fontSize = 14.sp, fontWeight = FontWeight.Medium), shape = RoundedCornerShape(16.dp))
}

@Composable
private fun GlassSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    Box(modifier = modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(theme.searchBgColor).border(BorderStroke(1.2.dp, Brush.horizontalGradient(listOf(Color.White.copy(if (theme.isDark) 0.35f else 0.94f), theme.lamp.light.copy(0.55f), Color.White.copy(if (theme.isDark) 0.12f else 0.38f)))), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(theme.lamp.primary.copy(0.14f)), contentAlignment = Alignment.Center) { Text("🔍", fontSize = 14.sp) }
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("جستجو کاربر، آی‌پی، نکته ... Search", color = theme.mutedColor.copy(0.65f), fontSize = 13.sp)
                BasicTextField(value = query, onValueChange = onQueryChange, singleLine = true, textStyle = TextStyle(color = theme.inkColor, fontSize = 14.sp, fontWeight = FontWeight.Medium), modifier = Modifier.fillMaxWidth())
            }
            if (query.isNotEmpty()) Box(Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(if (theme.isDark) 0.18f else 0.75f)).clickable { onQueryChange("") }, contentAlignment = Alignment.Center) { Text("×", color = theme.inkColor, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// === NEW LUXURY TOP HEADER with Stat Cards Grid ===
@Composable
private fun LuxuryTopStatsHeader(totalUsers: Int, activeUsers: Int, onlineUsers: Int, totalUsedTraffic: Long, onRefresh: () -> Unit, onLogout: () -> Unit, onOpenThemeDialog: () -> Unit, loading: Boolean) {
    val theme = LocalThemeState.current
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(theme.lamp.primary, theme.lamp.light))).border(BorderStroke(1.dp, Color.White.copy(0.75f)), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Text("PG", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("PasarGuard", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp), color = theme.inkColor)
                        AppLogo(height = 22.dp)
                    }
                    Text("MRM Manager • v1.1 Luxury", fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionIconButton(icon = { Text("🎨", fontSize = 16.sp) }, onClick = onOpenThemeDialog)
                ActionIconButton(icon = { if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = theme.inkColor, strokeWidth = 2.dp) else Text("🔄", fontSize = 15.sp) }, onClick = onRefresh, enabled = !loading)
                ActionIconButton(icon = { ExitIcon() }, onClick = onLogout, isRed = true)
            }
        }
        // 2x2 Grid of Stats - Much more luxurious than single row
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatGlassCard(icon = "👥", label = "کل کاربران", value = "$totalUsers", accent = theme.lamp.primary, modifier = Modifier.weight(1f))
                StatGlassCard(icon = "🟢", label = "کاربران فعال", value = "$activeUsers", accent = GlassGreen, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatGlassCard(icon = "⚡", label = "آنلاین در لحظه", value = "$onlineUsers", accent = Color(0xFF0EA89B), modifier = Modifier.weight(1f))
                StatGlassCard(icon = "📊", label = "کل ترافیک", value = formatBytes(totalUsedTraffic), accent = Color(0xFFD9822B), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FilterAndControlBar(currentFilter: UserFilter, onFilterChange: (UserFilter) -> Unit, currentSort: UserSort, onSortChange: (UserSort) -> Unit, viewMode: ViewMode, onViewModeChange: (ViewMode) -> Unit, users: List<PanelUser>) {
    val theme = LocalThemeState.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChipItem("🌟 همه ${users.size}", currentFilter == UserFilter.ALL) { onFilterChange(UserFilter.ALL) }
            FilterChipItem("🟢 فعال ${users.count { it.status == "active" }}", currentFilter == UserFilter.ACTIVE) { onFilterChange(UserFilter.ACTIVE) }
            FilterChipItem("🟡 لب مرز", currentFilter == UserFilter.NEAR_LIMIT) { onFilterChange(UserFilter.NEAR_LIMIT) }
            FilterChipItem("🔴 منقضی/پر", currentFilter == UserFilter.EXPIRED) { onFilterChange(UserFilter.EXPIRED) }
            FilterChipItem("⚪ غیرفعال", currentFilter == UserFilter.DISABLED) { onFilterChange(UserFilter.DISABLED) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("مرتب:", fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                SortPill("نام", currentSort == UserSort.NAME) { onSortChange(UserSort.NAME) }
                SortPill("مصرف", currentSort == UserSort.USAGE) { onSortChange(UserSort.USAGE) }
                SortPill("انقضا", currentSort == UserSort.EXPIRY) { onSortChange(UserSort.EXPIRY) }
                SortPill("ساخت", currentSort == UserSort.CREATED) { onSortChange(UserSort.CREATED) }
            }
            Spacer(Modifier.width(8.dp))
            Row(Modifier.clip(RoundedCornerShape(12.dp)).background(if (theme.isDark) Color.White.copy(0.1f) else Color.White.copy(0.60f)).border(BorderStroke(1.dp, if (theme.isDark) Color.White.copy(0.22f) else Color.White.copy(0.9f)), RoundedCornerShape(12.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ViewModeIcon("⊞", viewMode == ViewMode.GRID) { onViewModeChange(ViewMode.GRID) }
                ViewModeIcon("☰", viewMode == ViewMode.COMPACT_LIST) { onViewModeChange(ViewMode.COMPACT_LIST) }
                ViewModeIcon("≡", viewMode == ViewMode.MICRO_LIST) { onViewModeChange(ViewMode.MICRO_LIST) }
            }
        }
    }
}

@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val scale by animateFloatAsState(targetValue = if (selected) 1.02f else 1f, label = "chipScale")
    Box(modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale).clip(RoundedCornerShape(14.dp)).background(if (selected) Brush.linearGradient(listOf(theme.lamp.primary, theme.lamp.primary.copy(0.75f))) else Brush.linearGradient(listOf(if (theme.isDark) Color.White.copy(0.10f) else Color.White.copy(0.50f), if (theme.isDark) Color.White.copy(0.06f) else Color.White.copy(0.28f)))).border(BorderStroke(1.dp, if (selected) theme.lamp.primary else Color.White.copy(if (theme.isDark) 0.18f else 0.65f)), RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(label, color = if (selected) Color.White else theme.inkColor, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun SortPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) (if (theme.isDark) Color.White.copy(0.18f) else Color.White.copy(0.95f)) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(label, color = if (selected) theme.lamp.primary else theme.mutedColor, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ViewModeIcon(icon: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(if (selected) (if (theme.isDark) Color.White.copy(0.20f) else Color.White) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
        Text(icon, fontSize = 13.sp, color = if (selected) theme.lamp.primary else theme.mutedColor, fontWeight = FontWeight.Bold)
    }
}

// CARDS - LUXURY REDESIGN
@Composable
private fun LuxuryGridCard(user: PanelUser, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val progressPercent = if (user.dataLimit > 0) ((user.usedTraffic.toDouble() / user.dataLimit.toDouble()) * 100).toInt() else 0
    val progress = if (user.dataLimit > 0) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0f
    val progressColor = when { user.dataLimit <= 0L || progressPercent < 70 -> GlassGreen; progressPercent in 70..89 -> GlassAmber; else -> GlassRed }
    val statusColor = when (user.status) { "active" -> GlassGreen; "disabled" -> Color(0xFF8A8A8A); "expired" -> GlassRed; "limited" -> GlassAmber; "on_hold" -> Color(0xFF7A42D4); else -> theme.mutedColor }

    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(theme.cardBgColor).border(BorderStroke(1.dp, theme.cardBorderBrush), RoundedCornerShape(22.dp)).clickable(onClick = onClick)) {
        // left accent
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(4.dp).background(statusColor))
        Column(Modifier.padding(start = 4.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(statusColor).shadow(4.dp, RoundedCornerShape(4.dp), ambientColor = statusColor))
                Text(user.username, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (user.isOnline) Box(Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).background(GlassGreen.copy(0.18f)).border(BorderStroke(1.dp, GlassGreen.copy(0.3f)), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) { Text("●", fontSize = 9.sp, color = GlassGreen) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column { Text("مصرف شده", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor, letterSpacing = 0.3.sp); Text(formatBytes(user.usedTraffic), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor) }
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(progressColor.copy(0.14f)).padding(horizontal = 8.dp, vertical = 3.dp)) { Text(if (user.dataLimit == 0L) "∞ نامحدود" else "$progressPercent%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = progressColor) }
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = if (theme.isDark) 0.20f else 0.70f))) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(Brush.horizontalGradient(listOf(progressColor, progressColor.copy(0.7f)))))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (user.dataLimit == 0L) "بدون محدودیت" else formatBytes(user.dataLimit), fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                user.expire?.takeIf { it != "0" && it != "null" }?.let { iso ->
                    val shamsi = JalaliCalendar.isoToShamsi(iso)
                    Box(Modifier.background(if (theme.isDark) Color.White.copy(0.14f) else Color.White.copy(0.80f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                        Text(shamsi, fontSize = 10.sp, color = theme.inkColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LuxuryCompactRow(user: PanelUser, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val progressPercent = if (user.dataLimit > 0) ((user.usedTraffic.toDouble() / user.dataLimit.toDouble()) * 100).toInt() else 0
    val progress = if (user.dataLimit > 0) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0f
    val progressColor = when { user.dataLimit <= 0L || progressPercent < 70 -> GlassGreen; progressPercent in 70..89 -> GlassAmber; else -> GlassRed }
    val statusColor = when (user.status) { "active" -> GlassGreen; "disabled" -> Color(0xFF8A8A8A); "expired" -> GlassRed; "limited" -> GlassAmber; "on_hold" -> Color(0xFF7A42D4); else -> theme.mutedColor }
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(theme.cardBgColor).border(BorderStroke(1.dp, if (theme.isDark) Color.White.copy(0.16f) else Color.White.copy(0.80f)), RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(statusColor))
                Column(Modifier.widthIn(min = 110.dp, max = 160.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(user.username, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (user.isOnline) Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(GlassGreen))
                    }
                    Text(user.expire?.takeIf { it != "0" && it != "null" }?.let { "انقضا: ${JalaliCalendar.isoToShamsi(it)}" } ?: "بدون انقضا", fontSize = 11.sp, color = theme.mutedColor)
                }
            }
            Column(Modifier.width(145.dp), horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(formatBytes(user.usedTraffic), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                    Text(if (user.dataLimit == 0L) "∞" else "$progressPercent%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = progressColor)
                }
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = if (theme.isDark) 0.22f else 0.80f))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(progressColor, RoundedCornerShape(10.dp)))
                }
            }
            Box(Modifier.width(1.dp).height(28.dp).background(theme.cardBorderBrush))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.widthIn(min = 110.dp)) {
                Text(if (user.isOnline) "🟢 آنلاین" else "⚫ آفلاین", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (user.isOnline) GlassGreen else theme.mutedColor)
                Text("ساخت: ${JalaliCalendar.isoToShamsi(user.createdAt ?: "")}", fontSize = 11.sp, color = theme.mutedColor)
            }
            Box(Modifier.width(1.dp).height(28.dp).background(theme.cardBorderBrush))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (user.subUrl.isNotEmpty()) MiniGlassButton("📋") {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub URL", user.subUrl))
                    android.widget.Toast.makeText(context, "کپی شد ✓", android.widget.Toast.LENGTH_SHORT).show()
                }
                MiniGlassButton("✏") { onClick() }
            }
        }
    }
}

@Composable
private fun LuxuryMicroRow(user: PanelUser, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val context = LocalContext.current
    val progressPercent = if (user.dataLimit > 0) ((user.usedTraffic.toDouble() / user.dataLimit.toDouble()) * 100).toInt() else 0
    val progress = if (user.dataLimit > 0) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0f
    val progressColor = when { user.dataLimit <= 0L || progressPercent < 70 -> GlassGreen; progressPercent in 70..89 -> GlassAmber; else -> GlassRed }
    val statusColor = when (user.status) { "active" -> GlassGreen; else -> Color.Gray }
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (theme.isDark) Color(0xFF222226).copy(0.55f) else Color.White.copy(0.40f)).border(BorderStroke(0.8.dp, if (theme.isDark) Color.White.copy(0.12f) else Color.White.copy(0.70f)), RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 9.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(statusColor))
                Text(user.username, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(min = 90.dp, max = 130.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.width(110.dp)) {
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(if (theme.isDark) 0.20f else 0.70f))) { Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(progressColor, RoundedCornerShape(6.dp))) }
                Text(if (user.dataLimit == 0L) "∞" else "$progressPercent%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = progressColor)
            }
            Text(formatBytes(user.usedTraffic), fontSize = 11.sp, color = theme.mutedColor, maxLines = 1)
            Text(if (user.isOnline) "🟢" else "⚫", fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (user.subUrl.isNotEmpty()) MiniGlassButton("📋") {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub URL", user.subUrl))
                    android.widget.Toast.makeText(context, "کپی شد ✓", android.widget.Toast.LENGTH_SHORT).show()
                }
                MiniGlassButton("✏") { onClick() }
            }
        }
    }
}

// THEME EDITOR DIALOG - LUXURY UPGRADE
@Composable
private fun ThemeEditorDialog(themeState: ThemeState, onDismiss: () -> Unit, onThemeChange: (ThemeState) -> Unit) {
    val theme = LocalThemeState.current
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(theme.dialogBgColor).border(BorderStroke(1.5.dp, theme.cardBorderBrush), RoundedCornerShape(30.dp)).padding(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("🎨 ظاهر برنامه", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = theme.inkColor); Text("رنگ و تم را شخصی‌سازی کنید", fontSize = 12.sp, color = theme.mutedColor) }
                    AppLogo(height = 22.dp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("حالت پس‌زمینه", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ModeToggleBtn("☀️ روشن", !themeState.isDark, Modifier.weight(1f)) { onThemeChange(themeState.copy(isDark = false)) }
                        ModeToggleBtn("🌙 تیره", themeState.isDark, Modifier.weight(1f)) { onThemeChange(themeState.copy(isDark = true)) }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("رنگ تاکیدی (Spotlight)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LampColor.values().forEach { lamp ->
                            val isSelected = themeState.lamp == lamp
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (isSelected) lamp.primary.copy(0.16f) else Color.Transparent).border(BorderStroke(1.2.dp, if (isSelected) lamp.primary else Color.White.copy(if (theme.isDark) 0.14f else 0.45f)), RoundedCornerShape(16.dp)).clickable { onThemeChange(themeState.copy(lamp = lamp)) }.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(lamp.primary, lamp.light))).border(BorderStroke(1.dp, Color.White.copy(0.8f)), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Text(lamp.emoji, fontSize = 16.sp) }
                                    Column(Modifier.weight(1f)) { Text(lamp.labelFa, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = theme.inkColor, fontSize = 13.sp); Text(lamp.label, fontSize = 10.sp, color = theme.mutedColor) }
                                    if (isSelected) Box(Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(lamp.primary), contentAlignment = Alignment.Center) { Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
                GlassButton("✓ تایید و بستن", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ModeToggleBtn(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(modifier = modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) Brush.linearGradient(listOf(theme.lamp.primary, theme.lamp.primary.copy(0.78f))) else Brush.linearGradient(listOf(if (theme.isDark) Color.White.copy(0.10f) else Color.White.copy(0.6f), if (theme.isDark) Color.White.copy(0.04f) else Color.White.copy(0.30f)))).border(BorderStroke(1.dp, if (selected) theme.lamp.primary else Color.White.copy(0.35f)), RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) Color.White else theme.inkColor, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
    }
}

@Composable
private fun SubscriptionQrDialog(user: PanelUser, onDismiss: () -> Unit) {
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
            for (y in 0 until h) for (x in 0 until w) { val isBlack = getMethod.invoke(bitMatrix, x, y) as Boolean; pixels[y * w + x] = if (isBlack) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
            android.graphics.Bitmap.createBitmap(pixels, w, h, android.graphics.Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(theme.dialogBgColor).border(BorderStroke(1.5.dp, theme.cardBorderBrush), RoundedCornerShape(28.dp)).padding(22.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("📱 اشتراک ${user.username}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                Box(Modifier.size(240.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(14.dp).shadow(12.dp, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                    if (qrBitmap != null) Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    else Text("QR نیازمند ZXing", color = GlassRed, fontSize = 12.sp)
                }
                Text(user.subUrl, fontSize = 11.sp, color = theme.mutedColor, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.background(if (theme.isDark) Color.White.copy(0.06f) else Color.Black.copy(0.04f), RoundedCornerShape(8.dp)).padding(8.dp).fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    GlassButton("📋 کپی", onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub", user.subUrl))
                        android.widget.Toast.makeText(context, "کپی شد ✓", android.widget.Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.weight(1f))
                    PrimarySaveButton("📤 اشتراک", onClick = {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "پاسارگارد ${user.username}:\n${user.subUrl}") }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "اشتراک‌گذاری"))
                    }, modifier = Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text("بستن", color = theme.mutedColor, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun ShamsiCalendarPickerDialog(initialDateShamsi: String, onDismiss: () -> Unit, onDateSelected: (String) -> Unit) {
    val theme = LocalThemeState.current
    val today = JalaliCalendar.todayJalali()
    val parsedInitial = remember(initialDateShamsi) {
        val p = initialDateShamsi.replace("-", "/").split("/")
        if (p.size == 3) JalaliCalendar.Date(p[0].toIntOrNull() ?: today.year, p[1].toIntOrNull() ?: today.month, p[2].toIntOrNull() ?: today.day) else today
    }
    var selectedYear by remember { mutableStateOf(parsedInitial.year) }
    var selectedMonth by remember { mutableStateOf(parsedInitial.month) }
    var selectedDay by remember { mutableStateOf(parsedInitial.day) }
    val daysInMonth = when { selectedMonth in 1..6 -> 31; selectedMonth in 7..11 -> 30; else -> if (selectedYear % 4 == 3) 30 else 29 }
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(theme.dialogBgColor).border(BorderStroke(1.5.dp, theme.cardBorderBrush), RoundedCornerShape(28.dp)).padding(22.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("📅 تقویم شمسی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    TextButton(onClick = { selectedYear = today.year; selectedMonth = today.month; selectedDay = today.day }) { Text("امروز", color = theme.lamp.primary, fontWeight = FontWeight.Bold) }
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { if (selectedMonth > 1) selectedMonth-- else { selectedMonth = 12; selectedYear-- } }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp), colors = ButtonDefaults.buttonColors(containerColor = theme.lamp.primary.copy(0.18f), contentColor = theme.inkColor)) { Text("◀") }
                        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(0.1f)).padding(horizontal = 14.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                            val tempD = JalaliCalendar.Date(selectedYear, selectedMonth, 1)
                            Text("${tempD.getMonthName()} $selectedYear", fontWeight = FontWeight.Bold, color = theme.inkColor, fontSize = 14.sp)
                        }
                        Button(onClick = { if (selectedMonth < 12) selectedMonth++ else { selectedMonth = 1; selectedYear++ } }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp), colors = ButtonDefaults.buttonColors(containerColor = theme.lamp.primary.copy(0.18f), contentColor = theme.inkColor)) { Text("▶") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { selectedYear-- }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(30.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.08f), contentColor = theme.inkColor)) { Text("-", fontSize = 12.sp) }
                        Button(onClick = { selectedYear++ }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(30.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.08f), contentColor = theme.inkColor)) { Text("+", fontSize = 12.sp) }
                    }
                }
                LazyVerticalGrid(columns = GridCells.Fixed(7), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(210.dp)) {
                    items((1..daysInMonth).toList()) { day ->
                        val isSel = day == selectedDay
                        Box(modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(if (isSel) theme.lamp.primary else (if (theme.isDark) Color.White.copy(0.08f) else Color.White.copy(0.60f))).clickable { selectedDay = day }, contentAlignment = Alignment.Center) {
                            Text("$day", color = if (isSel) Color.White else theme.inkColor, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MutedCancelButton("انصراف", onClick = onDismiss, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    PrimarySaveButton("تایید تاریخ", onClick = { onDateSelected(JalaliCalendar.Date(selectedYear, selectedMonth, selectedDay).toString()); onDismiss() }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun JellyGlassActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    val theme = LocalThemeState.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed && enabled) 0.94f else 1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "jellyScale")
    val shineAlpha by animateFloatAsState(targetValue = if (isPressed && enabled) 0.95f else 0.55f, animationSpec = tween(140), label = "jellyShine")
    Box(modifier = modifier.height(56.dp).graphicsLayer(scaleX = scale, scaleY = scale).shadow(elevation = if (isPressed) 6.dp else 18.dp, shape = RoundedCornerShape(28.dp), spotColor = theme.lamp.primary.copy(alpha = 0.5f)).clip(RoundedCornerShape(28.dp)).background(Brush.horizontalGradient(if (isPressed) listOf(theme.lamp.primary, Color(0xFF38B6FF)) else listOf(theme.lamp.primary, theme.lamp.primary.copy(alpha = 0.82f)))).border(BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color.White.copy(0.9f), theme.lamp.light.copy(0.5f), Color.White.copy(0.2f)))), RoundedCornerShape(28.dp)).clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(colors = listOf(Color.White.copy(alpha = shineAlpha * 0.5f), Color.Transparent), center = Offset(150f, 20f), radius = 260f)))
        Box(Modifier.fillMaxWidth().height(24.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = shineAlpha), Color.White.copy(alpha = 0.02f)))))
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 0.2.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Box(Modifier.align(Alignment.CenterStart).padding(start = 22.dp)) { if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.5.dp) else Text("✨", fontSize = 16.sp) }
        }
    }
}

@Composable
private fun JellyGlassInputField(value: String, onValueChange: (String) -> Unit, label: String, leadingIcon: String, modifier: Modifier = Modifier, password: Boolean = false, keyboardType: KeyboardType = KeyboardType.Text) {
    val theme = LocalThemeState.current
    var passwordVisible by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (theme.isDark) Color(0xFF141418).copy(0.70f) else Color.White.copy(0.68f)).border(BorderStroke(1.2.dp, Brush.verticalGradient(listOf(Color.White.copy(if (theme.isDark) 0.35f else 0.95f), theme.lamp.primary.copy(0.26f), Color.White.copy(if (theme.isDark) 0.1f else 0.40f)))), RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 6.dp), contentAlignment = Alignment.CenterStart) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(leadingIcon, fontSize = 17.sp)
            Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                if (value.isEmpty()) Text(label, color = theme.mutedColor.copy(0.72f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                BasicTextField(value = value, onValueChange = onValueChange, singleLine = true, visualTransformation = if (password && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), textStyle = TextStyle(color = theme.inkColor, fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth())
            }
            if (password) Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(if (theme.isDark) Color.White.copy(0.10f) else Color.Black.copy(0.05f)).clickable { passwordVisible = !passwordVisible }, contentAlignment = Alignment.Center) { PasswordEyeIcon(visible = passwordVisible) }
            else if (value.isNotEmpty()) Box(Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.60f)).clickable { onValueChange("") }, contentAlignment = Alignment.Center) { Text("×", color = theme.inkColor, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// ==========================================
// 8. LOGIN & USERS SCREENS - REDESIGNED LUXURY
// ==========================================
@Composable
private fun LoginScreen(onLoggedIn: (Session) -> Unit, themeState: ThemeState, onThemeChange: (ThemeState) -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())) {
            // Soft watermark logo background - larger & more premium
            Box(Modifier.fillMaxSize().padding(top = 80.dp), contentAlignment = Alignment.TopCenter) {
                AppLogo(modifier = Modifier.graphicsLayer(alpha = if (themeState.isDark) 0.18f else 0.12f, scaleX = 3.2f, scaleY = 3.2f), height = 160.dp)
            }

            ActionIconButton(icon = { Text("🎨", fontSize = 16.sp) }, onClick = { showThemeDialog = true }, modifier = Modifier.align(Alignment.TopStart).padding(20.dp))

            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 56.dp), verticalArrangement = Arrangement.spacedBy(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Hero Brand Card - upgraded
                Box(modifier = Modifier.clip(RoundedCornerShape(28.dp)).background(if (themeState.isDark) Color.White.copy(0.07f) else Color.White.copy(0.62f)).border(BorderStroke(1.4.dp, themeState.cardBorderBrush), RoundedCornerShape(28.dp)).padding(horizontal = 28.dp, vertical = 22.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(themeState.lamp.primary, themeState.lamp.light))).border(BorderStroke(1.5.dp, Color.White.copy(0.85f)), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                            AppLogo(height = 40.dp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PasarGuard", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp), color = themeState.inkColor)
                            Text("MRM Manager • Luxury Edition", fontSize = 12.sp, color = themeState.mutedColor, fontWeight = FontWeight.SemiBold)
                        }
                        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(themeState.lamp.primary.copy(0.12f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Text("v1.1 • Vision OS Glass", fontSize = 10.sp, color = themeState.lamp.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Auth Card - using Jelly Inputs for premium feel
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(if (themeState.isDark) Color(0xFF1C1C22).copy(0.92f) else Color.White.copy(0.82f)).border(BorderStroke(1.6.dp, Brush.linearGradient(listOf(Color.White.copy(0.9f), themeState.lamp.primary.copy(0.45f), Color.White.copy(0.25f)))), RoundedCornerShape(30.dp)).padding(22.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("ورود به پنل مدیریت", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = themeState.inkColor)
                        JellyGlassInputField(value = url, onValueChange = { url = it }, label = "آدرس کامل پنل (https://...)", leadingIcon = "🌐", keyboardType = KeyboardType.Uri)
                        JellyGlassInputField(value = username, onValueChange = { username = it }, label = "نام کاربری", leadingIcon = "👤")
                        JellyGlassInputField(value = password, onValueChange = { password = it }, label = "رمز عبور", leadingIcon = "🔒", password = true)
                        error?.let { Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GlassRed.copy(0.10f)).border(BorderStroke(1.dp, GlassRed.copy(0.22f)), RoundedCornerShape(12.dp)).padding(12.dp)) { Text(it, color = GlassRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp) } }
                        JellyGlassActionButton(text = if (loading) "در حال اتصال ..." else "اتصال امن به پنل ✨", enabled = !loading, loading = loading, onClick = {
                            loading = true; error = null
                            scope.launch {
                                runCatching { PanelApi.login(url, username, password) }.onSuccess(onLoggedIn).onFailure { error = "خطا در اتصال: آدرس، نام کاربری یا رمز عبور اشتباه است.\n${it.message?.take(120)}" }
                                loading = false
                            }
                        }, modifier = Modifier.fillMaxWidth().height(58.dp))
                        Text("🔐 توکن با رمزنگاری امن ذخیره می‌شود. از HTTPS استفاده کنید.", fontSize = 11.sp, color = themeState.mutedColor, lineHeight = 14.sp)
                    }
                }
            }

            if (showThemeDialog) ThemeEditorDialog(themeState = themeState, onDismiss = { showThemeDialog = false }, onThemeChange = onThemeChange)
        }
    }
}

@Composable
private fun UsersScreen(session: Session, onLogout: () -> Unit, themeState: ThemeState, onThemeChange: (ThemeState) -> Unit) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<PanelUser>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedUser by remember { mutableStateOf<PanelUser?>(null) }
    var createUser by remember { mutableStateOf(false) }
    var deleteUser by remember { mutableStateOf<PanelUser?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var onlineCount by remember { mutableStateOf(0) }
    var isHeaderVisible by remember { mutableStateOf(true) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -20f && isHeaderVisible) isHeaderVisible = false
                else if (available.y > 20f && !isHeaderVisible) isHeaderVisible = true
                return Offset.Zero
            }
        }
    }

    var currentFilter by remember { mutableStateOf(UserFilter.ALL) }
    var currentSort by remember { mutableStateOf(UserSort.CREATED) }
    var viewMode by remember { mutableStateOf(ViewMode.MICRO_LIST) }

    fun load() {
        scope.launch {
            loading = true; error = null
            runCatching {
                val list = PanelApi.users(session)
                val sysOnline = PanelApi.onlineUserCount(session)
                users = list
                onlineCount = maxOf(sysOnline, list.count { it.isOnline })
            }.onFailure {
                error = it.message ?: "Unable to load users"
                if (it.message?.contains("401") == true) onLogout()
            }
            loading = false
        }
    }

    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            error = null
            runCatching { action() }.onFailure {
                error = it.message ?: "Action failed"
                if (it.message?.contains("401") == true) onLogout()
            }.onSuccess { load() }
        }
    }

    LaunchedEffect(Unit) { load() }

    val processedUsers = remember(users, query, currentFilter, currentSort) {
        var list = users.filter { it.username.contains(query, ignoreCase = true) }
        list = when (currentFilter) {
            UserFilter.ALL -> list
            UserFilter.ACTIVE -> list.filter { it.status == "active" }
            UserFilter.NEAR_LIMIT -> list.filter { val p = if (it.dataLimit > 0) (it.usedTraffic.toDouble() / it.dataLimit.toDouble()) else 0.0; p >= 0.72 }
            UserFilter.EXPIRED -> list.filter { val p = if (it.dataLimit > 0) (it.usedTraffic.toDouble() / it.dataLimit.toDouble()) else 0.0; p >= 1.0 || it.status == "expired" || it.status == "limited" }
            UserFilter.DISABLED -> list.filter { it.status == "disabled" }
        }
        when (currentSort) {
            UserSort.NAME -> list.sortedBy { it.username.lowercase() }
            UserSort.USAGE -> list.sortedByDescending { it.usedTraffic }
            UserSort.EXPIRY -> list.sortedBy { it.expire ?: "9999" }
            UserSort.CREATED -> list.sortedByDescending { if (it.id > 0) it.id else (it.createdAt ?: "").hashCode().toLong() }
        }
    }

    val totalUsedTraffic = remember(users) { users.sumOf { it.usedTraffic } }

    Scaffold(containerColor = Color.Transparent, modifier = Modifier.nestedScroll(nestedScrollConnection), floatingActionButton = {
        Box(modifier = Modifier.clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(themeState.lamp.primary, themeState.lamp.light))).border(BorderStroke(1.2.dp, Color.White.copy(0.9f)), RoundedCornerShape(26.dp)).clickable { createUser = true }.padding(horizontal = 20.dp, vertical = 13.dp).shadow(12.dp, RoundedCornerShape(26.dp), spotColor = themeState.lamp.primary.copy(0.4f)), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(0.22f)), contentAlignment = Alignment.Center) { Text("+", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White) }
                Text("کاربر جدید", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 13.sp)
            }
        }
    }) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            AnimatedVisibility(visible = isHeaderVisible, enter = expandVertically(spring(stiffness = Spring.StiffnessLow)) + fadeIn(tween(180)), exit = shrinkVertically(spring(stiffness = Spring.StiffnessLow)) + fadeOut(tween(150))) {
                Column {
                    LuxuryTopStatsHeader(totalUsers = users.size, activeUsers = users.count { it.status == "active" }, onlineUsers = onlineCount, totalUsedTraffic = totalUsedTraffic, onRefresh = { load() }, onLogout = onLogout, onOpenThemeDialog = { showThemeDialog = true }, loading = loading)
                    Spacer(Modifier.height(4.dp))
                }
            }

            GlassSearchBar(query = query, onQueryChange = { query = it })
            Spacer(Modifier.height(12.dp))
            FilterAndControlBar(currentFilter = currentFilter, onFilterChange = { currentFilter = it }, currentSort = currentSort, onSortChange = { currentSort = it }, viewMode = viewMode, onViewModeChange = { viewMode = it }, users = users)
            Spacer(Modifier.height(14.dp))

            when {
                loading -> {
                    // Shimmer skeleton grid
                    LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                        items(6) { SkeletonCard() }
                    }
                }
                error != null -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (themeState.isDark) Color.White.copy(0.08f) else Color.White.copy(0.62f)).border(BorderStroke(1.dp, GlassRed.copy(0.18f)), RoundedCornerShape(20.dp)).padding(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚠️ خطا در دریافت اطلاعات", fontWeight = FontWeight.Bold, color = GlassRed, fontSize = 14.sp)
                        Text(error ?: "", color = themeState.mutedColor, fontSize = 12.sp)
                        GlassButton("🔄 تلاش مجدد", onClick = { load() }, modifier = Modifier.fillMaxWidth())
                    }
                }
                else -> {
                    if (processedUsers.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(36.dp).clip(RoundedCornerShape(24.dp)).background(if (themeState.isDark) Color.White.copy(0.05f) else Color.White.copy(0.45f)).border(BorderStroke(1.dp, Color.White.copy(0.3f)), RoundedCornerShape(24.dp)).padding(28.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("🔍", fontSize = 36.sp)
                                Text("کاربری یافت نشد", fontWeight = FontWeight.Bold, color = themeState.inkColor, fontSize = 15.sp)
                                Text("فیلتر یا جستجو را تغییر دهید", color = themeState.mutedColor, fontSize = 12.sp)
                            }
                        }
                    } else when (viewMode) {
                        ViewMode.GRID -> LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 100.dp)) { items(processedUsers) { user -> LuxuryGridCard(user, onClick = { selectedUser = user }) } }
                        ViewMode.COMPACT_LIST -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 100.dp)) { items(processedUsers) { user -> LuxuryCompactRow(user, onClick = { selectedUser = user }) } }
                        ViewMode.MICRO_LIST -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 100.dp)) { items(processedUsers) { user -> LuxuryMicroRow(user, onClick = { selectedUser = user }) } }
                    }
                }
            }
        }
    }

    if (showThemeDialog) ThemeEditorDialog(themeState = themeState, onDismiss = { showThemeDialog = false }, onThemeChange = onThemeChange)

    selectedUser?.let { user ->
        UserEditorDialog(initial = user, onDismiss = { selectedUser = null }, onSave = { limitGb, expireShamsi ->
            selectedUser = null; runAction { val iso = JalaliCalendar.shamsiToIso(expireShamsi); PanelApi.modifyUser(session, user.username, limitGb.value, iso) }
        }, onToggle = { selectedUser = null; runAction { PanelApi.setDisabled(session, user.username, user.status != "disabled") } }, onDelete = { deleteUser = user; selectedUser = null }, onResetUsage = {
            runAction { PanelApi.resetUsage(session, user.username); val refreshed = PanelApi.users(session); users = refreshed; selectedUser = refreshed.find { it.username == user.username } }
        }, onResetExpiry = {
            runAction { PanelApi.modifyUser(session, user.username, (user.dataLimit.toDouble() / 1073741824.0), ""); val refreshed = PanelApi.users(session); users = refreshed; selectedUser = refreshed.find { it.username == user.username } }
        })
    }

    if (createUser) UserEditorDialog(initial = null, onDismiss = { createUser = false }, onSave = { limitGb, expireShamsi -> createUser = false; runAction { val iso = JalaliCalendar.shamsiToIso(expireShamsi); PanelApi.createUser(session, limitGb.username, limitGb.value, iso) } }, onToggle = null, onDelete = null, onResetUsage = null, onResetExpiry = null)

    deleteUser?.let { user ->
        val theme = LocalThemeState.current
        Dialog(onDismissRequest = { deleteUser = null }) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(GlassShape).background(theme.dialogBgColor).border(BorderStroke(1.2.dp, theme.cardBorderBrush), GlassShape)) {
                Box(Modifier.size(240.dp).align(Alignment.TopEnd).offset(x = 60.dp, y = (-50).dp).background(Brush.radialGradient(listOf(theme.lamp.spotHigh, Color.Transparent)), shape = RoundedCornerShape(200.dp)))
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("آیا ${user.username} حذف شود؟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    Text("این عملیات غیرقابل بازگشت است و تمام اطلاعات کاربر حذف خواهد شد.", color = theme.mutedColor, fontSize = 13.sp, lineHeight = 19.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        GlassButton("انصراف", onClick = { deleteUser = null }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        GlassButton("حذف کاربر", onClick = { deleteUser = null; runAction { PanelApi.deleteUser(session, user.username) } }, modifier = Modifier.weight(1f), isRed = true)
                    }
                }
            }
        }
    }
}

private data class UserEditorValues(val username: String, val value: Double)

@Composable
private fun UserEditorDialog(initial: PanelUser?, onDismiss: () -> Unit, onSave: (UserEditorValues, String) -> Unit, onToggle: (() -> Unit)?, onDelete: (() -> Unit)?, onResetUsage: (() -> Unit)?, onResetExpiry: (() -> Unit)?) {
    val theme = LocalThemeState.current
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var limitGb by remember { mutableStateOf(if (initial == null || initial.dataLimit == 0L) "" else "%.2f".format(Locale.US, initial.dataLimit / 1073741824.0).trimEnd('0').trimEnd('.')) }
    var expireShamsi by remember { mutableStateOf(if (initial?.expire != null && initial.expire != "0") JalaliCalendar.isoToShamsi(initial.expire) else "") }
    var formError by remember { mutableStateOf<String?>(null) }
    var showShamsiCalendar by remember { mutableStateOf(false) }
    var customAddDays by remember { mutableStateOf("") }
    var showQrDialog by remember { mutableStateOf(false) }
    var confirmResetUsage by remember { mutableStateOf(false) }
    var confirmResetExpiry by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(theme.dialogBgColor).border(BorderStroke(1.6.dp, theme.cardBorderBrush), RoundedCornerShape(30.dp))) {
            Box(Modifier.size(280.dp).align(Alignment.TopEnd).offset(x = 80.dp, y = (-60).dp).background(Brush.radialGradient(listOf(theme.lamp.spotHigh, theme.lamp.spotLow, Color.Transparent)), shape = RoundedCornerShape(200.dp)))
            Column(Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (initial == null) "➕ کاربر جدید" else "✏️ ویرایش ${initial.username}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(theme.lamp.primary.copy(0.12f)), contentAlignment = Alignment.Center) { Text(if (initial == null) "🆕" else "👤", fontSize = 18.sp) }
                }

                if (initial == null) JellyGlassInputField(value = username, onValueChange = { username = it }, label = "نام کاربری (3-32 کاراکتر)", leadingIcon = "👤")

                JellyGlassInputField(value = limitGb, onValueChange = { limitGb = it }, label = "حجم گیگابایت (خالی=نامحدود)", leadingIcon = "💾", keyboardType = KeyboardType.Decimal)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { JellyGlassInputField(value = expireShamsi, onValueChange = { expireShamsi = it }, label = "تاریخ انقضا شمسی", leadingIcon = "📅") }
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(theme.lamp.primary.copy(0.14f)).border(BorderStroke(1.dp, theme.lamp.primary.copy(0.25f)), RoundedCornerShape(14.dp)).clickable { showShamsiCalendar = true }, contentAlignment = Alignment.Center) { Text("📅", fontSize = 18.sp) }
                    }
                    Text("افزودن سریع:", fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        MiniGlassButton("+۳۰ روز") { val baseIso = if (initial?.expire != null && initial.expire != "0") initial.expire else null; expireShamsi = JalaliCalendar.isoToShamsi(JalaliCalendar.addDaysToIso(baseIso, 30)) }
                        MiniGlassButton("+۶۰ روز") { val baseIso = if (initial?.expire != null && initial.expire != "0") initial.expire else null; expireShamsi = JalaliCalendar.isoToShamsi(JalaliCalendar.addDaysToIso(baseIso, 60)) }
                        MiniGlassButton("+۹۰ روز") { val baseIso = if (initial?.expire != null && initial.expire != "0") initial.expire else null; expireShamsi = JalaliCalendar.isoToShamsi(JalaliCalendar.addDaysToIso(baseIso, 90)) }
                        Box(Modifier.width(76.dp).height(34.dp).clip(RoundedCornerShape(12.dp)).background(if (theme.isDark) Color.White.copy(0.10f) else Color.White.copy(0.60f)).border(BorderStroke(1.dp, theme.cardBorderBrush), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                            if (customAddDays.isEmpty()) Text("+روز", fontSize = 11.sp, color = theme.mutedColor.copy(0.8f))
                            BasicTextField(value = customAddDays, onValueChange = { customAddDays = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = TextStyle(color = theme.inkColor, fontSize = 12.sp, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth())
                        }
                        if (customAddDays.isNotEmpty()) MiniGlassButton("✓") {
                            val d = customAddDays.toIntOrNull() ?: 0; if (d > 0) { val baseIso = if (initial?.expire != null && initial.expire != "0") initial.expire else null; expireShamsi = JalaliCalendar.isoToShamsi(JalaliCalendar.addDaysToIso(baseIso, d)); customAddDays = "" }
                        }
                    }
                }

                initial?.let { user ->
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(if (theme.isDark) 0.06f else 0.45f)).border(BorderStroke(1.dp, Color.White.copy(0.22f)), RoundedCornerShape(18.dp)).padding(14.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(when (user.status) { "active" -> GlassGreen.copy(0.18f); "disabled" -> Color.Gray.copy(0.15f); else -> GlassRed.copy(0.15f) }), contentAlignment = Alignment.Center) { Text(if (user.status == "active") "🟢" else "🔴", fontSize = 14.sp) }
                                Column { Text("${formatBytes(user.usedTraffic)} مصرف • ${user.status.uppercase()}", color = theme.inkColor, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text("ایجاد: ${JalaliCalendar.isoToShamsi(user.createdAt ?: "")} • ${if (user.isOnline) "آنلاین" else "آفلاین"}", color = theme.mutedColor, fontSize = 11.sp) }
                            }
                            if (user.subUrl.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    Text("لینک اشتراک:", fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (theme.isDark) Color.White.copy(0.08f) else Color.White.copy(0.70f)).border(BorderStroke(1.dp, theme.cardBorderBrush), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                                            Text(user.subUrl, fontSize = 11.sp, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        MiniGlassButton("📋") {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sub", user.subUrl))
                                            android.widget.Toast.makeText(context, "کپی شد ✓", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        MiniGlassButton("📱") { showQrDialog = true }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                onResetUsage?.let { GlassButton("♻️ ریست حجم", onClick = { confirmResetUsage = true }, modifier = Modifier.weight(1f)) }
                                onResetExpiry?.let { GlassButton("⏰ ریست زمان", onClick = { confirmResetExpiry = true }, modifier = Modifier.weight(1f)) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                onToggle?.let { toggle -> GlassButton(text = if (user.status == "disabled") "🟢 فعال‌سازی" else "⚪ غیرفعال", onClick = toggle, modifier = Modifier.weight(1f)) }
                                onDelete?.let { del -> GlassButton(text = "🗑 حذف", onClick = del, modifier = Modifier.weight(1f), isRed = true) }
                            }
                        }
                    }
                }

                formError?.let { Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GlassRed.copy(0.10f)).border(BorderStroke(1.dp, GlassRed.copy(0.20f)), RoundedCornerShape(12.dp)).padding(12.dp)) { Text(it, color = GlassRed, fontSize = 12.sp, fontWeight = FontWeight.Medium) } }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MutedCancelButton("✕ انصراف", onClick = onDismiss, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    PrimarySaveButton("✓ ذخیره", onClick = {
                        val cleanLimitStr = limitGb.replace(',', '.').trim()
                        val limit = if (cleanLimitStr.isBlank()) 0.0 else cleanLimitStr.toDoubleOrNull()
                        if (username.length !in 3..32 && initial == null) formError = "نام کاربری باید ۳ تا ۳۲ کاراکتر باشد."
                        else if (limit == null || limit < 0) formError = "حجم نامعتبر است."
                        else if (expireShamsi.isNotBlank() && !Regex("^\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}$").matches(expireShamsi)) formError = "فرمت تاریخ شمسی مثل ۱۴۰۴/۰۵/۱۹"
                        else onSave(UserEditorValues(username, limit), expireShamsi)
                    }, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (confirmResetUsage && initial != null) {
        Dialog(onDismissRequest = { confirmResetUsage = false }) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(theme.dialogBgColor).border(BorderStroke(1.5.dp, theme.cardBorderBrush), RoundedCornerShape(24.dp)).padding(22.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("⚠️ تایید ریست حجم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = GlassRed)
                    Text("آیا مطمئن هستید که حجم مصرفی ${initial.username} به صفر بازنشانی شود؟", color = theme.inkColor, fontSize = 13.sp, lineHeight = 20.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        MutedCancelButton("انصراف", onClick = { confirmResetUsage = false }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        GlassButton("تایید و ریست", onClick = { confirmResetUsage = false; onResetUsage?.invoke() }, modifier = Modifier.weight(1f), isRed = true)
                    }
                }
            }
        }
    }

    if (confirmResetExpiry && initial != null) {
        Dialog(onDismissRequest = { confirmResetExpiry = false }) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(theme.dialogBgColor).border(BorderStroke(1.5.dp, theme.cardBorderBrush), RoundedCornerShape(24.dp)).padding(22.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("⏰ تایید ریست زمان", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = theme.lamp.primary)
                    Text("تاریخ انقضا برای ${initial.username} نامحدود شود؟", color = theme.inkColor, fontSize = 13.sp, lineHeight = 20.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        MutedCancelButton("انصراف", onClick = { confirmResetExpiry = false }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        GlassButton("تایید", onClick = { confirmResetExpiry = false; onResetExpiry?.invoke() }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (showQrDialog && initial != null && initial.subUrl.isNotEmpty()) SubscriptionQrDialog(user = initial, onDismiss = { showQrDialog = false })
    if (showShamsiCalendar) ShamsiCalendarPickerDialog(initialDateShamsi = expireShamsi, onDismiss = { showShamsiCalendar = false }, onDateSelected = { expireShamsi = it })
}

private fun formatBytes(value: Long): String {
    if (value <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val index = (kotlin.math.ln(value.toDouble()) / kotlin.math.ln(1024.0)).toInt().coerceAtMost(units.lastIndex)
    return "${DecimalFormat("#.##").format(value / Math.pow(1024.0, index.toDouble()))} ${units[index]}"
}

private object PanelApi {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private fun baseUrl(input: String): String {
        val prepared = if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input"
        val uri = URI(prepared)
        require(!uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) { "Invalid URL" }
        return buildString { append(uri.scheme); append("://"); append(uri.host); if (uri.port != -1) append(":${uri.port}") }
    }
    private fun userUrl(session: Session, username: String): String = "${session.baseUrl}/api/user/${URLEncoder.encode(username, "UTF-8")}"
    private fun requestBuilder(session: Session, url: String): Request.Builder = Request.Builder().url(url).header("Authorization", "Bearer ${session.token}")
    suspend fun login(address: String, username: String, password: String): Session = withContext(Dispatchers.IO) {
        require(username.isNotBlank() && password.isNotBlank()) { "Credentials required" }
        val base = baseUrl(address)
        val body = FormBody.Builder().add("username", username).add("password", password).build()
        val request = Request.Builder().url("$base/api/admin/token").post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Login failed: ${response.code}")
            val token = JSONObject(response.body?.string() ?: error("Empty login response")).getString("access_token")
            Session(base, token, username)
        }
    }
    suspend fun users(session: Session): List<PanelUser> = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, "${session.baseUrl}/api/users?offset=0&limit=1000&load_sub=true").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Request failed: ${response.code}")
            val data = JSONObject(response.body?.string() ?: error("Empty users response")).getJSONArray("users")
            List(data.length()) { index -> parseUser(data.getJSONObject(index)) }
        }
    }
    suspend fun createUser(session: Session, username: String, limitGb: Double, expireIso: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("username", username).put("status", "active").put("data_limit", gbToBytes(limitGb)).put("expire", expireValue(expireIso))
        executeJson(requestBuilder(session, "${session.baseUrl}/api/user").post(body.toString().toRequestBody(jsonType)).build())
    }
    suspend fun modifyUser(session: Session, username: String, limitGb: Double, expireIso: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("data_limit", gbToBytes(limitGb)).put("expire", expireValue(expireIso))
        executeJson(requestBuilder(session, userUrl(session, username)).put(body.toString().toRequestBody(jsonType)).build())
    }
    suspend fun resetUsage(session: Session, username: String) = withContext(Dispatchers.IO) {
        executeJson(requestBuilder(session, "${userUrl(session, username)}/reset").post("".toRequestBody(jsonType)).build())
    }
    suspend fun setDisabled(session: Session, username: String, disabled: Boolean) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("disabled", disabled)
        executeJson(requestBuilder(session, "${userUrl(session, username)}/disabled").put(body.toString().toRequestBody(jsonType)).build())
    }
    suspend fun deleteUser(session: Session, username: String) = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, userUrl(session, username)).delete().build()
        client.newCall(request).execute().use { response -> if (!response.isSuccessful) error("Delete failed: ${response.code}") }
    }
    private fun executeJson(request: Request) {
        client.newCall(request).execute().use { response -> if (!response.isSuccessful) { val details = response.body?.string()?.take(250).orEmpty(); error("Request failed: ${response.code} $details") } }
    }
    private fun parseUser(user: JSONObject) = PanelUser(id = user.optLong("id", 0L), username = user.getString("username"), status = user.optString("status", "unknown"), usedTraffic = user.optLong("used_traffic", 0), dataLimit = user.optLong("data_limit", 0), expire = if (user.isNull("expire")) null else user.optString("expire").takeIf { it != "null" && it != "0" }, createdAt = if (user.isNull("created_at")) null else user.optString("created_at"), subUrl = user.optString("subscription_url", "").ifBlank { user.optString("sub_url", "") }, onlineAt = if (user.isNull("online_at")) null else user.optString("online_at").takeIf { it != "null" }, isOnline = user.optBoolean("online", false) || (user.optLong("online_at", 0L) > System.currentTimeMillis() / 1000 - 300))
    suspend fun onlineUserCount(session: Session): Int = withContext(Dispatchers.IO) {
        runCatching {
            val req = requestBuilder(session, "${session.baseUrl}/api/system/users").get().build()
            client.newCall(req).execute().use { res -> if (res.isSuccessful) JSONObject(res.body?.string() ?: "{}").optInt("online_users", 0) else 0 }
        }.getOrDefault(0)
    }
    private fun gbToBytes(value: Double): Long = (value * 1024 * 1024 * 1024).toLong()
    private fun expireValue(date: String): Any = if (date.isBlank() || date == "null" || date == "0") 0 else "${date}T23:59:59Z"
}

private class SessionStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(context, "mrm_pg_manager", MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(), EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun read(): Session? {
        val base = prefs.getString("base", null) ?: return null
        val token = prefs.getString("token", null) ?: return null
        return Session(base, token, prefs.getString("username", "") ?: "")
    }
    fun save(value: Session) = prefs.edit().putString("base", value.baseUrl).putString("token", value.token).putString("username", value.username).apply()
    fun clear() = prefs.edit().clear().apply()
    fun readTheme(): ThemeState {
        val lampName = prefs.getString("theme_lamp", LampColor.SKY_BLUE.name) ?: LampColor.SKY_BLUE.name
        val isDark = prefs.getBoolean("theme_dark", false)
        val lamp = runCatching { LampColor.valueOf(lampName) }.getOrDefault(LampColor.SKY_BLUE)
        return ThemeState(lamp = lamp, isDark = isDark)
    }
    fun saveTheme(themeState: ThemeState) = prefs.edit().putString("theme_lamp", themeState.lamp.name).putBoolean("theme_dark", themeState.isDark).apply()
}
