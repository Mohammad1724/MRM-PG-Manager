package com.mrm.pgmanager.data.model

import java.util.Calendar
import java.util.TimeZone

/**
 * شدتِ یک خطِ لاگ.
 *
 * همان چهار دستهٔ پنل (`getLogType` در `dashboard/src/utils/logsUtils.ts`) — عمداً
 * نام‌ها با رشتهٔ پنل یکی است تا اگر روزی لاگ‌ها از مسیرِ دیگری آمدند، نگاشت
 * دوباره‌نوشته نشود.
 */
enum class NodeLogLevel(val key: String) {
    Error("error"),
    Warning("warning"),
    Info("info"),
    Debug("debug");

    companion object {
        /** از رشتهٔ چیپِ فیلتر؛ ناشناخته → null (یعنی «این شدت فیلتر نشود»). */
        fun of(key: String): NodeLogLevel? = entries.firstOrNull { it.key == key }
    }
}

/**
 * یک خطِ لاگِ تفکیک‌شده.
 *
 * `timestampText` همان رشتهٔ خامِ استخراج‌شده است؛ اگر خط تاریخِ قابل‌فهمی نداشت
 * null می‌ماند و UI هیچ تاریخِ جعلی‌ای نشان نمی‌دهد. `timestampMs` فقط برای
 * فیلترِ «از چند وقت تا حالا» لازم است.
 */
data class NodeLogLine(
    val id: Long,
    val level: NodeLogLevel = NodeLogLevel.Info,
    val message: String,
    val timestampText: String? = null,
    val timestampMs: Long? = null
)

/**
 * فیلترهای صفحهٔ لاگ.
 *
 * پیش‌فرضِ هر سه «هیچ‌چیز محدود نمی‌شود» است — یک چیپِ انتخاب‌نشده یعنی «همه»،
 * نه «هیچ». این را از [matches] می‌شود خواند و تستِ `NodeLogsLogicTest` قفلش
 * می‌کند؛ اگر روزی «مجموعۀ خالیِ شدت» به «هیچ خطی» تعبیر شود، صفحه به‌نظر
 * خراب می‌آید در حالی که فقط هیچ چیپی روشن نیست.
 */
data class NodeLogFilter(
    val query: String = "",
    val levels: Set<NodeLogLevel> = emptySet(),
    /** پنجرۀ زمانی بر حسب میلی‌ثانیه؛ null = «از همیشه». */
    val windowMs: Long? = null
) {
    /** آیا فیلتری روشن است؟ (برای پیام «هیچ خطی با این فیلترها نیست») */
    val isActive: Boolean
        get() = query.isNotBlank() || levels.isNotEmpty() || windowMs != null

    fun matches(line: NodeLogLine, nowMs: Long): Boolean {
        if (levels.isNotEmpty() && line.level !in levels) return false
        val q = query.trim()
        if (q.isNotEmpty() && !line.message.contains(q, ignoreCase = true)) return false
        val window = windowMs
        if (window != null) {
            val at = line.timestampMs
            /**
             * خطِ بی‌تاریخ همیشه می‌ماند. برخی نودها تاریخ را به فرمتِ شناخته‌شده
             * نمی‌نویسند؛ اگر آن‌ها را با فیلترِ زمانی حذف کنیم، کاربر «لاگی نیست»
             * می‌بیند در حالی که لاگ هست. پنل هم همین‌طور رفتار می‌کند.
             */
            if (at != null && at < nowMs - window) return false
        }
        return true
    }

    companion object {
        /**
         * پنجره‌های زمانیِ قابلِ انتخاب: `(کلیدِ چیپ، میلی‌ثانیه)`.
         *
         * یک‌جا تعریف شده تا UI و منطق دو فهرستِ جدا نداشته باشند (کلیدِ چیپ با
         * عددِ فیلتر یکی بماند). پله‌های پنل هستند، منهای ۵/۳۰ دقیقه و ۱۲ ساعت که
         * روی گوشی دو چیپِ هم‌معنی می‌شدند؛ `null` یعنی «همهٔ زمان‌ها».
         */
        val windows: List<Pair<String, Long?>> = listOf(
            "all" to null,
            "1m" to 60_000L,
            "15m" to 15L * 60_000L,
            "1h" to 60L * 60_000L,
            "6h" to 6L * 3_600_000L,
            "24h" to 24L * 3_600_000L
        )

        /** کلیدِ چیپِ متناظرِ یک پنجره (برای بازگرداندنِ انتخاب در UI). */
        fun keyOf(windowMs: Long?): String =
            windows.firstOrNull { it.second == windowMs }?.first ?: "all"

        /**
         * پنجرهٔ یک کلید؛ کلیدِ ناشناخته → «همهٔ زمان‌ها» (بی‌خطرترین حالت).
         * نامِ تابع `windowMs` نبود چون با property هم‌نامِ همین کلاس می‌جنگید.
         */
        fun msOf(key: String?): Long? = windows.firstOrNull { it.first == key }?.second
    }
}

