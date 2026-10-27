package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.HeaderPlacement
import com.mrm.pgmanager.data.model.PanelCustomVariable
import com.mrm.pgmanager.data.model.PanelGeneralSection
import com.mrm.pgmanager.data.model.PanelHwidSection
import com.mrm.pgmanager.data.model.PanelNotificationSection
import com.mrm.pgmanager.data.model.PanelSettingsErrors
import com.mrm.pgmanager.data.model.PanelSubscriptionSection
import com.mrm.pgmanager.data.model.ShadowsocksMethod
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.PGPrimaryButton
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.dialogs.ChipSelector
import com.mrm.pgmanager.ui.dialogs.CompactGlassField
import com.mrm.pgmanager.ui.dialogs.SettingsCard
import com.mrm.pgmanager.ui.dialogs.SettingsInfoRow
import com.mrm.pgmanager.ui.dialogs.SettingsStepper
import com.mrm.pgmanager.ui.dialogs.SettingsSwitchRow
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.theme.LocalThemeState

/**
 * بدنهٔ چهار تبِ «تنظیماتِ پنل».
 *
 * ### قاعدهٔ مشترکِ هر تب
 *  • یک **پیش‌نویس** (`draft`) از بخشِ سرور نگه داشته می‌شود؛ ذخیره فقط با
 *    «ذخیره» انجام می‌شود و «بازگردانی» پیش‌نویس را به مقدارِ فعلیِ سرور برمی‌گرداند.
 *    این‌طور یک سوئیچِ اشتباهی بی‌درنگ روی سرور نمی‌نشیند.
 *  • اعتبارسنجیِ محلی ([PanelSettingsErrors]) پیش از درخواست اجرا می‌شود؛ ولی
 *    حرفِ آخر با پنل است و ۴۲۲/۴۰۳ با `detail` خودش نشان داده می‌شود.
 *  • بدونِ مجوزِ `settings.update` فیلدها **فقط‌خواندنی** می‌شوند (به‌جای پنهان‌کردن،
 *    تا ادمینِ محدود هم بتواند ببیند سرور چه تنظیمی دارد).
 */

// ── تبِ ۱: عمومی ─────────────────────────────────────────────────────────────

