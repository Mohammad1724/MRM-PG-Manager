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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
        
        val gy2 = gy - 1600
        val gm2 = gm - 1
        val gd2 = gd - 1
        
        var gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
        for (i in 0 until gm2) gDayNo += gDaysInMonth[i]
        if (gm2 > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) gDayNo++
        gDayNo += gd2
        
        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053
        
        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461
        
        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }
        
        var i = 0
        while (i < 11 && jDayNo >= jDaysInMonth[i]) {
            jDayNo -= jDaysInMonth[i]
            i++
        }
        return Date(jy, i + 1, jDayNo + 1)
    }

    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): String {
        val jy2 = jy - 979
        val jm2 = jm - 1
        val jd2 = jd - 1
        
        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        var jDayNo = 365 * jy2 + (jy2 / 33) * 8 + (jy2 % 33 + 3) / 4
        for (i in 0 until jm2) jDayNo += jDaysInMonth[i]
        jDayNo += jd2
        
        var gDayNo = jDayNo + 79
        var gy = 1600 + 400 * (gDayNo / 146097)
        gDayNo %= 146097
        
        var leap = true
        if (gDayNo >= 36525) {
            gDayNo--
            gy += 100 * (gDayNo / 36524)
            gDayNo %= 36524
            if (gDayNo >= 365) gDayNo++ else leap = false
        }
        
        gy += 4 * (gDayNo / 1461)
        gDayNo %= 1461
        
        if (gDayNo >= 366) {
            leap = false
            gDayNo--
            gy += gDayNo / 365
            gDayNo %= 365
        }
        
        val gDaysInMonth = intArrayOf(31, if (leap && ((gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var i = 0
        while (i < 12 && gDayNo >= gDaysInMonth[i]) {
            gDayNo -= gDaysInMonth[i]
            i++
        }
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
        val baseDate = if (iso.isNullOrBlank() || iso == "0" || iso == "null") {
            LocalDate.now()
        } else {
            runCatching { LocalDate.parse(iso.take(10)) }.getOrDefault(LocalDate.now())
        }
        val target = baseDate.plusDays(daysToAdd.toLong())
        return target.toString()
    }
}

// ==========================================
// 2. THEME & PALETTE SYSTEM
// ==========================================
enum class LampColor(
    val label: String,
    val primary: Color,
    val light: Color,
    val spotHigh: Color,
    val spotLow: Color
) {
    GOLD("Champagne Gold", Color(0xFFC59B27), Color(0xFFF3E5AB), Color(0xBBF5D061), Color(0x44E5B84B)),
    MAGENTA("Royal Magenta", Color(0xFFC8327E), Color(0xFFFAD1E6), Color(0xBBC8327E), Color(0x44E86FA8)),
    TURQUOISE("Neon Turquoise", Color(0xFF0EA89B), Color(0xFFB5F2EC), Color(0xBB2AD4C5), Color(0x4414A094)),
    VIOLET("Cyber Violet", Color(0xFF7A42D4), Color(0xFFE2D1FC), Color(0xBB9862F5), Color(0x447A42D4)),
    EMERALD("Emerald Glow", Color(0xFF1A8C5B), Color(0xFFC2F2DC), Color(0xBB2EC486), Color(0x441A8C5B))
}

data class ThemeState(
    val lamp: LampColor = LampColor.GOLD,
    val isDark: Boolean = false
) {
    val inkColor: Color
        get() = if (isDark) Color(0xFFF4F4F6) else Color(0xFF1C1B18)

    val mutedColor: Color
        get() = if (isDark) Color(0xFFA09C94) else Color(0xFF6A655B)

    val cardBgColor: Color
        get() = if (isDark) Color(0xFF222226).copy(alpha = 0.68f) else Color.White.copy(alpha = 0.48f)

    val cardBorderBrush: Brush
        get() = if (isDark) {
            Brush.linearGradient(listOf(Color.White.copy(0.38f), Color.White.copy(0.08f)))
        } else {
            Brush.linearGradient(listOf(Color.White.copy(0.95f), Color.White.copy(0.2f)))
        }

    val dialogBgColor: Color
        get() = if (isDark) Color(0xFF18181C).copy(alpha = 0.94f) else Color(0xFFFFFDF8).copy(alpha = 0.92f)

    val searchBgColor: Color
        get() = if (isDark) Color(0xFF2C2C32).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.65f)
}

val LocalThemeState = compositionLocalOf { ThemeState() }

private val GlassGreen = Color(0xFF1A8C5B)
private val GlassAmber = Color(0xFFD9822B)
private val GlassRed = Color(0xFFC93B3B)
private val GlassShape = RoundedCornerShape(24.dp)

@Composable
private fun LiquidGlassTheme(themeState: ThemeState, content: @Composable () -> Unit) {
    val colors = if (themeState.isDark) {
        darkColorScheme(
            primary = themeState.lamp.primary,
            onPrimary = Color.White,
            secondary = themeState.lamp.light,
            background = Color(0xFF101012),
            surface = Color(0xFF1E1E22).copy(alpha = 0.70f),
            onSurface = themeState.inkColor,
            onBackground = themeState.inkColor,
            error = GlassRed
        )
    } else {
        lightColorScheme(
            primary = themeState.lamp.primary,
            onPrimary = Color.White,
            secondary = themeState.lamp.light,
            background = Color(0xFFFAF6EE),
            surface = Color.White.copy(alpha = 0.60f),
            onSurface = themeState.inkColor,
            onBackground = themeState.inkColor,
            error = GlassRed
        )
    }

    val bgGradient = if (themeState.isDark) {
        Brush.verticalGradient(listOf(Color(0xFF151518), Color(0xFF0E0E10), Color(0xFF08080A)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFFFFDF9), Color(0xFFFFF7E6), Color(0xFFFFF4DC)))
    }

    CompositionLocalProvider(LocalThemeState provides themeState) {
        MaterialTheme(colorScheme = colors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgGradient)
            ) {
                // Directional Spotlight Lamp from LEFT side
                Box(
                    Modifier
                        .size(420.dp)
                        .align(Alignment.TopStart)
                        .offset(x = (-110).dp, y = (-40).dp)
                        .background(
                            Brush.radialGradient(
                                listOf(themeState.lamp.spotHigh, themeState.lamp.spotLow, Color.Transparent)
                            ),
                            RoundedCornerShape(200.dp)
                        )
                )
                Box(
                    Modifier
                        .size(340.dp)
                        .align(Alignment.CenterStart)
                        .offset(x = (-120).dp, y = 180.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(themeState.lamp.spotHigh.copy(alpha = 0.5f), Color.Transparent)
                            ),
                            RoundedCornerShape(200.dp)
                        )
                )
                content()
            }
        }
    }
}

