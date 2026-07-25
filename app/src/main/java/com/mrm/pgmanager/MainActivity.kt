package com.mrm.pgmanager

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.work.MonitoringWorker
import java.util.concurrent.TimeUnit
import com.mrm.pgmanager.ui.components.PrimarySaveButton
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.screens.LoginScreen
import com.mrm.pgmanager.ui.screens.UsersScreen
import com.mrm.pgmanager.ui.screens.DashboardScreen
import com.mrm.pgmanager.ui.dialogs.ThemeEditorDialog
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.LiquidGlassTheme
import com.mrm.pgmanager.ui.theme.ThemeState

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7001)
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
    var monitoringSettings by remember { mutableStateOf(store.readMonitoringSettings()) }
    var isUnlocked by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var showQuickTabs by remember { mutableStateOf(true) }
    var showDashboardSettings by remember { mutableStateOf(false) }
    val tabScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -2f) showQuickTabs = false
                else if (available.y > 2f) showQuickTabs = true
                return Offset.Zero
            }
        }
    }

    // تمِ مؤثر: اگر «خودکار» فعّال باشد، از حالتِ روشن/تیرهٔ سیستم پیروی می‌کند.
    val systemDark = isSystemInDarkTheme()
    val effectiveTheme = if (themeState.followSystem) themeState.copy(isDark = systemDark) else themeState

    // پس از خروج اپ از foreground، در بازگشت دوباره قفل را نمایش بده.
    DisposableEffect(activity, isAppLockEnabled) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START && isAppLockEnabled && session != null) isUnlocked = false }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(session, isAppLockEnabled, isUnlocked) {
        if (session != null && isAppLockEnabled && !isUnlocked && activity != null) {
            authenticateBiometric(
                activity = activity,
                title = "ورود به پنل پاسارگارد",
                subtitle = "اثر انگشت یا پین/الگوی گوشی خود را اسکن کنید",
                onSuccess = { isUnlocked = true },
                onError = { /* stay on lock screen */ }
            )
        } else if (!isAppLockEnabled) {
            isUnlocked = true
        }
    }

    LaunchedEffect(session) {
        if (session != null) {
            val request = PeriodicWorkRequestBuilder<MonitoringWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("mrm_background_monitoring", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }

    LiquidGlassTheme(themeState = effectiveTheme) {
        if (session == null) {
            LoginScreen(
                onLoggedIn = { v -> store.save(v); session = v; isUnlocked = true },
                themeState = effectiveTheme,
                onThemeChange = { nt -> themeState = nt; store.saveTheme(nt) }
            )
        } else if (isAppLockEnabled && !isUnlocked) {
            AppLockScreen(
                themeState = effectiveTheme,
                onUnlockClick = {
                    if (activity != null) {
                        authenticateBiometric(
                            activity = activity,
                            title = "ورود به پنل پاسارگارد",
                            subtitle = "اثر انگشت یا پین/الگوی گوشی خود را اسکن کنید",
                            onSuccess = { isUnlocked = true },
                            onError = { Toast.makeText(context, "تایید هویت ناموفق بود", Toast.LENGTH_SHORT).show() }
                        )
                    }
                },
                onLogout = { store.clear(); session = null; isUnlocked = false }
            )
        } else {
            Box(Modifier.fillMaxSize().nestedScroll(tabScrollConnection)) {
                Box(Modifier.fillMaxSize()) {
                    if (selectedTab == 0) DashboardScreen(session!!, monitoringSettings, onSettings = { showDashboardSettings = true }, onLogout = { store.clear(); session = null; isUnlocked = false }) else UsersScreen(
                session = session!!,
                onLogout = { store.clear(); session = null; isUnlocked = false },
                themeState = effectiveTheme,
                onThemeChange = { nt -> themeState = nt; store.saveTheme(nt) },
                isAppLockEnabled = isAppLockEnabled,
                onAppLockChange = { enabled ->
                    if (enabled && activity != null) {
                        authenticateBiometric(
                            activity = activity,
                            title = "تایید فعال‌سازی قفل",
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
                AnimatedVisibility(
                    visible = showQuickTabs,
                    enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(220)) + slideInVertically(animationSpec = androidx.compose.animation.core.tween(220)) { it / 2 },
                    exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(180)) + slideOutVertically(animationSpec = androidx.compose.animation.core.tween(180)) { it / 2 },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("داشبورد", "کاربران").forEachIndexed { index, label ->
                            Box(Modifier.width(104.dp).height(42.dp).clip(RoundedCornerShape(13.dp)).background(if (selectedTab == index) effectiveTheme.lamp.primary.copy(.78f) else Color.White).border(BorderStroke(1.dp, effectiveTheme.cardBorderBrush), RoundedCornerShape(13.dp)).clickable { selectedTab = index }, contentAlignment = Alignment.Center) {
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selectedTab == index) Color(0xFF202124) else effectiveTheme.inkColor)
                            }
                        }
                    }
                }
                if (showDashboardSettings) ThemeEditorDialog(themeState = effectiveTheme, isAppLockEnabled = isAppLockEnabled, onDismiss = { showDashboardSettings = false }, onThemeChange = { nt -> themeState = nt; store.saveTheme(nt) }, onAppLockChange = { enabled -> store.saveAppLock(enabled); isAppLockEnabled = enabled }, monitoringSettings = monitoringSettings, onMonitoringChange = { value -> monitoringSettings = value; store.saveMonitoringSettings(value) }, appVersion = BuildConfig.VERSION_NAME)
            }
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
                RoundedAppIcon(AppIcon.Lock, tint = themeState.inkColor, size = 38.dp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("پنل پاسارگارد قفل است", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = themeState.inkColor)
                Text("برای دسترسی به کاربران، هویت خود را تایید کنید", fontSize = 12.sp, color = themeState.mutedColor, textAlign = TextAlign.Center)
            }
            PrimarySaveButton("ورود با اثر انگشت / رمز گوشی", onClick = onUnlockClick, modifier = Modifier.fillMaxWidth().height(52.dp))
            TextButton(onClick = onLogout) {
                Text("خروج از حساب کاربری", color = GlassRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