@Composable
internal fun GeneralSettingsTab(
    section: PanelGeneralSection,
    canEdit: Boolean,
    saving: Boolean,
    onSave: (PanelGeneralSection) -> Unit
) {
    val theme = LocalThemeState.current
    var draft by remember(section) { mutableStateOf(section) }
    var error by remember(section) { mutableStateOf<String?>(null) }

    val title = stringResource(R.string.ps_general_card)
    val methodLabel = stringResource(R.string.ps_default_method)
    val holdLabel = stringResource(R.string.ps_on_hold_action)
    val holdHint = stringResource(R.string.ps_on_hold_hint)
    val varsTitle = stringResource(R.string.ps_vars_card)
    val varsHint = stringResource(R.string.ps_vars_hint)
    val varKey = stringResource(R.string.ps_var_key)
    val varValue = stringResource(R.string.ps_var_value)
    val addLabel = stringResource(R.string.ps_var_add)
    val emptyLabel = stringResource(R.string.ps_vars_empty)
    val deleteLabel = stringResource(R.string.ps_var_delete)
    val holdLabels = listOf(
        stringResource(R.string.ps_on_hold_activate),
        stringResource(R.string.ps_on_hold_disable),
        stringResource(R.string.ps_on_hold_delete)
    )
    val holdValues = listOf("activate", "disable", "delete")

    TabScaffold {
        SettingsCard(title = title, icon = AppIcon.Tune) {
            InfraFieldLabel(methodLabel)
            ChipSelector(
                values = ShadowsocksMethod.all,
                labels = ShadowsocksMethod.all,
                selected = draft.defaultMethod,
                onSelect = { if (canEdit) draft = draft.copy(defaultMethod = it) }
            )
            InfraFieldLabel(holdLabel)
            ChipSelector(
                values = holdValues,
                labels = holdLabels,
                selected = draft.onHoldTimeoutAction,
                onSelect = { if (canEdit) draft = draft.copy(onHoldTimeoutAction = it) }
            )
            Text(holdHint, fontSize = 10.sp, color = theme.mutedColor)
        }

        SettingsCard(title = varsTitle, icon = AppIcon.Note) {
            Text(varsHint, fontSize = 10.sp, color = theme.mutedColor)
            if (draft.customVariables.isEmpty()) {
                Text(emptyLabel, fontSize = 11.sp, color = theme.mutedColor)
            }
            draft.customVariables.forEachIndexed { index, variable ->
                if (canEdit) VariableRow(
                    variable = variable,
                    keyLabel = varKey,
                    valueLabel = varValue,
                    deleteLabel = deleteLabel,
                    onChange = { updated ->
                        draft = draft.copy(
                            customVariables = draft.customVariables.toMutableList().also { it[index] = updated }
                        )
                    },
                    onDelete = {
                        draft = draft.copy(
                            customVariables = draft.customVariables.filterIndexed { i, _ -> i != index }
                        )
                    }
                ) else SettingsInfoRow(
                    label = variable.normalizedKey().ifBlank { varKey },
                    value = variable.value
                )
            }
            if (canEdit) PGSecondaryButton(
                text = addLabel,
                icon = AppIcon.Add,
                onClick = {
                    draft = draft.copy(customVariables = draft.customVariables + PanelCustomVariable(""))
                }
            )
        }

        DraftFooter(
            dirty = draft != section,
            canEdit = canEdit,
            saving = saving,
            error = error,
            onSave = {
                val problem = draft.validate()
                error = problem
                if (problem == null) onSave(draft)
            },
            onRevert = {
                draft = section
                error = null
            }
        )
    }
}

// ── تبِ ۲: اشتراک ────────────────────────────────────────────────────────────

