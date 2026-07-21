package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.AppLogo
import com.mrm.pgmanager.ui.components.UltraPremiumField
import com.mrm.pgmanager.ui.dialogs.ThemeEditorDialog
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.ThemeState
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
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
    val theme = themeState

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            // Aurora gold
            Box(
                Modifier.size(720.dp).align(Alignment.TopStart).offset(x = (-200).dp, y = (-200).dp)
                    .background(Brush.radialGradient(listOf(theme.lamp.spotHigh.copy(0.48f), Color.Transparent), radius = 460f), RoundedCornerShape(400.dp)).blur(36.dp)
            )
            Box(
                Modifier.size(600.dp).align(Alignment.BottomEnd).offset(x = 180.dp, y = 180.dp)
                    .background(Brush.radialGradient(listOf(theme.lamp.light.copy(0.22f), Color.Transparent)), RoundedCornerShape(400.dp)).blur(40.dp)
            )

            // Content
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(top = 80.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Card - ULTRA TRANSPARENT, no white rectangle
                Box(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 390.dp).clip(RoundedCornerShape(32.dp))
                        .background(
                            if (theme.isDark) Color(0xFF1E1E24).copy(alpha = 0.34f)
                            else Color.White.copy(alpha = 0.18f)
                        )
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = if (theme.isDark) 0.12f else 0.32f)), RoundedCornerShape(32.dp))
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        // FIX 1: Logo without any background box - pure transparent
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppLogo(height = 64.dp) // No shadow, no background
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Pasarguard", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                                Text("MRM Manager", fontSize = 13.sp, color = theme.mutedColor, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            UltraPremiumField(value = url, onValueChange = { url = it }, label = "آدرس پنل", placeholder = "https://panel.example.com:443", leadingIcon = "🌐", keyboardType = KeyboardType.Uri)
                            UltraPremiumField(value = username, onValueChange = { username = it }, label = "نام کاربری", placeholder = "نام کاربری", leadingIcon = "👤")
                            UltraPremiumField(value = password, onValueChange = { password = it }, label = "رمز عبور", placeholder = "رمز عبور", leadingIcon = "🔒", isPassword = true, keyboardType = KeyboardType.Password)
                        }

                        if (error != null) {
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GlassRed.copy(0.08f))
                                    .border(BorderStroke(1.dp, GlassRed.copy(0.18f)), RoundedCornerShape(14.dp)).padding(12.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("⚠️", fontSize = 14.sp)
                                    Text(error!!, color = GlassRed, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        // FIX: Button flat, no gradient with alpha, pure solid - no inner rectangle
                        Box(
                            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
                                .background(theme.lamp.primary)
                                .clickable(enabled = !loading) {
                                    if (loading) return@clickable
                                    loading = true; error = null
                                    scope.launch {
                                        runCatching { PanelApi.login(url, username, password) }.onSuccess(onLoggedIn).onFailure { e ->
                                            error = when {
                                                e.message?.contains("Credentials required", ignoreCase = true) == true -> "نام کاربری و رمز را وارد کنید."
                                                e.message?.contains("Invalid URL", ignoreCase = true) == true -> "آدرس پنل نامعتبر است."
                                                e.message?.contains("Cleartext", ignoreCase = true) == true || e.message?.contains("not permitted", ignoreCase = true) == true -> "این آدرس HTTP است و امن نیست؛ از HTTPS استفاده کنید."
                                                e is java.net.UnknownHostException -> "سرور پیدا نشد. آدرس پنل را بررسی کنید."
                                                e is java.net.SocketTimeoutException -> "پاسخی از سرور نگرفت شد (timeout)."
                                                e.message?.contains("Login failed: 401", ignoreCase = true) == true -> "نام کاربری یا رمز اشتباه است."
                                                e.message?.contains("Login failed: 404", ignoreCase = true) == true -> "آدرس یا مسیر پنل درست نیست (۴۰۴)."
                                                e.message?.startsWith("Login failed") == true -> "خطای سرور: ${e.message}"
                                                else -> "اتصال ناموفق بود: ${e.message ?: "خطای ناشناخته"}"
                                            }
                                        }
                                        loading = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.2.dp)
                            else Text("ورود به پنل", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.5.sp)
                        }

                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (theme.isDark) Color.White.copy(0.05f) else Color.Black.copy(0.03f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔒", fontSize = 11.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("قفل اثرانگشت / پین برنامه", fontSize = 10.5.sp, color = theme.inkColor, fontWeight = FontWeight.Bold)
                                Text("برای فعال‌سازی: اول وارد پنل شوید، سپس دکمهٔ 🎨 (بالا) ← بخش «قفل امنیتی»", fontSize = 9.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                            }
                            Text("🔐 HTTPS", fontSize = 9.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            // FIX 2: Theme button - absolute top layer with high zIndex, big hit area
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).align(Alignment.TopStart).zIndex(10f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.zIndex(10f)) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
                            .background(Brush.linearGradient(listOf(theme.lamp.primary, theme.lamp.light)))
                            .border(BorderStroke(1.dp, Color.White.copy(0.7f)), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text("M", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp) }
                    Column {
                        Text("MRM", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = theme.inkColor)
                        Text("PASARGUARD", fontSize = 9.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
                    }
                }
                // Extra big clickable area
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = if (theme.isDark) 0.18f else 0.9f))
                        .border(BorderStroke(1.5.dp, theme.lamp.primary.copy(0.4f)), RoundedCornerShape(14.dp))
                        .clickable { showThemeDialog = true }.zIndex(10f),
                    contentAlignment = Alignment.Center
                ) { Text("🎨", fontSize = 22.sp) }
            }

            if (showThemeDialog) ThemeEditorDialog(themeState = themeState, onDismiss = { showThemeDialog = false }, onThemeChange = onThemeChange)
        }
    }
}
