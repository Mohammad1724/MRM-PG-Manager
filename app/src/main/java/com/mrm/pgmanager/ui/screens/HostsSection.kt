package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.ui.designsystem.AppDialog
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.HostSecurity
import com.mrm.pgmanager.data.model.HostValidation
import com.mrm.pgmanager.data.model.PanelHost
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.MrmEmptyState
import com.mrm.pgmanager.ui.components.MrmErrorState
import com.mrm.pgmanager.ui.components.PGPrimaryButton
import com.mrm.pgmanager.ui.components.PGSearchBar
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.listIntro
import com.mrm.pgmanager.ui.designsystem.rememberListIntro
import com.mrm.pgmanager.ui.designsystem.MrmBusyIndicator
import com.mrm.pgmanager.ui.dialogs.ChipSelector
import com.mrm.pgmanager.ui.dialogs.CompactGlassField
import com.mrm.pgmanager.ui.dialogs.ConfirmActionDialog
import com.mrm.pgmanager.ui.theme.LocalThemeState
import kotlinx.coroutines.launch

/**
 * سه ستونِ پنل + اولویت. سرتیتر و هر ردیف همین لیست را می‌گیرند، پس لبهٔ
 * چپِ ستون‌ها در همهٔ سطرها یکی است (کارتِ قبلی جهتِ هر متن را خودش می‌گذاشت).
 */
private val HOST_COLUMNS = listOf(
    InfraColumn(R.string.col_name, 1f),
    InfraColumn(R.string.col_address, 0.78f),
    InfraColumn(R.string.col_inbound, 0.72f),
    InfraColumn(R.string.col_priority, 0.30f)
)

/**
 * مقادیرِ فرمِ هاست — یک تصویرِ ساده از آنچه کاربر تغییر می‌دهد.
 *
 * جدا از [PanelHost] است چون فرم هم برای «ساخت» (که هنوز هاستی وجود ندارد) و
 * هم برای «ویرایش» استفاده می‌شود.
 */
internal data class HostDraft(
    val remark: String,
    val addresses: List<String>,
    val inboundTag: String?,
    val port: Int?,
    val security: String,
    val disabled: Boolean
)

/**
 * بخشِ «هاست‌ها» — فهرست، ساخت، ویرایش، فعال/غیرفعال و حذف.
 *
 * ### تصمیمِ مهم: PUT کامل، ولی فرمِ کوچک
 * پنل برای ویرایش، **کلِ** بدنهٔ `CreateHost` را می‌خواهد. هاست می‌تواند
 * `transport_settings`، `mux_settings`، `fragment_settings`، `ech` و… داشته
 * باشد؛ اگر فرمِ موبایل فقط همان ۶ فیلدی را که نشان می‌دهد بفرستد، بقیه به
 * پیش‌فرض برمی‌گردند و هاستی که در پنلِ وب با dقت ساخته شده بی‌سروصدا خراب
 * می‌شود. پس `PanelApi.modifyHost` بدنهٔ خامِ همان ردیف را merge می‌کند و فقط
 * کلیدهای تغییرکرده را جایگزین می‌کند.
 */
