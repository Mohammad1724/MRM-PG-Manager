package com.mrm.pgmanager

import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.launch
import com.mrm.pgmanager.ui.components.PrimarySaveButton
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import androidx.compose.ui.res.stringResource
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.screens.LoginScreen
import com.mrm.pgmanager.ui.screens.UsersScreen
import com.mrm.pgmanager.ui.screens.DashboardScreen
import com.mrm.pgmanager.ui.screens.StatisticsScreen
import com.mrm.pgmanager.ui.screens.GroupsScreen
import com.mrm.pgmanager.ui.screens.TemplatesScreen
import com.mrm.pgmanager.ui.screens.SettingsScreen
import com.mrm.pgmanager.ui.components.PasarGuardDrawer
import com.mrm.pgmanager.ui.components.ImplementedDrawerIds
import com.mrm.pgmanager.ui.components.MrmFloatingNav
import com.mrm.pgmanager.ui.components.TAB_DASHBOARD
import com.mrm.pgmanager.ui.components.TAB_GROUPS
import com.mrm.pgmanager.ui.components.TAB_STATISTICS
import com.mrm.pgmanager.ui.components.TAB_TEMPLATES
import com.mrm.pgmanager.ui.components.TAB_USERS
import com.mrm.pgmanager.utils.NotificationHelper
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.LiquidGlassTheme
import com.mrm.pgmanager.ui.theme.ThemeState

class MainActivity : FragmentActivity() {
    /** دیپ‌لینک دریافتی از اعلان‌ها (مقصد، نام کاربری). توسط MRMApp مصرف و null می‌شود. */
    var pendingDeepLink by androidx.compose.runtime.mutableStateOf<Pair<String, String>?>(null)

    override fun attachBaseContext(newBase: android.content.Context) {
        val lang = SessionStore(newBase).readAppLanguage()
        val wrapped = com.mrm.pgmanager.utils.LocaleHelper.wrap(newBase, lang)
        val sysLocale = if (android.os.Build.VERSION.SDK_INT >= 24) {
            wrapped.resources.configuration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            wrapped.resources.configuration.locale
        }
        java.util.Locale.setDefault(sysLocale)
        super.attachBaseContext(wrapped)
    }

    override fun applyOverrideConfiguration(overrideConfiguration: android.content.res.Configuration?) {
        if (overrideConfiguration != null) {
            val uiMode = overrideConfiguration.uiMode
            try {
                overrideConfiguration.setTo(baseContext.resources.configuration)
            } catch (e: Exception) {
                // Safe guard during early initialization
            }
            overrideConfiguration.uiMode = uiMode
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // لبه‌به‌لبه از همین‌جا روشن می‌شود. قبلاً رنگِ نوارها مستقیم داخل تم ست
        // می‌شد (`window.statusBarColor`) که از اندروید ۱۵ منسوخ است و روی
        // نسخه‌های جدید بی‌اثر می‌شود؛ این API جایگزینِ رسمی‌اش است.
        enableEdgeToEdge()
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
            .setNegativeButtonText(activity.getString(R.string.bio_cancel))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MRMApp() {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val store = remember { SessionStore(context) }
    var session by remember { mutableStateOf(store.read()) }
    var themeState by remember { mutableStateOf(store.readTheme()) }
    var appLanguage by remember { mutableStateOf(store.readAppLanguage()) }
    var isAppLockEnabled by remember { mutableStateOf(store.readAppLock()) }
    var monitoringSettings by remember { mutableStateOf(store.readMonitoringSettings()) }
    var isUnlocked by rememberSaveable { mutableStateOf(false) }
    // مهلت قفل خودکار (ثانیه)؛ 0 یعنی قفل فوری هنگام خروج از برنامه.
    var appLockTimeout by remember { mutableStateOf(store.readAppLockTimeoutSecs()) }
    var lastStoppedAt by remember { mutableStateOf(0L) }
    // حالت «افزودن حساب»: صفحهٔ ورود بدون پاک‌کردن نشست فعلی نمایش داده می‌شود.
    var addingAccount by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showDashboardSettings by rememberSaveable { mutableStateOf(false) }
    // دیپ‌لینک اعلان: نام کاربری مقصد برای بازشدن مستقیم جزئیات او در تب کاربران.
    var deepLinkUsername by remember { mutableStateOf<String?>(null) }
    // درخواستِ «ساخت گروهی» از صفحهٔ تنظیمات: تب کاربران باز می‌شود و دیالوگ را
    // همان‌جا نشان می‌دهد تا لیست پس از ساخت رفرش شود.
    var pendingBulkCreate by rememberSaveable { mutableStateOf(false) }

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
                title = context.getString(R.string.bio_title),
                subtitle = context.getString(R.string.bio_subtitle),
                onSuccess = { isUnlocked = true },
                onError = { /* stay on lock screen */ }
            )
        } else if (!isAppLockEnabled) {
            isUnlocked = true
        }
    }

