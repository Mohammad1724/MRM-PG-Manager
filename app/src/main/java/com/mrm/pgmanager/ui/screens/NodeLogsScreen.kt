package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.AdminAccess
import com.mrm.pgmanager.data.api.NodeLogConnection
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.NodeLogFilter
import com.mrm.pgmanager.data.model.NodeLogLine
import com.mrm.pgmanager.data.model.NodeLogLevel
import com.mrm.pgmanager.data.model.NodeLogs
import com.mrm.pgmanager.data.model.PanelNode
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.MrmEmptyState
import com.mrm.pgmanager.ui.components.MrmText
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
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.utils.ApiErrorMapper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** وضعیتِ جریانِ لاگ — حالت‌هایی که کاربر باید از هم تشخیص بدهد. */
enum class NodeLogStream {
    /** نودی انتخاب نشده (یا مجوز نیست) — جریانی باز نیست. */
    Idle,

    /** درخواست رفته، هنوز اولین خط نیامده. */
    Connecting,

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
 *  • **اتصال فقط تا وقتی صفحه باز است.** جریانِ بی‌پایان روی گوشی یعنی باتری و
 *    یک سوکتِ نصفه روی سرور؛ `DisposableEffect.onDispose` همان لحظه می‌بنددش
 *    (شرحش در `data/api/NodeLogStream.kt`).
 *  • **بازاتصالِ خودکار نداریم.** مرورگرِ پنل EventSource را خودش دوباره باز
 *    می‌کند، ولی روی موبایلِ ضعیف همان یعنی حلقهٔ بی‌پایانِ درخواست. جریان که
 *    بسته شد، وضعیت روی صفحه می‌آید و «ادامهٔ دریافت» یک تپ است.
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
    val nodesKey = PanelCache.nodesKey(session.baseUrl)

    var nodes by remember(session) { mutableStateOf(PanelCache.get<List<PanelNode>>(nodesKey) ?: emptyList()) }
    var nodesLoading by remember(session) { mutableStateOf(PanelCache.get<List<PanelNode>>(nodesKey) == null) }
    var nodesError by remember(session) { mutableStateOf<String?>(null) }
    // فقط نودِ متصل لاگ می‌دهد (پنل هم در انتخاب‌گرش بقیه را نمی‌گذارد).
    val connected = remember(nodes) { nodes.filter { it.isConnected } }

    var selected by remember(session) { mutableStateOf(nodeId ?: connected.firstOrNull()?.id) }
    var stream by remember(session) { mutableStateOf(NodeLogStream.Idle) }
    var streamError by remember { mutableStateOf<String?>(null) }