@Composable
internal fun HostsSection(session: Session, sectionTabs: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val theme = LocalThemeState.current
    val scope = rememberCoroutineScope()

    val hostsKey = PanelCache.hostsKey(session.baseUrl)
    val inboundsKey = PanelCache.inboundsKey(session.baseUrl)
    var hosts by remember(session) {
        mutableStateOf(PanelCache.get<List<PanelHost>>(hostsKey) ?: emptyList())
    }
    var inbounds by remember(session) {
        mutableStateOf(PanelCache.get<List<String>>(inboundsKey) ?: emptyList())
    }
    var loading by remember(session) { mutableStateOf(PanelCache.get<List<PanelHost>>(hostsKey) == null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    // null = بسته، PanelHost با id = 0 = «ساختِ جدید».
    var editing by remember { mutableStateOf<PanelHost?>(null) }
    var deleting by remember { mutableStateOf<PanelHost?>(null) }
    var busyId by remember { mutableStateOf<Int?>(null) }

    val msgCreated = stringResource(R.string.host_created)
    val msgUpdated = stringResource(R.string.host_updated)
    val msgDeleted = stringResource(R.string.host_deleted)
    val msgEnabledFmt = stringResource(R.string.host_enabled_fmt)
    val msgDisabledFmt = stringResource(R.string.host_disabled_fmt)

    suspend fun load() {
        runCatching { PanelApi.hosts(session) }
            .onSuccess { hosts = it; loadError = null; PanelCache.put(hostsKey, it) }
            .onFailure { loadError = com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, it) }
        // تگ‌های inbound برای انتخابگرِ فرم؛ نبودشان صفحه را از کار نمی‌اندازد.
        runCatching { PanelApi.inboundTags(session) }
            .onSuccess { inbounds = it; PanelCache.put(inboundsKey, it) }
        loading = false
    }

    LaunchedEffect(session) {
        if (!PanelCache.isFresh(hostsKey)) {
            loading = hosts.isEmpty()
            load()
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(2200)
            toast = null
        }
    }

    val filtered = remember(hosts, query) {
        val q = query.trim()
        if (q.isEmpty()) hosts
        else hosts.filter { h ->
            h.remark.contains(q, ignoreCase = true) ||
                h.addresses.any { it.contains(q, ignoreCase = true) } ||
                h.inboundTag?.contains(q, ignoreCase = true) == true
        }
    }

    val canCreate = com.mrm.pgmanager.data.AdminAccess.can("hosts", "create")
    val canUpdate = com.mrm.pgmanager.data.AdminAccess.can("hosts", "update")

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A phone or large text needs more room per value than a four-column table.
        val readableRows = maxWidth < 560.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        val intro = if (filtered.isNotEmpty()) rememberListIntro() else null
        InfraScrollableContent(
            listTag = "infra_hosts_list",
            header = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)) {
                    sectionTabs()
                    InfraSectionHeader(
                        title = stringResource(R.string.hosts_title),
                        subtitle = stringResource(R.string.hosts_subtitle),
                        count = hosts.size,
                        onRefresh = { scope.launch { loading = hosts.isEmpty(); load() } },
                        onAdd = if (canCreate) ({ editing = PanelHost(id = 0, remark = "") }) else null,
                        addLabel = stringResource(R.string.add_host)
                    )
                    if (toast != null) InfraBanner(text = toast.orEmpty(), success = true)
                    banner?.let { InfraBanner(text = it, success = false) { banner = null } }
                    if (hosts.isNotEmpty()) {
                        PGSearchBar(
                            query = query, onQueryChange = { query = it },
                            placeholder = stringResource(R.string.search_hosts)
                        )
                    }
                    if (hosts.isNotEmpty() && filtered.isNotEmpty() && !readableRows) {
                        InfraTableHeader(HOST_COLUMNS)
                    }
                }
            }
        ) {
            when {
                loading && hosts.isEmpty() -> item(key = "hosts_loading") {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        MrmBusyIndicator(color = theme.accentPrimary, size = 40.dp, strokeWidth = 2.5.dp)
                    }
                }
                loadError != null && hosts.isEmpty() -> item(key = "hosts_error") {
                    MrmErrorState(onRetry = { scope.launch { loading = true; load() } }, message = loadError)
                }
                hosts.isEmpty() -> item(key = "hosts_empty") {
                    MrmEmptyState(
                        title = stringResource(R.string.no_hosts),
                        subtitle = stringResource(R.string.no_hosts_hint),
                        icon = AppIcon.Link
                    ) {
                        if (canCreate) PGPrimaryButton(
                            stringResource(R.string.add_host),
                            onClick = { editing = PanelHost(id = 0, remark = "") },
                            icon = AppIcon.Add
                        )
                    }
                }
                filtered.isEmpty() -> item(key = "hosts_no_results") {
                    MrmEmptyState(
                        title = stringResource(R.string.no_results),
                        subtitle = stringResource(R.string.no_results_hint),
                        icon = AppIcon.Search
                    ) { PGSecondaryButton(stringResource(R.string.clear_filter), onClick = { query = "" }) }
                }
                else -> itemsIndexed(filtered, key = { _, host -> host.id }) { index, host ->
                    Box(Modifier.animateItem().listIntro(requireNotNull(intro), index)) {
                        HostRow(
                            host = host,
                            readable = readableRows,
                            busy = busyId == host.id,
                            onEdit = if (canUpdate) ({ editing = host }) else null,
                            onToggle = if (canUpdate) ({
                                busyId = host.id
                                scope.launch {
                                    runCatching {
                                        PanelApi.setHostsDisabled(session, setOf(host.id), !host.isDisabled)
                                    }.onSuccess { count ->
                                        toast = if (host.isDisabled) String.format(msgEnabledFmt, count)
                                        else String.format(msgDisabledFmt, count)
                                        load()
                                    }.onFailure {
                                        banner = com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, it)
                                    }
                                    busyId = null
                                }
                            }) else null,
                            onDelete = if (canUpdate) ({ deleting = host }) else null
                        )
                    }
                }
            }
        }
    }

    // ── ویرایشگر (ساخت/ویرایش)
    editing?.let { target ->
        HostEditorDialog(
            initial = target,
            availableInbounds = inbounds,
            onDismiss = { editing = null },
            onSave = { draft, onResult ->
                scope.launch {
                    val isCreate = target.id == 0
                    runCatching {
                        if (isCreate) {
                            PanelApi.createHost(
                                session = session,
                                remark = draft.remark,
                                addresses = draft.addresses,
                                inboundTag = draft.inboundTag,
                                port = draft.port,
                                security = draft.security
                            )
                        } else {
                            // merge روی بدنهٔ خام: فیلدهای پیشرفتهٔ ناشناخته حفظ می‌شوند.
                            PanelApi.modifyHost(
                                session, target,
                                mapOf(
                                    "remark" to draft.remark,
                                    "address" to org.json.JSONArray(draft.addresses),
                                    "port" to (draft.port ?: org.json.JSONObject.NULL),
                                    "security" to draft.security,
                                    "inbound_tag" to (draft.inboundTag ?: org.json.JSONObject.NULL),
                                    "is_disabled" to draft.disabled
                                )
                            )
                        }
                    }.onSuccess {
                        editing = null
                        toast = if (isCreate) msgCreated else msgUpdated
                        load()
                    }.onFailure { e ->
                        // دیالوگ باز می‌ماند تا ورودی از دست نرود.
                        onResult(com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, e))
                    }
                }
            }
        )
    }

    deleting?.let { target ->
        ConfirmActionDialog(
            title = stringResource(R.string.delete_host),
            message = stringResource(R.string.delete_host_confirm, target.remark),
            confirmLabel = stringResource(R.string.delete_host),
            danger = true,
            onDismiss = { deleting = null },
            onConfirm = {
                scope.launch {
                    runCatching { PanelApi.deleteHost(session, target.id) }
                        .onSuccess { toast = msgDeleted; load() }
                        .onFailure { banner = com.mrm.pgmanager.utils.ApiErrorMapper.friendly(context, it) }
                    deleting = null
                }
            }
        )
    }
}

