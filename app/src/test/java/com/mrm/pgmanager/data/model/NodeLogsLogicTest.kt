package com.mrm.pgmanager.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * منطقِ خالصِ «لاگِ نود»: تجزیهٔ خط، تشخیصِ شدت، فیلتر، بافر و قاب‌های SSE.
 *
 * چرا این‌قدر ریز: صفحهٔ لاگ با جریانِ زنده در Robolectric آزمودنی نیست (جریان
 * هیچ‌وقت idle نمی‌شود)، پس همهٔ تصمیم‌های واقعی همین‌جا قفل می‌شوند — همان
 * تصمیم‌هایی که اگر غلط باشند کاربر «لاگی نیست» می‌بیند در حالی که لاگ هست.
 * قاعده‌ها از `dashboard/src/utils/logsUtils.ts`ِ پنل گرفته شده‌اند؛ تست همان
 * قرارداد را نگه می‌دارد، نه سلیقهٔ ما را.
 */
class NodeLogsLogicTest {

    private fun line(
        message: String,
        level: NodeLogLevel = NodeLogLevel.Info,
        ts: Long? = null,
        tsText: String? = null,
        id: Long = 0L
    ) = NodeLogLine(id = id, level = level, message = message, timestampText = tsText, timestampMs = ts)

    // ── تجزیه ───────────────────────────────────────────────────────────────

    @Test fun `xray line keeps its timestamp and loses the duplicate level label`() {
        val parsed = NodeLogs.parse("2025/09/27 13:26:58.279079 ERROR Xray: rejection: bad password", 0L)
        assertEquals(1, parsed.size)
        val l = parsed[0]
        assertEquals(NodeLogLevel.Error, l.level)
        assertEquals("2025/09/27 13:26:58.279079", l.timestampText)
        // ۲۰۲۵/۰۹/۲۷ ۱۳:۲۶:۵۸.۲۷۹ UTC — تاریخِ لاگ همیشه UTC است و ساعتِ گوشی
        // فقط موقعِ نمایش اعمال می‌شود.
        assertEquals(1_758_979_618_279L, l.timestampMs)
        // «ERROR Xray: …» دست‌نخورده می‌ماند: پنل فقط `[Error]` و `Error:` را کم می‌کند
        assertEquals("ERROR Xray: rejection: bad password", l.message)
    }

    @Test fun `bracketed labels are stripped from the message but kept as level`() {
        val warn = NodeLogs.parse("[Warning] disk usage above threshold", 0L).single()
        assertEquals(NodeLogLevel.Warning, warn.level)
        assertEquals("disk usage above threshold", warn.message)
        assertNull(warn.timestampText)

        val debug = NodeLogs.parse("[DEBUG] transport: websocket handshake", 0L).single()
        assertEquals(NodeLogLevel.Debug, debug.level)
        assertEquals("transport: websocket handshake", debug.message)
    }

    @Test fun `one sse event can carry several log lines with unique ids`() {
        val parsed = NodeLogs.parse("server started\n2025/01/01 00:00:00 INFO ready\n", 7L)
        assertEquals(2, parsed.size)
        assertEquals(listOf(7L, 8L), parsed.map { it.id })
        assertEquals("server started", parsed[0].message)
        assertEquals(NodeLogLevel.Info, parsed[1].level)
    }

    @Test fun `empty lines and stray sequence numbers never become rows`() {
        assertEquals(0, NodeLogs.parse("\n   \n", 0L).size)
        val single = NodeLogs.parse("41 2025-01-01T00:00:00.000Z panic: nil map", 0L).single()
        // شمارهٔ ترتیبِ docker-log-driver بخشی از پیام نیست
        assertEquals("panic: nil map", single.message)
        // «panic» خطا حساب نمی‌شود نه در پنل نه اینجا: قواعدِ شدت از متنِ برچسب‌دار
        // می‌آیند و اختراعِ قاعدۀ جدید، رنگِ خط‌ها را از پنل جدا می‌کرد.
        assertEquals(NodeLogLevel.Info, single.level)
    }

    @Test fun `error beats warning when a line mentions both`() {
        // ترتیبِ پنل: اول error/fatal، بعد warning — یک «failed with warning» خطا حساب می‌شود.
        assertEquals(NodeLogLevel.Error, NodeLogs.levelOf("ERROR accepted but warning: tls slow"))
        assertEquals(NodeLogLevel.Warning, NodeLogs.levelOf("[Warn] nothing to see"))
        assertEquals(NodeLogLevel.Info, NodeLogs.levelOf("starting xray version 25.9"))
    }

