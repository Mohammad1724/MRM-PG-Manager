package com.mrm.pgmanager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.components.PrimarySaveButton
import com.mrm.pgmanager.ui.screens.LoginScreen
import com.mrm.pgmanager.ui.screens.UsersScreen
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.LiquidGlassTheme
import com.mrm.pgmanager.ui.theme.ThemeState

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MRMApp() }
    }
}

fun authenticateBiometric(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_CANCELED && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                    onError(errString.toString())
                }
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()

    prompt.authenticate(promptInfo)
}

@Composable
fun MRMApp() {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val store = remember { SessionStore(context) }
    var session by remember { mutableStateOf(store.read()) }
    var themeState by remember { mutableStateOf(store.readTheme()) }
    var isAppLockEnabled by remember { mutableStateOf(store.readAppLock()) }
    var isUnlocked by remember { mutableStateOf(false) }

    LaunchedEffect(session, isAppLockEnabled) {
        if (session != null && isAppLockEnabled && !isUnlocked && activity != null) {
            authenticateBiometric(
                activity = activity,
                title = "🔒 ورود به پنل پاسارگارد",
                subtitle = "اثر انگشت یا پین/الگوی گوشی خود را اسکن کنید",
                onSuccess = { isUnlocked = true },
                onError = { /* stay on lock screen */ }
            )
        } else if (!isAppLockEnabled) {
            isUnlocked = true
        }
    }

    LiquidGlassTheme(themeState = themeState) {
        if (session == null) {
            LoginScreen(
                onLoggedIn = { v -> store.save(v); session = v; isUnlocked = true },
                themeState = themeState,
                onThemeChange = { nt -> themeState = nt; store.saveTheme(nt) }
            )
        } else if (isAppLockEnabled && !isUnlocked) {
            AppLockScreen(
                themeState = themeState,
                onUnlockClick = {
                    if (activity != null) {
                        authenticateBiometric(
                            activity = activity,
                            title = "🔒 ورود به پنل پاسارگارد",
                            subtitle = "اثر انگشت یا پین/الگوی گوشی خود را اسکن کنید",
                            onSuccess = { isUnlocked = true },
                            onError = { Toast.makeText(context, "تایید هویت ناموفق بود", Toast.LENGTH_SHORT).show() }
                        )
                    }
                },
                onLogout = { store.clear(); session = null; isUnlocked = false }
            )
        } else {
            UsersScreen(
                session = session!!,
                onLogout = { store.clear(); session = null; isUnlocked = false },
                themeState = themeState,
                onThemeChange = { nt -> themeState = nt; store.saveTheme(nt) },
                isAppLockEnabled = isAppLockEnabled,
                onAppLockChange = { enabled ->
                    if (enabled && activity != null) {
                        authenticateBiometric(
                            activity = activity,
                            title = "🔒 تایید فعال‌سازی قفل",
                            subtitle = "برای فعال‌سازی قفل برنامه، اثر انگشت خود را تایید کنید",
                            onSuccess = {
                                store.saveAppLock(true)
                                isAppLockEnabled = true
                            },
                            onError = {
                                Toast.makeText(context, "فعال‌سازی قفل لغو یا ناموفق بود", Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        store.saveAppLock(false)
                        isAppLockEnabled = false
                    }
                }
            )
        }
    }
}

@Composable
fun AppLockScreen(
    themeState: ThemeState,
    onUnlockClick: () -> Unit,
    onLogout: () -> Unit
) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(if (themeState.isDark) Color(0xFF1C1C24).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.92f)).border(BorderStroke(1.2.dp, themeState.cardBorderBrush), RoundedCornerShape(32.dp)).padding(32.dp)
        ) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(24.dp)).background(themeState.lamp.primary.copy(alpha = 0.18f)).border(BorderStroke(1.2.dp, themeState.lamp.primary), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                Text("🔒", fontSize = 38.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("پنل پاسارگارد قفل است", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = themeState.inkColor)
                Text("برای دسترسی به کاربران، هویت خود را تایید کنید", fontSize = 12.sp, color = themeState.mutedColor, textAlign = TextAlign.Center)
            }
            PrimarySaveButton("👆 ورود با اثر انگشت / رمز گوشی", onClick = onUnlockClick, modifier = Modifier.fillMaxWidth().height(52.dp))
            TextButton(onClick = onLogout) {
                Text("خروج از حساب کاربری", color = GlassRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
