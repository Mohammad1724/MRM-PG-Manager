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
import androidx.compose.material3.CircularProgressIndicator
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
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.PanelSettings
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.MrmErrorState
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.pressScale
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.theme.primarySurface
import com.mrm.pgmanager.utils.ApiErrorMapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * **«تنظیماتِ پنل»** — چهار بخشی از `SettingsSchema` که در گوشی معنا دارند:
 * پیش‌فرض‌های سرور (`general`)، اشتراک (`subscription`)، محدودیتِ دستگاه
 * (`hwid`) و کانال‌های اعلان (`notification_settings`).
 *
 * ### چرا صفحه‌ای جدا، و چرا چهار تب
 * این‌ها مالِ **آن سرور**‌اند، نه این گوشی؛ پس جایشان در «تنظیماتِ اپ» نیست
 * (قاعدهٔ `docs/SETTINGS-SPLIT.md`) و مثلِ «زیرساختِ پنل» یک صفحهٔ کاملِ رویی‌اند
 * که از «پنلِ من» باز می‌شود. چهار تب هم چون هر بخش مستقل ذخیره می‌شود: یک
 * `PUT` بدنهٔ همان بخش را کامل جایگزین می‌کند، پس قاطی‌کردنشان در یک فرمِ بلند
 * یعنی هر ذخیره همه‌چیز را بازنویسی می‌کند.
 *
 * ### چیزهایی که عمداً اینجا نیستند
 * رباتِ تلگرام، وبهوک، ماتریسِ کانالِ هر موجودیت (`channels`)، فهرستِ رویدادها
 * (`notification_enable`)، `rules` و `applications`ِ اشتراک. این‌ها در `PUT`
 * از پاسخِ قبلی دست‌نخورده برمی‌گردند (merge روی بدنهٔ خام) و ویرایششان فرمِ وب
 * را می‌خواهد؛ ساختنِ فرم برایشان در گوشی فقط ۴۲۲ می‌سازد.
 *
 * @param onBack بستنِ صفحه (دکمهٔ سربرگ و دکمهٔ برگشتِ گوشی).
 */
@Composable
fun PanelSettingsScreen(session: Session, onBack: () -> Unit) {
    val context = LocalContext.current
    val theme = LocalThemeState.current
    val scope = rememberCoroutineScope()
    val backLabel = stringResource(R.string.cd_back)
    var tab by rememberSaveable { mutableStateOf(0) }

    val cacheKey = PanelCache.panelSettingsKey(session.baseUrl)
    var settings by remember(session) { mutableStateOf(PanelCache.get<PanelSettings>(cacheKey)) }
    var loading by remember(session) { mutableStateOf(settings == null) }
    var loadError by remember(session) { mutableStateOf<String?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val canEdit = AdminAccess.can("settings", "update")
    val savedMessage = stringResource(R.string.ps_saved)
    val tabs = listOf(
        R.string.panel_settings_tab_general,
        R.string.panel_settings_tab_subscription,
        R.string.panel_settings_tab_hwid,
        R.string.panel_settings_tab_notifications
    )

    suspend fun load() {
        runCatching { PanelApi.panelSettings(session) }
            .onSuccess {
                settings = it
                loadError = null
                banner = null
                PanelCache.put(cacheKey, it)
            }
            .onFailure {
                val message = ApiErrorMapper.friendly(context, it)
                loadError = message
                // اگر دادهٔ کش‌شده روی صفحه هست، خطا هم باید دیده شود وگرنه کاربر
                // فکر می‌کند مقادیرِ کهنه همان مقدارِ سرور است.
                if (settings != null) banner = message
            }
        loading = false
    }

    LaunchedEffect(session) {
        if (!PanelCache.isFresh(cacheKey)) {
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
            saving = true
            runCatching { call() }
                .onSuccess { fresh ->
                    settings = fresh
                    PanelCache.put(cacheKey, fresh)
                    banner = null
                    toast = savedMessage
                }
                .onFailure { banner = ApiErrorMapper.friendly(context, it) }
            saving = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding()
            .padding(
                start = DsSpacing.Screen, end = DsSpacing.Screen,
                top = DsSpacing.Screen, bottom = DsSpacing.Mid
            ),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
    ) {
        // ── سربرگ (هم‌سبکِ «پنلِ من» و «زیرساخت»)
        Row(
            Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                .padding(horizontal = DsSpacing.Card, vertical = DsSpacing.Mid),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
        ) {
            Box(
                Modifier.padding(3.dp).size(34.dp).clip(DsRadius.Sm).clickable { onBack() }
                    .background(theme.searchBgColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm)
                    .semantics { contentDescription = backLabel },
                contentAlignment = Alignment.Center
            ) { RoundedAppIcon(AppIcon.Prev, tint = theme.mutedColor, size = 16.dp) }

            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.panel_settings_title), fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, color = theme.inkColor
                )
                Text(
                    stringResource(R.string.panel_settings_subtitle),
                    fontSize = 11.sp, color = theme.mutedColor
                )
            }
            InfraIconButton(
                icon = AppIcon.Refresh,
                description = stringResource(R.string.refresh),
                tint = theme.mutedColor,
                onClick = {
                    scope.launch {
                        loading = settings == null
                        load()
                    }
                }
            )
        }

        // ── تب‌ها
        Row(
            Modifier.fillMaxWidth().clip(DsRadius.Xl).background(theme.searchBgColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xl)
                .padding(DsSpacing.Xs),
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.Xs)
        ) {
            tabs.forEachIndexed { index, labelRes ->
                val selected = tab == index
                Box(
                    Modifier.weight(1f).heightIn(min = 40.dp).clip(DsRadius.Full)
                        .clickable { tab = index }
                        .primarySurface(theme, selected, DsRadius.Full, idle = Color.Transparent)
                        .pressScale(0.96f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(labelRes), fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) theme.onPrimary else theme.mutedColor,
                        maxLines = 1
                    )
                }
            }
        }

        if (toast != null) InfraBanner(text = toast.orEmpty(), success = true)
        banner?.let { InfraBanner(text = it, success = false) { banner = null } }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            val current = settings
            when {
                loading && current == null -> Box(
                    Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = theme.accentPrimary) }

                loadError != null && current == null -> MrmErrorState(
                    onRetry = { scope.launch { loading = true; load() } },
                    message = loadError
                )

                current != null -> when (tab) {
                    0 -> GeneralSettingsTab(
                        section = current.general,
                        canEdit = canEdit,
                        saving = saving,
                        onSave = { draft -> persist { PanelApi.savePanelGeneral(session, draft) } }
                    )
                    1 -> SubscriptionSettingsTab(
                        section = current.subscription,
                        canEdit = canEdit,
                        saving = saving,
                        onSave = { draft -> persist { PanelApi.savePanelSubscription(session, draft) } }
                    )
                    2 -> HwidSettingsTab(
                        section = current.hwid,
                        canEdit = canEdit,
                        saving = saving,
                        onSave = { draft -> persist { PanelApi.savePanelHwid(session, draft) } }
                    )
                    else -> NotificationsSettingsTab(
                        section = current.notifications,
                        canEdit = canEdit,
                        saving = saving,
                        onSave = { draft -> persist { PanelApi.savePanelNotifications(session, draft) } }
                    )
                }
            }
        }
    }
}
