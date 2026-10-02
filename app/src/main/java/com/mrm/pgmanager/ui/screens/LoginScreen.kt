package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mrm.pgmanager.R
import com.mrm.pgmanager.ui.theme.LocalThemeState
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoggedIn: (Session) -> Unit,
    themeState: ThemeState,
    appLanguage: String = "system",
    onLanguageChange: (String) -> Unit = {},
    onBack: (() -> Unit)? = null,
    /** حسابی که نشستش منقضی شده؛ آدرس و نامِ کاربری‌اش پیش‌پر می‌شود تا فقط رمز لازم باشد. */
    prefill: Session? = null,
    /** نمایشِ بنرِ «نشست منقضی شده» بالای فرم. */
    sessionExpired: Boolean = false
) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(prefill?.baseUrl.orEmpty()) }
    var username by remember { mutableStateOf(prefill?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    // ورود با کلید API (`pg_key_…`): برای کسانی که نمی‌خواهند هر ۲۴ ساعت دوباره رمز بزنند.
    var useApiKey by rememberSaveable { mutableStateOf(prefill?.isApiKey == true) }
    var apiKey by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // خطاهای میدانی (فاز ۲.۴): زیر همان فیلد نمایش داده می‌شوند، نه در بنر سراسری.
    var urlError by remember { mutableStateOf<String?>(null) }
    var userError by remember { mutableStateOf<String?>(null) }
    var passError by remember { mutableStateOf<String?>(null) }
    var keyError by remember { mutableStateOf<String?>(null) }
    val theme = themeState

    // پیام‌های خطا باید *قبل از* لامبدای کلیک خوانده شوند؛ stringResource فقط در
    // بدنهٔ کامپوزبل قابل فراخوانی است، نه داخلِ coroutine.
    val errCredentials = stringResource(R.string.login_err_credentials)
    val errUrl = stringResource(R.string.login_err_url)
    val errHttps = stringResource(R.string.login_err_https)
    val errHost = stringResource(R.string.login_err_host)
    val errTimeout = stringResource(R.string.login_err_timeout)
    val errAuth = stringResource(R.string.login_err_auth)
    val errNotFound = stringResource(R.string.login_err_not_found)
    val errUnknown = stringResource(R.string.login_err_unknown)
    val errGenericTemplate = stringResource(R.string.login_err_generic)
    val errApiKey = stringResource(R.string.login_err_api_key)
    val errApiKeyFormat = stringResource(R.string.login_err_api_key_format)
    val errRequired = stringResource(R.string.login_field_required)
    val stepConnecting = stringResource(R.string.login_step_connecting)

    // در حالتِ «افزودن حساب» (یا وقتی حسابِ ذخیره‌شده‌ای هست) دکمهٔ برگشتِ گوشی
    // باید همان کارِ دکمهٔ «بازگشت» را بکند، نه اینکه اپ را ببندد.
    if (onBack != null) androidx.activity.compose.BackHandler { onBack() }

    val focusManager = LocalFocusManager.current
    Box(Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = DsSpacing.Screen).padding(top = DsSpacing.Lg, bottom = DsSpacing.Xxxl), verticalArrangement = Arrangement.spacedBy(DsSpacing.Xl)) {
            // نوار بالا: فقط سوییچِ زبان. دکمهٔ تنظیمات حذف شد چون تنظیماتِ کامل
            // پس از ورود در دسترس است و اینجا فقط باعث شلوغی و ورودِ اتفاقی به
            // دیالوگِ قدیمی می‌شد.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                LanguageToggle(appLanguage = appLanguage, onLanguageChange = onLanguageChange, theme = theme)
            }

            Spacer(Modifier.height(DsSpacing.Lg))

            // Logo centered
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DsSpacing.Md)) {
                AppLogo(height = 56.dp)
                Text("PasarGuard", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                Text("MRM Manager", fontSize = 12.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.login_subtitle), fontSize = 11.sp, color = theme.mutedColor)
            }

            // Card — white, subtle border, same as PG
            Column(
                Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(DsSpacing.Xl),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.Screen)
            ) {
                if (sessionExpired) {
                    Row(Modifier.fillMaxWidth().clip(DsRadius.Md).background(DsSemantic.WarningBg).border(BorderStroke(DsBorder.Hairline, DsSemantic.WarningBorder), DsRadius.Md).padding(DsSpacing.Mid),
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                        RoundedAppIcon(AppIcon.Timer, tint = DsSemantic.OnWarning, size = 16.dp)
                        Text(stringResource(R.string.login_session_expired_banner), color = DsSemantic.OnWarning, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    }
                }

                // سوییچِ روشِ ورود: رمز عبور (JWT ۲۴ساعته) / کلید API (بدون انقضا).
                Row(Modifier.fillMaxWidth().clip(DsRadius.Md).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Md).padding(DsSpacing.Xs), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Xs)) {
                    @Composable fun ModeTab(selected: Boolean, label: String, onClick: () -> Unit) {
                        Box(
                            Modifier.padding(vertical = 5.dp).weight(1f).height(30.dp).clip(DsRadius.Sm).clickable(enabled = !loading) { onClick(); error = null; urlError = null; userError = null; passError = null; keyError = null }
                                .background(if (selected) theme.cardSurfaceColor else Color.Transparent)
                                .border(BorderStroke(DsBorder.Hairline, if (selected) theme.borderColor else Color.Transparent), DsRadius.Sm)
                                ,
                            contentAlignment = Alignment.Center
                        ) { Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = if (selected) theme.inkColor else theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    ModeTab(!useApiKey, stringResource(R.string.login_mode_password)) { useApiKey = false }
                    ModeTab(useApiKey, stringResource(R.string.login_mode_api_key)) { useApiKey = true }
                }

                PGField(label = stringResource(R.string.panel_address), value = url, onValueChange = { url = it; urlError = null }, placeholder = stringResource(R.string.panel_hint), icon = AppIcon.Link, imeAction = androidx.compose.ui.text.input.ImeAction.Next, error = urlError)
                if (useApiKey) {
                    PGField(label = stringResource(R.string.login_api_key), value = apiKey, onValueChange = { apiKey = it.trim(); keyError = null }, placeholder = stringResource(R.string.login_api_key_hint), icon = AppIcon.Lock, isPassword = true, imeAction = androidx.compose.ui.text.input.ImeAction.Done, onNext = { focusManager.clearFocus() }, error = keyError)
                    Text(stringResource(R.string.login_api_key_desc), fontSize = 11.sp, color = theme.mutedColor)
                } else {
                    PGField(label = stringResource(R.string.username), value = username, onValueChange = { input -> if (input.trim().startsWith(Session.API_KEY_PREFIX)) { username = ""; apiKey = input.trim(); useApiKey = true } else { username = input }; userError = null }, placeholder = stringResource(R.string.username), icon = AppIcon.User, imeAction = androidx.compose.ui.text.input.ImeAction.Next, error = userError)
                    PGField(label = stringResource(R.string.password), value = password, onValueChange = { input -> if (input.trim().startsWith(Session.API_KEY_PREFIX)) { password = ""; apiKey = input.trim(); useApiKey = true } else { password = input }; passError = null }, placeholder = stringResource(R.string.password), icon = AppIcon.Lock, isPassword = true, imeAction = androidx.compose.ui.text.input.ImeAction.Done, onNext = { focusManager.clearFocus() }, error = passError)
                }

                if (loading) {
                    // بازخوردِ مرحلهٔ اتصال (فاز ۲.۴): تا رسیدن پاسخ، وضعیتِ جاری زیرِ فرم نشان داده می‌شود.
                    Text(stepConnecting, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = theme.mutedColor, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                if (error != null) {
                    Row(Modifier.fillMaxWidth().clip(DsRadius.Md).background(DsSemantic.DangerBg).border(BorderStroke(DsBorder.Hairline, DsSemantic.DangerBorder), DsRadius.Md).padding(DsSpacing.Mid),
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                        RoundedAppIcon(AppIcon.Warning, tint = GlassRed, size = 16.dp)
                        Text(error!!, color = GlassRed, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    }
                }

                // دکمهٔ ورود — کپسولِ شیشه‌ایِ اصلی؛ اسپینر داخلِ سکهٔ آیکون می‌چرخد.
                PGPrimaryButton(
                    text = stringResource(R.string.sign_in),
                    onClick = {
                        if (loading) return@PGPrimaryButton
                        // مرحلهٔ ۱ — اعتبارسنجی محلی: خطا بلافاصله زیر همان فیلد، بدونِ اتصال.
                        error = null; urlError = null; userError = null; passError = null; keyError = null
                        var blocked = false
                        val trimmedUrl = url.trim()
                        val prepared = if (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) trimmedUrl else "https://$trimmedUrl"
                        val uri = runCatching { java.net.URI(prepared) }.getOrNull()
                        val host = uri?.host
                        val loopback = host == "localhost" || host == "127.0.0.1"
                        val isHttp = uri?.scheme.equals("http", ignoreCase = true)
                        when {
                            trimmedUrl.isEmpty() -> { urlError = errUrl; blocked = true }
                            uri == null || host.isNullOrBlank() -> { urlError = errUrl; blocked = true }
                            isHttp && !loopback -> { urlError = errHttps; blocked = true }
                        }
                        if (useApiKey) {
                            val key = apiKey.trim()
                            when {
                                key.isEmpty() -> { keyError = errRequired; blocked = true }
                                !key.startsWith(Session.API_KEY_PREFIX) || key.length <= Session.API_KEY_PREFIX.length -> { keyError = errApiKeyFormat; blocked = true }
                            }
                        } else {
                            if (username.isBlank()) { userError = errRequired; blocked = true }
                            if (password.isBlank()) { passError = errRequired; blocked = true }
                        }
                        if (blocked) return@PGPrimaryButton
                        // مرحلهٔ ۲ — اتصال: فقط خطاهای شبکه/سرور در بنر سراسری؛ بقیه زیر فیلدِ مربوط.
                        loading = true
                        scope.launch {
                            runCatching {
                                if (useApiKey) PanelApi.loginWithApiKey(url, apiKey) else PanelApi.login(url, username, password)
                            }.onSuccess(onLoggedIn).onFailure { e ->
                                when {
                                    e.message?.contains("Credentials required", true) == true -> passError = errCredentials
                                    e.message?.contains("Invalid API key", true) == true -> keyError = errApiKeyFormat
                                    e.message?.contains("Invalid URL", true) == true || e.message?.contains("Panel address is required", true) == true -> urlError = errUrl
                                    e.message?.contains("Cleartext http", true) == true -> urlError = errHttps
                                    PanelApi.isUnauthorized(e) -> if (useApiKey) keyError = errApiKey else passError = errAuth
                                    e is java.net.UnknownHostException -> error = errHost
                                    e is java.net.SocketTimeoutException -> error = errTimeout
                                    e.message?.contains("404", true) == true -> error = errNotFound
                                    else -> error = String.format(errGenericTemplate, e.message ?: errUnknown)
                                }
                            }
                            loading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    loading = loading,
                    icon = AppIcon.Lock,
                    compact = false
                )

                // info row
                Row(Modifier.fillMaxWidth().clip(DsRadius.Md).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Md).padding(DsSpacing.Mid),
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                    RoundedAppIcon(AppIcon.Lock, tint = theme.mutedColor, size = 14.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.login_biometric_title), fontSize = 11.sp, color = theme.inkColor, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.login_biometric_desc), fontSize = 11.sp, color = theme.mutedColor)
                    }
                }
            }

            if (onBack != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.TextButton(onClick = onBack) { Text(stringResource(R.string.login_back), color = theme.mutedColor, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                }
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("v${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_SHA}", fontSize = 11.sp, color = theme.mutedColor)
            }
        }
    }
}

