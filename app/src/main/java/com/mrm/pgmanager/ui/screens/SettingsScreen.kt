package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.MonitoringSettings
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.dialogs.SegmentedControl
import com.mrm.pgmanager.ui.dialogs.SettingsActionRow
import com.mrm.pgmanager.ui.dialogs.SettingsCard
import com.mrm.pgmanager.ui.dialogs.SettingsStepper
import com.mrm.pgmanager.ui.dialogs.SettingsSwitchRow
import com.mrm.pgmanager.ui.theme.GlassAmber
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.LampColor
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.theme.ThemeState

/**
 * شناسهٔ پایدارِ هر بخشِ تنظیمات.
 *
 * ⚠️ چرا enum و نه رشته: نسخهٔ قبلی بخشِ فعال را به‌صورتِ **متنِ ترجمه‌شده**
 * نگه می‌داشت (`section = context.getString(...)`) و با `stringResource(...)`
 * مقایسه می‌کرد. با عوض‌کردنِ زبان، مقایسه شکست می‌خورد و تبِ فعال گم می‌شد.
 * شناسهٔ پایدار این کلاسِ باگ را ریشه‌کن می‌کند.
 */
enum class SettingsSection { APPEARANCE, MONITORING, NOTIFICATIONS, SECURITY }

/**
 * صفحهٔ تنظیمات — بازطراحی‌شده به‌صورتِ صفحهٔ کامل، هم‌سبک با بقیهٔ صفحه‌ها.
 *
 * سه ایرادی که این بازنویسی رفع می‌کند:
 *
 *  ۱. **قاطی‌شدنِ زبان.** نسخهٔ قبلی ۲۱۳ رشتهٔ فارسی را مستقیم در کد داشت و
 *     ۲۶ شرطِ `if (isFa)` برای ترجمهٔ دستی. بدتر اینکه `isFa` را از *جهتِ
 *     چیدمان* حساب می‌کرد (`LayoutDirection.Rtl`) نه از زبانِ انتخابی؛ پس
 *     روی گوشیِ فارسی با زبانِ انگلیسی، این خط‌ها فارسی می‌ماندند در حالی که
 *     `R.string`ها انگلیسی می‌شدند. حالا **همه‌چیز از `R.string` می‌آید**.
 *
 *  ۲. **ناهماهنگی با اپ.** دیالوگِ شناور با تب‌های افقی بود؛ حالا صفحهٔ کامل
 *     با همان سربرگِ کارتی، دکمهٔ بازگشت و فاصله‌گذاریِ گروه‌ها/تمپلت‌ها.
 *
 *  ۳. **گم‌شدنِ تبِ فعال** هنگام تغییر زبان — با [SettingsSection] حل شد.
 */
