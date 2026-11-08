package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.AdminAccess
import com.mrm.pgmanager.data.api.InboundDetail
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.api.WireGuardSubnet
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.MrmEmptyState
import com.mrm.pgmanager.ui.components.MrmErrorState
import com.mrm.pgmanager.ui.components.PGSearchBar
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.MrmBusyIndicator
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.ApiErrorKind
import com.mrm.pgmanager.utils.ApiErrorMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

private val INBOUND_COLUMNS = listOf(
    InfraColumn(R.string.inbound_tag, 1f),
    InfraColumn(R.string.inbound_protocol, 0.8f),
    InfraColumn(R.string.inbound_network, 0.8f)
)

@Composable
internal fun InboundsSection(session: Session, sectionTabs: @Composable () -> Unit) {
    InfraReadSection(
        session = session, sectionTabs = sectionTabs, listTag = "infra_inbounds_list",
        title = stringResource(R.string.inbounds_title), subtitle = stringResource(R.string.inbounds_subtitle),
        searchLabel = stringResource(R.string.inbounds_search), emptyTitle = stringResource(R.string.inbounds_empty),
        permission = "system.read", allowed = AdminAccess.can("system", "read"),
        fetch = { PanelApi.inboundDetails(session) },
        matches = { item: InboundDetail, query ->
            item.tag.contains(query, true) || item.protocol.contains(query, true) || item.network.orEmpty().contains(query, true)
        },
        tableHeader = { InfraTableHeader(INBOUND_COLUMNS) }
    ) { item, _ ->
        InfraTableRow(columns = INBOUND_COLUMNS, name = item.tag,
            cells = listOf(item.protocol, item.network?.takeIf { it.isNotBlank() } ?: "—"))
    }
}

@Composable
internal fun WireGuardSection(session: Session, sectionTabs: @Composable () -> Unit) {
    InfraReadSection(
        session = session, sectionTabs = sectionTabs, listTag = "infra_wireguard_list",
        title = stringResource(R.string.wireguard_title), subtitle = stringResource(R.string.wireguard_subtitle),
        searchLabel = stringResource(R.string.wireguard_search), emptyTitle = stringResource(R.string.wireguard_empty),
        permission = "cores.read", allowed = AdminAccess.can("cores", "read"),
        fetch = { PanelApi.wireGuardSubnets(session) },
        matches = { item: WireGuardSubnet, query ->
            item.subnet.contains(query, true) || item.interfaceTags.any { it.contains(query, true) } ||
                item.freeIps.any { it.contains(query, true) }
        }
    ) { item, query -> WireGuardSubnetCard(item, query) }
}