/**
 * سوییچِ زبان در صفحهٔ ورود.
 *
 * سه حالتِ اپ («سیستم»/فارسی/انگلیسی) اینجا به یک دکمهٔ ساده خلاصه شده: با هر
 * کلیک بین فارسی و انگلیسی جابه‌جا می‌شود. اگر زبان روی «سیستم» باشد، زبانِ
 * *مؤثرِ* فعلی از locale خوانده می‌شود تا کلیکِ اول دقیقاً همان چیزی را بدهد
 * که کاربر انتظار دارد (نه اینکه بی‌اثر به‌نظر برسد). انتخابِ «پیروی از سیستم»
 * همچنان در تنظیمات → ظاهر در دسترس است.
 */
@Composable
private fun LanguageToggle(
    appLanguage: String,
    onLanguageChange: (String) -> Unit,
    theme: ThemeState
) {
    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val currentIsFa = when (appLanguage) {
        "fa" -> true
        "en" -> false
        else -> isRtl
    }
    // برچسبِ دکمه = زبانی که با کلیک به آن سوییچ می‌کنیم.
    val nextLabel = if (currentIsFa) stringResource(R.string.language_en) else stringResource(R.string.language_fa)
    val switchLabel = stringResource(R.string.cd_change_language)
    Row(
        Modifier.padding(vertical = 2.dp).height(36.dp).clip(DsRadius.Sm).clickable { onLanguageChange(if (currentIsFa) "en" else "fa") }
            .background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm)
            .semantics { contentDescription = switchLabel }
            
            .padding(horizontal = DsSpacing.Mid),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
    ) {
        RoundedAppIcon(AppIcon.Language, tint = theme.mutedColor, size = 15.dp)
        Text(nextLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
    }
}

