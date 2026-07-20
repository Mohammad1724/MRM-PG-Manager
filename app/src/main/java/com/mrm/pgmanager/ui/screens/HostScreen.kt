package com.mrm.pgmanager.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.PanelHost
import com.mrm.pgmanager.data.model.PanelHostEditValues
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.ViewMode
import com.mrm.pgmanager.ui.components.*
import com.mrm.pgmanager.ui.dialogs.HostDeleteConfirmDialog
import com.mrm.pgmanager.ui.dialogs.HostEditorDialog
import com.mrm.pgmanager.ui.theme.GlassAmber
import com.mrm.pgmanager.ui.theme.GlassGreen
import com.mrm.pgmanager.ui.theme.GlassRed
import com.mrm.pgmanager.ui.theme.glassBg
import com.mrm.pgmanager.ui.theme.glassBorder
import com.mrm.pgmanager.ui.theme.LocalThemeState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun HostsScreen(
    session: Session,
    onLogout: () -> Unit,
    onOpenThemeDialog: () -> Unit
) {
    val themeState = LocalThemeState.current
    val scope = rememberCoroutineScope()
    var hosts by remember { mutableStateOf<List<PanelHost>>(emptyList()) }
    var inbounds by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selectedHost by remember { mutableStateOf<PanelHost?>(null) }
    var createHost by remember { mutableStateOf(false) }
    var deleteHost by remember { mutableStateOf<PanelHost?>(null) }
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val statsCardsHeightPx = remember { mutableStateOf(0f) }
    val totalHeaderHeightPx = remember { mutableStateOf(0f) }

    val fallbackStatsPx = remember(density) { with(density) { 104.dp.toPx() } }
    val headerHeight = if (statsCardsHeightPx.value > 0f) statsCardsHeightPx.value else fallbackStatsPx
    val fallbackTotalDp = 224.dp
    val totalHeaderDp = if (totalHeaderHeightPx.value > 0f) with(density) { totalHeaderHeightPx.value.toDp() } else fallbackTotalDp
    val scrollOffset = remember { mutableStateOf(0f) }

    fun load() {
        scope.launch {
            loading = true; error = null
            runCatching {
                val list = PanelApi.hosts(session)
                val inb = PanelApi.inbounds(session)
                hosts = list; inbounds = inb
                scrollOffset.value = 0f
            }.onFailure {
                error = it.message; if (it.message?.contains("401") == true) onLogout()
            }
            loading = false
        }
    }

    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            runCatching { action() }.onFailure { error = it.message; if (it.message?.contains("401") == true) onLogout() }.onSuccess { load() }
        }
    }

    LaunchedEffect(Unit) { load() }

    val processedHosts = remember(hosts, query) {
        if (query.isBlank()) hosts else hosts.filter {
            it.remark.contains(query, ignoreCase = true) ||
            it.address.any { a -> a.contains(query, ignoreCase = true) } ||
            it.inboundTag.contains(query, ignoreCase = true)
        }.sortedByDescending { it.priority }
    }

    val nestedScrollConnection = remember(headerHeight) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (headerHeight <= 0f) return Offset.Zero
                val delta = -available.y
                val current = scrollOffset.value
                if (delta > 0f && current < headerHeight) {
                    val newOffset = (current + delta).coerceIn(0f, headerHeight)
                    val consumedY = newOffset - current
                    scrollOffset.value = newOffset
                    return Offset(0f, -consumedY)
                } else if (delta < 0f && current > 0f) {
                    val newOffset = (current + delta).coerceIn(0f, headerHeight)
                    val consumedY = newOffset - current
                    scrollOffset.value = newOffset
                    return Offset(0f, -consumedY)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = Offset.Zero
        }
    }

    Scaffold(containerColor = Color.Transparent, floatingActionButton = {
        Box(modifier = Modifier.padding(bottom = 66.dp).clip(RoundedCornerShape(26.dp)).background(themeState.lamp.primary).clickable { createHost = true }.padding(horizontal = 20.dp, vertical = 13.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("+", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text("هاست جدید", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 13.sp)
            }
        }
    }) { padding ->
        val topInsets = padding.calculateTopPadding()

        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .offset {
                        val current = scrollOffset.value.coerceIn(0f, headerHeight)
                        IntOffset(0, -current.roundToInt())
                    }
            ) {
                when {
                    loading -> LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = totalHeaderDp + topInsets + 4.dp, bottom = 140.dp)) { items(6) { SkeletonHostCard() } }
                    error != null -> Box(Modifier.fillMaxWidth().padding(top = totalHeaderDp + topInsets + 4.dp).clip(RoundedCornerShape(20.dp)).background(glassBg(themeState.isDark)).border(BorderStroke(1.dp, GlassRed.copy(0.18f)), RoundedCornerShape(20.dp)).padding(18.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⚠️ خطا در دریافت هاست‌ها", fontWeight = FontWeight.Bold, color = GlassRed, fontSize = 14.sp)
                            Text(error ?: "", color = themeState.mutedColor, fontSize = 12.sp)
                            GlassButton("🔄 تلاش مجدد", onClick = { load() }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    processedHosts.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = totalHeaderDp + topInsets + 4.dp).clip(RoundedCornerShape(24.dp)).background(glassBg(themeState.isDark)).border(BorderStroke(1.dp, glassBorder(themeState.isDark)), RoundedCornerShape(24.dp)).padding(28.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("🌐", fontSize = 36.sp); Text("هاستی یافت نشد", fontWeight = FontWeight.Bold, color = themeState.inkColor, fontSize = 15.sp)
                        }
                    }
                    else -> when (viewMode) {
                        ViewMode.GRID -> LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = totalHeaderDp + topInsets + 4.dp, bottom = 140.dp)) { items(processedHosts) { host -> HostGridCard(host, onClick = { selectedHost = host }, onToggle = { runAction { PanelApi.modifyHost(session, host.id, hostToEditValues(host.copy(isDisabled = !host.isDisabled))) } }, onDelete = { deleteHost = host }) } }
                        ViewMode.COMPACT_LIST -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = totalHeaderDp + topInsets + 4.dp, bottom = 140.dp)) { items(processedHosts) { host -> HostCompactRow(host, onClick = { selectedHost = host }, onToggle = { runAction { PanelApi.modifyHost(session, host.id, hostToEditValues(host.copy(isDisabled = !host.isDisabled))) } }, onDelete = { deleteHost = host }) } }
                        ViewMode.MICRO_LIST -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = totalHeaderDp + topInsets + 4.dp, bottom = 140.dp)) { items(processedHosts) { host -> HostCompactRow(host, onClick = { selectedHost = host }, onToggle = { runAction { PanelApi.modifyHost(session, host.id, hostToEditValues(host.copy(isDisabled = !host.isDisabled))) } }, onDelete = { deleteHost = host }) } }
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        if (scrollOffset.value == 0f && coords.size.height > 0) {
                            val h = (coords.size.height.toFloat() - with(density) { topInsets.toPx() }).coerceAtLeast(0f)
                            if (totalHeaderHeightPx.value != h) {
                                totalHeaderHeightPx.value = h
                            }
                        }
                    }
                    .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                    .background(if (themeState.isDark) Color(0xFF141418).copy(alpha = 0.98f) else Color(0xFFFAF5EC).copy(alpha = 0.98f))
                    .border(BorderStroke(1.2.dp, glassBorder(themeState.isDark)), RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                    .padding(top = topInsets)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 6.dp)
            ) {
                // Top Bar
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLogo(height = 22.dp)
                        Column {
                            Text("Pasarguard", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = themeState.inkColor)
                            Text("مدیریت هاست‌ها (Hosts)", fontSize = 10.sp, color = themeState.mutedColor)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ActionIconButton(icon = { Text("🎨", fontSize = 14.sp) }, onClick = onOpenThemeDialog)
                        ActionIconButton(icon = { if (loading) CircularProgressIndicator(Modifier.size(14.dp), color = themeState.inkColor, strokeWidth = 2.dp) else Text("🔄", fontSize = 14.sp) }, onClick = { load() }, enabled = !loading)
                        ActionIconButton(icon = { ExitIcon() }, onClick = onLogout, isRed = true)
                    }
                }

                // Stats Cards
                Box(
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            if (scrollOffset.value == 0f && coords.size.height > 0) {
                                if (statsCardsHeightPx.value != coords.size.height.toFloat()) {
                                    statsCardsHeightPx.value = coords.size.height.toFloat()
                                }
                            }
                        }
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val maxH = if (statsCardsHeightPx.value > 0f) statsCardsHeightPx.value else placeable.height.toFloat()
                            val progress = if (maxH > 0f) (scrollOffset.value / maxH).coerceIn(0f, 1f) else 0f
                            val currentH = (placeable.height * (1f - progress)).roundToInt().coerceAtLeast(0)
                            layout(placeable.width, currentH) {
                                placeable.placeRelative(0, (-progress * placeable.height * 0.38f).roundToInt())
                            }
                        }
                        .graphicsLayer {
                            val maxH = if (statsCardsHeightPx.value > 0f) statsCardsHeightPx.value else 1f
                            val progress = (scrollOffset.value / maxH).coerceIn(0f, 1f)
                            this.alpha = (1f - progress * 1.3f).coerceIn(0f, 1f)
                        }
                ) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            HostStatCard("🌐", "کل هاست‌ها", "${hosts.size}", themeState.lamp.primary, Modifier.weight(1f))
                            HostStatCard("🟢", "هاست‌های فعال", "${hosts.count { !it.isDisabled }}", GlassGreen, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            HostStatCard("🔒", "امنیت TLS", "${hosts.count { it.security == "tls" }}", Color(0xFF0EA89B), Modifier.weight(1f))
                            HostStatCard("🎯", "Inbounds فعال", "${hosts.map { it.inboundTag }.distinct().size}", Color(0xFFD9822B), Modifier.weight(1f))
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                // Search Bar
                Box(modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(13.dp)).background(glassBg(themeState.isDark)).border(BorderStroke(1.dp, glassBorder(themeState.isDark)), RoundedCornerShape(13.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🔍", fontSize = 13.5.sp)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) Text("جستجو هاست (عنوان، دامنه یا Inbound)...", color = themeState.mutedColor.copy(0.65f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = TextStyle(color = themeState.inkColor, fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { inner() } }
                            )
                        }
                        if (query.isNotEmpty()) Box(Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.14f)).clickable { query = "" }, contentAlignment = Alignment.Center) { Text("×", color = themeState.inkColor, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("لیست هاست‌ها (${processedHosts.size} مورد)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = themeState.mutedColor)
                    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(glassBg(themeState.isDark)).border(BorderStroke(1.dp, glassBorder(themeState.isDark)), RoundedCornerShape(8.dp)).padding(1.5.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        HostViewModeIcon("⊞", viewMode == ViewMode.GRID) { viewMode = ViewMode.GRID }
                        HostViewModeIcon("☰", viewMode == ViewMode.COMPACT_LIST) { viewMode = ViewMode.COMPACT_LIST }
                        HostViewModeIcon("≡", viewMode == ViewMode.MICRO_LIST) { viewMode = ViewMode.MICRO_LIST }
                    }
                }
            }
        }
    }

    selectedHost?.let { host ->
        HostEditorDialog(initial = host, inbounds = inbounds, onDismiss = { selectedHost = null }, onSave = { vals ->
            selectedHost = null; runAction { PanelApi.modifyHost(session, host.id, vals) }
        })
    }
    if (createHost) {
        HostEditorDialog(initial = null, inbounds = inbounds, onDismiss = { createHost = false }, onSave = { vals ->
            createHost = false; runAction { PanelApi.createHost(session, vals) }
        })
    }
    deleteHost?.let { host ->
        HostDeleteConfirmDialog(host = host, onDismiss = { deleteHost = null }, onDelete = {
            deleteHost = null; runAction { PanelApi.deleteHost(session, host.id) }
        })
    }
}

