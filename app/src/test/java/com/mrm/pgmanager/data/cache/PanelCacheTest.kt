package com.mrm.pgmanager.data.cache

import com.mrm.pgmanager.data.model.TrafficPoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * قراردادهای کلید کش — D5: ترافیک بازه‌های مختلف نباید روی هم بنویسند.
 */
class PanelCacheTest {

    // ساعتِ دستی: `SystemClock.elapsedRealtime` در JVM پیاده‌سازی ندارد و
    // «کهنه شدن» با ساعتِ واقعی هم غیرقطعی است.
    private var now = 1_000L

    @Before fun installClock() {
        now = 1_000L
        PanelCache.clock = { now }
    }

    @After fun clear() {
        PanelCache.clear()
        PanelCache.resetClockForTests()
    }

    @Test fun `freshness follows the injected clock`() {
        val key = PanelCache.statsKey("https://panel.example")
        PanelCache.put(key, 1)
        assertEquals(0L, requireNotNull(PanelCache.ageMs(key)))
        assertTrue(PanelCache.isFresh(key))

        now += PanelCache.DefaultFreshMs - 1
        assertTrue(PanelCache.isFresh(key))

        now += 2
        assertFalse(PanelCache.isFresh(key))
        assertEquals(PanelCache.DefaultFreshMs + 1, requireNotNull(PanelCache.ageMs(key)))
    }

    @Test fun `range-less traffic key keeps the legacy shape`() {
        assertEquals("traffic:https://panel.example", PanelCache.trafficKey("https://panel.example"))
    }

    @Test fun `each range gets its own traffic key`() {
        val base = "https://panel.example"
        val key7d = PanelCache.trafficKey(base, "LAST_7D")
        val key1h = PanelCache.trafficKey(base, "LAST_1H")

        assertNotEquals(key7d, key1h)
        assertNotEquals(PanelCache.trafficKey(base), key7d)
    }

    @Test fun `cached points for one range do not overwrite another range`() {
        val base = "https://panel.example"
        val week = listOf(TrafficPoint("2026-10-14T00:00:00Z", 100))
        val hour = listOf(TrafficPoint("2026-10-21T10:00:00Z", 5))

        PanelCache.put(PanelCache.trafficKey(base, "LAST_7D"), week)
        PanelCache.put(PanelCache.trafficKey(base, "LAST_1H"), hour)

        assertEquals(week, PanelCache.get<List<TrafficPoint>>(PanelCache.trafficKey(base, "LAST_7D")))
        assertEquals(hour, PanelCache.get<List<TrafficPoint>>(PanelCache.trafficKey(base, "LAST_1H")))
    }

    @Test fun `blank range falls back to the shared key`() {
        PanelCache.put(PanelCache.trafficKey("b", ""), listOf(TrafficPoint("t", 1)))
        assertEquals(
            listOf(TrafficPoint("t", 1)),
            PanelCache.get<List<TrafficPoint>>(PanelCache.trafficKey("b"))
        )
    }

    @Test fun `invalidating nodes does not remove unrelated cached resources`() {
        val nodesKey = PanelCache.nodesKey("https://panel.example")
        val statsKey = PanelCache.statsKey("https://panel.example")
        PanelCache.put(nodesKey, listOf("node"))
        PanelCache.put(statsKey, "stats")
        PanelCache.invalidate(nodesKey)
        assertNull(PanelCache.get<Any>(nodesKey))
        assertEquals("stats", PanelCache.get<String>(statsKey))
    }

    @Test fun `clear wipes every range for the account`() {
        PanelCache.put(PanelCache.trafficKey("b", "LAST_7D"), listOf(TrafficPoint("t", 1)))
        PanelCache.clear()
        assertNull(PanelCache.get<Any>(PanelCache.trafficKey("b", "LAST_7D")))
    }
}