/** A host keeps the same actions in both the wide table and narrow list. */
@Composable
internal fun HostRow(
    host: PanelHost,
    readable: Boolean,
    busy: Boolean,
    onEdit: (() -> Unit)?,
    onToggle: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val theme = LocalThemeState.current
    val explicitSecurity = when (host.security) {
        HostSecurity.NONE, HostSecurity.TLS -> securityLabel(host.security)
        else -> ""
    }
    val tag = host.inboundTag?.takeIf { it.isNotBlank() }
    val address = buildString {
        append(host.addresses.joinToString(", "))
        host.port?.let { append(":").append(it) }
    }.ifBlank { "—" }
    val inbound = when {
        tag != null && explicitSecurity.isNotEmpty() -> "$tag · $explicitSecurity"
        tag != null -> tag
        else -> explicitSecurity.ifEmpty { "—" }
    }
    val priority = host.priority?.toString() ?: "—"
    val actions = hostRowActions(host, onEdit, onToggle, onDelete)
    if (readable) {
        HostReadableRow(
            name = host.remark, address = address, inbound = inbound, priority = priority,
            disabled = host.isDisabled, busy = busy, actions = actions, onEdit = onEdit
        )
    } else {
        InfraTableRow(
            columns = HOST_COLUMNS,
            name = host.remark,
            cells = listOf(address, inbound, priority),
            dot = if (host.isDisabled) theme.mutedColor else DsSemantic.Success,
            muted = host.isDisabled,
            busy = busy,
            actions = actions,
            onEdit = onEdit
        )
    }
}

internal fun hostRowActions(
    host: PanelHost,
    onEdit: (() -> Unit)?,
    onToggle: (() -> Unit)?,
    onDelete: (() -> Unit)?
): List<InfraRowAction> = buildList {
    if (onEdit != null) add(InfraRowAction(R.string.qa_edit) { onEdit() })
    if (onToggle != null) add(
        InfraRowAction(if (host.isDisabled) R.string.host_toggle_enable else R.string.host_toggle_disable) { onToggle() }
    )
    if (onDelete != null) add(InfraRowAction(R.string.delete_host, danger = true) { onDelete() })
}