@Composable
internal fun SubscriptionSettingsTab(
    section: PanelSubscriptionSection,
    canEdit: Boolean,
    saving: Boolean,
    onSave: (PanelSubscriptionSection) -> Unit
) {
    val theme = LocalThemeState.current
    var draft by remember(section) { mutableStateOf(section) }
    var error by remember(section) { mutableStateOf<String?>(null) }

    val linkTitle = stringResource(R.string.ps_sub_link_card)
    val prefixLabel = stringResource(R.string.ps_url_prefix)
    val prefixHint = stringResource(R.string.ps_url_prefix_hint)
    val intervalLabel = stringResource(R.string.ps_update_interval)
    val profileLabel = stringResource(R.string.ps_profile_title)
    val supportLabel = stringResource(R.string.ps_support_url)
    val announceTitle = stringResource(R.string.ps_announce_card)
    val announceLabel = stringResource(R.string.ps_announce)
    val announceUrlLabel = stringResource(R.string.ps_announce_url)
    val headersTitle = stringResource(R.string.ps_headers_card)
    val placementLabel = stringResource(R.string.ps_header_placement)
    val placementLabels = listOf(
        stringResource(R.string.ps_header_header),
        stringResource(R.string.ps_header_body_start),
        stringResource(R.string.ps_header_body_end)
    )
    val behaviourTitle = stringResource(R.string.ps_behavior_card)
    val allowBrowser = stringResource(R.string.ps_allow_browser)
    val allowBrowserHint = stringResource(R.string.ps_allow_browser_hint)
    val disableTemplate = stringResource(R.string.ps_disable_template)
    val disableTemplateHint = stringResource(R.string.ps_disable_template_hint)
    val randomize = stringResource(R.string.ps_randomize_order)
    val randomizeHint = stringResource(R.string.ps_randomize_order_hint)
    val formatsTitle = stringResource(R.string.ps_formats_card)
    val formatsHint = stringResource(R.string.ps_formats_hint)
    val webOnly = stringResource(R.string.ps_web_only_hint)
    val headersCountLabel = stringResource(R.string.ps_headers_count)
    val rulesCountLabel = stringResource(R.string.ps_rules_count)
    val appsCountLabel = stringResource(R.string.ps_apps_count)

    TabScaffold {
        SettingsCard(title = linkTitle, icon = AppIcon.Link) {
            LabeledField(
                label = prefixLabel,
                value = draft.urlPrefix,
                placeholder = "sub",
                enabled = canEdit,
                onChange = { draft = draft.copy(urlPrefix = it) }
            )
            Text(prefixHint, fontSize = 10.sp, color = theme.mutedColor)
            if (canEdit) SettingsStepper(
                label = intervalLabel,
                value = draft.updateInterval.coerceIn(1, 168),
                unit = "h",
                range = 1..168,
                onChange = { draft = draft.copy(updateInterval = it) }
            ) else SettingsInfoRow(intervalLabel, draft.updateInterval.toString())
            LabeledField(
                label = profileLabel,
                value = draft.profileTitle,
                enabled = canEdit,
                onChange = { draft = draft.copy(profileTitle = it) }
            )
            LabeledField(
                label = supportLabel,
                value = draft.supportUrl,
                placeholder = "https://t.me/…",
                enabled = canEdit,
                keyboardType = KeyboardType.Uri,
                onChange = { draft = draft.copy(supportUrl = it) }
            )
        }

        SettingsCard(title = announceTitle, icon = AppIcon.Note) {
            LabeledField(
                label = announceLabel,
                value = draft.announce,
                enabled = canEdit,
                onChange = { draft = draft.copy(announce = it.take(128)) }
            )
            Text("${draft.announce.length}/128", fontSize = 10.sp, color = theme.mutedColor)
            LabeledField(
                label = announceUrlLabel,
                value = draft.announceUrl,
                placeholder = "https://…",
                enabled = canEdit,
                keyboardType = KeyboardType.Uri,
                onChange = { draft = draft.copy(announceUrl = it) }
            )
        }

        SettingsCard(title = headersTitle, icon = AppIcon.GridView) {
            InfraFieldLabel(placementLabel)
            ChipSelector(
                values = HeaderPlacement.all,
                labels = placementLabels,
                selected = draft.headerPlacement,
                onSelect = { if (canEdit) draft = draft.copy(headerPlacement = it) }
            )
            // هدرهای سفارشی آرایهٔ کلید/مقدارند و در پنلِ وب ویرایش می‌شوند؛ اینجا
            // فقط می‌گوییم چند تا هست تا ادمین بداند چیزی از چشمش پنهان نیست.
            val headerCount = draft.raw?.optJSONObject("response_headers")?.length() ?: 0
            if (headerCount > 0) SettingsInfoRow(label = headersCountLabel, value = "$headerCount")
            Text(webOnly, fontSize = 10.sp, color = theme.mutedColor)
        }

        SettingsCard(title = behaviourTitle, icon = AppIcon.Tune) {
            SettingsSwitchRow(
                title = allowBrowser,
                subtitle = allowBrowserHint,
                checked = draft.allowBrowserConfig,
                enabled = canEdit,
                onChange = { draft = draft.copy(allowBrowserConfig = it) }
            )
            SettingsSwitchRow(
                title = disableTemplate,
                subtitle = disableTemplateHint,
                checked = draft.disableSubTemplate,
                enabled = canEdit,
                onChange = { draft = draft.copy(disableSubTemplate = it) }
            )
            SettingsSwitchRow(
                title = randomize,
                subtitle = randomizeHint,
                checked = draft.randomizeOrder,
                enabled = canEdit,
                onChange = { draft = draft.copy(randomizeOrder = it) }
            )
        }

        SettingsCard(title = formatsTitle, icon = AppIcon.Template) {
            Text(formatsHint, fontSize = 10.sp, color = theme.mutedColor)
            draft.manualFormats.keys.forEach { key ->
                val on = draft.manualFormats[key] ?: true
                FormatToggleRow(
                    name = formatLabel(key),
                    checked = on,
                    enabled = canEdit,
                    onChange = { draft = draft.copy(manualFormats = draft.manualFormats + (key to it)) }
                )
            }
            if (draft.rulesCount > 0) SettingsInfoRow(label = rulesCountLabel, value = "${draft.rulesCount}")
            if (draft.applicationsCount > 0) SettingsInfoRow(label = appsCountLabel, value = "${draft.applicationsCount}")
        }

        DraftFooter(
            dirty = draft != section,
            canEdit = canEdit,
            saving = saving,
            error = error,
            onSave = {
                val problem = draft.validate()
                error = problem
                if (problem == null) onSave(draft)
            },
            onRevert = {
                draft = section
                error = null
            }
        )
    }
}