@Composable
fun SettingsScreen(
    themeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    onBack: () -> Unit,
    isAppLockEnabled: Boolean = false,
    onAppLockChange: (Boolean) -> Unit = {},
    monitoringSettings: MonitoringSettings = MonitoringSettings(),
    onMonitoringChange: (MonitoringSettings) -> Unit = {},
    appLockTimeout: Int = 0,
    onLockTimeoutChange: (Int) -> Unit = {},
    appLanguage: String = "system",
    onLanguageChange: (String) -> Unit = {},
    onLogout: (() -> Unit)? = null,
    appVersion: String = ""
) {
    val theme = LocalThemeState.current
    val backLabel = stringResource(R.string.cd_back)
    var section by rememberSaveable { mutableStateOf(SettingsSection.APPEARANCE) }

    Column(
        Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding()
            .padding(start = DsSpacing.Screen, end = DsSpacing.Screen, top = 14.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── سربرگ: دکمهٔ بازگشت + عنوان (هم‌سبکِ سربرگِ گروه‌ها و تمپلت‌ها)
        Row(
            Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // با Alignment/padding نسبی، در فارسی خودکار سمت راست می‌نشیند.
            Box(
                Modifier.size(34.dp).clip(DsRadius.Sm).background(theme.searchBgColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm)
                    .semantics { contentDescription = backLabel }
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { RoundedAppIcon(AppIcon.Prev, tint = theme.mutedColor, size = 16.dp) }

            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_title), fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, color = theme.inkColor
                )
                Text(stringResource(R.string.appearance_desc), fontSize = 10.sp, color = theme.mutedColor)
            }
        }

        // ── نوار بخش‌ها
        val tabs = listOf(
            SettingsSection.APPEARANCE to stringResource(R.string.appearance),
            SettingsSection.MONITORING to stringResource(R.string.monitoring_title),
            SettingsSection.NOTIFICATIONS to stringResource(R.string.notifications_title),
            SettingsSection.SECURITY to stringResource(R.string.security_title)
        )
        Row(
            Modifier.fillMaxWidth().clip(DsRadius.Xl).background(theme.searchBgColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xl)
                .horizontalScroll(rememberScrollState())
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEach { (id, label) ->
                val selected = section == id
                Box(
                    Modifier.height(34.dp).clip(DsRadius.Md)
                        .background(if (selected) theme.accentPrimary.copy(.78f) else Color.Transparent)
                        .clickable { section = id }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = if (selected) Color(0xFF202124) else theme.mutedColor, maxLines = 1
                    )
                }
            }
        }

        // ── محتوای بخشِ انتخاب‌شده
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            when (section) {
                SettingsSection.APPEARANCE -> AppearanceSection(
                    themeState = themeState,
                    onThemeChange = onThemeChange,
                    appLanguage = appLanguage,
                    onLanguageChange = onLanguageChange
                )

                SettingsSection.MONITORING -> MonitoringSection(
                    monitoringSettings = monitoringSettings,
                    onMonitoringChange = onMonitoringChange
                )

                SettingsSection.NOTIFICATIONS -> NotificationsSection(
                    monitoringSettings = monitoringSettings,
                    onMonitoringChange = onMonitoringChange
                )

                SettingsSection.SECURITY -> SecuritySection(
                    isAppLockEnabled = isAppLockEnabled,
                    onAppLockChange = onAppLockChange,
                    appLockTimeout = appLockTimeout,
                    onLockTimeoutChange = onLockTimeoutChange,
                    onLogout = onLogout
                )
            }
            AboutFooter(appVersion = appVersion)
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** پاورقیِ «درباره»: نسخهٔ برنامه و لینکِ مخزن. */
@Composable
private fun AboutFooter(appVersion: String) {
    val theme = LocalThemeState.current
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.set_version, appVersion.ifBlank { "—" }),
            fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = theme.mutedColor, modifier = Modifier.weight(1f)
        )
        Row(
            Modifier.clip(DsRadius.Sm).background(theme.searchBgColor)
                .clickable {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/Mohammad1724/MRM-PG-Manager")
                            )
                        )
                    }
                }
                .padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RoundedAppIcon(AppIcon.OpenNew, tint = theme.mutedColor, size = 11.dp)
            Text(
                stringResource(R.string.set_github), fontSize = 10.sp,
                fontWeight = FontWeight.Bold, color = theme.mutedColor
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  ظاهر
// ─────────────────────────────────────────────────────────────

@Composable
private fun AppearanceSection(
    themeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    appLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    val theme = LocalThemeState.current

    SettingsCard(stringResource(R.string.language), AppIcon.Settings) {
        SegmentedControl(
            options = listOf(
                stringResource(R.string.language_system),
                stringResource(R.string.language_fa),
                stringResource(R.string.language_en)
            ),
            selectedIndex = when (appLanguage) { "fa" -> 1; "en" -> 2; else -> 0 },
            onSelect = { idx ->
                onLanguageChange(when (idx) { 1 -> "fa"; 2 -> "en"; else -> "system" })
            }
        )
        Text(stringResource(R.string.language_desc), fontSize = 11.sp, color = theme.mutedColor)
    }

    SettingsCard(stringResource(R.string.set_display_mode), AppIcon.Palette) {
        SegmentedControl(
            options = listOf(
                stringResource(R.string.set_mode_light),
                stringResource(R.string.set_mode_dark),
                stringResource(R.string.set_mode_auto)
            ),
            selectedIndex = if (themeState.followSystem) 2 else if (themeState.isDark) 1 else 0,
            onSelect = { index ->
                when (index) {
                    0 -> onThemeChange(themeState.copy(followSystem = false, isDark = false))
                    1 -> onThemeChange(themeState.copy(followSystem = false, isDark = true))
                    else -> onThemeChange(themeState.copy(followSystem = true))
                }
            },
            icons = listOf(AppIcon.LightMode, AppIcon.DarkMode, AppIcon.AutoMode)
        )
        Text(stringResource(R.string.set_mode_desc), fontSize = 11.sp, color = theme.mutedColor)
    }

    SettingsCard(stringResource(R.string.set_primary_color), AppIcon.Palette) {
        LampColor.values().toList().chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { lamp ->
                    val selected = themeState.customColor == null && themeState.lamp == lamp
                    LampSwatch(lamp = lamp, selected = selected, modifier = Modifier.weight(1f)) {
                        onThemeChange(themeState.copy(lamp = lamp, customColor = null))
                    }
                }
                if (rowItems.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }

    CustomColorCard(themeState = themeState, onThemeChange = onThemeChange)

    ThemePreviewCard(themeState = themeState)

    if (themeState.isDark) {
        SettingsCard(stringResource(R.string.set_amoled), AppIcon.DarkMode) {
            SettingsSwitchRow(
                stringResource(R.string.set_amoled_switch),
                stringResource(R.string.set_amoled_desc),
                themeState.amoledDark
            ) { onThemeChange(themeState.copy(amoledDark = it)) }
        }
    } else {
        // در حالت روشن این گزینه بی‌معنی است؛ به‌جای مخفی‌کردن، دلیلش را می‌گوییم.
        Column(
            Modifier.fillMaxWidth().clip(DsRadius.Md).background(theme.searchBgColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundedAppIcon(AppIcon.DarkMode, tint = theme.mutedColor, size = 14.dp)
                Text(
                    stringResource(R.string.set_amoled), fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, color = theme.mutedColor
                )
            }
            Text(stringResource(R.string.set_amoled_disabled), fontSize = 10.sp, color = theme.mutedColor)
        }
    }
}

/** یک خانهٔ انتخابِ رنگِ آماده. */
@Composable
private fun LampSwatch(
    lamp: LampColor,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val theme = LocalThemeState.current
    Row(
        modifier.height(44.dp).clip(DsRadius.Md)
            .background(if (selected) lamp.primary.copy(.14f) else theme.searchBgColor)
            .border(
                BorderStroke(
                    if (selected) 1.2.dp else DsBorder.Hairline,
                    if (selected) lamp.primary.copy(.55f) else theme.borderColor
                ),
                DsRadius.Md
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(20.dp).clip(DsRadius.Xs)
                .background(Brush.linearGradient(listOf(lamp.primary, lamp.light))),
            contentAlignment = Alignment.Center
        ) {
            if (selected) RoundedAppIcon(AppIcon.Check, tint = Color.White, size = 12.dp)
        }
        // نامِ رنگ از منابع می‌آید، نه از فیلدهای label/labelFa؛ نسخهٔ قبلی
        // بینشان با LayoutDirection انتخاب می‌کرد که با زبان یکی نیست.
        Text(
            stringResource(
                when (lamp) {
                    LampColor.GOLD -> R.string.lamp_gold
                    LampColor.MAGENTA -> R.string.lamp_magenta
                    LampColor.TURQUOISE -> R.string.lamp_turquoise
                    LampColor.SKY_BLUE -> R.string.lamp_sky_blue
                    LampColor.VIOLET -> R.string.lamp_violet
                    LampColor.EMERALD -> R.string.lamp_emerald
                }
            ),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) theme.inkColor else theme.mutedColor,
            maxLines = 1
        )
    }
}

/** انتخابِ رنگِ دلخواه با اسلایدرهای HSV و پیش‌نمایشِ زنده. */
@Composable
private fun CustomColorCard(themeState: ThemeState, onThemeChange: (ThemeState) -> Unit) {
    val theme = LocalThemeState.current
    val activeCustom = themeState.customColor

    val seed = remember(activeCustom) {
        val out = FloatArray(3)
        if (activeCustom != null) android.graphics.Color.colorToHSV(activeCustom.toArgb(), out)
        else { out[0] = 42f; out[1] = 0.85f; out[2] = 0.96f }
        out
    }
    var hue by remember(activeCustom) { mutableStateOf(seed[0]) }
    var sat by remember(activeCustom) { mutableStateOf(seed[1].coerceIn(0.25f, 1f)) }
    var bright by remember(activeCustom) { mutableStateOf(seed[2].coerceIn(0.45f, 1f)) }
    val preview = Color.hsv(hue, sat, bright)

    SettingsCard(
        stringResource(R.string.set_custom_color),
        AppIcon.Palette,
        accent = activeCustom ?: theme.accentPrimary
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(38.dp).clip(DsRadius.Lg)
                    .background(Brush.linearGradient(listOf(preview, preview.copy(alpha = .6f))))
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg),
                contentAlignment = Alignment.Center
            ) {
                if (activeCustom != null) RoundedAppIcon(AppIcon.Check, tint = Color.White, size = 15.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        if (activeCustom != null) R.string.set_custom_active else R.string.set_custom_hint
                    ),
                    fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = theme.inkColor
                )
                Text(stringResource(R.string.set_custom_applied), fontSize = 10.sp, color = theme.mutedColor)
            }
        }

        ColorSlider(stringResource(R.string.set_hue), hue, 0f..360f, preview, { hue = it }) {
            onThemeChange(themeState.copy(customColor = Color.hsv(hue, sat, bright)))
        }
        ColorSlider(stringResource(R.string.set_saturation), sat, 0.25f..1f, preview, { sat = it }) {
            onThemeChange(themeState.copy(customColor = Color.hsv(hue, sat, bright)))
        }
        ColorSlider(stringResource(R.string.set_value), bright, 0.45f..1f, preview, { bright = it }) {
            onThemeChange(themeState.copy(customColor = Color.hsv(hue, sat, bright)))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                Modifier.weight(1f).height(30.dp).clip(DsRadius.Sm)
                    .background(preview.copy(.18f))
                    .border(BorderStroke(1.dp, preview.copy(.4f)), DsRadius.Sm)
                    .clickable { onThemeChange(themeState.copy(customColor = preview)) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.set_apply_color), fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, color = theme.inkColor
                )
            }
            if (activeCustom != null) {
                Box(
                    Modifier.weight(1f).height(30.dp).clip(DsRadius.Sm)
                        .background(GlassRed.copy(.10f))
                        .border(BorderStroke(1.dp, GlassRed.copy(.3f)), DsRadius.Sm)
                        .clickable { onThemeChange(themeState.copy(customColor = null)) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.set_remove_color), fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, color = GlassRed
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    onChange: (Float) -> Unit,
    onDone: () -> Unit
) {
    val theme = LocalThemeState.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, fontSize = 10.sp, color = theme.mutedColor, modifier = Modifier.width(56.dp))
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            onValueChangeFinished = onDone,
            colors = SliderDefaults.colors(
                thumbColor = accent, activeTrackColor = accent,
                inactiveTrackColor = theme.searchBgColor
            ),
            modifier = Modifier.weight(1f).height(22.dp)
        )
    }
}