/** Separate labeled values on narrow screens instead of squeezing four columns together. */
@Composable
private fun HostReadableRow(
    name: String,
    address: String,
    inbound: String,
    priority: String,
    disabled: Boolean,
    busy: Boolean,
    actions: List<InfraRowAction>,
    onEdit: (() -> Unit)?
) {
    val theme = LocalThemeState.current
    val statusLabel = stringResource(if (disabled) R.string.host_disabled_badge else R.string.active)
    Column(
        Modifier.fillMaxWidth().testTag("host_readable_row")
            .clip(DsRadius.Lg).background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
            .then(if (onEdit != null) Modifier.clickable { onEdit() } else Modifier)
            .padding(start = DsSpacing.Card, end = DsSpacing.Card, top = DsSpacing.Xxs, bottom = DsSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.Xxs)
    ) {
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
        ) {
            Box(
                Modifier.size(12.dp).semantics { contentDescription = statusLabel },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(7.dp).clip(DsRadius.Full)
                        .background(if (disabled) theme.mutedColor else DsSemantic.Success)
                )
            }
            Text(
                name, modifier = Modifier.weight(1f).testTag("host_readable_name"),
                fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
                color = if (disabled) theme.mutedColor else theme.inkColor,
                textAlign = com.mrm.pgmanager.ui.components.PGStartAlign()
            )
            InfraRowActions(busy = busy, actions = actions)
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = DsSpacing.Xs),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.Xxs)
        ) {
            HostInfoLine(stringResource(R.string.col_address), address, "host_readable_address")
            HostInfoLine(stringResource(R.string.col_inbound), inbound, "host_readable_inbound")
            HostInfoLine(stringResource(R.string.col_priority), priority, "host_readable_priority")
        }
    }
}

@Composable
private fun HostInfoLine(label: String, value: String, tag: String) {
    val theme = LocalThemeState.current
    Row(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
    ) {
        Text(
            "$label:", fontSize = 10.sp, lineHeight = 16.sp, color = theme.mutedColor,
            modifier = Modifier.widthIn(min = 54.dp, max = 96.dp).alignByBaseline().testTag("${tag}_label"),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = com.mrm.pgmanager.ui.components.PGStartAlign()
        )
        Text(
            value, modifier = Modifier.weight(1f).alignByBaseline().testTag(tag),
            fontSize = 11.sp, lineHeight = 16.sp, color = theme.inkColor,
            style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
            textAlign = androidx.compose.ui.text.style.TextAlign.Left
        )
    }
}

/** برچسبِ فارسی/انگلیسیِ `security` — مقدارِ خامِ پنل نگاشت می‌شود. */
@Composable
private fun securityLabel(value: String): String = when (value) {
    HostSecurity.NONE -> stringResource(R.string.host_security_none)
    HostSecurity.TLS -> stringResource(R.string.host_security_tls)
    else -> stringResource(R.string.host_security_inbound_default)
}

/**
 * دیالوگِ ساخت/ویرایشِ هاست.
 *
 * فیلدها عمداً کم و کوتاه‌اند؛ هر چیزی که اینجا نیست (transport، mux، fragment،
 * ECH، wireguard، final-mask…) در ویرایش دست‌نخورده می‌ماند. برای هاستِ تازه هم
 * پنل پیش‌فرض‌های خودش را می‌گذارد.
 */