// ── تبِ ۳: دستگاه‌ها (HWID) ──────────────────────────────────────────────────

@Composable
internal fun HwidSettingsTab(
    section: PanelHwidSection,
    canEdit: Boolean,
    saving: Boolean,
    onSave: (PanelHwidSection) -> Unit
) {
    val theme = LocalThemeState.current
    var draft by remember(section) { mutableStateOf(section) }
    // سقف‌ها اختیاری‌اند (null = بی‌حد)؛ برای همین به‌صورت متن نگه داشته می‌شوند
    // تا «خالی» از «صفر» قابلِ تفکیک بماند.
    var fallback by remember(section) { mutableStateOf(section.fallbackLimit?.toString() ?: "") }
    var minLimit by remember(section) { mutableStateOf(section.minLimit?.toString() ?: "") }
    var maxLimit by remember(section) { mutableStateOf(section.maxLimit?.toString() ?: "") }
    var error by remember(section) { mutableStateOf<String?>(null) }

    val title = stringResource(R.string.ps_hwid_card)
    val enabledLabel = stringResource(R.string.ps_hwid_enabled)
    val enabledHint = stringResource(R.string.ps_hwid_enabled_hint)
    val forcedLabel = stringResource(R.string.ps_hwid_forced)
    val forcedHint = stringResource(R.string.ps_hwid_forced_hint)
    val manualLabel = stringResource(R.string.ps_hwid_manual)
    val manualHint = stringResource(R.string.ps_hwid_manual_hint)
    val fallbackLabel = stringResource(R.string.ps_hwid_fallback)
    val minLabel = stringResource(R.string.ps_hwid_min)
    val maxLabel = stringResource(R.string.ps_hwid_max)
    val unlimited = stringResource(R.string.ps_hwid_unlimited)
    val hint = stringResource(R.string.ps_hwid_hint)

    fun draftNow(): PanelHwidSection = draft.copy(
        fallbackLimit = fallback.toIntOrNull()?.coerceAtLeast(0),
        minLimit = minLimit.toIntOrNull()?.coerceAtLeast(0),
        maxLimit = maxLimit.toIntOrNull()?.coerceAtLeast(0)
    )

    TabScaffold {
        SettingsCard(title = title, icon = AppIcon.Device) {
            SettingsSwitchRow(
                title = enabledLabel,
                subtitle = enabledHint,
                checked = draft.enabled,
                enabled = canEdit,
                onChange = { draft = draft.copy(enabled = it) }
            )
            SettingsSwitchRow(
                title = forcedLabel,
                subtitle = forcedHint,
                checked = draft.forced,
                enabled = canEdit && draft.enabled,
                onChange = { draft = draft.copy(forced = it) }
            )
            SettingsSwitchRow(
                title = manualLabel,
                subtitle = manualHint,
                checked = draft.requireHwidForManualSub,
                enabled = canEdit && draft.enabled,
                onChange = { draft = draft.copy(requireHwidForManualSub = it) }
            )
            LabeledField(
                label = fallbackLabel,
                value = fallback,
                placeholder = unlimited,
                enabled = canEdit && draft.enabled,
                keyboardType = KeyboardType.Number,
                onChange = { fallback = it.filter(Char::isDigit).take(4) }
            )
            LabeledField(
                label = minLabel,
                value = minLimit,
                placeholder = unlimited,
                enabled = canEdit && draft.enabled,
                keyboardType = KeyboardType.Number,
                onChange = { minLimit = it.filter(Char::isDigit).take(4) }
            )
            LabeledField(
                label = maxLabel,
                value = maxLimit,
                placeholder = unlimited,
                enabled = canEdit && draft.enabled,
                keyboardType = KeyboardType.Number,
                onChange = { maxLimit = it.filter(Char::isDigit).take(4) }
            )
            Text(hint, fontSize = 10.sp, color = theme.mutedColor)
        }

        DraftFooter(
            dirty = draftNow() != section,
            canEdit = canEdit,
            saving = saving,
            error = error,
            onSave = {
                val candidate = draftNow()
                val problem = candidate.validate()
                error = problem
                if (problem == null) onSave(candidate)
            },
            onRevert = {
                draft = section
                fallback = section.fallbackLimit?.toString() ?: ""
                minLimit = section.minLimit?.toString() ?: ""
                maxLimit = section.maxLimit?.toString() ?: ""
                error = null
            }
        )
    }
}

