package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.PGPageHeader
import com.mrm.pgmanager.ui.components.PGTabBar
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.theme.LocalThemeState

/** Server infrastructure, including node management and read-only network inventories.
 * The page header stays fixed while section tabs and content scroll together.
 */
@Composable
fun PanelInfraScreen(session: Session, onBack: () -> Unit) {
    val theme = LocalThemeState.current
    // ایندکسِ تب با rememberSaveable نگه داشته می‌شود تا با چرخشِ صفحه یا
    // بازسازیِ activity (تغییرِ زبان) کاربر به تبِ اول برنگردد.
    var tab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf(
        R.string.infra_tab_hosts,
        R.string.infra_tab_cores,
        R.string.infra_tab_templates,
        R.string.infra_tab_nodes,
        R.string.infra_tab_inbounds,
        R.string.infra_tab_wireguard
    )

    // Tabs and section controls share the section's scroll viewport; the page header does not.
    val sectionTabs: @Composable () -> Unit = {
        PGTabBar(
            labels = tabs.map { stringResource(it) },
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier.testTag("infra_scrollable_tabs"),
            compact = true,
            scrollWithParent = true
        )
    }

    // Consume system-bar insets once around the fixed header and section viewport.
    Column(Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().padding(start = DsSpacing.Screen, end = DsSpacing.Screen, top = DsSpacing.Md)) {
            PGPageHeader(
                title = stringResource(R.string.infra_title),
                subtitle = stringResource(R.string.infra_subtitle),
                onBack = onBack,
                modifier = Modifier.testTag("infra_page_header"),
                compact = true
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                0 -> HostsSection(session, sectionTabs)
                1 -> CoresSection(session, sectionTabs)
                2 -> ClientTemplatesSection(session, sectionTabs)
                3 -> NodesSection(session, sectionTabs)
                4 -> InboundsSection(session, sectionTabs)
                5 -> WireGuardSection(session, sectionTabs)
                else -> HostsSection(session, sectionTabs)
            }
        }
    }
}