private fun hostToEditValues(host: PanelHost) = PanelHostEditValues(
    remark = host.remark,
    address = host.address,
    inboundTag = host.inboundTag,
    port = host.port,
    sni = host.sni,
    host = host.host,
    path = host.path,
    security = host.security,
    fingerprint = host.fingerprint,
    alpn = host.alpn,
    allowInsecure = host.allowInsecure,
    isDisabled = host.isDisabled,
    priority = host.priority
)

@Composable
private fun HostStatCard(icon: String, label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val theme = LocalThemeState.current
    Box(modifier = modifier.clip(RoundedCornerShape(14.dp)).background(glassBg(theme.isDark)).border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(accent.copy(0.14f)).border(BorderStroke(1.dp, accent.copy(0.18f)), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) { Text(icon, fontSize = 11.sp) }
                Text(label, fontSize = 10.5.sp, color = theme.mutedColor, fontWeight = FontWeight.Bold)
            }
            Text(value, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HostViewModeIcon(icon: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (selected) theme.lamp.primary.copy(0.16f) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 2.5.dp), contentAlignment = Alignment.Center) {
        Text(icon, fontSize = 10.5.sp, color = if (selected) theme.lamp.primary else theme.mutedColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HostGridCard(host: PanelHost, onClick: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
    val theme = LocalThemeState.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(glassBg(theme.isDark))
            .border(BorderStroke(1.2.dp, glassBorder(theme.isDark)), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (host.security == "tls") "🔒" else "🔓", fontSize = 13.sp)
                    Text(host.remark, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(theme.lamp.primary.copy(0.14f)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                    Text(host.inboundTag.ifEmpty { "Default" }, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = theme.lamp.primary)
                }
            }

            // Address & Port
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(host.address.joinToString(", ").ifEmpty { "تمام دامنه‌ها (*)" }, fontSize = 11.sp, color = theme.inkColor, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(if (host.port != null) "پورت: ${host.port}" else "پورت: پیش‌فرض Inbound", fontSize = 10.sp, color = theme.mutedColor)
            }

            // Actions row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.height(26.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (host.isDisabled) Color.White.copy(0.08f) else GlassGreen.copy(0.14f))
                        .border(BorderStroke(1.dp, if (host.isDisabled) Color.White.copy(0.14f) else GlassGreen.copy(0.20f)), RoundedCornerShape(8.dp))
                        .clickable { onToggle() }.padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (host.isDisabled) "⚪ غیرفعال" else "🟢 فعال", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = if (host.isDisabled) theme.inkColor else GlassGreen)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.10f)).border(BorderStroke(1.dp, Color.White.copy(0.16f)), RoundedCornerShape(8.dp)).clickable { onClick() }, contentAlignment = Alignment.Center) {
                        Text("✏️", fontSize = 12.sp)
                    }
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(GlassRed.copy(0.10f)).border(BorderStroke(1.dp, GlassRed.copy(0.18f)), RoundedCornerShape(8.dp)).clickable { onDelete() }, contentAlignment = Alignment.Center) {
                        Text("🗑", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HostCompactRow(host: PanelHost, onClick: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
    val theme = LocalThemeState.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(glassBg(theme.isDark))
            .border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                Text(if (host.security == "tls") "🔒" else "🔓", fontSize = 13.sp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(host.remark, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = theme.inkColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(theme.lamp.primary.copy(0.14f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(host.inboundTag.ifEmpty { "Default" }, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = theme.lamp.primary)
                        }
                    }
                    Text(host.address.joinToString(", ").ifEmpty { "تمام دامنه‌ها (*)" }, fontSize = 10.5.sp, color = theme.mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.height(26.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (host.isDisabled) Color.White.copy(0.08f) else GlassGreen.copy(0.14f))
                        .clickable { onToggle() }.padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (host.isDisabled) "⚪" else "🟢", fontSize = 11.sp)
                }
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.10f)).clickable { onClick() }, contentAlignment = Alignment.Center) { Text("✏️", fontSize = 12.sp) }
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(GlassRed.copy(0.10f)).clickable { onDelete() }, contentAlignment = Alignment.Center) { Text("🗑", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun SkeletonHostCard() {
    val theme = LocalThemeState.current
    Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(20.dp)).background(glassBg(theme.isDark).copy(0.3f)).border(BorderStroke(1.dp, glassBorder(theme.isDark)), RoundedCornerShape(20.dp)))
}