// ── تبِ ۴: اعلان‌ها ──────────────────────────────────────────────────────────

@Composable
internal fun NotificationsSettingsTab(
    section: PanelNotificationSection,
    canEdit: Boolean,
    saving: Boolean,
    onSave: (PanelNotificationSection) -> Unit
) {
    val theme = LocalThemeState.current
    var draft by remember(section) { mutableStateOf(section) }
    var chat by remember(section) { mutableStateOf(section.telegramChatId?.toString() ?: "") }
    var topic by remember(section) { mutableStateOf(section.telegramTopicId?.toString() ?: "") }
    var error by remember(section) { mutableStateOf<String?>(null) }

    val title = stringResource(R.string.ps_notif_card)
    val telegramLabel = stringResource(R.string.ps_notif_telegram)
    val discordLabel = stringResource(R.string.ps_notif_discord)
    val tokenLabel = stringResource(R.string.ps_tg_token)
    val tokenHint = stringResource(R.string.ps_tg_token_hint)
    val chatLabel = stringResource(R.string.ps_tg_chat)
    val topicLabel = stringResource(R.string.ps_tg_topic)
    val webhookLabel = stringResource(R.string.ps_discord_webhook)
    val retriesLabel = stringResource(R.string.ps_retries)
    val channelsHint = stringResource(R.string.ps_notif_channels_hint)

    fun draftNow(): PanelNotificationSection = draft.copy(
        telegramChatId = chat.trim().toLongOrNull(),
        telegramTopicId = topic.trim().toLongOrNull()
    )

    TabScaffold {
        SettingsCard(title = title, icon = AppIcon.Bell) {
            SettingsSwitchRow(
                title = telegramLabel,
                checked = draft.notifyTelegram,
                enabled = canEdit,
                onChange = { draft = draft.copy(notifyTelegram = it) }
            )
            LabeledField(
                label = tokenLabel,
                value = draft.telegramApiToken,
                placeholder = if (draft.tokenMasked) "********" else "123456789:AA…",
                enabled = canEdit,
                onChange = { draft = draft.copy(telegramApiToken = it) }
            )
            Text(tokenHint, fontSize = 10.sp, color = theme.mutedColor)
            LabeledField(
                label = chatLabel,
                value = chat,
                placeholder = "-100…",
                enabled = canEdit,
                keyboardType = KeyboardType.Number,
                onChange = { chat = it.trim() }
            )
            LabeledField(
                label = topicLabel,
                value = topic,
                enabled = canEdit,
                keyboardType = KeyboardType.Number,
                onChange = { topic = it.trim() }
            )
        }

        SettingsCard(title = discordLabel, icon = AppIcon.Language) {
            SettingsSwitchRow(
                title = discordLabel,
                checked = draft.notifyDiscord,
                enabled = canEdit,
                onChange = { draft = draft.copy(notifyDiscord = it) }
            )
            LabeledField(
                label = webhookLabel,
                value = draft.discordWebhookUrl,
                placeholder = "https://discord.com/api/webhooks/…",
                enabled = canEdit,
                keyboardType = KeyboardType.Uri,
                onChange = { draft = draft.copy(discordWebhookUrl = it) }
            )
            if (canEdit) SettingsStepper(
                label = retriesLabel,
                value = draft.maxRetries.coerceIn(2, 10),
                unit = "×",
                range = 2..10,
                onChange = { draft = draft.copy(maxRetries = it) }
            ) else SettingsInfoRow(retriesLabel, draft.maxRetries.toString())
            Text(channelsHint, fontSize = 10.sp, color = theme.mutedColor)
        }

        DraftFooter(
            dirty = draftNow() != section,
            canEdit = canEdit,
            saving = saving,
            error = error,
            onSave = {
                val candidate = draftNow()
                val problem = candidate.validate()
                error = problem
                if (problem == null) onSave(candidate)
            },
            onRevert = {
                draft = section
                chat = section.telegramChatId?.toString() ?: ""
                topic = section.telegramTopicId?.toString() ?: ""
                error = null
            }
        )
    }
}