/**
 * منطقِ خالصِ لاگِ نود: تجزیهٔ خط، تشخیصِ شدت، فیلتر و بافرِ حلقوی.
 *
 * ### چرا این‌جا و نه داخلِ صفحه
 * جریانِ لاگ یک `Flow` بی‌پایان است؛ صفحه‌ای که داده‌اش بی‌پایان می‌آید در
 * Robolectric قابلِ آزمون نیست (`waitForIdle` هرگز برنمی‌گردد). پس همه‌چیزی که
 * «عدد می‌دهد» — تجزیه، شدت، فیلتر، سقفِ بافر — این‌جاست و با تستِ JVMِ ساده
 * سنجیده می‌شود؛ صفحه فقط حالت را نشان می‌دهد.
 *
 * ### قراردادی که از پنل گرفته شده
 * پنل لاگ را همان‌طور که نود می‌فرستد می‌دهد: گاهی یک رویدادِ SSE چند خط است،
 * گاهی یک خطِ کاملِ `access log` با پیشوندِ `[Error]`. [parse] همان کاری را می‌کند
 * که `parseLogs`ِ داشبورد می‌کند: تقسیمِ خط‌ها، بیرون‌کشیدنِ تاریخ، تشخیصِ شدت و
 * پاک‌کردنِ برچسبِ تکراری از متن (چون شدت را رنگ/برچسب نشان می‌دهد).
 */
object NodeLogs {

    /**
     * سقفِ خط‌های نگه‌داشته‌شده.
     *
     * چرا ۸۰۰: روی گوشیِ میان‌رده هر خط یک شیءِ کوچک + یک ردیفِ LazyColumn است؛
     * پنل ۱۰٬۰۰۰ رویدادِ خام را نگه می‌دارد چون مرورگر حافظه دارد. ۸۰۰ خطِ آخر هم
     * برای «چه اتفاقی وسطِ این قطع افتاد» کافی است و از پرشدنِ حافظه‌ای که باعث
     * کشته‌شدنِ پروسه می‌شود جلوگیری می‌کند. خط‌های قدیمی‌تر از سرِ فهرست می‌روند.
     */
    const val MaxLines = 800

    /**
     * `1 2025/09/27 13:26:58.279079 Xray: …` یا `2024-12-10T10:00:00.000Z …`
     *
     * شمارهٔ ابتدایی از docker-log-driver است و اختیاری؛ تاریخ هم در سه قالبِ
     * دیده‌شده می‌آید (لاگِ xray با «/»، ISO با «T»، و پسوندِ « UTC»)، پس یک الگوی
     * واحد به‌جای سه شاخه.
     */
    private val linePattern = Regex(
        """^(?:(\d+)\s+)?(\d{4}[-/]\d{2}[-/]\d{2}[ T]\d{2}:\d{2}:\d{2}(?:[.,]\d{1,9})?(?:\s*Z|\s*UTC)?)?\s*(.*)$"""
    )

