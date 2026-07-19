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
import androidx.compose.material3.MaterialTheme
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
            // === AURORA BACKGROUND EXACTLY LIKE PRETTY IMAGE ===
            // Big blurred blobs: blue, purple, green
            Box(
                Modifier.size(680.dp).align(Alignment.TopStart).offset(x = (-180).dp, y = (-180).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF3B82F6).copy(alpha = 0.42f), Color(0xFF8B5CF6).copy(alpha = 0.22f), Color.Transparent),
                            radius = 420f
                        ),
                        RoundedCornerShape(400.dp)
                    )
                    .blur(32.dp)
            )
            Box(
                Modifier.size(620.dp).align(Alignment.BottomEnd).offset(x = 180.dp, y = 180.dp)
                    .background(
                        Brush.radialGradient(listOf(Color(0xFF06D6A0).copy(0.30f), Color(0xFF3B82F6).copy(0.18f), Color.Transparent)),
                        RoundedCornerShape(400.dp)
                    )
                    .blur(36.dp)
            )
            Box(
                Modifier.size(500.dp).align(Alignment.CenterStart).offset(x = (-200).dp, y = 100.dp)
                    .background(
                        Brush.radialGradient(listOf(Color(0xFFA855F7).copy(0.22f), Color.Transparent)),
                        RoundedCornerShape(400.dp)
                    )
                    .blur(40.dp)
            )
            Box(
                Modifier.size(400.dp).align(Alignment.CenterEnd).offset(x = 150.dp, y = (-120).dp)
                    .background(
                        Brush.radialGradient(listOf(Color(0xFF22D3EE).copy(0.22f), Color.Transparent)),
                        RoundedCornerShape(300.dp)
                    )
                    .blur(28.dp)
            )

            // Top minimal bar
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
                ActionIconButton(icon = { Text("🎨", fontSize = 15.sp) }, onClick = { showThemeDialog = true })
            }

            // CENTERED CARD LIKE IMAGE
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 72.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // === CARD EXACT IMAGE STYLE ===
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 380.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (theme.isDark) Color(0xFF1F1F26).copy(alpha = 0.86f) else Color.White.copy(alpha = 0.88f))
                        .border(BorderStroke(1.2.dp, Color.White.copy(alpha = if (theme.isDark) 0.18f else 0.72f)), RoundedCornerShape(28.dp))
                        .shadow(28.dp, RoundedCornerShape(28.dp), spotColor = theme.lamp.primary.copy(0.14f), ambientColor = Color.Black.copy(0.06f))
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Logo circle with P - like image
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier.size(72.dp).clip(RoundedCornerShape(36.dp))
                                    .background(Brush.linearGradient(listOf(Color(0xFF0EA5E9), Color(0xFF06B6D4), Color(0xFF22D3EE))))
                                    .border(BorderStroke(2.dp, Color.White.copy(0.85f)), RoundedCornerShape(36.dp))
                                    .shadow(16.dp, RoundedCornerShape(36.dp), spotColor = Color(0xFF0EA5E9).copy(0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                // Try logo, fallback to P
                                val logoFallback = remember { true }
                                if (logoFallback) {
                                    Text("P", color = Color.White, fontWeight = FontWeight.Black, fontSize = 34.sp)
                                } else {
                                    AppLogo(height = 40.dp)
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("پاسارگارد", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor)
                                Text("(MRM Manager) مدیریت ریسک", fontSize = 12.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                            }
                        }

                        // Fields - like image: icon left, Persian placeholder right
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            UltraPremiumField(
                                value = url,
                                onValueChange = { url = it },
                                label = "آدرس پنل",
                                placeholder = "panel.example.com",
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

                        // Button - EXACT IMAGE: gradient blue to cyan, 52dp, rounded 16
                        Box(
                            Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xFF3B82F6), Color(0xFF06B6D4))))
                                .border(BorderStroke(1.dp, Color.White.copy(0.7f)), RoundedCornerShape(16.dp))
                                .shadow(14.dp, RoundedCornerShape(16.dp), spotColor = Color(0xFF3B82F6).copy(0.35f))
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
                            else Text("ورود", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }

                        // Small links like image
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("فراموشی رمز عبور؟", color = Color(0xFF3B82F6), fontSize = 12.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { })
                            Text("ایجاد حساب کاربری", color = Color(0xFF3B82F6), fontSize = 12.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { })
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text("MRM Studio • Vision OS 2025", fontSize = 10.sp, color = theme.mutedColor.copy(0.7f), letterSpacing = 0.5.sp)
            }

            if (showThemeDialog) ThemeEditorDialog(themeState = themeState, onDismiss = { showThemeDialog = false }, onThemeChange = onThemeChange)
        }
    }
}