// ── اجزای مشترک ──────────────────────────────────────────────────────────────

/** ستونِ اسکرول‌شو با فاصلهٔ استاندارد؛ هر تب فقط محتوایش را می‌دهد. */
@Composable
private fun TabScaffold(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.SectionGap)
    ) {
        content()
        Box(Modifier.heightIn(min = DsSpacing.Xl))
    }
}

/** برچسب + فیلدِ متنی؛ در حالتِ فقط‌خواندنی همان مقدار به‌صورت ردیفِ اطلاعات. */
@Composable
private fun LabeledField(
    label: String,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text
) {
    if (!enabled) {
        SettingsInfoRow(label = label, value = value.ifBlank { "—" })
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Xs)) {
        InfraFieldLabel(label)
        CompactGlassField(
            value = value,
            onValueChange = onChange,
            placeholder = placeholder,
            keyboardType = keyboardType
        )
    }
}

/** یک ردیفِ متغیرِ سفارشی: کلید + مقدار + حذف. */
@Composable
private fun VariableRow(
    variable: PanelCustomVariable,
    keyLabel: String,
    valueLabel: String,
    deleteLabel: String,
    onChange: (PanelCustomVariable) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
    ) {
        CompactGlassField(
            value = variable.key,
            onValueChange = { onChange(variable.copy(key = it)) },
            placeholder = keyLabel,
            keyboardType = KeyboardType.Ascii,
            modifier = Modifier.weight(1f)
        )
        CompactGlassField(
            value = variable.value,
            onValueChange = { onChange(variable.copy(value = it)) },
            placeholder = valueLabel,
            keyboardType = KeyboardType.Text,
            modifier = Modifier.weight(1.3f)
        )
        InfraIconButton(
            icon = AppIcon.Delete,
            description = deleteLabel,
            tint = DsSemantic.Danger,
            onClick = onDelete
        )
    }
}

/** چیپِ روشن/خاموشِ یک فرمتِ اشتراک (چندانتخابی، نه تک‌انتخابی). */
@Composable
private fun FormatToggleRow(
    name: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    val theme = LocalThemeState.current
    val stateText = stringResource(if (checked) R.string.a11y_switch_on else R.string.a11y_switch_off)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(DsRadius.Sm)
            .clickable(enabled = enabled) { onChange(!checked) }
            .background(if (checked) theme.accentPrimary.copy(0.16f) else theme.searchBgColor)
            .border(
                BorderStroke(
                    DsBorder.Hairline,
                    if (checked) theme.accentPrimary.copy(0.45f) else theme.borderColor
                ),
                DsRadius.Sm
            )
            .padding(horizontal = DsSpacing.Mid, vertical = DsSpacing.Md)
            .semantics { if (enabled) stateDescription = stateText },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
    ) {
        Text(
            name,
            Modifier.weight(1f),
            fontSize = 11.sp,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            color = if (checked) theme.accentPrimary else theme.mutedColor
        )
        if (checked) RoundedAppIcon(
            AppIcon.Check,
            tint = theme.accentPrimary,
            size = 14.dp
        )
    }
}