    private val stampPattern = Regex(
        """^(\d{4})[-/](\d{2})[-/](\d{2})[ T](\d{2}):(\d{2}):(\d{2})(?:[.,](\d{1,9}))?"""
    )

    /** Parse an SSE event and assign contiguous IDs only to rows that will be displayed. */
    fun parse(event: String, firstId: Long): List<NodeLogLine> {
        val parsed = ArrayList<NodeLogLine>()
        for (line in event.lineSequence()) {
            val raw = line.trim()
            if (raw.isEmpty()) continue
            val match = linePattern.matchEntire(raw)
            val stamp = match?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }
            val body = (match?.groupValues?.getOrNull(3) ?: raw).trim()
            val message = cleanMessage(body)
            if (message.isEmpty()) continue
            parsed.add(
                NodeLogLine(
                    id = firstId + parsed.size,
                    level = levelOf(body),
                    message = message,
                    timestampText = stamp,
                    timestampMs = stamp?.let { epochMs(it) }
                )
            )
        }
        return parsed
    }

    // Compile the panel's patterns once: every streamed line uses these expressions.
    private val errorTag = Regex("""\[error]""", RegexOption.IGNORE_CASE)
    private val errorPrefix = Regex("""^\s*(error|fatal)\b""", RegexOption.IGNORE_CASE)
    private val warningTag = Regex("""\[(warning|warn)]""", RegexOption.IGNORE_CASE)
    private val warningPrefix = Regex("""^\s*(warning|warn)\b""", RegexOption.IGNORE_CASE)
    private val debugTag = Regex("""\[debug]""", RegexOption.IGNORE_CASE)
    private val debugPrefix = Regex("""^\s*debug\b""", RegexOption.IGNORE_CASE)
    private val accessLine = Regex(
        """from\s+\S+:\d+\s+accepted\s+(tcp|udp):\S+\s+\[[^\]]+]\s+email:\s+\S+""",
        RegexOption.IGNORE_CASE
    )
    private val infoTag = Regex("""\[info]""", RegexOption.IGNORE_CASE)
    private val infoPrefix = Regex("""^\s*info\b""", RegexOption.IGNORE_CASE)
    private val bracketedPrefix = Regex("""^\[(Debug|Info|Warn|Warning|Error)]\s*""", RegexOption.IGNORE_CASE)
    private val colonPrefix = Regex("""^(Debug|Info|Warn|Warning|Error):\s*""", RegexOption.IGNORE_CASE)

    /** Classify a line using the same precedence and patterns as the panel. */
    fun levelOf(message: String): NodeLogLevel = when {
        errorTag.containsMatchIn(message) || errorPrefix.containsMatchIn(message) -> NodeLogLevel.Error
        warningTag.containsMatchIn(message) || warningPrefix.containsMatchIn(message) -> NodeLogLevel.Warning
        debugTag.containsMatchIn(message) || debugPrefix.containsMatchIn(message) -> NodeLogLevel.Debug
        accessLine.containsMatchIn(message) -> NodeLogLevel.Info
        infoTag.containsMatchIn(message) || infoPrefix.containsMatchIn(message) -> NodeLogLevel.Info
        else -> NodeLogLevel.Info
    }

    /** Remove the redundant leading level label without changing the log message. */
    fun cleanMessage(message: String): String = message
        .replaceFirst(bracketedPrefix, "")
        .replaceFirst(colonPrefix, "")
        .trim()

    /**
     * تاریخِ لاگ به میلی‌ثانیه (UTC).
     *
     * نودها ساعتِ UTC می‌نویسند؛ نمایش به ساعتِ گوشی در UI انجام می‌شود. اگر قالب
     * ناشناخته بود null برمی‌گردد تا فیلترِ زمانی آن خط را حذف نکند.
     */
    internal fun epochMs(stamp: String): Long? {
        val m = stampPattern.find(stamp.trim()) ?: return null
        val g = m.groupValues
        // `component6()` روی List در stdlib نیست، پس با ایندکس خوانده می‌شود
        // (تجزیهٔ شش‌تایی خطای کامپایل می‌گرفت).
        val parts = g.drop(1).take(6).map { it.toIntOrNull() ?: -1 }
        if (parts.size < 6 || parts.any { it < 0 }) return null
        val fraction = g.getOrNull(7)?.let { frac ->
            // «.279079» → ۲۷۹ میلی‌ثانیه؛ با پدینگ/برش به سه رقم.
            frac.padEnd(3, '0').take(3).toIntOrNull() ?: 0
        } ?: 0
        return runCatching {
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(parts[0], parts[1] - 1, parts[2], parts[3], parts[4], parts[5])
                set(Calendar.MILLISECOND, fraction)
            }.timeInMillis
        }.getOrNull()
    }

    /**
     * افزودنِ خط‌های تازه و چیدنِ بافر به سقف.
     *
     * اگر فهرستِ خالی بدهیم همان فهرستِ قبلی برمی‌گردد (بدونِ کپیِ بی‌دلیل)؛ اگر
     * از سقف رد شد، از **سر** کم می‌شود چون آخرین خط‌ها مهم‌ترین‌ها هستند.
     */
    fun appendTrim(prev: List<NodeLogLine>, next: List<NodeLogLine>, max: Int = MaxLines): List<NodeLogLine> {
        if (next.isEmpty()) return prev
        if (max <= 0) return emptyList()
        val keepNext = minOf(next.size, max)
        val keepPrev = minOf(prev.size, max - keepNext)
        // Copy retained rows so an oversized event cannot keep its discarded backing list alive.
        return ArrayList<NodeLogLine>(keepPrev + keepNext).apply {
            addAll(prev.subList(prev.size - keepPrev, prev.size))
            addAll(next.subList(next.size - keepNext, next.size))
        }
    }

    /** خط‌های قابلِ نمایش (فیلتر شده، به‌ترتیبِ رسیدن). */
    fun visible(lines: List<NodeLogLine>, filter: NodeLogFilter, nowMs: Long): List<NodeLogLine> {
        if (!filter.isActive) return lines
        return lines.filter { filter.matches(it, nowMs) }
    }
}

