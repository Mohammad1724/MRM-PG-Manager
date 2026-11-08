package com.mrm.pgmanager.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.AdminAccess
import com.mrm.pgmanager.data.api.NodeLogReadResult
import com.mrm.pgmanager.data.api.NodeLogUnexpectedContentException
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.api.readNodeLogEvents
import com.mrm.pgmanager.data.model.NodeLogFilter
import com.mrm.pgmanager.data.model.NodeLogLine
import com.mrm.pgmanager.data.model.NodeLogLevel
import com.mrm.pgmanager.data.model.NodeLogRetryPolicy
import com.mrm.pgmanager.data.model.NodeLogs
import com.mrm.pgmanager.data.model.PanelNode
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.PGPageHeader
import com.mrm.pgmanager.ui.components.MrmEmptyState
import com.mrm.pgmanager.ui.components.MrmErrorState
import com.mrm.pgmanager.ui.components.MrmText
import com.mrm.pgmanager.ui.components.PGWrapRow
import com.mrm.pgmanager.ui.components.PGBadge
import com.mrm.pgmanager.ui.components.PGSecondaryButton
import com.mrm.pgmanager.ui.components.PGSearchBar
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.components.TechnicalContainer
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSemantic
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.MrmBusyIndicator
import com.mrm.pgmanager.ui.designsystem.listIntro
import com.mrm.pgmanager.ui.designsystem.rememberListIntro
import com.mrm.pgmanager.ui.dialogs.ChipSelector
import com.mrm.pgmanager.ui.dialogs.ConfirmActionDialog
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.ApiErrorMapper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** وضعیتِ جریانِ لاگ — حالت‌هایی که کاربر باید از هم تشخیص بدهد. */
enum class NodeLogStream {
    /** نودی انتخاب نشده (یا مجوز نیست) — جریانی باز نیست. */
    Idle,

    /** درخواست رفته، هنوز اولین خط نیامده. */
    Connecting,

    /** A transient failure is waiting for a bounded automatic reconnect. */
    Retrying,

    /** حداقل یک خط رسیده و جریان باز است. */
    Streaming,

    /** پنل جریان را بست (نود قطع شد یا سرور بست) — با «ادامه» دوباره باز می‌شود. */
    Ended,

    /** خطای شبکه/اجازه؛ متنش در بنر است. */
    Failed
}

/**
 * **لاگِ زندهٔ نود** — جریانِ `GET /api/node/{id}/logs` (SSE، مجوزِ `nodes.logs`).
 *
 * ### چرا یک صفحهٔ کامل
 * نصفِ «نود قطع شد»ها در گفتگو با پشتیبانی با یک سؤالِ ساده جواب می‌گیرد: لاگِ
 * خودِ نود چه می‌گوید. تا امروز برای دیدنش باید پشتِ ssh یا تبِ «Logs» پنل
 * می‌نشستی؛ با همین صفحه، ادمینِ فقط-گوشی همان جریانِ زنده را می‌بیند و همان
 * خط‌ها را کپی می‌کند.
 *
 * ### سه تصمیمی که فرق می‌کنند
 *  • The screen's coroutine cancels its own connection on disposal or restart;
 *    the stream reader interrupts a blocked handshake or read and closes its response.
 *  • Transient disconnects use a bounded exponential backoff. Authorization and
 *    invalid-content failures stop immediately; manual reconnect remains available.
 *  • **بافرِ حلقوی، نه بی‌نهایت.** فقط `NodeLogs.MaxLines` خطِ آخر می‌ماند؛ با
 *    نودِ پرحجم (access log) وگرنه چند دقیقه نگاه‌داشتنِ لاگ پروسه را می‌کُشد.
 *  • «محدودۀ خط»ِ جدا (مثلِ `LineCountFilter` پنل) ندارد: همان سقفِ بافر روی
 *    گوشی هم کارِ محدودکردنِ حافظه را می‌کند و هم چیپِ اضافه‌ای به نوارِ فیلتر
 *    اضافه نمی‌کند.
 *
 * منطقِ تجزیه/فیلتر در `NodeLogs` است و تستِ JVM دارد؛ صفحه فقط حالت را نگه
 * می‌دارد و به [NodeLogsContent] می‌دهد تا همان محتوا با دادهٔ ثابت قابلِ آزمون
 * باشد (جریانِ زنده در Robolectric هرگز idle نمی‌شود).
 *
 * @param nodeId نودِ انتخابی از جایی که صفحه باز شده (کارتِ نود در «آمار»);
 *   `null` یعنی «هر نودی که متصل است».
 */