// ==========================================
// 3. LOGO & CRISP VECTOR ICONS
// ==========================================
@Composable
private fun AppLogo(modifier: Modifier = Modifier, height: Dp = 24.dp) {
    val context = LocalContext.current
    val resId = remember(context) {
        context.resources.getIdentifier("logo_mrm", "drawable", context.packageName)
    }
    if (resId != 0) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = "MRM Logo",
            contentScale = ContentScale.Fit,
            modifier = modifier
                .height(height)
                .widthIn(max = height * 2.8f)
        )
    } else {
        val theme = LocalThemeState.current
        Box(
            modifier = modifier
                .height(height)
                .widthIn(max = height * 2.8f)
                .clip(RoundedCornerShape(height / 3.2f))
                .background(Brush.linearGradient(listOf(theme.lamp.primary, theme.lamp.light)))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.85f)), RoundedCornerShape(height / 3.2f))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "MRM",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (height.value * 0.45f).sp
            )
        }
    }
}

/**
 * Universal Eye Icon for Password toggle (علامت چشم مرسوم در سایت‌ها)
 */
@Composable
private fun PasswordEyeIcon(visible: Boolean) {
    val theme = LocalThemeState.current
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        // Outer eye oval contour
        drawOval(
            color = theme.inkColor,
            topLeft = Offset(1f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w - 2f, h * 0.56f),
            style = Stroke(width = 2.2f)
        )
        // Center Pupil circle
        drawCircle(
            color = if (visible) theme.lamp.primary else theme.inkColor,
            radius = if (visible) w * 0.20f else w * 0.14f,
            center = Offset(w * 0.5f, h * 0.5f)
        )
        // Diagonal slash across the eye when password is hidden
        if (!visible) {
            drawLine(
                color = theme.lamp.primary,
                start = Offset(w * 0.10f, h * 0.90f),
                end = Offset(w * 0.90f, h * 0.10f),
                strokeWidth = 2.8f
            )
        }
    }
}

/**
 * Crisp Universal Exit Icon (آیکون تمیز و استاندارد خروج به جای علامت به هم ریخته)
 */
@Composable
private fun ExitIcon() {
    Canvas(modifier = Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        // Left door bracket
        drawRect(
            color = GlassRed,
            topLeft = Offset(0f, 1f),
            size = androidx.compose.ui.geometry.Size(w * 0.45f, h - 2f),
            style = Stroke(width = 2f)
        )
        // Arrow shaft exiting to the right
        drawLine(color = GlassRed, start = Offset(w * 0.25f, h * 0.5f), end = Offset(w, h * 0.5f), strokeWidth = 2.2f)
        // Arrow head right
        drawLine(color = GlassRed, start = Offset(w * 0.68f, h * 0.22f), end = Offset(w, h * 0.5f), strokeWidth = 2.2f)
        drawLine(color = GlassRed, start = Offset(w * 0.68f, h * 0.78f), end = Offset(w, h * 0.5f), strokeWidth = 2.2f)
    }
}

data class Session(val baseUrl: String, val token: String, val username: String)
data class PanelUser(
    val id: Long,
    val username: String,
    val status: String,
    val usedTraffic: Long,
    val dataLimit: Long,
    val expire: String?,
    val createdAt: String?
)

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
            LoginScreen(
                onLoggedIn = { value ->
                    store.save(value)
                    session = value
                },
                themeState = themeState,
                onThemeChange = { newTheme ->
                    themeState = newTheme
                    store.saveTheme(newTheme)
                }
            )
        } else {
            UsersScreen(
                session = session!!,
                onLogout = {
                    store.clear()
                    session = null
                },
                themeState = themeState,
                onThemeChange = { newTheme ->
                    themeState = newTheme
                    store.saveTheme(newTheme)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val theme = LocalThemeState.current
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 14.sp) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        visualTransformation = if (password && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = if (password) {
            {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { passwordVisible = !passwordVisible },
                    contentAlignment = Alignment.Center
                ) {
                    PasswordEyeIcon(visible = passwordVisible)
                }
            }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = if (theme.isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.50f),
            unfocusedContainerColor = if (theme.isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.30f),
            focusedBorderColor = theme.lamp.primary,
            unfocusedBorderColor = if (theme.isDark) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.90f),
            focusedLabelColor = theme.lamp.primary,
            unfocusedLabelColor = theme.mutedColor,
            cursorColor = theme.lamp.primary
        ),
        textStyle = TextStyle(color = theme.inkColor, fontSize = 15.sp),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun GlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalThemeState.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(theme.searchBgColor)
            .border(
                BorderStroke(
                    1.2.dp,
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = if (theme.isDark) 0.4f else 0.98f),
                            theme.lamp.light.copy(alpha = 0.6f),
                            Color.White.copy(alpha = if (theme.isDark) 0.15f else 0.40f)
                        )
                    )
                ),
                RoundedCornerShape(25.dp)
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("🔍", fontSize = 15.sp)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "Search by username...",
                        color = theme.mutedColor.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = theme.inkColor, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = if (theme.isDark) 0.25f else 0.8f))
                        .clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", color = theme.inkColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 4. TOP HEADER (علامت تغییر تم سمت چپ زیر PasarGuard + دکمه خروج تمیز)