/**
 * رمزگشایِ قاب‌های SSE.
 *
 * پنل لاگ را با `EventSourceResponse` می‌فرستد؛ یک «رویداد» یکی یا چند خطِ
 * `data:` است که با یک خطِ خالی تمام می‌شود. پس نباید هر خطِ فیزیکی را یک رویداد
 * دانست (خطای واقعیِ همین پیاده‌سازی: لاگِ چندخطیِ panic به قطعه‌قطعه می‌شد).
 *
 * زیرمجموعۀ پروتکل که پنل تولید می‌کند پیاده شده: `data:` (با یا بدون فاصله)،
 * خطِ خالی = پایانِ رویداد، خطوطِ شروع‌شونده با `:` = keep-alive، و `event:`/`id:`/
 * `retry:` خوانده ولی نادیده گرفته می‌شوند (پنل چیزی جز جریانِ ساده نمی‌فرستد).
 */
class SseDecoder {

    private val data = StringBuilder()
    private var hasData = false

    /**
     * یک خطِ متن می‌گیرد و اگر رویدادی کامل شده باشد متنِ آن را برمی‌گرداند.
     * چند `data:` در یک رویداد با `\n` به هم می‌چسبند (قاعدهٔ خودِ SSE).
     */
    fun feed(line: String): String? {
        val text = line.trimEnd('\r')
        if (text.isEmpty()) {
            if (!hasData) return null
            val event = data.toString()
            data.setLength(0)
            hasData = false
            return event
        }
        if (text.startsWith(":")) return null
        val field = text.substringBefore(':', "")
        if (field != "data") return null
        val value = text.substringAfter(':', "").removePrefix(" ")
        if (hasData) data.append('\n')
        data.append(value)
        hasData = true
        return null
    }

    /** رویدادِ نیمه‌کاره دور ریخته می‌شود (اتصال قطع شده). */
    fun reset() {
        data.setLength(0)
        hasData = false
    }
}