@Composable
private fun ThemePreviewCard(themeState: ThemeState) {
    val theme = LocalThemeState.current
    SettingsCard(
        stringResource(R.string.set_theme_preview),
        AppIcon.Palette,
        accent = themeState.accentPrimary
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                Modifier.weight(1f).clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(22.dp).clip(DsRadius.Sm)
                            .background(theme.accentPrimary.copy(if (theme.isDark) 0.18f else 0.12f))
                            .border(BorderStroke(DsBorder.Hairline, theme.accentPrimary.copy(0.25f)), DsRadius.Sm),
                        contentAlignment = Alignment.Center
                    ) { RoundedAppIcon(AppIcon.Gauge, tint = theme.accentPrimary, size = 12.dp) }
                    Text(
                        stringResource(R.string.set_sample_card), fontSize = 11.sp,
                        color = theme.inkColor, fontWeight = FontWeight.Medium
                    )
                }
                Text(stringResource(R.string.set_preview_desc), fontSize = 10.sp, color = theme.mutedColor)
            }
            Box(
                Modifier.weight(1f).height(66.dp).clip(DsRadius.Lg).background(theme.searchBgColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.search), fontSize = 11.sp, color = theme.mutedColor)
            }
        }
        Text(stringResource(R.string.set_preview_hint), fontSize = 9.sp, color = theme.mutedColor)
    }
}