@Composable
private fun LuxuryTopStatsHeader(
    totalUsers: Int,
    activeUsers: Int,
    totalUsedTraffic: Long,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onOpenThemeDialog: () -> Unit,
    loading: Boolean
) {
    val theme = LocalThemeState.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left side: PasarGuard + AppLogo, and Theme selector button underneath it
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "PasarGuard",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = theme.inkColor
                    )
                    AppLogo(height = 24.dp)
                }
                
                // Theme selector button placed on the LEFT side right under PasarGuard
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (theme.isDark) Color.White.copy(0.12f) else Color.White.copy(alpha = 0.70f))
                        .border(BorderStroke(1.dp, if (theme.isDark) Color.White.copy(0.3f) else Color.White), RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenThemeDialog)
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("🎨", fontSize = 13.sp)
                        Text("Theme", color = theme.inkColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Right side: Refresh & Exit buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (theme.isDark) Color.White.copy(0.12f) else Color.White.copy(alpha = 0.70f))
                        .border(BorderStroke(1.dp, if (theme.isDark) Color.White.copy(0.3f) else Color.White), RoundedCornerShape(16.dp))
                        .clickable(enabled = !loading, onClick = onRefresh)
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(13.dp), color = theme.inkColor, strokeWidth = 2.dp)
                        } else {
                            Text("🔄", fontSize = 12.sp)
                        }
                        Text("Refresh", color = theme.inkColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFFF2F2).copy(alpha = if (theme.isDark) 0.18f else 0.80f))
                        .border(BorderStroke(1.dp, Color(0xFFF2BABA)), RoundedCornerShape(16.dp))
                        .clickable(onClick = onLogout)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExitIcon()
                        Text("Exit", color = GlassRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Luxury Summary Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(if (theme.isDark) Color.White.copy(0.08f) else Color.White.copy(alpha = 0.45f))
                .border(
                    BorderStroke(1.dp, Brush.horizontalGradient(listOf(Color.White.copy(if (theme.isDark) 0.3f else 0.9f), Color.White.copy(if (theme.isDark) 0.08f else 0.3f)))),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(label = "Total Users", value = "$totalUsers", color = theme.inkColor)
            Box(Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = if (theme.isDark) 0.2f else 0.8f)))
            StatItem(label = "Active Now", value = "$activeUsers", color = GlassGreen)
            Box(Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = if (theme.isDark) 0.2f else 0.8f)))
            StatItem(label = "Total Traffic", value = formatBytes(totalUsedTraffic), color = theme.lamp.primary)
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    val theme = LocalThemeState.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FilterAndControlBar(
    currentFilter: UserFilter,
    onFilterChange: (UserFilter) -> Unit,
    currentSort: UserSort,
    onSortChange: (UserSort) -> Unit,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    users: List<PanelUser>
) {
    val theme = LocalThemeState.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChipItem("🌟 All (${users.size})", currentFilter == UserFilter.ALL) { onFilterChange(UserFilter.ALL) }
            FilterChipItem("🟢 Active (${users.count { it.status == "active" }})", currentFilter == UserFilter.ACTIVE) { onFilterChange(UserFilter.ACTIVE) }
            FilterChipItem("🟡 Near Limit", currentFilter == UserFilter.NEAR_LIMIT) { onFilterChange(UserFilter.NEAR_LIMIT) }
            FilterChipItem("🔴 Expired/Full", currentFilter == UserFilter.EXPIRED) { onFilterChange(UserFilter.EXPIRED) }
            FilterChipItem("⚪ Disabled", currentFilter == UserFilter.DISABLED) { onFilterChange(UserFilter.DISABLED) }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sort:", fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                SortPill("Name", currentSort == UserSort.NAME) { onSortChange(UserSort.NAME) }
                SortPill("Usage", currentSort == UserSort.USAGE) { onSortChange(UserSort.USAGE) }
                SortPill("Expiry", currentSort == UserSort.EXPIRY) { onSortChange(UserSort.EXPIRY) }
                SortPill("Created", currentSort == UserSort.CREATED) { onSortChange(UserSort.CREATED) }
            }
            Spacer(Modifier.width(6.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (theme.isDark) Color.White.copy(0.1f) else Color.White.copy(alpha = 0.55f))
                    .border(BorderStroke(1.dp, if (theme.isDark) Color.White.copy(0.3f) else Color.White), RoundedCornerShape(12.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ViewModeIcon("田", viewMode == ViewMode.GRID) { onViewModeChange(ViewMode.GRID) }
                ViewModeIcon("☰", viewMode == ViewMode.COMPACT_LIST) { onViewModeChange(ViewMode.COMPACT_LIST) }
                ViewModeIcon("≡", viewMode == ViewMode.MICRO_LIST) { onViewModeChange(ViewMode.MICRO_LIST) }
            }
        }
    }
}

@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) theme.lamp.primary else (if (theme.isDark) Color.White.copy(0.12f) else Color.White.copy(alpha = 0.45f)))
            .border(
                BorderStroke(1.dp, if (selected) theme.lamp.primary else Color.White.copy(alpha = if (theme.isDark) 0.25f else 0.8f)),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.White else theme.inkColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun SortPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) (if (theme.isDark) Color.White.copy(0.2f) else Color.White.copy(alpha = 0.95f)) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            color = if (selected) theme.lamp.primary else theme.mutedColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ViewModeIcon(icon: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) (if (theme.isDark) Color.White.copy(0.25f) else Color.White) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(icon, fontSize = 13.sp, color = if (selected) theme.lamp.primary else theme.mutedColor, fontWeight = FontWeight.Bold)
    }
}