@Composable
private fun HostEditorDialog(
    initial: PanelHost,
    availableInbounds: List<String>,
    onDismiss: () -> Unit,
    onSave: (HostDraft, (String) -> Unit) -> Unit
) {
    val theme = LocalThemeState.current
    val isCreate = initial.id == 0

    var remark by remember(initial.id) { mutableStateOf(initial.remark) }
    var addresses by remember(initial.id) { mutableStateOf(initial.addresses.joinToString(", ")) }
    var port by remember(initial.id) { mutableStateOf(initial.port?.toString() ?: "") }
    var security by remember(initial.id) { mutableStateOf(initial.security) }
    var inbound by remember(initial.id) { mutableStateOf(initial.inboundTag ?: "") }
    var disabled by remember(initial.id) { mutableStateOf(initial.isDisabled) }
    var saving by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    var serverError by remember(initial.id) { mutableStateOf<String?>(null) }

    // تگِ فعلی هم در فهرست می‌ماند حتی اگر در کورها نباشد؛ وگرنه با یک ذخیره
    // بی‌صدا حذف می‌شد.
    val tags = remember(availableInbounds, initial.inboundTag) {
        (listOf("") + (availableInbounds + listOfNotNull(initial.inboundTag)).distinct()).distinct()
    }
    val addressList = remember(addresses) {
        addresses.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    val errorKey = HostValidation.validate(remark, addressList, port)
    val errRemark = stringResource(R.string.err_host_remark)
    val errAddress = stringResource(R.string.err_host_address)
    val errPort = stringResource(R.string.err_host_port)
    val errorText = when {
        serverError != null -> serverError
        !touched -> null
        errorKey == HostValidation.ERR_REMARK -> errRemark
        errorKey == HostValidation.ERR_ADDRESS -> errAddress
        errorKey == HostValidation.ERR_PORT -> errPort
        else -> null
    }
    val canSave = errorKey == null && !saving

    AppDialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().imePadding().clip(DsRadius.Xxl).background(theme.dialogBgColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Xxl)
                .padding(DsSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.Lg)
        ) {
            Text(
                stringResource(if (isCreate) R.string.create_host else R.string.edit_host),
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.inkColor
            )

            InfraFieldLabel(stringResource(R.string.host_remark))
            CompactGlassField(
                value = remark,
                onValueChange = { remark = it; touched = true; serverError = null },
                placeholder = stringResource(R.string.host_remark_hint),
                keyboardType = KeyboardType.Text,
                leadingAppIcon = AppIcon.Note
            )

            InfraFieldLabel(stringResource(R.string.host_address))
            CompactGlassField(
                value = addresses,
                onValueChange = { addresses = it; touched = true; serverError = null },
                placeholder = stringResource(R.string.host_address_hint),
                keyboardType = KeyboardType.Text,
                leadingAppIcon = AppIcon.Link
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
                    InfraFieldLabel(stringResource(R.string.host_port))
                    CompactGlassField(
                        value = port,
                        onValueChange = { raw ->
                            port = com.mrm.pgmanager.utils.normalizePersianDigits(raw).filter(Char::isDigit)
                            touched = true
                            serverError = null
                        },
                        placeholder = stringResource(R.string.host_port_hint),
                        keyboardType = KeyboardType.Number
                    )
                }
            }

            InfraFieldLabel(stringResource(R.string.host_security))
            ChipSelector(
                values = HostSecurity.ALL,
                labels = HostSecurity.ALL.map { securityLabel(it) },
                selected = security,
                onSelect = { security = it; touched = true; serverError = null }
            )

            InfraFieldLabel(stringResource(R.string.host_inbound))
            if (tags.size <= 1) {
                Text(stringResource(R.string.host_no_inbounds), fontSize = 11.sp, color = theme.mutedColor)
            } else {
                ChipSelector(
                    values = tags,
                    labels = tags.map { if (it.isEmpty()) stringResource(R.string.host_inbound_auto) else it },
                    selected = inbound,
                    onSelect = { inbound = it; touched = true; serverError = null }
                )
            }

            if (!isCreate) {
                Row(
                    Modifier.fillMaxWidth().clip(DsRadius.Md).clickable { disabled = !disabled },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
                ) {
                    com.mrm.pgmanager.ui.dialogs.CheckboxIcon(
                        selected = disabled, onToggle = { disabled = !disabled }
                    )
                    Text(
                        stringResource(R.string.host_disabled_badge),
                        fontSize = 12.sp, color = theme.inkColor
                    )
                }
            }

            Text(stringResource(R.string.host_raw_note), fontSize = 10.sp, color = theme.mutedColor)

            errorText?.let {
                Text(it, fontSize = 11.sp, color = DsSemantic.Danger, fontWeight = FontWeight.SemiBold)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.Md)) {
                PGSecondaryButton(
                    stringResource(R.string.cancel),
                    onClick = { if (!saving) onDismiss() },
                    modifier = Modifier.weight(1f)
                )
                PGPrimaryButton(
                    stringResource(R.string.save_changes),
                    onClick = {
                        touched = true
                        if (canSave) {
                            saving = true
                            serverError = null
                            onSave(
                                HostDraft(
                                    remark = remark.trim(),
                                    addresses = addressList,
                                    inboundTag = inbound.takeIf { it.isNotBlank() },
                                    port = port.toIntOrNull(),
                                    security = security,
                                    disabled = disabled
                                )
                            ) { err ->
                                saving = false
                                serverError = err
                            }
                        }
                    },
                    // بی‌وزن تا متنِ دکمه بریده نشود؛ «لغو» باقیِ جا را می‌گیرد.
                    enabled = canSave
                )
            }
        }
    }
}
