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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LiquidGlassTheme { MRMApp(this) } }
    }
}

// Luxury Champagne & Brushed Gold Glass Palette
private val GlassGold = Color(0xFFC59B27)
private val GlassGoldLight = Color(0xFFF3E5AB)
private val GlassCream = Color(0xFFFAF6EE)
private val GlassInk = Color(0xFF1C1B18)
private val GlassMuted = Color(0xFF6A655B)
private val GlassGreen = Color(0xFF1A8C5B)
private val GlassAmber = Color(0xFFD9822B)
private val GlassRed = Color(0xFFC93B3B)
private val GlassShape = RoundedCornerShape(24.dp)

@Composable
private fun LiquidGlassTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = GlassGold,
        onPrimary = Color.White,
        secondary = GlassGoldLight,
        background = GlassCream,
        surface = Color.White.copy(alpha = 0.60f),
        onSurface = GlassInk,
        onBackground = GlassInk,
        error = GlassRed
    )
    MaterialTheme(colorScheme = colors) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFFFFDF9),
                            Color(0xFFFFF7E6),
                            Color(0xFFFFF4DC)
                        )
                    )
                )
        ) {
            // Luxury Spotlight Lamp shining from the LEFT side on main pages (نور لامپ از سمت چپ صفحه اصلی)
            Box(
                Modifier
                    .size(420.dp)
                    .align(Alignment.TopStart)
                    .offset(x = (-110).dp, y = (-40).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xBBF5D061), // Champagne Gold Spotlight
                                Color(0x44E5B84B),
                                Color.Transparent
                            )
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
                            listOf(
                                Color(0x66F5D061),
                                Color.Transparent
                            )
                        ),
                        RoundedCornerShape(200.dp)
                    )
            )
            content()
        }
    }
}