// ─────────────────────────────────────────────────────────────
//  پایش
// ─────────────────────────────────────────────────────────────

@Composable
private fun MonitoringSection(
    monitoringSettings: MonitoringSettings,
    onMonitoringChange: (MonitoringSettings) -> Unit
) {
    val theme = LocalThemeState.current

    SettingsCard(stringResource(R.string.set_auto_monitor), AppIcon.Tune) {
        SettingsSwitchRow(
            stringResource(R.string.set_auto_refresh),
            stringResource(R.string.set_auto_refresh_desc),
            monitoringSettings.autoRefreshEnabled
        ) { onMonitoringChange(monitoringSettings.copy(autoRefreshEnabled = it)) }

        SettingsStepper(
            stringResource(R.string.set_interval),
            monitoringSettings.refreshIntervalSeconds,
            stringResource(R.string.set_unit_seconds),
            5..3600, step = 5,
            enabled = monitoringSettings.autoRefreshEnabled
        ) { onMonitoringChange(monitoringSettings.copy(refreshIntervalSeconds = it)) }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.set_scope), fontSize = 11.sp,
                fontWeight = FontWeight.Bold, color = theme.inkColor
            )
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.set_scope_dashboard),
                    stringResource(R.string.set_scope_app)
                ),
                selectedIndex = if (monitoringSettings.refreshWhileAppOpen) 1 else 0
            ) { index -> onMonitoringChange(monitoringSettings.copy(refreshWhileAppOpen = index == 1)) }
        }

        SettingsSwitchRow(
            stringResource(R.string.set_offline_cache),
            stringResource(R.string.set_offline_desc),
            monitoringSettings.offlineCacheEnabled
        ) { onMonitoringChange(monitoringSettings.copy(offlineCacheEnabled = it)) }
    }

    SettingsCard(stringResource(R.string.set_reset), AppIcon.Reset, accent = GlassAmber) {
        SettingsActionRow(
            stringResource(R.string.set_reset_defaults),
            stringResource(R.string.set_reset_desc),
            AppIcon.Reset,
            GlassAmber
        ) { onMonitoringChange(MonitoringSettings()) }
    }
}