@Composable
fun NodeLogsScreen(session: Session, nodeId: Int? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canLogs = AdminAccess.can("nodes", "logs")
    var pendingExport by remember(session) { mutableStateOf<String?>(null) }
    var confirmExport by remember(session) { mutableStateOf<List<NodeLogLine>?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = pendingExport
        pendingExport = null
        if (uri != null && text != null) scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("Unable to open output")
                    output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }.isSuccess
            }
            if (saved) com.mrm.pgmanager.ui.feedback.AppFeedback.success(context.getString(R.string.us_file_saved))
            else com.mrm.pgmanager.ui.feedback.AppFeedback.error(context.getString(R.string.us_file_save_failed))
        }
    }

    // Do not reuse another account's panel-wide node cache before its access is verified.
    var nodes by remember(session) { mutableStateOf(emptyList<PanelNode>()) }
    var nodesLoading by remember(session) { mutableStateOf(true) }
    var nodesReady by remember(session) { mutableStateOf(false) }
    var nodesError by remember(session) { mutableStateOf<String?>(null) }
    // فقط نودِ متصل لاگ می‌دهد (پنل هم در انتخاب‌گرش بقیه را نمی‌گذارد).
    val connected = remember(nodes) { nodes.filter { it.isConnected } }

    var selected by remember(session) { mutableStateOf(nodeId ?: connected.firstOrNull()?.id) }
    var stream by remember(session) { mutableStateOf(NodeLogStream.Idle) }
    var streamError by remember(session) { mutableStateOf<String?>(null) }

    var lines by remember(session) { mutableStateOf(emptyList<NodeLogLine>()) }
    var pending by remember(session) { mutableStateOf(emptyList<NodeLogLine>()) }
    // شناسهٔ یکتا برای `key = { it.id }` لازم است؛ بافر از سرِ فهرست کم می‌شود،
    // پس هیچ‌وقت به صفر برنمی‌گردد تا کلیدها قاطی نشوند.
    var seq by remember(session) { mutableStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var levels by remember { mutableStateOf(emptySet<NodeLogLevel>()) }
    var windowMs by remember { mutableStateOf<Long?>(null) }
    var filterNow by remember { mutableStateOf(System.currentTimeMillis()) }
    var restartToken by remember { mutableStateOf(0) }

    val filter = NodeLogFilter(query = query, levels = levels, windowMs = windowMs)

    var nodesRequest by remember(session) { mutableStateOf(0L) }
    suspend fun loadNodes() {
        val request = ++nodesRequest
        nodesLoading = nodes.isEmpty()
        try {
            val result = PanelApi.nodesStrict(session)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (request == nodesRequest) {
                nodes = result
                nodesError = null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (request == nodesRequest) {
                val kind = ApiErrorMapper.kindOf(error)
                if (kind == com.mrm.pgmanager.utils.ApiErrorKind.FORBIDDEN ||
                    kind == com.mrm.pgmanager.utils.ApiErrorKind.UNAUTHORIZED) nodes = emptyList()
                nodesError = ApiErrorMapper.friendly(context, error)
            }
        } finally {
            if (request == nodesRequest) {
                nodesLoading = false
                nodesReady = true
            }
        }
    }

    LaunchedEffect(session, canLogs) {
        if (canLogs) loadNodes() else {
            nodesRequest++
            nodes = emptyList()
            nodesLoading = false
            nodesReady = false
            nodesError = null
            lines = emptyList()
            pending = emptyList()
            confirmExport = null
            pendingExport = null
            streamError = null
        }
    }

    // Preserve a requested node until the initial list has actually been loaded.
    LaunchedEffect(connected, nodesReady) {
        if (nodesReady) {
            val id = selected
            if (id == null || connected.none { it.id == id }) selected = connected.firstOrNull()?.id
        }
    }

    // Rows from a previous node must never appear under an automatically selected fallback.
    LaunchedEffect(session, selected) {
        lines = emptyList()
        pending = emptyList()
        confirmExport = null
    }

    val selectedConnected = connected.any { it.id == selected }
    LaunchedEffect(session, selected, restartToken, canLogs, nodesReady, selectedConnected) {
        val node = selected
        if (!nodesReady || !canLogs || node == null || !selectedConnected) {
            stream = NodeLogStream.Idle
            return@LaunchedEffect
        }
        var attempts = 0
        while (true) {
            stream = NodeLogStream.Connecting
            streamError = null
            var connectedAtNanos: Long? = null
            var failure: Exception? = null
            try {
                val connection = PanelApi.openNodeLogs(session, node)
                // A quiet SSE stream is already connected once the server accepts it.
                connectedAtNanos = System.nanoTime()
                stream = NodeLogStream.Streaming
                val result = readNodeLogEvents(connection) { event ->
                    val parsed = NodeLogs.parse(event, seq)
                    seq += parsed.size.toLong()
                    if (parsed.isNotEmpty()) {
                        if (paused) pending = NodeLogs.appendTrim(pending, parsed)
                        else lines = NodeLogs.appendTrim(lines, parsed)
                    }
                }
                if (result == NodeLogReadResult.Closed) return@LaunchedEffect
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failure = error
            }
            // A stable stream resets the budget; an immediate error event followed by EOF does not.
            val connectedAt = connectedAtNanos
            if (connectedAt != null && System.nanoTime() - connectedAt >= 60_000_000_000L) attempts = 0
            val transient = failure == null ||
                (failure !is NodeLogUnexpectedContentException && ApiErrorMapper.isTransient(ApiErrorMapper.kindOf(failure)))
            if (!transient || attempts >= NodeLogRetryPolicy.MaxAttempts) {
                stream = if (failure == null) NodeLogStream.Ended else NodeLogStream.Failed
                streamError = when (failure) {
                    null -> null
                    is NodeLogUnexpectedContentException -> context.getString(R.string.nl_invalid_stream)
                    else -> ApiErrorMapper.friendly(context, failure)
                }
                return@LaunchedEffect
            }
            attempts++
            stream = NodeLogStream.Retrying
            streamError = failure?.let { ApiErrorMapper.friendly(context, it) }
            delay(NodeLogRetryPolicy.delayMillis(attempts))
        }
    }

    // A quiet or paused stream must still age rows out of an active time window.
    LaunchedEffect(windowMs) {
        if (windowMs != null) {
            while (true) {
                filterNow = System.currentTimeMillis()
                delay(15_000)
            }
        }
    }
    val visible = remember(lines, query, levels, windowMs, filterNow) {
        NodeLogs.visible(lines, filter, filterNow)
    }

    fun formatLogRows(rows: List<NodeLogLine>): String = rows.joinToString("\n") { row ->
        val stamp = row.timestampText?.takeIf { it.isNotBlank() }
            ?: row.timestampMs?.let { logTimeFormat.format(Date(it)) }
        if (stamp.isNullOrBlank()) row.message else "$stamp ${row.message}"
    }

    NodeLogsContent(
        nodes = connected,
        selectedNode = selected,
        stream = stream,
        streamError = streamError,
        nodesLoading = nodesLoading,
        nodesError = nodesError,
        canLogs = canLogs,
        total = lines.size,
        visible = visible,
        hidden = lines.size - visible.size,
        buffered = pending.size,
        paused = paused,
        filter = filter,
        onBack = onBack,
        onDismissError = { streamError = null },
        onRefresh = {
            scope.launch {
                if (canLogs) {
                    loadNodes()
                    restartToken += 1
                }
            }
        },
        onSelectNode = { id -> selected = id },
        onTogglePause = {
            if (paused) {
                if (pending.isNotEmpty()) lines = NodeLogs.appendTrim(lines, pending)
                pending = emptyList()
            }
            paused = !paused
        },
        onClear = {
            lines = emptyList()
            pending = emptyList()
        },
        onReconnect = { restartToken += 1 },
        onQueryChange = { query = it },
        onToggleLevel = { level ->
            levels = if (level in levels) levels - level else levels + level
        },
        onWindowChange = { key -> windowMs = NodeLogFilter.msOf(key) },
        onCopy = { rows ->
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(context.getString(R.string.nl_title), formatLogRows(rows)))
            com.mrm.pgmanager.ui.feedback.AppFeedback.success(context.getString(R.string.copied))
        },
        onExport = { rows -> confirmExport = rows.toList() }
    )
    confirmExport?.let { rows ->
        ConfirmActionDialog(
            title = stringResource(R.string.nl_export), message = stringResource(R.string.nl_export_warning),
            confirmLabel = stringResource(R.string.nl_export), onDismiss = { confirmExport = null },
            onConfirm = {
                pendingExport = formatLogRows(rows)
                confirmExport = null
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                exportLauncher.launch("node-logs-$stamp.txt")
            }
        )
    }
}