@Composable
private fun AppLogo(modifier: Modifier = Modifier, height: Dp = 24.dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
        // Build-safe fallback when logo_mrm.png hasn't been pushed/committed to res/drawable yet
        Box(
            modifier = modifier
                .height(height)
                .widthIn(max = height * 2.8f)
                .clip(RoundedCornerShape(height / 3.2f))
                .background(
                    Brush.linearGradient(
                        listOf(GlassGold, Color(0xFFF5D061), Color(0xFF9C6700))
                    )
                )
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.85f)),
                    RoundedCornerShape(height / 3.2f)
                )
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

    if (session == null) {
        LoginScreen(onLoggedIn = { value ->
            store.save(value)
            session = value
        })
    } else {
        UsersScreen(session = session!!, onLogout = {
            store.clear()
            session = null
        })
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 14.sp) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.50f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.30f),
            focusedBorderColor = GlassGold,
            unfocusedBorderColor = Color.White.copy(alpha = 0.90f),
            focusedLabelColor = GlassGold,
            cursorColor = GlassGold
        ),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun GlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(Color.White.copy(alpha = 0.65f))
            .border(
                BorderStroke(
                    1.2.dp,
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.98f),
                            GlassGoldLight.copy(alpha = 0.6f),
                            Color.White.copy(alpha = 0.40f)
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
                        color = GlassMuted.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = GlassInk, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                        .clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", color = GlassInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LuxuryTopStatsHeader(
    totalUsers: Int,
    activeUsers: Int,
    totalUsedTraffic: Long,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    loading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "PasarGuard",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = GlassInk
                )
                // Logo replacing the previous PRO badge with exactly the same badge height (~24.dp)
                AppLogo(height = 24.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Refresh Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.70f))
                        .border(BorderStroke(1.dp, Color.White), RoundedCornerShape(16.dp))
                        .clickable(enabled = !loading, onClick = onRefresh)
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(13.dp), color = GlassInk, strokeWidth = 2.dp)
                        } else {
                            Text("🔄", fontSize = 12.sp)
                        }
                        Text("Refresh", color = GlassInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                // Logout Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFFF2F2).copy(alpha = 0.80f))
                        .border(BorderStroke(1.dp, Color(0xFFF2BABA)), RoundedCornerShape(16.dp))
                        .clickable(onClick = onLogout)
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("⎋", fontSize = 13.sp, color = GlassRed)
                        Text("Exit", color = GlassRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Luxury Summary Banner (کادر خلاصه وضعیت باکلاس و شیک)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.45f))
                .border(
                    BorderStroke(1.dp, Brush.horizontalGradient(listOf(Color.White.copy(0.9f), Color.White.copy(0.3f)))),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(label = "Total Users", value = "$totalUsers", color = GlassInk)
            Box(Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.8f)))
            StatItem(label = "Active Now", value = "$activeUsers", color = GlassGreen)
            Box(Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.8f)))
            StatItem(label = "Total Traffic", value = formatBytes(totalUsedTraffic), color = GlassGold)
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 11.sp, color = GlassMuted, fontWeight = FontWeight.Medium)
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Quick Filters Scrollable Bar
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

        // Sort & View Toggle (پشتیبانی از ۳ حالت نمایش و مرتب‌سازی به ترتیب ساخت)
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
                Text("Sort:", fontSize = 11.sp, color = GlassMuted, fontWeight = FontWeight.Medium)
                SortPill("Name", currentSort == UserSort.NAME) { onSortChange(UserSort.NAME) }
                SortPill("Usage", currentSort == UserSort.USAGE) { onSortChange(UserSort.USAGE) }
                SortPill("Expiry", currentSort == UserSort.EXPIRY) { onSortChange(UserSort.EXPIRY) }
                SortPill("Created", currentSort == UserSort.CREATED) { onSortChange(UserSort.CREATED) }
            }
            Spacer(Modifier.width(6.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.55f))
                    .border(BorderStroke(1.dp, Color.White), RoundedCornerShape(12.dp))
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
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) GlassGold else Color.White.copy(alpha = 0.45f))
            .border(
                BorderStroke(1.dp, if (selected) GlassGold else Color.White.copy(alpha = 0.8f)),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.White else GlassInk,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun SortPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color.White.copy(alpha = 0.95f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            color = if (selected) GlassGold else GlassMuted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ViewModeIcon(icon: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(icon, fontSize = 13.sp, color = if (selected) GlassGold else GlassMuted, fontWeight = FontWeight.Bold)
    }
}

// 1. Luxury 2-Column Grid Card (کارت گرید فشرده و باکلاس - 田)
@Composable
private fun LuxuryGridCard(user: PanelUser, onClick: () -> Unit) {
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
            .background(Color.White.copy(alpha = 0.48f))
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(listOf(Color.White.copy(0.95f), Color.White.copy(0.2f)))
                ),
                RoundedCornerShape(22.dp)
            )
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
                    color = GlassInk,
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
                    Text("USED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GlassMuted)
                    Text(
                        formatBytes(user.usedTraffic),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = GlassInk
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
                trackColor = Color.White.copy(alpha = 0.85f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (user.dataLimit == 0L) "No Limit" else "/ " + formatBytes(user.dataLimit),
                    fontSize = 11.sp,
                    color = GlassMuted
                )
                user.expire?.takeIf { it != "0" && it != "null" }?.let {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(0.65f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(it.take(10), fontSize = 10.sp, color = GlassInk, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// 2. Luxury Compact Row (نمای لیستی متوسط - ☰)
@Composable
private fun LuxuryCompactRow(user: PanelUser, onClick: () -> Unit) {
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
            .background(Color.White.copy(alpha = 0.45f))
            .border(BorderStroke(1.dp, Color.White.copy(0.8f)), RoundedCornerShape(16.dp))
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
                Text(user.username, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GlassInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    user.expire?.takeIf { it != "0" && it != "null" }?.let { "Exp: ${it.take(10)}" } ?: "No Expiry",
                    fontSize = 11.sp,
                    color = GlassMuted
                )
            }

            Column(modifier = Modifier.weight(1.5f), horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(formatBytes(user.usedTraffic), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GlassInk)
                    Text(if (user.dataLimit == 0L) "∞" else "${progressPercent}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = progressColor)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(10.dp)),
                    color = progressColor,
                    trackColor = Color.White.copy(alpha = 0.85f)
                )
            }
            Text("›", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GlassMuted)
        }
    }
}

// 3. Luxury Micro Slim Row (نمای ستونی باریک‌تر برای مشاهده تعداد بسیار زیاد کاربر در یک صفحه - ≡)
@Composable
private fun LuxuryMicroRow(user: PanelUser, onClick: () -> Unit) {
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
            .background(Color.White.copy(alpha = 0.38f))
            .border(BorderStroke(0.8.dp, Color.White.copy(0.7f)), RoundedCornerShape(12.dp))
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
                color = GlassInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.1f)
            )

            // Ultra-slim progress bar & percentage right in the middle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1.3f)
            ) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(6.dp)),
                    color = progressColor,
                    trackColor = Color.White.copy(alpha = 0.85f)
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
                color = GlassMuted,
                maxLines = 1
            )
            Text("›", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GlassMuted)
        }
    }
}