    @Test fun `xray access log lines count as info not noise`() {
        val l = "from 10.0.0.7:51434 accepted tcp:1.1.1.1:443 [xray-api] email: ali@example.com"
        assertEquals(NodeLogLevel.Info, NodeLogs.levelOf(l))
    }

    @Test fun `timestamps in the three known shapes agree and unknown ones stay null`() {
        val iso = NodeLogs.epochMs("2024-12-10T10:00:00.000Z")
        val spaced = NodeLogs.epochMs("2024-12-10 10:00:00.000 UTC")
        assertEquals(iso, spaced)
        assertEquals(1_733_824_800_000L, iso)
        assertEquals(1_733_824_800_123L, NodeLogs.epochMs("2024-12-10 10:00:00.123456"))
        assertNull(NodeLogs.epochMs("10:00:00"))
        assertNull(NodeLogs.epochMs(""))
    }

    // ── فیلتر ───────────────────────────────────────────────────────────────

    @Test fun `no chip selected means all lines, not none`() {
        // مهم‌ترین قاعدهٔ این صفحه: مجموعۀ خالیِ شدت = «فیلتر نشود». اگر روزی
        // «هیچ» تعبیر شود، کاربر با سه چیپِ خاموش صفحهٔ خالی می‌بیند.
        val lines = listOf(
            line("boom", NodeLogLevel.Error),
            line("hmm", NodeLogLevel.Warning),
            line("tick", NodeLogLevel.Info)
        )
        assertEquals(3, NodeLogs.visible(lines, NodeLogFilter(), 0L).size)
        assertEquals(
            listOf("boom"),
            NodeLogs.visible(lines, NodeLogFilter(levels = setOf(NodeLogLevel.Error)), 0L).map { it.message }
        )
        assertEquals(
            listOf("boom", "tick"),
            NodeLogs.visible(
                lines,
                NodeLogFilter(levels = setOf(NodeLogLevel.Error, NodeLogLevel.Info)),
                0L
            ).map { it.message }
        )
    }

    @Test fun `search is case insensitive on the message only`() {
        val lines = listOf(line("Handshake FAILED"), line("note: FAILED elsewhere"))
        val hits = NodeLogs.visible(lines, NodeLogFilter(query = "handshake failed"), 0L)
        assertEquals(listOf("Handshake FAILED"), hits.map { it.message })
    }

    @Test fun `time window drops old lines but never a line without a date`() {
        val now = 1_000_000_000_000L
        val fresh = line("fresh", ts = now - 60_000L)
        val old = line("old", ts = now - 10L * 60_000L)
        val undated = line("no date at all")
        val filtered = NodeLogs.visible(listOf(fresh, old, undated), NodeLogFilter(windowMs = 5L * 60_000L), now)
        assertEquals(listOf("fresh", "no date at all"), filtered.map { it.message })
        // «همهٔ زمان‌ها» هیچ خطی را کم نمی‌کند
        assertEquals(3, NodeLogs.visible(listOf(fresh, old, undated), NodeLogFilter(), now).size)
    }

    @Test fun `window chips and milliseconds are one source of truth`() {
        assertEquals(NodeLogFilter.windows.size, NodeLogFilter.windows.map { it.first }.distinct().size)
        assertEquals(3_600_000L, NodeLogFilter.msOf("1h"))
        assertNull(NodeLogFilter.msOf("no-such-window"))
        assertEquals("1h", NodeLogFilter.keyOf(3_600_000L))
        // مقدارِ ناشناخته به «همهٔ زمان‌ها» می‌افتد، نه به یک پنجرۀ بی‌معنا
        assertEquals("all", NodeLogFilter.keyOf(123L))
        assertNull("پیش‌فرضِ صفحه نباید فیلترِ زمانی داشته باشد", NodeLogFilter.msOf("all"))
    }

    // ── بافر ────────────────────────────────────────────────────────────────