// 5. CARDS (تاریخ‌ها به شمسی تبدیل و نمایش داده می‌شوند)
@Composable
private fun LuxuryGridCard(user: PanelUser, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val progressPercent = if (user.dataLimit > 0) ((user.usedTraffic.toDouble() / user.dataLimit.toDouble()) * 100).toInt() else 0
    val progress = if (user.dataLimit > 0) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0f
    
    val progressColor = when {
        user.dataLimit <= 0L || progressPercent < 75 -> GlassGreen
        progressPercent in 75..99 -> GlassAmber
        else -> GlassRed
    }
    val statusColor = if (user.status == "active") GlassGreen else GlassRed

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(theme.cardBgColor)
            .border(BorderStroke(1.dp, theme.cardBorderBrush), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(statusColor))
                Text(
                    user.username,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.inkColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text("USED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                    Text(
                        formatBytes(user.usedTraffic),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = theme.inkColor
                    )
                }
                Text(
                    if (user.dataLimit == 0L) "∞" else "${progressPercent}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
            }

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(10.dp)),
                color = progressColor,
                trackColor = Color.White.copy(alpha = if (theme.isDark) 0.25f else 0.85f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (user.dataLimit == 0L) "No Limit" else "/ " + formatBytes(user.dataLimit),
                    fontSize = 11.sp,
                    color = theme.mutedColor
                )
                user.expire?.takeIf { it != "0" && it != "null" }?.let { iso ->
                    val shamsi = JalaliCalendar.isoToShamsi(iso)
                    Box(
                        modifier = Modifier
                            .background(if (theme.isDark) Color.White.copy(0.18f) else Color.White.copy(0.65f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
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
    val progressPercent = if (user.dataLimit > 0) ((user.usedTraffic.toDouble() / user.dataLimit.toDouble()) * 100).toInt() else 0
    val progress = if (user.dataLimit > 0) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0f
    
    val progressColor = when {
        user.dataLimit <= 0L || progressPercent < 75 -> GlassGreen
        progressPercent in 75..99 -> GlassAmber
        else -> GlassRed
    }
    val statusColor = if (user.status == "active") GlassGreen else GlassRed

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(theme.cardBgColor)
            .border(BorderStroke(1.dp, if (theme.isDark) Color.White.copy(0.18f) else Color.White.copy(0.8f)), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(statusColor))
            
            Column(modifier = Modifier.weight(1.2f)) {
                Text(user.username, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    user.expire?.takeIf { it != "0" && it != "null" }?.let { "انقضا: ${JalaliCalendar.isoToShamsi(it)}" } ?: "بدون انقضا",
                    fontSize = 11.sp,
                    color = theme.mutedColor
                )
            }

            Column(modifier = Modifier.weight(1.5f), horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(formatBytes(user.usedTraffic), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                    Text(if (user.dataLimit == 0L) "∞" else "${progressPercent}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = progressColor)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(10.dp)),
                    color = progressColor,
                    trackColor = Color.White.copy(alpha = if (theme.isDark) 0.25f else 0.85f)
                )
            }
            Text("›", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
        }
    }
}

@Composable
private fun LuxuryMicroRow(user: PanelUser, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val progressPercent = if (user.dataLimit > 0) ((user.usedTraffic.toDouble() / user.dataLimit.toDouble()) * 100).toInt() else 0
    val progress = if (user.dataLimit > 0) (user.usedTraffic.toFloat() / user.dataLimit.toFloat()).coerceIn(0f, 1f) else 0f
    
    val progressColor = when {
        user.dataLimit <= 0L || progressPercent < 75 -> GlassGreen
        progressPercent in 75..99 -> GlassAmber
        else -> GlassRed
    }
    val statusColor = if (user.status == "active") GlassGreen else GlassRed

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (theme.isDark) Color(0xFF222226).copy(0.55f) else Color.White.copy(alpha = 0.38f))
            .border(BorderStroke(0.8.dp, if (theme.isDark) Color.White.copy(0.15f) else Color.White.copy(0.7f)), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(statusColor))
            
            Text(
                user.username,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = theme.inkColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1.3f)
            ) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(6.dp)),
                    color = progressColor,
                    trackColor = Color.White.copy(alpha = if (theme.isDark) 0.25f else 0.85f)
                )
                Text(
                    if (user.dataLimit == 0L) "∞" else "${progressPercent}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
            }

            Text(
                formatBytes(user.usedTraffic) + if (user.dataLimit > 0) " / " + formatBytes(user.dataLimit) else "",
                fontSize = 11.sp,
                color = theme.mutedColor,
                maxLines = 1
            )
            Text("›", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
        }
    }
}

