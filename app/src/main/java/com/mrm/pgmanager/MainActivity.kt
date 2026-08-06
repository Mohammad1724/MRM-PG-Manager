package com.mrm.pgmanager

import android.os.Bundle
import android.Manifest
import android.content.Intent
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
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
import com.mrm.pgmanager.work.BackupWorker
import com.mrm.pgmanager.work.MonitoringWorker
import java.util.concurrent.TimeUnit
import com.mrm.pgmanager.ui.components.PrimarySaveButton
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.screens.LoginScreen
import com.mrm.pgmanager.ui.screens.UsersScreen
import com.mrm.pgmanager.ui.screens.DashboardScreen
import com.mrm.pgmanager.utils.NotificationHelper
import com.mrm.pgmanager.ui.dialogs.ThemeEditorDialog
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.LiquidGlassTheme
import com.mrm.pgmanager.ui.theme.ThemeState

class MainActivity : FragmentActivity() {
    /** دیپ‌لینک دریافتی از اعلان‌ها (مقصد، نام کاربری). توسط MRMApp مصرف و null می‌شود. */
    var pendingDeepLink by androidx.compose.runtime.mutableStateOf<Pair<String, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7001)
        readDeepLink(intent)
        setContent { MRMApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readDeepLink(intent)
    }

    /** استخراج یک‌بارمصرفِ مقصد اعلان از Intent و پاک‌کردن extras تا دوباره پردازش نشود. */
    private fun readDeepLink(intent: Intent?) {
        if (intent == null) return
        val dest = intent.getStringExtra(NotificationHelper.EXTRA_DEST) ?: return
        pendingDeepLink = dest to (intent.getStringExtra(NotificationHelper.EXTRA_USERNAME) ?: "")
        NotificationHelper.consumeDeepLink(intent)
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

    // در اندروید 9 و 10 (API 28-29) ترکیب BIOMETRIC_STRONG با DEVICE_CREDENTIAL پشتیبانی نمی‌شود
    // و کتابخانه IllegalArgumentException پرتاب می‌کند؛ روی آن نسخه‌ها فقط بیومتریک + دکمهٔ انصراف.
    val promptInfo = if (android.os.Build.VERSION.SDK_INT in 28..29) {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("انصراف")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
    } else {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
    }

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
    var isUnlocked by rememberSaveable { mutableStateOf(false) }
    // مهلت قفل خودکار (ثانیه)؛ 0 یعنی قفل فوری هنگام خروج از برنامه.
    var appLockTimeout by remember { mutableStateOf(store.readAppLockTimeoutSecs()) }
    var lastStoppedAt by remember { mutableStateOf(0L) }
    // حالت «افزودن حساب»: صفحهٔ ورود بدون پاک‌کردن نشست فعلی نمایش داده می‌شود.
    var addingAccount by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var showQuickTabs by remember { mutableStateOf(true) }
    var showDashboardSettings by remember { mutableStateOf(false) }
    // دیپ‌لینک اعلان: نام کاربری مقصد برای بازشدن مستقیم جزئیات او در تب کاربران.
    var deepLinkUsername by remember { mutableStateOf<String?>(null) }
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

    // پس از خروج اپ از foreground، در بازگشت «با رعایت مهلت» دوباره قفل را نمایش بده.
    // مهلت 0 = قفل فوری (رفتار قبلی)؛ در غیر این صورت فقط اگر از آخرین خروج بیشتر از مهلت گذشته باشد.
    DisposableEffect(activity, isAppLockEnabled, session, appLockTimeout) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (isAppLockEnabled) lastStoppedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> if (isAppLockEnabled && session != null) {
                    val elapsedIfStopped = if (lastStoppedAt > 0L) System.currentTimeMillis() - lastStoppedAt else Long.MAX_VALUE
                    if (appLockTimeout <= 0 || elapsedIfStopped > appLockTimeout * 1000L) isUnlocked = false
                }
                else -> {}
            }
        }
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
            // زمان‌بندی پشتیبان‌گیری خودکار (store بیرونی از MRMApp استفاده می‌شود)
            val hours = if (store.readBackupEnabled()) store.readBackupIntervalHours() else 0
            BackupWorker.schedule(context, hours)
        }
    }

    // مصرف دیپ‌لینک اعلان‌ها: مقصد را به تب مربوطه ببر و اگر کاربری همراه بود، جزئیات او را باز کن.
    // کلیدِ effect شامل «بودن نشست» است تا اگر اعلان قبل از ورود رسیده باشد، بعد از ورود پردازش شود.
    val mainActivity = context as? MainActivity
    val pending = mainActivity?.pendingDeepLink
    LaunchedEffect(pending, session != null) {
        // pending غیرنال بودن یعنی mainActivity هم غیرنال است (کامپایلر smart-cast می‌کند).
        if (pending != null && session != null) {
            mainActivity.pendingDeepLink = null
            selectedTab = if (pending.first == NotificationHelper.DEST_USERS) 1 else 0
            deepLinkUsername = pending.second.takeIf { it.isNotBlank() }
        }
    }

    LiquidGlassTheme(themeState = effectiveTheme) {
        // سوئیچ حساب: نشست فعال بدون دست‌خوردن لیست حساب‌ها عوض می‌شود.
        val switchAccount: (com.mrm.pgmanager.data.model.Session) -> Unit = { acc ->
            store.setActive(acc); session = acc; isUnlocked = false; addingAccount = false; showDashboardSettings = false
        }
        if (session == null || addingAccount) {
            LoginScreen(
                onLoggedIn = { v -> store.save(v); session = v; isUnlocked = true; addingAccount = false },
                themeState = effectiveTheme,
                onThemeChange = { nt -> themeState = nt; store.saveTheme(nt) },
                onBack = when {
                    addingAccount && session != null -> { { addingAccount = false } }
                    session == null && store.readAccounts().isNotEmpty() -> { { val acc = store.readAccounts().first(); store.setActive(acc); session = acc } }
                    else -> null
                }
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
            // فعال‌سازی قفل از هر دو مسیر (تنظیمات داشبورد / کاربران) با تأیید بیومتریک انجام می‌شود.
            val handleAppLockChange: (Boolean) -> Unit = { enabled ->
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
            Box(Modifier.fillMaxSize().nestedScroll(tabScrollConnection)) {
                Box(Modifier.fillMaxSize()) {
                    if (selectedTab == 0) DashboardScreen(session!!, monitoringSettings, onSettings = { showDashboardSettings = true }, onLogout = { store.clear(); session = null; isUnlocked = false }) else UsersScreen(
                session = session!!,
                onLogout = { store.clear(); session = null; isUnlocked = false },
                themeState = effectiveTheme,
                onThemeChange = { nt -> themeState = nt; store.saveTheme(nt) },
                monitoringSettings = monitoringSettings,
                onMonitoringChange = { value -> monitoringSettings = value; store.saveMonitoringSettings(value) },
                isAppLockEnabled = isAppLockEnabled,
                onAppLockChange = handleAppLockChange,
                appLockTimeout = appLockTimeout,
                onLockTimeoutChange = { t -> appLockTimeout = t; store.saveAppLockTimeoutSecs(t) },
                onSwitchAccount = switchAccount,
                onAddAccount = { addingAccount = true },
                deepLinkUsername = deepLinkUsername,
                onDeepLinkHandled = { deepLinkUsername = null }
            )
                }
                // تب‌بار پایین: دقیقاً همان کپسول سگمنت‌شدهٔ تب‌های تنظیمات (کاشی خاکستری + آیتم فعال اکسنت).
                // منطق مخفی/پیداشدن هنگام اسکرول (AnimatedVisibility) بدون تغییر باقی مانده است.
                AnimatedVisibility(
                    visible = showQuickTabs,
                    enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(220)) + slideInVertically(animationSpec = androidx.compose.animation.core.tween(220)) { it / 2 },
                    exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(180)) + slideOutVertically(animationSpec = androidx.compose.animation.core.tween(180)) { it / 2 },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp)
                ) {
                    Row(
                        Modifier.height(56.dp).width(224.dp).clip(com.mrm.pgmanager.ui.designsystem.DsRadius.Xl)
                            .shadow(
                                elevation = com.mrm.pgmanager.ui.designsystem.DsElevation.High.ambient.dp,
                                shape = com.mrm.pgmanager.ui.designsystem.DsRadius.Xl,
                                ambientColor = effectiveTheme.accentPrimary.copy(0.28f),
                                spotColor = Color.Black.copy(0.20f)
                            )
                            .background(if (effectiveTheme.isDark) effectiveTheme.cardSurfaceColor.copy(alpha = 0.96f) else effectiveTheme.cardSurfaceColor.copy(alpha = 0.96f))
                            .border(BorderStroke(com.mrm.pgmanager.ui.designsystem.DsBorder.Thin, com.mrm.pgmanager.ui.theme.glassBorder(effectiveTheme.isDark, effectiveTheme.amoledDark)), com.mrm.pgmanager.ui.designsystem.DsRadius.Xl)
                            .padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        listOf("داشبورد" to AppIcon.Gauge, "کاربران" to AppIcon.Users).forEachIndexed { index, (label, icon) ->
                            val selected = selectedTab == index
                            val scale by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (selected) 1f else 0.92f,
                                animationSpec = com.mrm.pgmanager.ui.designsystem.DsMotion.ScaleSpring,
                                label = "navScale"
                            )
                            Box(
                                Modifier.weight(1f).fillMaxHeight().graphicsLayer(scaleX = scale, scaleY = scale)
                                    .clip(com.mrm.pgmanager.ui.designsystem.DsRadius.Lg)
                                    .shadow(
                                        elevation = if (selected) com.mrm.pgmanager.ui.designsystem.DsElevation.Low.spot.dp else 0.dp,
                                        shape = com.mrm.pgmanager.ui.designsystem.DsRadius.Lg,
                                        ambientColor = effectiveTheme.accentPrimary.copy(0.4f),
                                        spotColor = effectiveTheme.accentPrimary.copy(0.5f)
                                    )
                                    .background(if (selected) effectiveTheme.accentPrimary.copy(.9f) else Color.Transparent)
                                    .clickable { selectedTab = index },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    RoundedAppIcon(icon, tint = if (selected) Color(0xFF1A1A1A) else effectiveTheme.mutedColor, size = 18.dp)
                                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) Color(0xFF1A1A1A) else effectiveTheme.mutedColor)
                                }
                            }
                        }
                    }
                }
                if (showDashboardSettings) ThemeEditorDialog(themeState = effectiveTheme, isAppLockEnabled = isAppLockEnabled, onDismiss = { showDashboardSettings = false }, onThemeChange = { nt -> themeState = nt; store.saveTheme(nt) }, onAppLockChange = handleAppLockChange, monitoringSettings = monitoringSettings, onMonitoringChange = { value -> monitoringSettings = value; store.saveMonitoringSettings(value) }, appVersion = BuildConfig.VERSION_NAME, session = session, onLogout = { store.clear(); session = null; isUnlocked = false; showDashboardSettings = false }, appLockTimeout = appLockTimeout, onLockTimeoutChange = { t -> appLockTimeout = t; store.saveAppLockTimeoutSecs(t) }, onSwitchAccount = switchAccount, onAddAccount = { addingAccount = true; showDashboardSettings = false })
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
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(if (themeState.isDark) themeState.dialogBgColor.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.92f)).border(BorderStroke(1.2.dp, themeState.cardBorderBrush), RoundedCornerShape(32.dp)).padding(32.dp)
        ) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(24.dp)).background(themeState.accentPrimary.copy(alpha = 0.18f)).border(BorderStroke(1.2.dp, themeState.accentPrimary), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                RoundedAppIcon(AppIcon.Lock, tint = themeState.inkColor, size = 38.dp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("پنل پاسارگارد قفل است", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = themeState.inkColor)
                Text("برای دسترسی به کاربران، هویت خود را تایید کنید", fontSize = 12.sp, color = themeState.mutedColor, textAlign = TextAlign.Center)
            }
            com.mrm.pgmanager.ui.components.PrimaryButton("ورود با اثر انگشت / رمز گوشی", onClick = onUnlockClick, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.TextButton(onClick = onLogout) {
                Text("خروج از حساب کاربری", color = GlassRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
