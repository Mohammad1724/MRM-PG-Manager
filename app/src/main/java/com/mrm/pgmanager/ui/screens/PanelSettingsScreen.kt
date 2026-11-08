package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.AdminAccess
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.api.SettingsEditConflictException
import com.mrm.pgmanager.data.api.SettingsMaskedSecretException
import com.mrm.pgmanager.data.api.SettingsUnavailableException
import com.mrm.pgmanager.data.api.SettingsWriteUnconfirmedException
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.PanelSettings
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.PGPageHeader
import com.mrm.pgmanager.ui.components.PGTabBar
import com.mrm.pgmanager.ui.components.MrmErrorState
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.pressScale
import com.mrm.pgmanager.ui.designsystem.MrmBusyIndicator
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.theme.primarySurface
import com.mrm.pgmanager.utils.ApiErrorKind
import com.mrm.pgmanager.utils.ApiErrorMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Server settings are edited as separate drafts, with a fresh read before each write. */
@Composable
fun PanelSettingsScreen(session: Session, onBack: () -> Unit) {
    val context = LocalContext.current
    val theme = LocalThemeState.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(0) }

    val cacheKey = remember(session) { PanelCache.panelSettingsKey(session) }
    val canRead = AdminAccess.canFor(session, "settings", "read")
    var settings by remember(session, canRead) {
        mutableStateOf(if (canRead) PanelCache.get<PanelSettings>(cacheKey) else null)
    }
    var loading by remember(session, canRead) { mutableStateOf(canRead && settings == null) }
    var loadError by remember(session, canRead) { mutableStateOf<String?>(null) }
    var loadRequest by remember(session) { mutableStateOf(0L) }
    var banner by remember(session, canRead) { mutableStateOf<String?>(null) }
    var toast by remember(session, canRead) { mutableStateOf<String?>(null) }
    var saving by remember(session, canRead) { mutableStateOf(false) }

    val canEdit = canRead && AdminAccess.canFor(session, "settings", "update")
    val savedMessage = stringResource(R.string.ps_saved)
    val tabs = listOf(
        R.string.panel_settings_tab_general,
        R.string.panel_settings_tab_subscription,
        R.string.panel_settings_tab_hwid,
        R.string.panel_settings_tab_notifications
    ) + if (settings?.runtime != null || settings?.cleanup != null) listOf(R.string.panel_settings_tab_advanced) else emptyList()
    LaunchedEffect(tabs.size) { if (tab >= tabs.size) tab = 0 }

    suspend fun load() {
        if (!AdminAccess.canFor(session, "settings", "read")) return
        val request = ++loadRequest
        try {
            val result = PanelApi.panelSettings(session)
            currentCoroutineContext().ensureActive()
            if (request == loadRequest && AdminAccess.canFor(session, "settings", "read")) {
                settings = result
                loadError = null
                banner = null
                PanelCache.put(cacheKey, result)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (request == loadRequest && AdminAccess.canFor(session, "settings", "read")) {
                val kind = ApiErrorMapper.kindOf(error)
                if (kind == ApiErrorKind.FORBIDDEN || kind == ApiErrorKind.UNAUTHORIZED) {
                    settings = null
                    PanelCache.invalidate(cacheKey)
                }
                val message = ApiErrorMapper.friendly(context, error)
                loadError = message
                if (settings != null) banner = message
            }
        } finally {
            if (request == loadRequest) loading = false
        }
    }

    LaunchedEffect(session, canRead) {
        loadRequest++
        if (!canRead) {
            PanelCache.invalidate(cacheKey)
        } else if (!PanelCache.isFresh(cacheKey)) {
            loading = settings == null
            load()
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2200)
            toast = null
        }
    }

    /** پس از هر ذخیره، پاسخِ کاملِ سرور جای حالتِ محلی می‌نشیند. */
    fun persist(call: suspend () -> PanelSettings) {
        scope.launch {
            if (!AdminAccess.canFor(session, "settings", "update")) return@launch
            saving = true
            try {
                val fresh = call()
                currentCoroutineContext().ensureActive()
                if (AdminAccess.canFor(session, "settings", "read")) {
                    settings = fresh
                    PanelCache.put(cacheKey, fresh)
                    banner = null
                    toast = savedMessage
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (AdminAccess.canFor(session, "settings", "read")) {
                    banner = when (error) {
                        is SettingsEditConflictException -> context.getString(R.string.ps_edit_conflict)
                        is SettingsMaskedSecretException -> context.getString(R.string.ps_masked_secret)
                        is SettingsUnavailableException -> context.getString(R.string.ps_section_unavailable)
                        is SettingsWriteUnconfirmedException -> context.getString(R.string.ps_write_unconfirmed)
                        else -> ApiErrorMapper.friendly(context, error)
                    }
                }
            } finally {
                saving = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding().navigationBarsPadding()
            .padding(
                start = DsSpacing.Screen, end = DsSpacing.Screen,
                top = DsSpacing.Screen, bottom = DsSpacing.Mid
            ),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
    ) {
        // ── سربرگ (هم‌سبکِ «پنلِ من» و «زیرساخت»)
        PGPageHeader(
            title = stringResource(R.string.panel_settings_title),
            subtitle = stringResource(R.string.panel_settings_subtitle),
            onBack = onBack
        ) {
            if (canRead) InfraIconButton(
                icon = AppIcon.Refresh,
                description = stringResource(R.string.refresh),
                tint = theme.mutedColor,
                onClick = { scope.launch { loading = settings == null; load() } }
            )
        }

        // ── تب‌ها
        PGTabBar(
            labels = tabs.map { stringResource(it) },
            selectedIndex = tab,
            onSelect = { tab = it }
        )

        if (toast != null) InfraBanner(text = toast.orEmpty(), success = true)
        banner?.let { InfraBanner(text = it, success = false) { banner = null } }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            val current = settings
            when {
                !canRead -> Text(stringResource(R.string.err_forbidden_panel), color = theme.mutedColor)
                loading && current == null -> Box(
                    Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    contentAlignment = Alignment.Center
                ) { MrmBusyIndicator(color = theme.accentPrimary, size = 40.dp, strokeWidth = 2.5.dp) }

                loadError != null && current == null -> MrmErrorState(
                    onRetry = { scope.launch { loading = true; load() } },
                    message = loadError
                )

                current != null -> when (tab) {
                    0 -> GeneralSettingsTab(
                        section = current.general,
                        canEdit = canEdit,
                        saving = saving,
                        onSave = { draft -> persist { PanelApi.savePanelGeneral(session, current.general, draft) } }
                    )
                    1 -> SubscriptionSettingsTab(
                        section = current.subscription,
                        canEdit = canEdit,
                        saving = saving,
                        onSave = { draft -> persist { PanelApi.savePanelSubscription(session, current.subscription, draft) } }
                    )
                    2 -> HwidSettingsTab(
                        section = current.hwid,
                        canEdit = canEdit,
                        saving = saving,
                        onSave = { draft -> persist { PanelApi.savePanelHwid(session, current.hwid, draft) } }
                    )
                    3 -> NotificationsSettingsTab(
                        section = current.notifications,
                        canEdit = canEdit,
                        saving = saving,
                        onSave = { draft -> persist { PanelApi.savePanelNotifications(session, current.notifications, draft) } }
                    )
                    else -> AdvancedSettingsTab(
                        runtime = current.runtime,
                        cleanup = current.cleanup,
                        canEdit = canEdit,
                        saving = saving,
                        onSaveRuntime = { base, draft -> persist { PanelApi.savePanelRuntime(session, base, draft) } },
                        onSaveCleanup = { base, draft -> persist { PanelApi.savePanelCleanup(session, base, draft) } }
                    )
                }
            }
        }
    }
}