/** A failed response remains an error, never a fabricated empty data set. */
@Composable
private fun <T> InfraReadSection(
    session: Session,
    sectionTabs: @Composable () -> Unit,
    listTag: String,
    title: String,
    subtitle: String,
    searchLabel: String,
    emptyTitle: String,
    permission: String,
    allowed: Boolean,
    fetch: suspend () -> List<T>,
    matches: (T, String) -> Boolean,
    tableHeader: (@Composable () -> Unit)? = null,
    row: @Composable (T, String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val theme = LocalThemeState.current
    val scope = rememberCoroutineScope()
    var items by remember(session) { mutableStateOf(emptyList<T>()) }
    var loading by remember(session) { mutableStateOf(true) }
    var error by remember(session) { mutableStateOf<String?>(null) }
    var query by remember(session) { mutableStateOf("") }
    var request by remember(session) { mutableStateOf(0L) }

    suspend fun load() {
        val id = ++request
        if (!allowed) {
            items = emptyList(); error = null; loading = false
            return
        }
        try {
            val result = fetch()
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (id == request) { items = result; error = null }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (id == request) {
                val kind = ApiErrorMapper.kindOf(e)
                if (kind == ApiErrorKind.FORBIDDEN || kind == ApiErrorKind.UNAUTHORIZED) items = emptyList()
                error = ApiErrorMapper.friendly(context, e)
            }
        } finally {
            if (id == request) loading = false
        }
    }
    LaunchedEffect(session, allowed) { loading = allowed; load() }
    val filtered = remember(items, query) {
        val q = query.trim()
        if (q.isEmpty()) items else items.filter { matches(it, q) }
    }
    InfraScrollableContent(listTag = listTag, header = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)) {
            sectionTabs()
            InfraSectionHeader(title, subtitle, if (allowed) items.size else null,
                onRefresh = { if (allowed) scope.launch { loading = items.isEmpty(); load() } },
                onAdd = null, addLabel = "")
            if (items.isNotEmpty()) error?.let { InfraBanner(it, success = false) { error = null } }
            if (items.isNotEmpty()) PGSearchBar(query, onQueryChange = { query = it }, placeholder = searchLabel)
            if (filtered.isNotEmpty()) tableHeader?.invoke()
        }
    }) {
        when {
            !allowed -> item(key = "permission") {
                MrmEmptyState(title = stringResource(R.string.infra_read_permission),
                    subtitle = stringResource(R.string.infra_read_permission_hint, permission), icon = AppIcon.Lock)
            }
            loading && items.isEmpty() -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    MrmBusyIndicator(color = theme.accentPrimary, size = 40.dp, strokeWidth = 2.5.dp)
                }
            }
            error != null && items.isEmpty() -> item(key = "error") {
                MrmErrorState(onRetry = { scope.launch { loading = true; load() } }, message = error)
            }
            items.isEmpty() -> item(key = "empty") {
                MrmEmptyState(title = emptyTitle, subtitle = stringResource(R.string.infra_read_only_empty_hint), icon = AppIcon.Storage)
            }
            filtered.isEmpty() -> item(key = "no_results") {
                MrmEmptyState(title = stringResource(R.string.no_results), subtitle = stringResource(R.string.no_results_hint), icon = AppIcon.Search) {
                    PGSecondaryButton(stringResource(R.string.clear_filter), onClick = { query = "" })
                }
            }
            else -> itemsIndexed(filtered) { _, item -> row(item, query.trim()) }
        }
    }
}

@Composable
internal fun WireGuardSubnetCard(item: WireGuardSubnet, query: String = "") {
    val theme = LocalThemeState.current
    var requestedPage by remember(item.subnet, query) { mutableIntStateOf(0) }
    val page = remember(item, query, requestedPage) { pageWireGuardIps(item.freeIps, query, requestedPage) }
    Column(
        Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg).padding(DsSpacing.Card),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
    ) {
        Text(item.subnet, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.inkColor,
            style = TextStyle(textDirection = TextDirection.Ltr))
        Text(stringResource(R.string.wireguard_counts, item.used.toString(), item.free.toString(), item.capacity.toString()),
            fontSize = 12.sp, color = theme.inkColor)
        Text(stringResource(R.string.wireguard_tags, item.interfaceTags.joinToString(", ").ifBlank { "—" }),
            fontSize = 12.sp, color = theme.mutedColor)
        Text(stringResource(R.string.wireguard_free_ips), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.inkColor)
        Text(page.ips.joinToString("\n").ifBlank { "—" }, fontSize = 12.sp, color = theme.mutedColor,
            style = TextStyle(textDirection = TextDirection.Ltr))
        if (page.total > 0) {
            Text(stringResource(R.string.wireguard_ip_page, page.start + 1, page.end, page.total), fontSize = 12.sp, color = theme.mutedColor)
        }
        if (page.lastPage > 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                PGSecondaryButton(stringResource(R.string.wireguard_previous_ips), onClick = { requestedPage = page.page - 1 },
                    modifier = Modifier.weight(1f), enabled = page.page > 0)
                PGSecondaryButton(stringResource(R.string.wireguard_next_ips), onClick = { requestedPage = page.page + 1 },
                    modifier = Modifier.weight(1f), enabled = page.page < page.lastPage)
            }
        }
    }
}