@Composable
private fun LoginScreen(onLoggedIn: (Session) -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // Large centered background watermark logo (لوگوی بزرگ زیر کل صفحه ورود)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 50.dp),
                contentAlignment = Alignment.Center
            ) {
                AppLogo(
                    modifier = Modifier.graphicsLayer(alpha = 0.08f, scaleX = 2.5f, scaleY = 2.5f),
                    height = 140.dp
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 60.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Centered top logo right above title (لوگوی وسط صفحه بالا در ورود)
                AppLogo(height = 64.dp)
                Spacer(Modifier.height(4.dp))
                
                Text("PasarGuard", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold), color = GlassInk)
                Text("Manager Pro", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = GlassGold)
                Text("Sign in to manage your server with diamond security", color = GlassMuted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White.copy(alpha = 0.55f))
                        .border(BorderStroke(1.2.dp, Brush.linearGradient(listOf(Color.White, Color.White.copy(0.2f)))), RoundedCornerShape(28.dp))
                        .padding(22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Authentication", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GlassInk)
                        GlassTextField(url, { url = it }, "Full panel address", keyboardType = KeyboardType.Uri)
                        GlassTextField(username, { username = it }, "Username")
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
                            colors = ButtonDefaults.buttonColors(containerColor = GlassGold, contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("Connect to Panel", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsersScreen(session: Session, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<PanelUser>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedUser by remember { mutableStateOf<PanelUser?>(null) }
    var createUser by remember { mutableStateOf(false) }
    var deleteUser by remember { mutableStateOf<PanelUser?>(null) }

    // Navigation state for quick filters and views
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

    // Filter and Sort logic (پشتیبانی از ۴ نوع مرتب‌سازی از جمله به ترتیب ساخت)
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
            // Sort by creation order (بزرگترین ID یا تاریخ ساخت به معنای جدیدترین کاربر ساخته شده است)
            UserSort.CREATED -> list.sortedByDescending { if (it.id > 0) it.id else (it.createdAt ?: "").hashCode().toLong() }
        }
    }

    val totalUsedTraffic = remember(users) { users.sumOf { it.usedTraffic } }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            // Sleek Luxury Floating Action Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(25.dp))
                    .background(Brush.linearGradient(listOf(GlassGold, Color(0xFFE5B84B))))
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
                    CircularProgressIndicator(color = GlassGold)
                }
                error != null -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(GlassShape)
                        .background(Color.White.copy(0.6f))
                        .padding(20.dp)
                ) {
                    Text("Error: $error", color = GlassRed, fontWeight = FontWeight.Medium)
                }
                else -> {
                    if (processedUsers.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("No matching users found in this filter.", color = GlassMuted, fontSize = 14.sp)
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

    selectedUser?.let { user ->
        UserEditorDialog(
            initial = user,
            onDismiss = { selectedUser = null },
            onSave = { limitGb, expire ->
                selectedUser = null
                runAction {
                    PanelApi.modifyUser(session, user.username, limitGb.value, expire)
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
            }
        )
    }

    if (createUser) {
        UserEditorDialog(
            initial = null,
            onDismiss = { createUser = false },
            onSave = { limitGb, expire ->
                createUser = false
                runAction {
                    PanelApi.createUser(session, limitGb.username, limitGb.value, expire)
                }
            },
            onToggle = null,
            onDelete = null
        )
    }

    deleteUser?.let { user ->
        Dialog(onDismissRequest = { deleteUser = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(GlassShape)
                    .background(Color(0xFFFFFBF1).copy(alpha = 0.90f))
                    .border(BorderStroke(1.2.dp, Color.White), GlassShape)
            ) {
                // Lamp light from RIGHT side on Delete Dialog (نور لامپ از سمت راست)
                Box(
                    Modifier
                        .size(240.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 60.dp, y = (-50).dp)
                        .background(
                            Brush.radialGradient(listOf(Color(0xBBF5D061), Color.Transparent)),
                            shape = RoundedCornerShape(200.dp)
                        )
                )
                Column(
                    Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Delete ${user.username}?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = GlassInk)
                    Text("This action will permanently remove the user and cannot be undone.", color = GlassMuted, fontSize = 14.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { deleteUser = null }) {
                            Text("Cancel", color = GlassMuted)
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

@Composable
private fun UserEditorDialog(
    initial: PanelUser?,
    onDismiss: () -> Unit,
    onSave: (UserEditorValues, String) -> Unit,
    onToggle: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var limitGb by remember {
        mutableStateOf(
            if (initial == null || initial.dataLimit == 0L) "" else "%.2f".format(
                Locale.US,
                initial.dataLimit / 1073741824.0
            ).trimEnd('0').trimEnd('.')
        )
    }
    var expireDate by remember { mutableStateOf(initial?.expire?.take(10) ?: "") }
    var formError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(Color(0xFFFFFDF8).copy(alpha = 0.92f))
                .border(
                    BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.98f),
                                GlassGoldLight.copy(alpha = 0.70f),
                                Color.White.copy(alpha = 0.35f)
                            )
                        )
                    ),
                    RoundedCornerShape(30.dp)
                )
        ) {
            // Spotlight / Lamp light shining specifically from the RIGHT side (نور لامپ از سمت راست در پنجره باز شده)
            Box(
                Modifier
                    .size(260.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 70.dp, y = (-50).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xBBF5D061), // Champagne Gold Lamp light from right
                                Color(0x44E5B84B),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(200.dp)
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    if (initial == null) "Create New User" else "Edit ${initial.username}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = GlassInk
                )

                if (initial == null) {
                    GlassTextField(value = username, onValueChange = { username = it }, label = "Username")
                }

                GlassTextField(
                    value = limitGb,
                    onValueChange = { limitGb = it },
                    label = "Data limit (GB, blank = unlimited)",
                    keyboardType = KeyboardType.Decimal
                )

                GlassTextField(
                    value = expireDate,
                    onValueChange = { expireDate = it },
                    label = "Expiry date (YYYY-MM-DD, blank = unlimited)"
                )

                initial?.let {
                    Text(
                        "Used: ${formatBytes(it.usedTraffic)} • Status: ${it.status.uppercase()}",
                        color = GlassMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        onToggle?.let { toggle ->
                            val isDisabled = it.status == "disabled"
                            Button(
                                onClick = toggle,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDisabled) GlassGreen else GlassAmber,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp)
                                ) {
                                Text(if (isDisabled) "Activate" else "Disable", fontWeight = FontWeight.Bold)
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
                                Text("Delete", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                formError?.let {
                    Text(it, color = GlassRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = GlassMuted, fontWeight = FontWeight.SemiBold)
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
                            } else if (expireDate.isNotBlank() && !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(expireDate)) {
                                formError = "Use YYYY-MM-DD for expiry date."
                            } else {
                                onSave(UserEditorValues(username, limit), expireDate)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GlassGold, contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
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

    suspend fun createUser(session: Session, username: String, limitGb: Double, expireDate: String) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("username", username)
            .put("status", "active")
            .put("data_limit", gbToBytes(limitGb))
            .put("expire", expireValue(expireDate))
        executeJson(requestBuilder(session, "${session.baseUrl}/api/user").post(body.toString().toRequestBody(jsonType)).build())
    }

    suspend fun modifyUser(session: Session, username: String, limitGb: Double, expireDate: String) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("data_limit", gbToBytes(limitGb))
            .put("expire", expireValue(expireDate))
        executeJson(requestBuilder(session, userUrl(session, username)).put(body.toString().toRequestBody(jsonType)).build())
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
}