    @Test fun `buffer keeps the newest lines and is capped`() {
        val first = (0 until 5).map { line("old $it", id = it.toLong()) }
        val more = (5 until 10).map { line("new $it", id = it.toLong()) }
        val kept = NodeLogs.appendTrim(first, more, max = 6)
        assertEquals(6, kept.size)
        // خط‌های قدیمی از سرِ فهرست می‌روند، پس آخرین خط همان آخرِ ورودی است
        assertEquals("new 9", kept.last().message)
        assertEquals("old 4", kept.first().message)
        // ورودیِ خالی نباید کپی بسازد (هر رویدادِ SSE یک appendTrim است)
        assertSame(first, NodeLogs.appendTrim(first, emptyList(), max = 6))
    }

    @Test fun `trim threshold is applied per event not per line`() {
        val lines = (0 until NodeLogs.MaxLines).map { line("l$it", id = it.toLong()) }
        val after = NodeLogs.appendTrim(lines, listOf(line("fresh", id = 999L)))
        assertEquals(NodeLogs.MaxLines, after.size)
        assertEquals("fresh", after.last().message)
        assertEquals("l1", after.first().message)
    }

    // ── قاب‌های SSE ──────────────────────────────────────────────────────────

    @Test fun `event is emitted only on the blank line`() {
        val d = SseDecoder()
        assertNull(d.feed("data: hello"))
        assertEquals("hello", d.feed(""))
        assertNull(d.feed(""))
    }

    @Test fun `multi line event is joined so a panic stays one row group`() {
        val d = SseDecoder()
        d.feed("data: first")
        d.feed("data: second")
        assertEquals("first\nsecond", d.feed(""))
    }

    @Test fun `single optional space after the colon is the spec`() {
        val d = SseDecoder()
        d.feed("data:tight")
        assertEquals("tight", d.feed(""))
        d.feed("data:  two")
        assertEquals(" two", d.feed(""))
    }

    @Test fun `keep-alive comments and unused fields are skipped`() {
        val d = SseDecoder()
        assertNull(d.feed(": ping - 2026-10-26 10:00:00 UTC"))
        assertNull(d.feed("event: log"))
        assertNull(d.feed("id: 42"))
        assertNull(d.feed("retry: 3000"))
        d.feed("data: real")
        assertEquals("real", d.feed("\r"))
        d.feed("data: x")
        assertEquals("x", d.feed(""))
    }

    @Test fun `reset drops a half received event`() {
        val d = SseDecoder()
        d.feed("data: partial")
        d.reset()
        assertNull(d.feed(""))
    }

    // ── مدل ─────────────────────────────────────────────────────────────────

    @Test fun `level lookup accepts only panel keys`() {
        assertEquals(NodeLogLevel.Warning, NodeLogLevel.of("warning"))
        assertNull(NodeLogLevel.of("critical"))
        assertTrue(NodeLogLevel.entries.all { NodeLogLevel.of(it.key) == it })
    }

    @Test fun `filter active flag follows the visible affordances`() {
        assertFalse(NodeLogFilter().isActive)
        assertTrue(NodeLogFilter(query = "  x  ").isActive)
        assertFalse("فاصلهٔ خالی فیلتر نیست", NodeLogFilter(query = "   ").isActive)
        assertTrue(NodeLogFilter(levels = setOf(NodeLogLevel.Debug)).isActive)
        assertTrue(NodeLogFilter(windowMs = 1L).isActive)
    }

    @Test fun `parsing keeps ids sequential across events for stable list keys`() {
        val a = NodeLogs.parse("one\ntwo", 0L)
        val b = NodeLogs.parse("three", 2L)
        assertEquals(listOf(0L, 1L, 2L), (a + b).map { it.id })
        assertTrue("کلیدِ تکراری یعنی ردیف‌های لیست جابه‌جا می‌شوند", (a + b).map { it.id }.distinct().size == 3)
    }

    @Test fun `discarded label-only lines cannot duplicate the next event id`() {
        val first = NodeLogs.parse("alpha\n[Info]\nomega", 0L)
        val second = NodeLogs.parse("next", first.size.toLong())
        assertEquals(listOf(0L, 1L, 2L), (first + second).map { it.id })
        assertEquals(listOf("alpha", "omega", "next"), (first + second).map { it.message })
    }

    @Test fun `trimmed buffer does not retain or depend on the oversized source list`() {
        val source = (0 until 1_000).map { line("row $it", id = it.toLong()) }.toMutableList()
        val retained = NodeLogs.appendTrim(emptyList(), source, max = 3)
        source[999] = line("changed", id = 999L)
        source.clear()
        assertEquals(listOf("row 997", "row 998", "row 999"), retained.map { it.message })
    }
}