// ─────────────────────────────────────────────────────────────
//  اعلان‌ها
// ─────────────────────────────────────────────────────────────

@Composable
private fun NotificationsSection(
    monitoringSettings: MonitoringSettings,
    onMonitoringChange: (MonitoringSettings) -> Unit
) {
    val theme = LocalThemeState.current
    val master = monitoringSettings.notificationsEnabled
    val pct = stringResource(R.string.set_unit_percent)

    SettingsCard(stringResource(R.string.set_general), AppIcon.Bell) {
        SettingsSwitchRow(
            stringResource(R.string.set_notif_enable),
            stringResource(R.string.set_notif_enable_desc),
            master
        ) { onMonitoringChange(monitoringSettings.copy(notificationsEnabled = it)) }

        SettingsSwitchRow(
            stringResource(R.string.set_notif_user_ops),
            stringResource(R.string.set_notif_user_ops_desc),
            monitoringSettings.notifyUserActions, enabled = master
        ) { onMonitoringChange(monitoringSettings.copy(notifyUserActions = it)) }
    }

    SettingsCard(stringResource(R.string.set_sub_alerts), AppIcon.Users) {
        SettingsSwitchRow(
            stringResource(R.string.set_notif_limited),
            checked = monitoringSettings.notifyLimited, enabled = master
        ) { onMonitoringChange(monitoringSettings.copy(notifyLimited = it)) }

        SettingsSwitchRow(
            stringResource(R.string.set_notif_expired),
            checked = monitoringSettings.notifyExpired, enabled = master
        ) { onMonitoringChange(monitoringSettings.copy(notifyExpired = it)) }

        SettingsSwitchRow(
            stringResource(R.string.set_notif_near_limit),
            checked = monitoringSettings.notifyNearLimit, enabled = master
        ) { onMonitoringChange(monitoringSettings.copy(notifyNearLimit = it)) }

        SettingsStepper(
            stringResource(R.string.set_near_limit_threshold),
            monitoringSettings.nearLimitPercent, pct, 10..100, step = 5,
            enabled = master && monitoringSettings.notifyNearLimit
        ) { onMonitoringChange(monitoringSettings.copy(nearLimitPercent = it)) }

        SettingsSwitchRow(
            stringResource(R.string.set_notif_near_expiry),
            checked = monitoringSettings.notifyNearExpiry, enabled = master
        ) { onMonitoringChange(monitoringSettings.copy(notifyNearExpiry = it)) }

        SettingsStepper(
            stringResource(R.string.set_expiry_warn_from),
            monitoringSettings.nearExpiryDays,
            stringResource(R.string.set_unit_days_before), 1..30,
            enabled = master && monitoringSettings.notifyNearExpiry
        ) { onMonitoringChange(monitoringSettings.copy(nearExpiryDays = it)) }
    }

    SettingsCard(stringResource(R.string.set_debtors), AppIcon.Warning, accent = GlassRed) {
        SettingsSwitchRow(
            stringResource(R.string.set_notif_debtor),
            checked = monitoringSettings.notifyDebtor, enabled = master
        ) { onMonitoringChange(monitoringSettings.copy(notifyDebtor = it)) }

        SettingsSwitchRow(
            stringResource(R.string.set_notif_debtor_overdue),
            checked = monitoringSettings.notifyDebtorOverdue, enabled = master
        ) { onMonitoringChange(monitoringSettings.copy(notifyDebtorOverdue = it)) }

        Text(stringResource(R.string.set_debtor_desc), fontSize = 10.sp, color = theme.mutedColor)
    }

    SettingsCard(stringResource(R.string.set_health), AppIcon.Warning, accent = GlassRed) {
        val healthEnabled = master && monitoringSettings.notifySystemHealth

        SettingsSwitchRow(
            stringResource(R.string.set_notif_health),
            stringResource(R.string.set_notif_health_desc),
            monitoringSettings.notifySystemHealth, enabled = master
        ) { onMonitoringChange(monitoringSettings.copy(notifySystemHealth = it)) }

        SettingsStepper(
            stringResource(R.string.set_cpu_threshold),
            monitoringSettings.cpuThreshold, pct, 50..100, step = 5, enabled = healthEnabled
        ) { onMonitoringChange(monitoringSettings.copy(cpuThreshold = it)) }

        SettingsStepper(
            stringResource(R.string.set_ram_threshold),
            monitoringSettings.ramThreshold, pct, 50..100, step = 5, enabled = healthEnabled
        ) { onMonitoringChange(monitoringSettings.copy(ramThreshold = it)) }

        SettingsStepper(
            stringResource(R.string.set_disk_threshold),
            monitoringSettings.diskThreshold, pct, 50..100, step = 5, enabled = healthEnabled
        ) { onMonitoringChange(monitoringSettings.copy(diskThreshold = it)) }

        SettingsSwitchRow(
            stringResource(R.string.set_notif_panel_offline),
            checked = monitoringSettings.notifyPanelOffline, enabled = master
        ) { onMonitoringChange(monitoringSettings.copy(notifyPanelOffline = it)) }

        SettingsSwitchRow(
            stringResource(R.string.set_notif_node_offline),
            checked = monitoringSettings.notifyNodeOffline, enabled = master
        ) { onMonitoringChange(monitoringSettings.copy(notifyNodeOffline = it)) }

        SettingsSwitchRow(
            stringResource(R.string.set_notif_capacity),
            stringResource(R.string.set_notif_capacity_desc),
            checked = monitoringSettings.notifyCapacity, enabled = master
        ) { onMonitoringChange(monitoringSettings.copy(notifyCapacity = it)) }

        SettingsStepper(
            stringResource(R.string.set_capacity_limit),
            monitoringSettings.capacityOnlineLimit,
            stringResource(R.string.set_unit_users), 10..10000, step = 10,
            enabled = master && monitoringSettings.notifyCapacity
        ) { onMonitoringChange(monitoringSettings.copy(capacityOnlineLimit = it)) }
    }
}