    LaunchedEffect(session) {
        if (session != null) {
            // بدونِ این محدودیت، WorkManager بررسی را حتی وقتی گوشی اینترنت
            // ندارد اجرا می‌کرد؛ درخواست شکست می‌خورد و اعلانِ «اتصال به پنل
            // ناموفق» می‌آمد در حالی که پنل سالم بود.
            val networkConstraint = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<MonitoringWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraint)
                .build()
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
            selectedTab = if (pending.first == NotificationHelper.DEST_USERS) 1 else 0 // statistics is tab 2, deep-link still goes to dashboard/users
            deepLinkUsername = pending.second.takeIf { it.isNotBlank() }
        }
    }

    val handleLanguageChange: (String) -> Unit = { lang ->
        store.saveAppLanguage(lang)
        appLanguage = lang
        activity?.recreate()
    }

    LiquidGlassTheme(themeState = effectiveTheme) {
        // سوئیچ حساب: نشست فعال بدون دست‌خوردن لیست حساب‌ها عوض می‌شود.
        val switchAccount: (com.mrm.pgmanager.data.model.Session) -> Unit = { acc ->
            // کشِ حافظه به پنلِ قبلی تعلق دارد؛ اگر پاک نشود، یک لحظه دادهٔ
            // حسابِ قبلی روی حسابِ جدید دیده می‌شود.
            com.mrm.pgmanager.data.cache.PanelCache.clear()
            store.setActive(acc); session = acc; isUnlocked = false; addingAccount = false; showDashboardSettings = false
        }
        if (session == null || addingAccount) {
            LoginScreen(
                onLoggedIn = { v -> store.save(v); session = v; isUnlocked = true; addingAccount = false },
                themeState = effectiveTheme,
                appLanguage = appLanguage,
                onLanguageChange = handleLanguageChange,
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
                            title = context.getString(R.string.bio_title),
                            subtitle = context.getString(R.string.bio_subtitle),
                            onSuccess = { isUnlocked = true },
                            onError = { Toast.makeText(context, context.getString(R.string.bio_failed), Toast.LENGTH_SHORT).show() }
                        )
                    }
                },
                onLogout = { store.clear(); com.mrm.pgmanager.data.cache.PanelCache.clear(); session = null; isUnlocked = false }
            )
        } else {
            // فعال‌سازی قفل از هر دو مسیر (تنظیمات داشبورد / کاربران) با تأیید بیومتریک انجام می‌شود.
            val handleAppLockChange: (Boolean) -> Unit = { enabled ->
                if (enabled && activity != null) {
                    authenticateBiometric(
                        activity = activity,
                        title = context.getString(R.string.bio_lock_title),
                        subtitle = context.getString(R.string.bio_lock_subtitle),
                        onSuccess = {
                            store.saveAppLock(true)
                            isAppLockEnabled = true
                        },
                        onError = {
                            Toast.makeText(context, context.getString(R.string.bio_lock_failed), Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    store.saveAppLock(false)
                    isAppLockEnabled = false
                }
            }
            // وضعیت کشوی کناری به بیرون منتقل شد تا دکمهٔ منو بتواند بازش کند و
            // دکمهٔ «×» داخل کشو واقعاً ببندَدش. پیش از این drawerState به‌صورت inline
            // ساخته می‌شد و هیچ ارجاعی به آن وجود نداشت، پس کشو فقط با کشیدن انگشت باز می‌شد.
            val drawerState = androidx.compose.material3.rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
            val drawerScope = rememberCoroutineScope()
            androidx.compose.material3.ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = true,
                drawerContent = {
                    PasarGuardDrawer(
                        selectedId = ImplementedDrawerIds[selectedTab.coerceIn(0, ImplementedDrawerIds.lastIndex)],
                        onSelect = { id ->
                            // ایندکسِ هر بخش در ImplementedDrawerIds همان selectedTab است؛
                            // بخش‌های پیاده‌نشده اصلاً قابل کلیک نیستند و به اینجا نمی‌رسند.
                            ImplementedDrawerIds.indexOf(id).takeIf { it >= 0 }?.let { selectedTab = it }
                        },
                        onClose = { drawerScope.launch { drawerState.close() } },
                        onOpenSettings = { showDashboardSettings = true },
                        adminName = session?.username ?: "mrm",
                        traffic = "12.43 TB"
                    )
                }
            ) {
            // ── دکمهٔ برگشتِ گوشی.
            //
            // تا امروز هیچ‌جا رهگیری نمی‌شد و هر بار اپ بسته می‌شد؛ حتی وسطِ
            // تنظیمات. یک هندلرِ واحد با ترتیبِ اولویت گذاشته شده تا رفتار
            // قابلِ پیش‌بینی باشد (چند BackHandlerِ پراکنده، ترتیبشان به ترتیبِ
            // composition وابسته می‌شود و دیباگش سخت است).
            //
            // دیالوگ‌ها اینجا نمی‌آیند: هر Dialog پنجرهٔ جداست و خودش back را
            // مصرف می‌کند، پس این هندلر اصلاً صدا زده نمی‌شود.
            var lastBackAt by remember { mutableStateOf(0L) }
            val exitHint = stringResource(R.string.back_exit_hint)
            androidx.activity.compose.BackHandler {
                when {
                    // ۱. تنظیمات باز است → ببند
                    showDashboardSettings -> showDashboardSettings = false
                    // ۲. کشو باز است → ببند
                    drawerState.isOpen -> drawerScope.launch { drawerState.close() }
                    // ۳. در بخشی غیر از داشبورد → برگرد به داشبورد
                    selectedTab != TAB_DASHBOARD -> selectedTab = TAB_DASHBOARD
                    // ۴. روی داشبورد → دو بار پشت‌سرهم برای خروج، تا با یک لمسِ
                    //    اتفاقی کلِ اپ بسته نشود.
                    else -> {
                        val now = System.currentTimeMillis()
                        if (now - lastBackAt < 2000L) activity?.finish()
                        else {
                            lastBackAt = now
                            Toast.makeText(context, exitHint, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            // ── سوایپ بین بخش‌ها: صفحه‌ها روی یک Pager می‌نشینند تا با انگشت
            // هم بشود بینشان جابه‌جا شد. ترتیب صفحه‌ها = همان ایندکس‌های
            // selectedTab (۰ داشبورد … ۴ تمپلت‌ها).
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                initialPage = selectedTab,
                pageCount = { ImplementedDrawerIds.size }
            )
            // کلیک روی نوار پایین → پرش نرم به صفحهٔ متناظر.
            LaunchedEffect(selectedTab) {
                if (pagerState.currentPage != selectedTab) pagerState.animateScrollToPage(selectedTab)
            }
            // سوایپِ کاربر → به‌روزرسانیِ آیتمِ فعالِ نوار پایین. settledPage
            // یعنی وقتی حرکت واقعاً تمام شد، نه در میانهٔ کشیدن.
            LaunchedEffect(pagerState.settledPage) {
                if (selectedTab != pagerState.settledPage) selectedTab = pagerState.settledPage
            }

            // ── محو/ظاهر شدنِ کپسولِ ناوبری با جهتِ اسکرول.
            // از nested scroll استفاده می‌شود تا هیچ تغییری در خودِ صفحه‌ها لازم
            // نباشد: هر لیست/اسکرولی که داخلِ Pager باشد رویدادش به اینجا می‌رسد.
            var navVisible by remember { mutableStateOf(true) }
            val navScrollConnection = remember {
                object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                    override fun onPreScroll(
                        available: androidx.compose.ui.geometry.Offset,
                        source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
                    ): androidx.compose.ui.geometry.Offset {
                        // آستانهٔ کوچک تا لرزشِ انگشت باعثِ پلک‌زدنِ نوار نشود.
                        if (available.y < -6f) navVisible = false
                        else if (available.y > 6f) navVisible = true
                        return androidx.compose.ui.geometry.Offset.Zero
                    }
                }
            }
            // با عوض‌شدنِ بخش، نوار دوباره دیده شود؛ وگرنه اگر در صفحهٔ قبل
            // پنهان شده بود، در صفحهٔ جدید هم غایب می‌ماند.
            LaunchedEffect(selectedTab) { navVisible = true }

            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().nestedScroll(navScrollConnection)) {
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        // یک صفحه از هر طرف از قبل ساخته می‌شود تا سوایپ محتوای
                        // آماده را نشان بدهد، نه صفحه‌ای که وسطِ انیمیشن دارد
                        // ساخته می‌شود. قبلاً صفر بود چون هر صفحه با ساخته‌شدن
                        // یک ریکوئست می‌فرستاد؛ حالا PanelCache جلوی درخواستِ
                        // تکراری را می‌گیرد، پس پیش‌ساختن هزینهٔ شبکه ندارد.
                        beyondViewportPageCount = 1,
                        key = { it }
                    ) { page ->
                        // حرکتِ عمق: صفحهٔ در حالِ رفتن کمی عقب می‌نشیند و محو
                        // می‌شود. بدونِ این، سوایپ حسِ «کاغذِ تخت» داشت.
                        val offset = ((pagerState.currentPage - page) +
                            pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
                        Box(
                            Modifier.graphicsLayer {
                                val distance = kotlin.math.abs(offset)
                                alpha = 1f - distance * 0.35f
                                val scale = 1f - distance * 0.06f
                                scaleX = scale
                                scaleY = scale
                            }
                        ) {
                        when (page) {
                            TAB_DASHBOARD -> DashboardScreen(session!!, monitoringSettings, onLogout = { store.clear(); com.mrm.pgmanager.data.cache.PanelCache.clear(); session = null; isUnlocked = false }, onOpenSettings = { showDashboardSettings = true })
                            TAB_STATISTICS -> StatisticsScreen(session!!, onOpenSettings = { showDashboardSettings = true })
                            TAB_GROUPS -> GroupsScreen(session!!, onOpenSettings = { showDashboardSettings = true })
                            TAB_TEMPLATES -> TemplatesScreen(session!!, onOpenSettings = { showDashboardSettings = true })
                            else -> UsersScreen(
                                session = session!!,
                                onLogout = { store.clear(); com.mrm.pgmanager.data.cache.PanelCache.clear(); session = null; isUnlocked = false },
                                themeState = effectiveTheme,
                                monitoringSettings = monitoringSettings,
                                deepLinkUsername = deepLinkUsername,
                                onDeepLinkHandled = { deepLinkUsername = null },
                                openBulkCreate = pendingBulkCreate,
                                onBulkCreateHandled = { pendingBulkCreate = false },
                                onOpenSettings = { showDashboardSettings = true }
                            )
                        }
                        }
                    }
                }
                // کپسولِ شناور: روی محتوا و چسبیده به پایینِ صفحه، در ناحیهٔ شست.
                MrmFloatingNav(
                    selectedTab = selectedTab,
                    visible = navVisible,
                    onSelect = { selectedTab = it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
                // دکمهٔ همبرگری حذف شد: ناوبری به نوار پایین منتقل شده و کشو
                // فقط با کشیدنِ انگشت از لبه باز می‌شود (gesturesEnabled).
                // تنظیمات: صفحهٔ کامل روی محتوا (نه دیالوگ) تا با بقیهٔ اپ یکدست باشد.
                // پس‌زمینهٔ مات جلوی دیده‌شدن صفحهٔ زیرین را می‌گیرد.
                androidx.compose.animation.AnimatedVisibility(
                    visible = showDashboardSettings,
                    enter = com.mrm.pgmanager.ui.designsystem.DsTransition.screenEnter,
                    exit = com.mrm.pgmanager.ui.designsystem.DsTransition.screenExit
                ) {
                    Box(Modifier.fillMaxSize().background(effectiveTheme.backgroundColor)) {
                        SettingsScreen(
                            themeState = effectiveTheme,
                            onThemeChange = { nt -> themeState = nt; store.saveTheme(nt) },
                            onBack = { showDashboardSettings = false },
                            isAppLockEnabled = isAppLockEnabled,
                            onAppLockChange = handleAppLockChange,
                            monitoringSettings = monitoringSettings,
                            onMonitoringChange = { value -> monitoringSettings = value; store.saveMonitoringSettings(value) },
                            appLockTimeout = appLockTimeout,
                            onLockTimeoutChange = { t -> appLockTimeout = t; store.saveAppLockTimeoutSecs(t) },
                            appLanguage = appLanguage,
                            onLanguageChange = handleLanguageChange,
                            onLogout = { store.clear(); com.mrm.pgmanager.data.cache.PanelCache.clear(); session = null; isUnlocked = false; showDashboardSettings = false },
                            appVersion = BuildConfig.VERSION_NAME,
                            session = session,
                            store = store,
                            onSwitchAccount = switchAccount,
                            onAddAccount = { showDashboardSettings = false; addingAccount = true },
                            // ساخت گروهی در صفحهٔ کاربران باز می‌شود تا بعد از پایان،
                            // لیستِ کاربران خودش رفرش شود.
                            onBulkCreate = {
                                showDashboardSettings = false
                                selectedTab = TAB_USERS
                                pendingBulkCreate = true
                            }
                        )
                    }
                }
            }
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
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(themeState.cardSurfaceColor).border(BorderStroke(1.dp, themeState.borderColor), RoundedCornerShape(16.dp)).padding(24.dp)
        ) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFFBEB)).border(BorderStroke(1.dp, Color(0xFFFDE68A)), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                RoundedAppIcon(AppIcon.Lock, tint = themeState.inkColor, size = 38.dp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.lock_title), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = themeState.inkColor)
                Text(stringResource(R.string.lock_subtitle), fontSize = 12.sp, color = themeState.mutedColor, textAlign = TextAlign.Center)
            }
            com.mrm.pgmanager.ui.components.PrimaryButton(stringResource(R.string.unlock_with_biometric), onClick = onUnlockClick, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.TextButton(onClick = onLogout) {
                Text(stringResource(R.string.logout), color = GlassRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