    var lines by remember(session) { mutableStateOf(emptyList<NodeLogLine>()) }
    var pending by remember(session) { mutableStateOf(emptyList<NodeLogLine>()) }
    // شناسهٔ یکتا برای `key = { it.id }` لازم است؛ بافر از سرِ فهرست کم می‌شود،
    // پس هیچ‌وقت به صفر برنمی‌گردد تا کلیدها قاطی نشوند.
    var seq by remember(session) { mutableStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var levels by remember { mutableStateOf(emptySet<NodeLogLevel>()) }
    var windowMs by remember { mutableStateOf<Long?>(null) }
    var restartToken by remember { mutableStateOf(0) }
    var connection by remember { mutableStateOf<NodeLogConnection?>(null) }

    val filter = NodeLogFilter(query = query, levels = levels, windowMs = windowMs)

    suspend fun loadNodes(force: Boolean) {
        if (!force && PanelCache.isFresh(nodesKey)) {
            nodesLoading = false
            return
        }
        runCatching { PanelApi.nodes(session) }
            .onSuccess {
                nodes = it
                nodesError = null
                PanelCache.put(nodesKey, it)
            }
            .onFailure { nodesError = ApiErrorMapper.friendly(context, it) }
        nodesLoading = false
    }

    LaunchedEffect(session) { loadNodes(force = false) }

    // نودِ انتخابی باید همیشه متصل بماند: اگر فهرست دیر بیاید یا نود وسطِ کار
    // قطع شود، جریانِ بی‌حاصل باز نمی‌ماند.
    LaunchedEffect(connected) {
        val id = selected
        if (id == null || connected.none { it.id == id }) selected = connected.firstOrNull()?.id
    }

    LaunchedEffect(session, selected, restartToken, canLogs) {
        val node = selected
        if (!canLogs || node == null) {
            stream = NodeLogStream.Idle
            return@LaunchedEffect
        }
        stream = NodeLogStream.Connecting
        streamError = null
        val conn = runCatching { PanelApi.openNodeLogs(session, node) }
            .onFailure {
                stream = NodeLogStream.Failed
                streamError = ApiErrorMapper.friendly(context, it)
            }
            .getOrNull() ?: return@LaunchedEffect
        connection = conn
        var failed = false
        try {
            while (true) {
                // خواندن بلاک است؛ `nextEvent()` اگر خودمان بسته باشیم null
                // می‌دهد (خطا نیست، رفتنِ کاربر است) و در غیر این صورت می‌پراند.
                val event = withContext(Dispatchers.IO) { conn.nextEvent() } ?: break
                val parsed = NodeLogs.parse(event, seq)
                seq += parsed.size.toLong()
                if (parsed.isEmpty()) continue
                if (paused) pending = NodeLogs.appendTrim(pending, parsed)
                else lines = NodeLogs.appendTrim(lines, parsed)
                if (stream != NodeLogStream.Streaming) stream = NodeLogStream.Streaming
            }
        } catch (e: Exception) {
            // لغوِ عمدی (خروج از صفحه یا تازه‌سازی) خطا نیست: اگر آن را بلعید،
            // یک بنرِ «جریان قطع شد» روی صفحهٔ تازه می‌نشست که با دستِ کاربر هم
            // پاک نمی‌شد. `kotlinx.coroutines.isActive` در این بلوک در دسترس نیست
            // (block داخلِ `withContext` است)، پس نوعِ خودِ استثنا معیار است —
            // `CancellationException` در JVM همان `java.util.concurrent` است.
            if (e is java.util.concurrent.CancellationException) throw e
            // هر خطای خواندنِ جریان (TIMEOUT، TLS، connection reset) همین‌جا تمام
            // می‌شود؛ صفحه نباید به‌خاطرِ یک سوکتِ مرده کرش کند، به‌جایش «ادامهٔ
            // دریافت» پیشنهاد می‌شود.
            if (!conn.closed) {
                failed = true
                stream = NodeLogStream.Failed
                streamError = ApiErrorMapper.friendly(context, e)
            }
        } finally {
            // NonCancellable: بستنِ سوکت باید حتی وسطِ لغو هم انجام شود، وگرنه
            // هر خروجِ صفحه یک اتصالِ نیمه روی سرور می‌گذارد.
            withContext(NonCancellable + Dispatchers.IO) { conn.close() }
            if (connection === conn) connection = null
        }
        // بستنِ معمولی (سرور جریان را بست) با بستنِ ما فرق دارد؛ در اولی
        // «ادامهٔ دریافت» معنا دارد، در دومی صفحه دیگر روی صفحه نیست.
        if (!failed && !conn.closed) stream = NodeLogStream.Ended
    }

    // بستنِ صفحه (یا رفتنِ نودِ دیگر) باید سوکت را همان لحظه ببندد؛ انتظارِ
    // لغوِ کوروتین کافی نیست چون خواننده روی `read` بلاک است.
    DisposableEffect(selected) {
        onDispose { connection?.close() }
    }

    val visible = remember(lines, query, levels, windowMs) {
        NodeLogs.visible(lines, filter, System.currentTimeMillis())
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
                loadNodes(force = true)
                restartToken += 1
            }
        },
        onSelectNode = { id ->
            selected = id
            // خط‌های نودِ قبلی مالِ آن نودند؛ نگه‌داشتنشان یعنی «لاگِ این نود»
            // دروغ می‌گوید.
            lines = emptyList()
            pending = emptyList()
        },
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
            val text = rows.joinToString("\n") { row ->
                val stamp = row.timestampText?.takeIf { it.isNotBlank() }
                    ?: row.timestampMs?.let { logTimeFormat.format(Date(it)) }
                if (stamp.isNullOrBlank()) row.message else "$stamp ${row.message}"
            }
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(context.getString(R.string.nl_title), text))
            com.mrm.pgmanager.ui.feedback.AppFeedback.success(context.getString(R.string.copied))
        }
    )
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
    onCopy: (List<NodeLogLine>) -> Unit
) {
    val theme = LocalThemeState.current
    val backLabel = stringResource(R.string.cd_back)

    Column(
        Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding()
            .padding(
                start = DsSpacing.Screen, end = DsSpacing.Screen,
                top = DsSpacing.Screen, bottom = DsSpacing.Mid
            ),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
    ) {
        // ── سربرگ (هم‌سبکِ «کلیدهای API» و «تنظیماتِ پنل»)
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
                    stringResource(R.string.nl_title), fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, color = theme.inkColor
                )
                Text(
                    stringResource(R.string.nl_subtitle),
                    fontSize = 11.sp, color = theme.mutedColor, maxLines = 2
                )
            }
            if (total > 0) PGBadge("$total")
            InfraIconButton(
                icon = AppIcon.Refresh,
                description = stringResource(R.string.refresh),
                tint = theme.mutedColor,
                onClick = onRefresh
            )
        }

        streamError?.let { InfraBanner(text = it, success = false) { onDismissError() } }

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

            nodes.isEmpty() -> MrmEmptyState(
                title = stringResource(R.string.nl_no_node),
                subtitle = nodesError ?: stringResource(R.string.nl_no_node_hint),
                icon = AppIcon.Wifi
            ) {
                PGSecondaryButton(
                    text = stringResource(R.string.refresh),
                    onClick = onRefresh
                )
            }

            else -> {
                // ── انتخابِ نود (چیپ، نه دیالوگ: معمولاً یک تا سه نود متصل است)
                ChipSelector(
                    values = nodes.map { it.id.toString() },
                    labels = nodes.map { it.name },
                    selected = selectedNode?.toString(),
                    onSelect = { id -> id.toIntOrNull()?.let(onSelectNode) }
                )

                // ── جعبهٔ ابزار: جست‌وجو + توقف/ادامه + کپی + پاک‌کردن
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
                ) {
                    PGSearchBar(
                        query = filter.query,
                        onQueryChange = onQueryChange,
                        placeholder = stringResource(R.string.nl_search),
                        modifier = Modifier.weight(1f)
                    )
                    InfraIconButton(
                        icon = if (paused) AppIcon.Play else AppIcon.Pause,
                        description = stringResource(if (paused) R.string.nl_resume else R.string.nl_pause),
                        tint = theme.accentPrimary,
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
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
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

                // ── وضعیتِ جریان
                val statusLine = when (stream) {
                    NodeLogStream.Connecting -> stringResource(R.string.nl_connecting)
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
                    if (stream == NodeLogStream.Connecting) {
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
                    if (stream == NodeLogStream.Ended || stream == NodeLogStream.Failed) {
                        val reconnectLabel = stringResource(R.string.nl_reconnect)
                        Text(
                            reconnectLabel, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, color = theme.accentTextColor,
                            modifier = Modifier.clip(DsRadius.Sm).clickable { onReconnect() }
                                .heightIn(min = 40.dp)
                                .padding(horizontal = DsSpacing.Mid, vertical = DsSpacing.Xs)
                                .semantics { contentDescription = reconnectLabel }
                        )
                    }
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
                    LaunchedEffect(visible.size, follow) {
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
                                        if (stream == NodeLogStream.Connecting) R.string.nl_waiting else R.string.nl_empty
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
                                        .heightIn(min = 40.dp)
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

/** ردیفِ یک خطِ لاگ: نقطهٔ شدت، تاریخِ کوتاه و متن. با لمس باز/بسته می‌شود. */
@Composable
private fun NodeLogRow(line: NodeLogLine, expanded: Boolean, onClick: () -> Unit) {
    val theme = LocalThemeState.current
    val stamp = line.timestampText?.takeIf { it.isNotBlank() }
        ?: line.timestampMs?.let { logTimeFormat.format(Date(it)) }
    Row(
        Modifier.fillMaxWidth().clip(DsRadius.Sm).background(theme.searchBgColor)
            .clickable { onClick() }
            .padding(horizontal = DsSpacing.Mid, vertical = DsSpacing.Sm),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Sm)
    ) {
        Box(
            Modifier.padding(top = 4.dp).size(6.dp).clip(RoundedCornerShape(50))
                .background(logColor(line.level))
        )
        if (!stamp.isNullOrBlank()) Text(
            stamp, fontSize = 10.sp, color = theme.tertiaryColor,
            maxLines = 1, fontWeight = FontWeight.Medium
        )
        MrmText(
            line.message,
            fontSize = 11.sp,
            color = theme.inkColor,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis,
            isTechnical = true,
            modifier = Modifier.weight(1f)
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
        Modifier.heightIn(min = 40.dp).clip(DsRadius.Sm).clickable { onClick() }
            .background(if (selected) accent.copy(0.16f) else theme.searchBgColor)
            .border(
                BorderStroke(DsBorder.Hairline, if (selected) accent.copy(0.45f) else theme.borderColor),
                DsRadius.Sm
            )
            .padding(horizontal = DsSpacing.Mid, vertical = DsSpacing.Md)
    ) {
        Text(
            label, fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else theme.mutedColor,
            maxLines = 1
        )
    }
}