@Composable
private fun PGField(label: String, value: String, onValueChange: (String)->Unit, placeholder: String, icon: AppIcon, isPassword: Boolean = false, imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Next, onNext: (() -> Unit)? = null, error: String? = null) {
    val theme = LocalThemeState.current
    var visible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    // یک استایلِ مشترک برای متنِ واقعی و متنِ راهنما.
    //
    // ⚠️ چرا مهم است: `Text(placeholder, fontSize = 13.sp)` فقط اندازهٔ فونت را
    // عوض می‌کرد ولی `lineHeight` را از LocalTextStyle (bodyLarge = 24.sp) ارث
    // می‌برد؛ در حالی که TextStyle خودِ فیلد lineHeight تعریف‌نشده داشت (~۱۵.۶.sp
    // طبیعیِ فونت). یعنی جعبهٔ خطِ متنِ راهنما بلندتر از جعبهٔ خطِ فیلد بود و
    // کِرسر نسبت به متنِ راهنما بالاتر می‌نشست. با استایلِ واحد، هر دو دقیقاً
    // یک ارتفاعِ خط دارند.
    val fieldStyle = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, color = theme.inkColor)
    Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (error != null) GlassRed else theme.inkColor)
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(DsRadius.Md).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, if (error != null) GlassRed else theme.borderColor), DsRadius.Md)
                .padding(horizontal = DsSpacing.FieldHorizontal),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Mid)) {
                RoundedAppIcon(icon, tint = theme.mutedColor, size = 16.dp)
                androidx.compose.foundation.text.BasicTextField(
                    value = value, onValueChange = onValueChange, singleLine = true,
                    visualTransformation = if (isPassword && !visible) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text, imeAction = imeAction),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onNext = { if (onNext != null) onNext() else focusManager.moveFocus(FocusDirection.Down) }, onDone = { focusManager.clearFocus() }),
                    textStyle = fieldStyle,
                    cursorBrush = SolidColor(theme.accentPrimary),
                    modifier = Modifier.weight(1f),
                    // متنِ راهنما و متنِ ورودی روی هم و هر دو وسط‌چینِ عمودی؛
                    // قبلاً بدونِ Box کنارِ هم رها شده بودند و هم‌تراز نبودند.
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) Text(
                                placeholder,
                                style = fieldStyle.copy(color = theme.mutedColor),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            inner()
                        }
                    }
                )
                if (isPassword) {
                    Box(Modifier.padding(2.dp).size(36.dp).clip(DsRadius.Sm).clickable { visible = !visible }.background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm), contentAlignment = Alignment.Center) {
                        PasswordEyeIcon(visible = visible)
                    }
                }
            }
        }
        if (error != null) {
            Text(error, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = GlassRed)
        }
    }
}
