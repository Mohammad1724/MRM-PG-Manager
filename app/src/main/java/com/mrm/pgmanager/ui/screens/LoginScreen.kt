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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    onBack: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val theme = themeState

    // پیام‌های خطا باید *قبل از* لامبدای کلیک خوانده شوند؛ stringResource فقط در
    // بدنهٔ کامپوزبل قابل فراخوانی است، نه داخلِ coroutine.
    val errCredentials = stringResource(R.string.login_err_credentials)
    val errUrl = stringResource(R.string.login_err_url)
    val errHost = stringResource(R.string.login_err_host)
    val errTimeout = stringResource(R.string.login_err_timeout)
    val errAuth = stringResource(R.string.login_err_auth)
    val errNotFound = stringResource(R.string.login_err_not_found)
    val errUnknown = stringResource(R.string.login_err_unknown)
    val errGenericTemplate = stringResource(R.string.login_err_generic)

    val focusManager = LocalFocusManager.current
    Box(Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = DsSpacing.Screen).padding(top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // نوار بالا: فقط سوییچِ زبان. دکمهٔ تنظیمات حذف شد چون تنظیماتِ کامل
            // پس از ورود در دسترس است و اینجا فقط باعث شلوغی و ورودِ اتفاقی به
            // دیالوگِ قدیمی می‌شد.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                LanguageToggle(appLanguage = appLanguage, onLanguageChange = onLanguageChange, theme = theme)
            }

            Spacer(Modifier.height(12.dp))

            // Logo centered
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLogo(height = 56.dp)
                Text("PasarGuard", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = theme.inkColor)
                Text("MRM Manager", fontSize = 12.sp, color = theme.mutedColor, fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.login_subtitle), fontSize = 11.sp, color = theme.mutedColor)
            }

            // Card — white, subtle border, same as PG
            Column(
                Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PGField(label = stringResource(R.string.panel_address), value = url, onValueChange = { url = it }, placeholder = stringResource(R.string.panel_hint), icon = AppIcon.Link, imeAction = androidx.compose.ui.text.input.ImeAction.Next)
                PGField(label = stringResource(R.string.username), value = username, onValueChange = { username = it }, placeholder = stringResource(R.string.username), icon = AppIcon.User, imeAction = androidx.compose.ui.text.input.ImeAction.Next)
                PGField(label = stringResource(R.string.password), value = password, onValueChange = { password = it }, placeholder = stringResource(R.string.password), icon = AppIcon.Lock, isPassword = true, imeAction = androidx.compose.ui.text.input.ImeAction.Done, onNext = { focusManager.clearFocus() })

                if (error != null) {
                    Row(Modifier.fillMaxWidth().clip(DsRadius.Md).background(Color(0xFFFEE2E2)).border(BorderStroke(DsBorder.Hairline, Color(0xFFFECACA)), DsRadius.Md).padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundedAppIcon(AppIcon.Warning, tint = GlassRed, size = 16.dp)
                        Text(error!!, color = GlassRed, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    }
                }

                // Primary yellow button — توکن‌محور
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(DsRadius.Md).background(themeState.accentPrimary)
                        .clickable(enabled = !loading) {
                            if (loading) return@clickable
                            loading = true; error = null
                            scope.launch {
                                runCatching { PanelApi.login(url, username, password) }.onSuccess(onLoggedIn).onFailure { e ->
                                    error = when {
                                        e.message?.contains("Credentials required", true) == true -> errCredentials
                                        e.message?.contains("Invalid URL", true) == true -> errUrl
                                        e is java.net.UnknownHostException -> errHost
                                        e is java.net.SocketTimeoutException -> errTimeout
                                        e.message?.contains("401", true) == true -> errAuth
                                        e.message?.contains("404", true) == true -> errNotFound
                                        else -> String.format(errGenericTemplate, e.message ?: errUnknown)
                                    }
                                }
                                loading = false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Color(0xFF422006), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.sign_in), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF422006))
                }

                // info row
                Row(Modifier.fillMaxWidth().clip(DsRadius.Md).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderSubtle), DsRadius.Md).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundedAppIcon(AppIcon.Lock, tint = theme.mutedColor, size = 14.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.login_biometric_title), fontSize = 11.sp, color = theme.inkColor, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.login_biometric_desc), fontSize = 10.sp, color = theme.mutedColor)
                    }
                }
            }

            if (onBack != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.TextButton(onClick = onBack) { Text(stringResource(R.string.login_back), color = theme.mutedColor, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                }
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("v${BuildConfig.VERSION_NAME}", fontSize = 10.sp, color = theme.mutedColor)
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
        Modifier.height(36.dp).clip(DsRadius.Sm)
            .background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm)
            .semantics { contentDescription = switchLabel }
            .clickable { onLanguageChange(if (currentIsFa) "en" else "fa") }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RoundedAppIcon(AppIcon.Language, tint = theme.mutedColor, size = 15.dp)
        Text(nextLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
    }
}

@Composable
private fun PGField(label: String, value: String, onValueChange: (String)->Unit, placeholder: String, icon: AppIcon, isPassword: Boolean = false, imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Next, onNext: (() -> Unit)? = null) {
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(DsRadius.Md).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    Box(Modifier.size(36.dp).clip(DsRadius.Sm).background(theme.searchBgColor).border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm).clickable { visible = !visible }, contentAlignment = Alignment.Center) {
                        PasswordEyeIcon(visible = visible)
                    }
                }
            }
        }
    }
}
