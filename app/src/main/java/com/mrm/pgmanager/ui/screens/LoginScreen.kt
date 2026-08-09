package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.BuildConfig
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.ThemeState
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoggedIn: (Session) -> Unit,
    themeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }
    val theme = themeState

    Box(Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Top bar minimal
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(themeState.accentPrimary), contentAlignment = Alignment.Center) {
                        Text("M", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                    Column { Text("MRM", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = theme.inkColor); Text("PASARGUARD", fontSize = 8.sp, color = theme.mutedColor, fontWeight = FontWeight.SemiBold) }
                }
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(theme.cardSurfaceColor).border(BorderStroke(1.dp, theme.borderColor), RoundedCornerShape(8.dp)).let { it }, contentAlignment = Alignment.Center) {
                    // use icon button-like
                    androidx.compose.foundation.clickable { showThemeDialog = true }
                    RoundedAppIcon(AppIcon.Settings, tint = theme.mutedColor, size = 16.dp)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Logo centered
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLogo(height = 56.dp)
                Text("PasarGuard", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                Text("MRM Manager", fontSize = 12.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                Text("مدیریت پنل پاسارگارد", fontSize = 11.sp, color = theme.mutedColor)
            }

            // Card — white, subtle border, same as PG
            Column(
                Modifier.fillMaxWidth().clip(DsRadius.Xl).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xl).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PGField(label = "آدرس پنل", value = url, onValueChange = { url = it }, placeholder = "https://panel.example.com:443", icon = AppIcon.Link)
                PGField(label = "نام کاربری", value = username, onValueChange = { username = it }, placeholder = "نام کاربری", icon = AppIcon.User)
                PGField(label = "رمز عبور", value = password, onValueChange = { password = it }, placeholder = "رمز عبور", icon = AppIcon.Lock, isPassword = true)

                if (error != null) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFFEE2E2)).border(BorderStroke(1.dp, Color(0xFFFECACA)), RoundedCornerShape(10.dp)).padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundedAppIcon(AppIcon.Warning, tint = GlassRed, size = 16.dp)
                        Text(error!!, color = GlassRed, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    }
                }

                // Primary yellow button
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(themeState.accentPrimary)
                        .let { if (!loading) it else it }
                        .run { androidx.compose.foundation.clickable(enabled = !loading) {
                            if (loading) return@clickable
                            loading = true; error = null
                            scope.launch {
                                runCatching { PanelApi.login(url, username, password) }.onSuccess(onLoggedIn).onFailure { e ->
                                    error = when {
                                        e.message?.contains("Credentials required", true) == true -> "نام کاربری و رمز را وارد کنید."
                                        e.message?.contains("Invalid URL", true) == true -> "آدرس پنل نامعتبر است."
                                        e is java.net.UnknownHostException -> "سرور پیدا نشد. آدرس را بررسی کنید."
                                        e is java.net.SocketTimeoutException -> "پاسخی از سرور نگرفت شد."
                                        e.message?.contains("401", true) == true -> "نام کاربری یا رمز اشتباه است."
                                        e.message?.contains("404", true) == true -> "آدرس یا مسیر پنل درست نیست (۴۰۴)."
                                        else -> "اتصال ناموفق: ${e.message ?: "خطای ناشناخته"}"
                                    }
                                }
                                loading = false
                            }
                        } },
                    contentAlignment = Alignment.Center
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Color(0xFF422006), strokeWidth = 2.dp)
                    else Text("ورود به پنل", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF422006))
                }

                // info row
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(theme.searchBgColor).border(BorderStroke(0.7.dp, theme.borderSubtle), RoundedCornerShape(10.dp)).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundedAppIcon(AppIcon.Lock, tint = theme.mutedColor, size = 14.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("قفل اثرانگشت / پین", fontSize = 10.sp, color = theme.inkColor, fontWeight = FontWeight.SemiBold)
                        Text("بعد از ورود: تنظیمات → امنیت → قفل برنامه", fontSize = 9.sp, color = theme.mutedColor)
                    }
                }
            }

            if (onBack != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.TextButton(onClick = onBack) { Text("بازگشت", color = theme.mutedColor, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                }
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("v${BuildConfig.VERSION_NAME}", fontSize = 10.sp, color = theme.mutedLightColor)
            }
        }
        // clickable overlay for settings icon (since we placed icon but not click)
        // Actually handle via Row click above — add invisible clickable
        if (showThemeDialog) com.mrm.pgmanager.ui.dialogs.ThemeEditorDialog(themeState = themeState, onDismiss = { showThemeDialog = false }, onThemeChange = onThemeChange, appVersion = BuildConfig.VERSION_NAME)
    }
}

@Composable
private fun PGField(label: String, value: String, onValueChange: (String)->Unit, placeholder: String, icon: AppIcon, isPassword: Boolean = false) {
    val theme = LocalThemeState.current
    var visible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(theme.searchBgColor).border(BorderStroke(1.dp, theme.borderColor), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundedAppIcon(icon, tint = theme.mutedColor, size = 16.dp)
                androidx.compose.foundation.text.BasicTextField(
                    value = value, onValueChange = onValueChange, singleLine = true,
                    visualTransformation = if (isPassword && !visible) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = theme.inkColor),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, fontSize = 13.sp, color = theme.mutedLightColor); inner() }
                )
                if (isPassword) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).clickable { visible = !visible }, contentAlignment = Alignment.Center) {
                        Text(if (visible) "◯" else "◎", fontSize = 12.sp, color = theme.mutedColor)
                    }
                }
            }
        }
    }
}