/** نامِ نمایشیِ یک فرمتِ `SubFormatEnable` (نام‌های فنی، بدونِ ترجمه). */
private fun formatLabel(key: String): String = when (key) {
    "links" -> "links"
    "links_base64" -> "links (base64)"
    "xray" -> "xray"
    "wireguard" -> "wireguard"
    "sing_box" -> "sing-box"
    "clash" -> "clash"
    "clash_meta" -> "clash-meta"
    "outline" -> "outline"
    else -> key
}

/**
 * پابرگِ «ذخیره/بازگردانی» + پیامِ اعتبارسنجی.
 *
 * دکمه‌ها فقط وقتی فعال‌اند که چیزی عوض شده باشد؛ این‌طور یک PUTِ بی‌مورد که
 * سرویس‌های پنل را ری‌استارت می‌کند (`reset_services`) فرستاده نمی‌شود.
 */
@Composable
private fun DraftFooter(
    dirty: Boolean,
    canEdit: Boolean,
    saving: Boolean,
    error: String?,
    // `error` کدِ خطاست (نه متن) تا نگاشت به رشته فقط در فازِ ترکیب انجام شود؛
    // `stringResource` را نمی‌شود داخلِ لامبدای onSave صدا زد.
    onSave: () -> Unit,
    onRevert: () -> Unit
) {
    val theme = LocalThemeState.current
    val saveLabel = stringResource(R.string.ps_save)
    val revertLabel = stringResource(R.string.ps_revert)
    val readOnly = stringResource(R.string.ps_readonly)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
        if (!canEdit) Text(readOnly, fontSize = 10.sp, color = theme.mutedColor)
        error?.let { InfraBanner(text = settingsErrorText(it), success = false) }
        if (canEdit) Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)
        ) {
            PGSecondaryButton(
                text = revertLabel,
                onClick = onRevert,
                enabled = dirty && !saving,
                modifier = Modifier.weight(1f)
            )
            PGPrimaryButton(
                text = saveLabel,
                onClick = onSave,
                enabled = dirty && !saving,
                loading = saving,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** کدِ خطای مدل → متنِ کاربر. */
@Composable
private fun settingsErrorText(code: String): String = when (code) {
    PanelSettingsErrors.VAR_KEY -> stringResource(R.string.settings_err_var_key)
    PanelSettingsErrors.VAR_DUP -> stringResource(R.string.settings_err_var_dup)
    PanelSettingsErrors.VAR_BUILTIN -> stringResource(R.string.settings_err_var_builtin)
    PanelSettingsErrors.TELEGRAM_TOKEN -> stringResource(R.string.settings_err_telegram_token)
    PanelSettingsErrors.TELEGRAM_NEEDS_TOKEN -> stringResource(R.string.settings_err_telegram_token_required)
    PanelSettingsErrors.TELEGRAM_NEEDS_CHAT -> stringResource(R.string.settings_err_telegram_chat_required)
    PanelSettingsErrors.DISCORD_NEEDS_URL -> stringResource(R.string.settings_err_discord_required)
    PanelSettingsErrors.ANNOUNCE_TOO_LONG -> stringResource(R.string.settings_err_announce_long)
    PanelSettingsErrors.URL_INVALID -> stringResource(R.string.settings_err_url)
    PanelSettingsErrors.LIMITS_ORDER -> stringResource(R.string.settings_err_limits)
    PanelSettingsErrors.RETRIES_RANGE -> stringResource(R.string.settings_err_retries)
    else -> code
}