/**
 * بدنهٔ صفحهٔ لاگ، جدا از هر شبکه.
 *
 * چرا پارامترهایش این‌قدرند و چرا کامپوزبلِ جداست: هر چیزی که اینجا دیده
 * می‌شود از پارامتر می‌آید، پس می‌توان با چند خطِ ثابت و بدونِ جریانِ زنده آزمود
 * («هیچ چیپی روشن نیست یعنی همه، نه هیچ»، «پیامِ درست وقتی خط‌ها هست ولی هیچ‌کدام
 * با فیلتر نمی‌خوانند»، «کپی همان چیزی است که نمایش داده می‌شود»). صفحهٔ واقعی
 * فقط حالت را نگه می‌دارد و همین را صدا می‌زند.
 */
@Composable
internal fun NodeLogsContent(
    nodes: List<PanelNode>,
    selectedNode: Int?,
    stream: NodeLogStream,
    streamError: String?,
    nodesLoading: Boolean,
    nodesError: String?,
    canLogs: Boolean,
    total: Int,
    visible: List<NodeLogLine>,
    hidden: Int,
    buffered: Int,
    paused: Boolean,
    filter: NodeLogFilter,
    onBack: () -> Unit,
    onDismissError: () -> Unit,
    onRefresh: () -> Unit,
    onSelectNode: (Int) -> Unit,
    onTogglePause: () -> Unit,
    onClear: () -> Unit,
    onReconnect: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleLevel: (NodeLogLevel) -> Unit,
    onWindowChange: (String) -> Unit,
    onCopy: (List<NodeLogLine>) -> Unit,
    onExport: (List<NodeLogLine>) -> Unit = {}
) {
    val theme = LocalThemeState.current

    Column(
        Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding()
            .navigationBarsPadding().imePadding().padding(
                start = DsSpacing.Screen, end = DsSpacing.Screen,
                top = DsSpacing.Screen, bottom = DsSpacing.Mid
            ),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
    ) {
        // ── سربرگ (هم‌سبکِ «کلیدهای API» و «تنظیماتِ پنل»)
        PGPageHeader(
            title = stringResource(R.string.nl_title),
            subtitle = stringResource(R.string.nl_subtitle),
            onBack = onBack
        ) {
            if (total > 0) PGBadge("$total")
            InfraIconButton(
                icon = AppIcon.Refresh,
                description = stringResource(R.string.refresh),
                tint = theme.mutedColor,
                onClick = onRefresh
            )
        }

        streamError?.let { InfraBanner(text = it, success = false) { onDismissError() } }
        if (nodes.isNotEmpty()) nodesError?.let { InfraBanner(text = it, success = false) }

        when {
            // نقشِ بدونِ `nodes.logs`: درخواست ۴۰۳ می‌گرفت و پیامِ پنل برای
            // کاربرِ گوشی خوانا نیست؛ اینجا صریح گفته می‌شود.
            !canLogs -> MrmEmptyState(
                title = stringResource(R.string.nl_need_permission),
                subtitle = stringResource(R.string.nl_need_permission_hint),
                icon = AppIcon.Lock
            )

            nodes.isEmpty() && nodesLoading -> Box(
                Modifier.fillMaxWidth().heightIn(min = 160.dp),
                contentAlignment = Alignment.Center
            ) { MrmBusyIndicator(color = theme.accentPrimary, size = 40.dp, strokeWidth = 2.5.dp) }

            nodes.isEmpty() && nodesError != null -> MrmErrorState(onRetry = onRefresh, message = nodesError)

            nodes.isEmpty() -> MrmEmptyState(
                title = stringResource(R.string.nl_no_node),
                subtitle = stringResource(R.string.nl_no_node_hint),
                icon = AppIcon.Wifi
            ) {
                PGSecondaryButton(
                    text = stringResource(R.string.refresh),
                    onClick = onRefresh
                )
            }

            else -> {
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val filterMaxHeight = maxHeight * 0.58f
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)) {
                        // Keep the log viewport available when filters wrap or the keyboard is open.
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = filterMaxHeight)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
                        ) {
                            // ── انتخابِ نود (چیپ، نه دیالوگ: معمولاً یک تا سه نود متصل است)
                            ChipSelector(
                                values = nodes.map { it.id.toString() },
                                labels = nodes.map { it.name },
                                selected = selectedNode?.toString(),
                                onSelect = { id -> id.toIntOrNull()?.let(onSelectNode) }
                            )

                            // ── وضعیتِ جریان
                            val statusLine = when (stream) {
                                NodeLogStream.Connecting -> stringResource(R.string.nl_connecting)
                                NodeLogStream.Retrying -> stringResource(R.string.nl_retrying)
                                NodeLogStream.Streaming -> stringResource(R.string.nl_streaming)
                                NodeLogStream.Ended -> stringResource(R.string.nl_ended)
                                NodeLogStream.Failed -> stringResource(R.string.nl_failed)
                                NodeLogStream.Idle -> stringResource(R.string.nl_idle)
                            }
                            Row(
                                Modifier.fillMaxWidth().clip(DsRadius.Md).background(theme.searchBgColor)
                                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Md)
                                    .padding(horizontal = DsSpacing.Mid, vertical = DsSpacing.Sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
                            ) {
                                if (stream == NodeLogStream.Connecting || stream == NodeLogStream.Retrying) {
                                    MrmBusyIndicator(color = theme.accentPrimary, size = 12.dp, strokeWidth = 1.6.dp)
                                } else {
                                    Box(
                                        Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(
                                            when (stream) {
                                                NodeLogStream.Streaming -> DsSemantic.Success
                                                NodeLogStream.Failed -> DsSemantic.Danger
                                                else -> theme.mutedColor
                                            }
                                        )
                                    )
                                }
                                Text(
                                    statusLine, fontSize = 10.sp, color = theme.mutedColor,
                                    modifier = Modifier.weight(1f)
                                )
                                if (stream == NodeLogStream.Ended || stream == NodeLogStream.Failed || stream == NodeLogStream.Retrying) {
                                    val reconnectLabel = stringResource(R.string.nl_reconnect)
                                    Text(
                                        reconnectLabel, fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold, color = theme.accentTextColor,
                                        modifier = Modifier.clip(DsRadius.Sm).clickable { onReconnect() }
                                            .heightIn(min = 48.dp)
                                            .padding(horizontal = DsSpacing.Mid, vertical = DsSpacing.Xs)
                                            .semantics { contentDescription = reconnectLabel }
                                    )
                                }
                            }

                            // Keep search usable on narrow screens while preserving all actions.
                            PGSearchBar(
                                query = filter.query,
                                onQueryChange = onQueryChange,
                                placeholder = stringResource(R.string.nl_search)
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
                            ) {
                                InfraIconButton(
                                    icon = if (paused) AppIcon.Play else AppIcon.Pause,
                                    description = stringResource(if (paused) R.string.nl_resume else R.string.nl_pause),
                                    tint = theme.accentTextColor,
                                    onClick = onTogglePause
                                )
                                InfraIconButton(
                                    icon = AppIcon.Copy,
                                    description = stringResource(R.string.nl_copy),
                                    tint = theme.mutedColor,
                                    onClick = { onCopy(visible) },
                                    enabled = visible.isNotEmpty()
                                )
                                InfraIconButton(
                                    icon = AppIcon.Download,
                                    description = stringResource(R.string.nl_export),
                                    tint = theme.mutedColor,
                                    onClick = { onExport(visible) },
                                    enabled = visible.isNotEmpty()
                                )
                                InfraIconButton(
                                    icon = AppIcon.Delete,
                                    description = stringResource(R.string.nl_clear),
                                    tint = theme.mutedColor,
                                    onClick = onClear,
                                    enabled = total > 0
                                )
                            }

                            if (paused) Text(
                                if (buffered > 0) stringResource(R.string.nl_paused_buffered, buffered.toString())
                                else stringResource(R.string.nl_paused),
                                fontSize = 10.sp, color = theme.warningTextColor
                            )

                            // ── چیپ‌های شدت (چندانتخابی: «فقط خطاها» + «فقط آخرش» پرتکرارترین
                            // استفاده است) و بازهٔ زمانی (تک‌انتخابی، مثلِ پنل)
                            val levelLabels = listOf(
                                NodeLogLevel.Error to stringResource(R.string.nl_level_error),
                                NodeLogLevel.Warning to stringResource(R.string.nl_level_warning),
                                NodeLogLevel.Info to stringResource(R.string.nl_level_info),
                                NodeLogLevel.Debug to stringResource(R.string.nl_level_debug)
                            )
                            PGWrapRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalGap = DsSpacing.Sm,
                                verticalGap = DsSpacing.Xxs
                            ) {
                                levelLabels.forEach { (level, label) ->
                                    NodeLogFilterChip(
                                        label = label,
                                        selected = level in filter.levels,
                                        accent = logColor(level),
                                        onClick = { onToggleLevel(level) }
                                    )
                                }
                            }
                            ChipSelector(
                                values = NodeLogFilter.windows.map { it.first },
                                labels = listOf(
                                    stringResource(R.string.nl_time_all),
                                    stringResource(R.string.nl_time_1m),
                                    stringResource(R.string.nl_time_15m),
                                    stringResource(R.string.nl_time_1h),
                                    stringResource(R.string.nl_time_6h),
                                    stringResource(R.string.nl_time_24h)
                                ),
                                selected = NodeLogFilter.keyOf(filter.windowMs),
                                onSelect = onWindowChange
                            )



                        }
                        // ── خط‌ها
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            var follow by remember { mutableStateOf(true) }
                            val listState = rememberLazyListState()
                            LaunchedEffect(listState) {
                                // تصمیم فقط وقتی: «پایین است» → دنبال کن، «کاربر می‌کشد» →
                                // ولش کن. یک فریمِ کوتاهِ وسطِ انیمیشن نباید دنبال‌کردنِ
                                // خودکار را خاموش کند، پس شرطِ واسط لازم نیست.
                                // `kotlinx.coroutines.flow.filter` عمداً به‌کار نرفته: همان
                                // دو شرطِ داخلِ collect است و importِ اضافه معنای جدید به
                                // واژۀ این فایل نمی‌دهد.
                                snapshotFlow { listState.isScrollInProgress to logAtBottom(listState) }
                                    .collect { (dragging, atBottom) ->
                                        if (atBottom) follow = true else if (dragging) follow = false
                                    }
                            }
                            LaunchedEffect(visible.size, visible.lastOrNull()?.id, follow) {
                                if (follow && visible.isNotEmpty()) listState.scrollToItem(visible.lastIndex)
                            }
                            TechnicalContainer(modifier = Modifier.fillMaxSize()) {
                                when {
                                    visible.isEmpty() -> Column(
                                        Modifier.fillMaxSize().padding(DsSpacing.Xl),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            if (total == 0) stringResource(
                                                if (stream == NodeLogStream.Connecting || stream == NodeLogStream.Retrying) R.string.nl_waiting else R.string.nl_empty
                                            ) else stringResource(R.string.nl_filtered_empty),
                                            fontSize = 11.sp, color = theme.mutedColor
                                        )
                                        if (total > 0) Text(
                                            stringResource(R.string.nl_filtered_count, hidden.toString(), total.toString()),
                                            fontSize = 10.sp, color = theme.tertiaryColor
                                        )
                                    }

                                    else -> {
                                        val intro = rememberListIntro()
                                        var expandedId by remember { mutableStateOf<Long?>(null) }
                                        LazyColumn(
                                            state = listState,
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(DsSpacing.Xs),
                                            contentPadding = PaddingValues(bottom = DsSpacing.Xl)
                                        ) {
                                            itemsIndexed(visible, key = { _, line -> line.id }) { index, line ->
                                                Box(Modifier.listIntro(intro, index)) {
                                                    NodeLogRow(
                                                        line = line,
                                                        expanded = expandedId == line.id,
                                                        onClick = {
                                                            expandedId = if (expandedId == line.id) null else line.id
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        if (!follow) Box(
                                            Modifier.align(Alignment.BottomCenter).padding(DsSpacing.Mid)
                                                .clip(DsRadius.Full).background(theme.cardSurfaceColor)
                                                .border(
                                                    BorderStroke(DsBorder.Hairline, theme.borderColor),
                                                    DsRadius.Full
                                                )
                                                .clickable { follow = true }
                                                .heightIn(min = 48.dp)
                                                .padding(horizontal = DsSpacing.Lg, vertical = DsSpacing.Md),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(DsSpacing.Xs)
                                            ) {
                                                RoundedAppIcon(AppIcon.ChevronDown, tint = theme.accentTextColor, size = 14.dp)
                                                Text(
                                                    stringResource(R.string.nl_jump), fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold, color = theme.accentTextColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A log entry shows its severity in text as well as color and can be expanded. */
@Composable
private fun NodeLogRow(line: NodeLogLine, expanded: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val stamp = line.timestampText?.takeIf { it.isNotBlank() }
        ?: line.timestampMs?.let { logTimeFormat.format(Date(it)) }
    val levelLabel = stringResource(when (line.level) {
        NodeLogLevel.Error -> R.string.nl_row_error
        NodeLogLevel.Warning -> R.string.nl_row_warning
        NodeLogLevel.Info -> R.string.nl_row_info
        NodeLogLevel.Debug -> R.string.nl_row_debug
    })
    val expansionLabel = stringResource(if (expanded) R.string.nl_line_expanded else R.string.nl_line_collapsed)
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(DsRadius.Sm).background(theme.searchBgColor)
            .semantics { stateDescription = expansionLabel }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = DsSpacing.Mid, vertical = DsSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.Xxs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(logColor(line.level)))
            Text(levelLabel, fontSize = 11.sp, color = theme.inkColor, fontWeight = FontWeight.SemiBold)
            if (!stamp.isNullOrBlank()) Text(
                stamp, fontSize = 11.sp, color = theme.mutedColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        MrmText(
            line.message,
            fontSize = 12.sp,
            color = theme.inkColor,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis,
            isTechnical = true
        )
    }
}

/**
 * ساعتِ خط‌های لاگ. عمداً یک نمونهٔ ثابتِ سطح‌فایل است: `SimpleDateFormat`
 * نخ‌امن نیست، ولی اینجا فقط از ترکیب‌بندی (نخِ اصلی) خوانده می‌شود.
 */
private val logTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

/** رنگِ شدت — همان چهار رنگِ پنل، برای نشانگرِ گرافیکی (متن، ink می‌ماند). */
@Composable
private fun logColor(level: NodeLogLevel): Color = when (level) {
    NodeLogLevel.Error -> DsSemantic.Danger
    NodeLogLevel.Warning -> DsSemantic.Warning
    NodeLogLevel.Info -> DsSemantic.Info
    NodeLogLevel.Debug -> LocalThemeState.current.mutedColor
}

/** آیا پایینِ فهرست دیده می‌شود؟ (باید خودکار دنبال کند) */
private fun logAtBottom(state: LazyListState): Boolean {
    val info = state.layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return true
    return last.index >= info.totalItemsCount - 1
}

@Composable
private fun NodeLogFilterChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    Box(
        Modifier.heightIn(min = 48.dp).clip(DsRadius.Sm)
            .background(if (selected) accent.copy(0.16f) else theme.searchBgColor)
            .border(
                BorderStroke(DsBorder.Hairline, if (selected) accent.copy(0.45f) else theme.borderColor),
                DsRadius.Sm
            )
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(horizontal = DsSpacing.Mid, vertical = DsSpacing.Md),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.Xs)) {
            if (selected) RoundedAppIcon(AppIcon.Check, tint = theme.inkColor, size = 14.dp)
            Text(
                label, fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) theme.inkColor else theme.mutedColor,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