// ─────────────────────────────────────────────────────────────
//  امنیت
// ─────────────────────────────────────────────────────────────

@Composable
private fun SecuritySection(
    isAppLockEnabled: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    appLockTimeout: Int,
    onLockTimeoutChange: (Int) -> Unit,
    onLogout: (() -> Unit)?
) {
    val theme = LocalThemeState.current
    val timeouts = listOf(0, 60, 300, 900)

    SettingsCard(stringResource(R.string.set_app_lock), AppIcon.Lock, accent = GlassGreen) {
        SettingsSwitchRow(
            stringResource(R.string.set_app_lock_switch),
            stringResource(R.string.set_app_lock_desc),
            isAppLockEnabled
        ) { onAppLockChange(it) }

        Text(
            stringResource(if (isAppLockEnabled) R.string.set_app_lock_on else R.string.set_app_lock_off),
            fontSize = 11.sp, color = theme.mutedColor
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.set_lock_timeout), fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isAppLockEnabled) theme.inkColor else theme.mutedColor
            )
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.set_timeout_instant),
                    stringResource(R.string.set_timeout_1m),
                    stringResource(R.string.set_timeout_5m),
                    stringResource(R.string.set_timeout_15m)
                ),
                selectedIndex = timeouts.indexOf(appLockTimeout).coerceAtLeast(0),
                enabled = isAppLockEnabled
            ) { index -> onLockTimeoutChange(timeouts[index]) }

            if (isAppLockEnabled) {
                Text(
                    stringResource(R.string.set_lock_timeout_hint),
                    fontSize = 8.5.sp, color = theme.mutedColor
                )
            }
        }
    }

    if (onLogout != null) {
        SettingsCard(stringResource(R.string.set_account), AppIcon.Logout, accent = GlassRed) {
            SettingsActionRow(
                stringResource(R.string.set_logout),
                stringResource(R.string.set_logout_desc),
                AppIcon.Logout,
                GlassRed
            ) { onLogout() }
        }
    }
}
