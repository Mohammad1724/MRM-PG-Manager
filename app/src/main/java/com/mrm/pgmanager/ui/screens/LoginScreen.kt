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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.ActionIconButton
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
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // === AURORA BACKGROUND - GOLD THEME ===
            Box(
                Modifier.size(720.dp).align(Alignment.TopStart).offset(x = (-200).dp, y = (-200).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(theme.lamp.spotHigh.copy(alpha = 0.52f), theme.lamp.spotLow.copy(0.20f), Color.Transparent),
                            radius = 460f
                        ),
                        RoundedCornerShape(400.dp)
                    )
                    .blur(36.dp)
            )
            Box(
                Modifier.size(600.dp).align(Alignment.BottomEnd).offset(x = 180.dp, y = 180.dp)
                    .background(
                        Brush.radialGradient(listOf(theme.lamp.light.copy(0.26f), Color.Transparent)),
                        RoundedCornerShape(400.dp)
                    )
                    .blur(40.dp)
            )
            Box(
                Modifier.size(540.dp).align(Alignment.Center).offset(x = (-40).dp, y = 80.dp)
                    .background(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = if (theme.isDark) 0.04f else 0.10f), Color.Transparent)),
                        RoundedCornerShape(400.dp)
                    )
            )

            // CENTERED CONTENT - moved before top bar to avoid covering it
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
                    .padding(top = 72.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // === CARD - FIX 3: No white rectangle, true liquid glass ===
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 390.dp)
                        .clip(RoundedCornerShape(32.dp))
                        // FIX 3: Much more transparent + subtle gradient, not solid white
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    if (theme.isDark) Color(0xFF23232B).copy(alpha = 0.58f) else Color.White.copy(alpha = 0.38f),
                                    if (theme.isDark) Color(0xFF1A1A20).copy(alpha = 0.42f) else Color.White.copy(alpha = 0.22f)
                                )
                            )
                        )
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = if (theme.isDark) 0.16f else 0.52f)),
                            RoundedCornerShape(32.dp)
                        )
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // === FIX 1: MRM logo without background ===
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // No background box - just logo transparent
                            AppLogo(height = 68.dp, modifier = Modifier.shadow(8.dp, RoundedCornerShape(16.dp), spotColor = theme.lamp.primary.copy(0.12f)))
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("پاسارگارد", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                                // FIX 4: Only MRM Manager
                                Text("MRM Manager", fontSize = 13.sp, color = theme.mutedColor, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
                            }
                        }

                        // Fields
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            UltraPremiumField(
                                value = url,
                                onValueChange = { url = it },
                                label = "آدرس پنل",
                                placeholder = "https://panel.example.com:443",
                                leadingIcon = "🌐",
                                keyboardType = KeyboardType.Uri
                            )
                            UltraPremiumField(
                                value = username,
                                onValueChange = { username = it },
                                label = "نام کاربری",
                                placeholder = "نام کاربری",
                                leadingIcon = "👤"
                            )
                            UltraPremiumField(
                                value = password,
                                onValueChange = { password = it },
                                label = "رمز عبور",
                                placeholder = "رمز عبور",
                                leadingIcon = "🔒",
                                isPassword = true,
                                keyboardType = KeyboardType.Password
                            )
                        }

                        // Error
                        if (error != null) {
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .background(GlassRed.copy(alpha = 0.08f))
                                    .border(BorderStroke(1.dp, GlassRed.copy(alpha = 0.18f)), RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("⚠️", fontSize = 14.sp)
                                    Text(error!!, color = GlassRed, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        // Button - FIX: No inner light rectangle, flat chic gradient
                        Box(
                            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
                                .background(Brush.horizontalGradient(listOf(theme.lamp.primary, theme.lamp.primary.copy(0.84f))))
                                .border(BorderStroke(1.dp, Color.White.copy(0.62f)), RoundedCornerShape(18.dp))
                                .shadow(10.dp, RoundedCornerShape(18.dp), spotColor = theme.lamp.primary.copy(0.24f))
                                .clickable(enabled = !loading) {
                                    if (loading) return@clickable
                                    loading = true; error = null
                                    scope.launch {
                                        runCatching { PanelApi.login(url, username, password) }
                                            .onSuccess(onLoggedIn)
                                            .onFailure { error = "اتصال ناموفق بود. آدرس و مشخصات را بررسی کنید." }
                                        loading = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.2.dp)
                            else Text("ورود به پنل", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.5.sp)
                        }

                        // FIX 5: Removed forgot password and create account - not needed

                        // Security note - smaller
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (theme.isDark) Color.White.copy(0.06f) else Color.Black.copy(0.04f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔐", fontSize = 11.sp)
                            Text("اتصال امن • HTTPS", fontSize = 10.5.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text("v2.1 • Gold Theme • Vision OS", fontSize = 10.sp, color = theme.mutedColor.copy(0.6f), letterSpacing = 0.5.sp)
            }

            // Top bar - FIX 2: Moved to end so it's on top layer and clickable
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp).align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
                            .background(Brush.linearGradient(listOf(theme.lamp.primary, theme.lamp.light)))
                            .border(BorderStroke(1.dp, Color.White.copy(0.7f)), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text("M", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp) }
                    Column {
                        Text("MRM", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = theme.inkColor, letterSpacing = 0.8.sp)
                        Text("PASARGUARD", fontSize = 9.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    }
                }
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (theme.isDark) Color.White.copy(0.14f) else Color.White.copy(0.82f))
                        .border(BorderStroke(1.dp, Color.White.copy(0.72f)), RoundedCornerShape(14.dp))
                        .clickable { showThemeDialog = true },
                    contentAlignment = Alignment.Center
                ) { Text("🎨", fontSize = 20.sp) }
            }

            if (showThemeDialog) ThemeEditorDialog(themeState = themeState, onDismiss = { showThemeDialog = false }, onThemeChange = onThemeChange)
        }
    }
}