// ==========================================
// 6. THEME EDITOR DIALOG
// ==========================================
@Composable
private fun ThemeEditorDialog(
    themeState: ThemeState,
    onDismiss: () -> Unit,
    onThemeChange: (ThemeState) -> Unit
) {
    val theme = LocalThemeState.current
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(theme.dialogBgColor)
                .border(BorderStroke(1.5.dp, theme.cardBorderBrush), RoundedCornerShape(30.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("🎨 Appearance & Theme", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    TextButton(onClick = onDismiss) { Text("Done", fontWeight = FontWeight.Bold, color = theme.lamp.primary) }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Background Mode", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ModeToggleBtn("☀️ Light Mode", !themeState.isDark, Modifier.weight(1f)) {
                            onThemeChange(themeState.copy(isDark = false))
                        }
                        ModeToggleBtn("🌙 Dark Mode", themeState.isDark, Modifier.weight(1f)) {
                            onThemeChange(themeState.copy(isDark = true))
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Spotlight Accent Color", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.mutedColor)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LampColor.values().forEach { lamp ->
                            val isSelected = themeState.lamp == lamp
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) lamp.primary.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(
                                        BorderStroke(1.dp, if (isSelected) lamp.primary else Color.White.copy(alpha = if (theme.isDark) 0.15f else 0.5f)),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable { onThemeChange(themeState.copy(lamp = lamp)) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).background(lamp.primary))
                                    Text(lamp.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = theme.inkColor, fontSize = 14.sp)
                                    Spacer(Modifier.weight(1f))
                                    if (isSelected) Text("✓", color = lamp.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeToggleBtn(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) theme.lamp.primary else (if (theme.isDark) Color.White.copy(0.08f) else Color.White.copy(0.6f)))
            .border(BorderStroke(1.dp, if (selected) theme.lamp.primary else Color.White.copy(0.4f)), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else theme.inkColor, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

// ==========================================
// 7. SHAMSI (JALALI) CALENDAR PICKER POPUP
// ==========================================
@Composable
private fun ShamsiCalendarPickerDialog(
    initialDateShamsi: String,
    onDismiss: () -> Unit,
    onDateSelected: (String) -> Unit
) {
    val theme = LocalThemeState.current
    val today = JalaliCalendar.todayJalali()
    
    // Parse initial date or default to today
    val parsedInitial = remember(initialDateShamsi) {
        val p = initialDateShamsi.replace("-", "/").split("/")
        if (p.size == 3) {
            val y = p[0].toIntOrNull() ?: today.year
            val m = p[1].toIntOrNull() ?: today.month
            val d = p[2].toIntOrNull() ?: today.day
            JalaliCalendar.Date(y, m, d)
        } else today
    }

    var selectedYear by remember { mutableStateOf(parsedInitial.year) }
    var selectedMonth by remember { mutableStateOf(parsedInitial.month) }
    var selectedDay by remember { mutableStateOf(parsedInitial.day) }

    val daysInMonth = when {
        selectedMonth in 1..6 -> 31
        selectedMonth in 7..11 -> 30
        else -> if (selectedYear % 4 == 3) 30 else 29
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(theme.dialogBgColor)
                .border(BorderStroke(1.5.dp, theme.cardBorderBrush), RoundedCornerShape(26.dp))
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("📅 تقویم شمسی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                    TextButton(onClick = {
                        selectedYear = today.year
                        selectedMonth = today.month
                        selectedDay = today.day
                    }) { Text("امروز", color = theme.lamp.primary, fontWeight = FontWeight.Bold) }
                }

                // Month / Year Header Selector
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { if (selectedMonth > 1) selectedMonth-- else { selectedMonth = 12; selectedYear-- } },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(34.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.lamp.primary.copy(0.2f), contentColor = theme.inkColor)
                        ) { Text("◀") }
                        
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(0.1f))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val tempD = JalaliCalendar.Date(selectedYear, selectedMonth, 1)
                            Text("${tempD.getMonthName()} $selectedYear", fontWeight = FontWeight.Bold, color = theme.inkColor, fontSize = 15.sp)
                        }

                        Button(
                            onClick = { if (selectedMonth < 12) selectedMonth++ else { selectedMonth = 1; selectedYear++ } },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(34.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.lamp.primary.copy(0.2f), contentColor = theme.inkColor)
                        ) { Text("▶") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { selectedYear-- },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(30.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f), contentColor = theme.inkColor)
                        ) { Text("-", fontSize = 12.sp) }
                        Button(
                            onClick = { selectedYear++ },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(30.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f), contentColor = theme.inkColor)
                        ) { Text("+", fontSize = 12.sp) }
                    }
                }

                // Days Grid (6 columns or 7)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.height(200.dp)
                ) {
                    items((1..daysInMonth).toList()) { day ->
                        val isSel = day == selectedDay
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) theme.lamp.primary else (if (theme.isDark) Color.White.copy(0.08f) else Color.White.copy(0.6f)))
                                .clickable { selectedDay = day },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$day", color = if (isSel) Color.White else theme.inkColor, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("انصراف", color = theme.mutedColor) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val finalDate = JalaliCalendar.Date(selectedYear, selectedMonth, selectedDay)
                            onDateSelected(finalDate.toString())
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.lamp.primary, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("تایید تاریخ", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// 8. LOGIN & USERS SCREENS
// ==========================================
@Composable
private fun LoginScreen(
    onLoggedIn: (Session) -> Unit,
    themeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit
) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // Large centered MRM watermark
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 50.dp),
                contentAlignment = Alignment.Center
            ) {
                AppLogo(
                    modifier = Modifier.graphicsLayer(alpha = if (themeState.isDark) 0.28f else 0.22f, scaleX = 2.6f, scaleY = 2.6f),
                    height = 140.dp
                )
            }

            // 1. Theme selector moved to TOP LEFT on Login Screen (سمت چپ صفحه ورود)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (themeState.isDark) Color.White.copy(0.12f) else Color.White.copy(0.70f))
                    .border(BorderStroke(1.dp, Color.White.copy(0.5f)), RoundedCornerShape(16.dp))
                    .clickable { showThemeDialog = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🎨", fontSize = 15.sp)
                    Text("Theme", color = themeState.inkColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 60.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppLogo(height = 64.dp)
                Spacer(Modifier.height(4.dp))
                
                // 2. Removed "Manager Pro" text as requested
                Text("PasarGuard", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold), color = themeState.inkColor)
                Text("Sign in to manage your server with diamond security", color = themeState.mutedColor, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (themeState.isDark) Color(0xFF1E1E22).copy(0.85f) else Color.White.copy(0.55f))
                        .border(BorderStroke(1.2.dp, themeState.cardBorderBrush), RoundedCornerShape(28.dp))
                        .padding(22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Authentication", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = themeState.inkColor)
                        GlassTextField(url, { url = it }, "Full panel address", keyboardType = KeyboardType.Uri)
                        GlassTextField(username, { username = it }, "Username")
                        // 3. Password field equipped with standard Eye Icon (علامت چشم مرسوم در سایت‌ها)
                        GlassTextField(password, { password = it }, "Password", password = true)
                        error?.let { Text(it, color = GlassRed, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                        Button(
                            enabled = !loading,
                            onClick = {
                                loading = true
                                error = null
                                scope.launch {
                                    runCatching { PanelApi.login(url, username, password) }
                                        .onSuccess(onLoggedIn)
                                        .onFailure {
                                            error = "Unable to connect. Check panel address and credentials."
                                        }
                                    loading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = themeState.lamp.primary, contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("Connect to Panel", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (showThemeDialog) {
                ThemeEditorDialog(
                    themeState = themeState,
                    onDismiss = { showThemeDialog = false },
                    onThemeChange = onThemeChange
                )
            }
        }
    }
}

@Composable
private fun UsersScreen(
    session: Session,
    onLogout: () -> Unit,
    themeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit
) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<PanelUser>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedUser by remember { mutableStateOf<PanelUser?>(null) }
    var createUser by remember { mutableStateOf(false) }
    var deleteUser by remember { mutableStateOf<PanelUser?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }

    var currentFilter by remember { mutableStateOf(UserFilter.ALL) }
    var currentSort by remember { mutableStateOf(UserSort.NAME) }
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching { PanelApi.users(session) }
                .onSuccess { users = it }
                .onFailure {
                    error = it.message ?: "Unable to load users"
                    if (it.message?.contains("401") == true) onLogout()
                }
            loading = false
        }
    }

    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            error = null
            runCatching { action() }
                .onFailure {
                    error = it.message ?: "Action failed"
                    if (it.message?.contains("401") == true) onLogout()
                }
                .onSuccess { load() }
        }
    }

    LaunchedEffect(Unit) { load() }

    val processedUsers = remember(users, query, currentFilter, currentSort) {
        var list = users.filter { it.username.contains(query, ignoreCase = true) }
        
        list = when (currentFilter) {
            UserFilter.ALL -> list
            UserFilter.ACTIVE -> list.filter { it.status == "active" }
            UserFilter.NEAR_LIMIT -> list.filter {
                val progress = if (it.dataLimit > 0) (it.usedTraffic.toDouble() / it.dataLimit.toDouble()) else 0.0
                progress >= 0.75 || (it.expire != null && it.expire != "0" && it.expire != "null")
            }
            UserFilter.EXPIRED -> list.filter {
                val progress = if (it.dataLimit > 0) (it.usedTraffic.toDouble() / it.dataLimit.toDouble()) else 0.0
                progress >= 1.0 || it.status == "expired"
            }
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

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(25.dp))
                    .background(Brush.linearGradient(listOf(themeState.lamp.primary, themeState.lamp.light)))
                    .border(BorderStroke(1.2.dp, Color.White.copy(0.9f)), RoundedCornerShape(25.dp))
                    .clickable { createUser = true }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("+", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("New User", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            LuxuryTopStatsHeader(
                totalUsers = users.size,
                activeUsers = users.count { it.status == "active" },
                totalUsedTraffic = totalUsedTraffic,
                onRefresh = { load() },
                onLogout = onLogout,
                onOpenThemeDialog = { showThemeDialog = true },
                loading = loading
            )
            GlassSearchBar(query = query, onQueryChange = { query = it })
            Spacer(Modifier.height(10.dp))
            FilterAndControlBar(
                currentFilter = currentFilter,
                onFilterChange = { currentFilter = it },
                currentSort = currentSort,
                onSortChange = { currentSort = it },
                viewMode = viewMode,
                onViewModeChange = { viewMode = it },
                users = users
            )
            Spacer(Modifier.height(12.dp))

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = themeState.lamp.primary)
                }
                error != null -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(GlassShape)
                        .background(if (themeState.isDark) Color.White.copy(0.1f) else Color.White.copy(0.6f))
                        .padding(20.dp)
                ) {
                    Text("Error: $error", color = GlassRed, fontWeight = FontWeight.Medium)
                }
                else -> {
                    if (processedUsers.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("No matching users found in this filter.", color = themeState.mutedColor, fontSize = 14.sp)
                        }
                    } else when (viewMode) {
                        ViewMode.GRID -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 90.dp)
                            ) {
                                items(processedUsers) { user ->
                                    LuxuryGridCard(user, onClick = { selectedUser = user })
                                }
                            }
                        }
                        ViewMode.COMPACT_LIST -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 90.dp)
                            ) {
                                items(processedUsers) { user ->
                                    LuxuryCompactRow(user, onClick = { selectedUser = user })
                                }
                            }
                        }
                        ViewMode.MICRO_LIST -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(bottom = 90.dp)
                            ) {
                                items(processedUsers) { user ->
                                    LuxuryMicroRow(user, onClick = { selectedUser = user })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showThemeDialog) {
        ThemeEditorDialog(
            themeState = themeState,
            onDismiss = { showThemeDialog = false },
            onThemeChange = onThemeChange
        )
    }

    selectedUser?.let { user ->
        UserEditorDialog(
            initial = user,
            onDismiss = { selectedUser = null },
            onSave = { limitGb, expireShamsi ->
                selectedUser = null
                runAction {
                    val iso = JalaliCalendar.shamsiToIso(expireShamsi)
                    PanelApi.modifyUser(session, user.username, limitGb.value, iso)
                }
            },
            onToggle = {
                selectedUser = null
                runAction {
                    PanelApi.setDisabled(session, user.username, user.status != "disabled")
                }
            },
            onDelete = {
                deleteUser = user
                selectedUser = null
            },
            onResetUsage = {
                selectedUser = null
                runAction { PanelApi.resetUsage(session, user.username) }
            },
            onResetExpiry = {
                selectedUser = null
                runAction { PanelApi.modifyUser(session, user.username, (user.dataLimit.toDouble() / 1073741824.0), "") }
            }
        )
    }

    if (createUser) {
        UserEditorDialog(
            initial = null,
            onDismiss = { createUser = false },
            onSave = { limitGb, expireShamsi ->
                createUser = false
                runAction {
                    val iso = JalaliCalendar.shamsiToIso(expireShamsi)
                    PanelApi.createUser(session, limitGb.username, limitGb.value, iso)
                }
            },
            onToggle = null,
            onDelete = null,
            onResetUsage = null,
            onResetExpiry = null
        )
    }

    deleteUser?.let { user ->
        val theme = LocalThemeState.current
        Dialog(onDismissRequest = { deleteUser = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(GlassShape)
                    .background(theme.dialogBgColor)
                    .border(BorderStroke(1.2.dp, theme.cardBorderBrush), GlassShape)
            ) {
                Box(
                    Modifier
                        .size(240.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 60.dp, y = (-50).dp)
                        .background(
                            Brush.radialGradient(listOf(theme.lamp.spotHigh, Color.Transparent)),
                            shape = RoundedCornerShape(200.dp)
                        )
                )
                Column(
                    Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Delete ${user.username}?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = theme.inkColor)
                    Text("This action will permanently remove the user and cannot be undone.", color = theme.mutedColor, fontSize = 14.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { deleteUser = null }) {
                            Text("Cancel", color = theme.mutedColor)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                deleteUser = null
                                runAction { PanelApi.deleteUser(session, user.username) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GlassRed, contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Delete", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private data class UserEditorValues(val username: String, val value: Double)

// ==========================================
// 9. USER EDITOR DIALOG (با تقویم شمسی، افزودن سریع روز، و ریست حجم/زمان)
// ==========================================
@Composable
private fun UserEditorDialog(
    initial: PanelUser?,
    onDismiss: () -> Unit,
    onSave: (UserEditorValues, String) -> Unit,
    onToggle: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onResetUsage: (() -> Unit)?,
    onResetExpiry: (() -> Unit)?
) {
    val theme = LocalThemeState.current
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var limitGb by remember {
        mutableStateOf(
            if (initial == null || initial.dataLimit == 0L) "" else "%.2f".format(
                Locale.US,
                initial.dataLimit / 1073741824.0
            ).trimEnd('0').trimEnd('.')
        )
    }
    // Convert initial ISO date to Shamsi string
    var expireShamsi by remember {
        mutableStateOf(if (initial?.expire != null && initial.expire != "0") JalaliCalendar.isoToShamsi(initial.expire) else "")
    }
    var formError by remember { mutableStateOf<String?>(null) }
    var showShamsiCalendar by remember { mutableStateOf(false) }
    var customAddDays by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(theme.dialogBgColor)
                .border(BorderStroke(1.5.dp, theme.cardBorderBrush), RoundedCornerShape(30.dp))
        ) {
            Box(
                Modifier
                    .size(260.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 70.dp, y = (-50).dp)
                    .background(
                        Brush.radialGradient(listOf(theme.lamp.spotHigh, theme.lamp.spotLow, Color.Transparent)),
                        shape = RoundedCornerShape(200.dp)
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    if (initial == null) "ایجاد کاربر جدید" else "ویرایش ${initial.username}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.inkColor
                )

                if (initial == null) {
                    GlassTextField(value = username, onValueChange = { username = it }, label = "Username")
                }

                GlassTextField(
                    value = limitGb,
                    onValueChange = { limitGb = it },
                    label = "حجم مصرفی (گیگابایت، خالی = نامحدود)",
                    keyboardType = KeyboardType.Decimal
                )

                // Shamsi Date Field + Calendar Selector Button
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            GlassTextField(
                                value = expireShamsi,
                                onValueChange = { expireShamsi = it },
                                label = "تاریخ انقضا شمسی (مثلا ۱۴۰۵/۰۵/۱۰)"
                            )
                        }
                        Button(
                            onClick = { showShamsiCalendar = true },
                            modifier = Modifier.height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.lamp.primary.copy(0.2f), contentColor = theme.inkColor),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("📅 تقویم", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Quick Day Adders (+۳۰ روز، +۶۰ روز، یا ورود دستی روز)
                    Text("افزودن سریع به زمان:", fontSize = 11.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        QuickAddDayPill("+۳۰ روز") {
                            val baseIso = if (initial?.expire != null && initial.expire != "0") initial.expire else null
                            val newIso = JalaliCalendar.addDaysToIso(baseIso, 30)
                            expireShamsi = JalaliCalendar.isoToShamsi(newIso)
                        }
                        QuickAddDayPill("+۶۰ روز") {
                            val baseIso = if (initial?.expire != null && initial.expire != "0") initial.expire else null
                            val newIso = JalaliCalendar.addDaysToIso(baseIso, 60)
                            expireShamsi = JalaliCalendar.isoToShamsi(newIso)
                        }
                        QuickAddDayPill("+۹۰ روز") {
                            val baseIso = if (initial?.expire != null && initial.expire != "0") initial.expire else null
                            val newIso = JalaliCalendar.addDaysToIso(baseIso, 90)
                            expireShamsi = JalaliCalendar.isoToShamsi(newIso)
                        }
                        
                        // Custom numeric day adder input
                        Box(Modifier.width(80.dp)) {
                            OutlinedTextField(
                                value = customAddDays,
                                onValueChange = { customAddDays = it },
                                placeholder = { Text("+روز", fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White.copy(0.2f),
                                    unfocusedContainerColor = Color.White.copy(0.1f)
                                ),
                                textStyle = TextStyle(color = theme.inkColor, fontSize = 12.sp),
                                modifier = Modifier.height(44.dp)
                            )
                        }
                        if (customAddDays.isNotEmpty()) {
                            Button(
                                onClick = {
                                    val d = customAddDays.toIntOrNull() ?: 0
                                    if (d > 0) {
                                        val baseIso = if (initial?.expire != null && initial.expire != "0") initial.expire else null
                                        val newIso = JalaliCalendar.addDaysToIso(baseIso, d)
                                        expireShamsi = JalaliCalendar.isoToShamsi(newIso)
                                        customAddDays = ""
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.lamp.primary, contentColor = Color.White),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("+") }
                        }
                    }
                }

                initial?.let { user ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(if (theme.isDark) 0.06f else 0.4f))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("وضعیت فعلی: ${formatBytes(user.usedTraffic)} مصرف شده • ${user.status.uppercase()}", color = theme.mutedColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            
                            // 5. Quick Reset Actions (ریست حجم و زمان کاربر)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                onResetUsage?.let { resetU ->
                                    Button(
                                        onClick = resetU,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA89B), contentColor = Color.White),
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text("♻ ریست حجم (۰ B)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                }
                                onResetExpiry?.let { resetE ->
                                    Button(
                                        onClick = resetE,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7A42D4), contentColor = Color.White),
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text("♻ ریست زمان (نامحدود)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                onToggle?.let { toggle ->
                                    val isDisabled = user.status == "disabled"
                                    Button(
                                        onClick = toggle,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isDisabled) GlassGreen else GlassAmber,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(if (isDisabled) "فعال‌سازی" else "غیرفعال‌سازی", fontWeight = FontWeight.Bold)
                                    }
                                }
                                onDelete?.let { delete ->
                                    OutlinedButton(
                                        onClick = delete,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GlassRed),
                                        border = BorderStroke(1.dp, GlassRed.copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text("حذف کاربر", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                formError?.let {
                    Text(it, color = GlassRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("انصراف", color = theme.mutedColor, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cleanLimitStr = limitGb.replace(',', '.').trim()
                            val limit = if (cleanLimitStr.isBlank()) 0.0 else cleanLimitStr.toDoubleOrNull()
                            if (username.length !in 3..32 && initial == null) {
                                formError = "Username must be 3 to 32 characters."
                            } else if (limit == null || limit < 0) {
                                formError = "Data limit must be a valid number."
                            } else if (expireShamsi.isNotBlank() && !Regex("^\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}$").matches(expireShamsi)) {
                                formError = "فرمت تاریخ شمسی صحیح نیست (مثلا ۱۴۰۵/۰۵/۱۰)."
                            } else {
                                onSave(UserEditorValues(username, limit), expireShamsi)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.lamp.primary, contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("ذخیره تغییرات", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showShamsiCalendar) {
        ShamsiCalendarPickerDialog(
            initialDateShamsi = expireShamsi,
            onDismiss = { showShamsiCalendar = false },
            onDateSelected = { dateStr -> expireShamsi = dateStr }
        )
    }
}

@Composable
private fun QuickAddDayPill(label: String, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(if (theme.isDark) 0.15f else 0.7f))
            .border(BorderStroke(1.dp, theme.lamp.primary.copy(0.4f)), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 11.sp, color = theme.inkColor, fontWeight = FontWeight.Bold)
    }
}

private fun formatBytes(value: Long): String {
    if (value <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val index = (kotlin.math.ln(value.toDouble()) / kotlin.math.ln(1024.0)).toInt().coerceAtMost(units.lastIndex)
    return "${DecimalFormat("#.##").format(value / Math.pow(1024.0, index.toDouble()))} ${units[index]}"
}

private object PanelApi {
    private val client = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun baseUrl(input: String): String {
        val prepared = if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input"
        val uri = URI(prepared)
        require(!uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) { "Invalid URL" }
        return buildString {
            append(uri.scheme)
            append("://")
            append(uri.host)
            if (uri.port != -1) append(":${uri.port}")
        }
    }

    private fun userUrl(session: Session, username: String): String =
        "${session.baseUrl}/api/user/${URLEncoder.encode(username, "UTF-8")}"

    private fun requestBuilder(session: Session, url: String): Request.Builder =
        Request.Builder().url(url).header("Authorization", "Bearer ${session.token}")

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
        val request = requestBuilder(session, "${session.baseUrl}/api/users?offset=0&limit=1000").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Request failed: ${response.code}")
            val data = JSONObject(response.body?.string() ?: error("Empty users response")).getJSONArray("users")
            List(data.length()) { index -> parseUser(data.getJSONObject(index)) }
        }
    }

    suspend fun createUser(session: Session, username: String, limitGb: Double, expireIso: String) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("username", username)
            .put("status", "active")
            .put("data_limit", gbToBytes(limitGb))
            .put("expire", expireValue(expireIso))
        executeJson(requestBuilder(session, "${session.baseUrl}/api/user").post(body.toString().toRequestBody(jsonType)).build())
    }

    suspend fun modifyUser(session: Session, username: String, limitGb: Double, expireIso: String) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("data_limit", gbToBytes(limitGb))
            .put("expire", expireValue(expireIso))
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
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Delete failed: ${response.code}")
        }
    }

    private fun executeJson(request: Request) {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val details = response.body?.string()?.take(250).orEmpty()
                error("Request failed: ${response.code} $details")
            }
        }
    }

    private fun parseUser(user: JSONObject) = PanelUser(
        id = user.optLong("id", 0L),
        username = user.getString("username"),
        status = user.optString("status", "unknown"),
        usedTraffic = user.optLong("used_traffic", 0),
        dataLimit = user.optLong("data_limit", 0),
        expire = if (user.isNull("expire")) null else user.optString("expire").takeIf { it != "null" && it != "0" },
        createdAt = if (user.isNull("created_at")) null else user.optString("created_at")
    )

    private fun gbToBytes(value: Double): Long = (value * 1024 * 1024 * 1024).toLong()
    private fun expireValue(date: String): Any = if (date.isBlank() || date == "null" || date == "0") 0 else "${date}T23:59:59Z"
}

private class SessionStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "mrm_pg_manager",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun read(): Session? {
        val base = prefs.getString("base", null) ?: return null
        val token = prefs.getString("token", null) ?: return null
        return Session(base, token, prefs.getString("username", "") ?: "")
    }

    fun save(value: Session) = prefs.edit()
        .putString("base", value.baseUrl)
        .putString("token", value.token)
        .putString("username", value.username)
        .apply()

    fun clear() = prefs.edit().clear().apply()

    fun readTheme(): ThemeState {
        val lampName = prefs.getString("theme_lamp", LampColor.GOLD.name) ?: LampColor.GOLD.name
        val isDark = prefs.getBoolean("theme_dark", false)
        val lamp = runCatching { LampColor.valueOf(lampName) }.getOrDefault(LampColor.GOLD)
        return ThemeState(lamp = lamp, isDark = isDark)
    }

    fun saveTheme(themeState: ThemeState) = prefs.edit()
        .putString("theme_lamp", themeState.lamp.name)
        .putBoolean("theme_dark", themeState.isDark)
        .apply()
}
