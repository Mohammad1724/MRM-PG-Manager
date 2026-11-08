package com.mrm.pgmanager.data.cache

import com.mrm.pgmanager.data.model.Session
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
    private val session = Session("https://panel.example", "token-a", "alice")

    @Before fun installClock() {
        now = 1_000L
        PanelCache.clock = { now }
    }

    @After fun clear() {
        PanelCache.clear()
        PanelCache.resetClockForTests()
    }

    @Test fun `freshness follows the injected clock`() {
        val key = PanelCache.statsKey(session)
        PanelCache.put(key, 1)
        assertEquals(0L, requireNotNull(PanelCache.ageMs(key)))
        assertTrue(PanelCache.isFresh(key))

        now += PanelCache.DefaultFreshMs - 1
        assertTrue(PanelCache.isFresh(key))

        now += 2
        assertFalse(PanelCache.isFresh(key))
        assertEquals(PanelCache.DefaultFreshMs + 1, requireNotNull(PanelCache.ageMs(key)))
    }

    @Test fun `range-less traffic key is session scoped`() {
        val key = PanelCache.trafficKey(session)
        assertTrue(key.startsWith("traffic:session:"))
        assertFalse(key.contains(session.token))
        assertEquals(key, PanelCache.trafficKey(session, ""))
    }

    @Test fun `each range gets its own traffic key`() {
        val base = session
        val key7d = PanelCache.trafficKey(base, "LAST_7D")
        val key1h = PanelCache.trafficKey(base, "LAST_1H")

        assertNotEquals(key7d, key1h)
        assertNotEquals(PanelCache.trafficKey(base), key7d)
    }

    @Test fun `cached points for one range do not overwrite another range`() {
        val base = session
        val week = listOf(TrafficPoint("2026-10-14T00:00:00Z", 100))
        val hour = listOf(TrafficPoint("2026-10-21T10:00:00Z", 5))

        PanelCache.put(PanelCache.trafficKey(base, "LAST_7D"), week)
        PanelCache.put(PanelCache.trafficKey(base, "LAST_1H"), hour)

        assertEquals(week, PanelCache.get<List<TrafficPoint>>(PanelCache.trafficKey(base, "LAST_7D")))
        assertEquals(hour, PanelCache.get<List<TrafficPoint>>(PanelCache.trafficKey(base, "LAST_1H")))
    }

    @Test fun `blank range falls back to the shared key`() {
        PanelCache.put(PanelCache.trafficKey(session, ""), listOf(TrafficPoint("t", 1)))
        assertEquals(
            listOf(TrafficPoint("t", 1)),
            PanelCache.get<List<TrafficPoint>>(PanelCache.trafficKey(session))
        )
    }

    @Test fun `invalidating nodes does not remove unrelated cached resources`() {
        val nodesKey = PanelCache.nodesKey(session)
        val statsKey = PanelCache.statsKey(session)
        PanelCache.put(nodesKey, listOf("node"))
        PanelCache.put(statsKey, "stats")
        PanelCache.invalidate(nodesKey)
        assertNull(PanelCache.get<Any>(nodesKey))
        assertEquals("stats", PanelCache.get<String>(statsKey))
    }

    @Test fun `sensitive cache entries are isolated by session without embedding credentials`() {
        val first = Session("https://panel.example", "token-one", "alice")
        val otherToken = first.copy(token = "token-two")
        val otherUser = first.copy(username = "bob")
        val settings = PanelCache.panelSettingsKey(first)
        val keys = PanelCache.apiKeysKey(first)
        PanelCache.put(settings, "settings")
        PanelCache.put(keys, "keys")
        assertEquals(settings, PanelCache.panelSettingsKey(first.copy()))
        assertNotEquals(settings, PanelCache.panelSettingsKey(otherToken))
        assertNotEquals(keys, PanelCache.apiKeysKey(otherUser))
        assertNull(PanelCache.get<Any>(PanelCache.panelSettingsKey(otherToken)))
        assertNull(PanelCache.get<Any>(PanelCache.apiKeysKey(otherUser)))
        assertFalse(settings.contains(first.token))
        assertFalse(keys.contains(first.token))
    }

    @Test fun `all resource keys isolate accounts and late responses`() {
        val other = session.copy(username = "bob", token = "token-b")
        val sameUserNewToken = session.copy(token = "rotated")
        val keys: List<(Session) -> String> = listOf(
            PanelCache::statsKey, { PanelCache.trafficKey(it, "LAST_7D") },
            PanelCache::statsTrafficKey, PanelCache::statsCountKey, PanelCache::statsNodeUsageKey,
            PanelCache::nodesKey, PanelCache::usersKey, PanelCache::groupsKey,
            PanelCache::inboundsKey, PanelCache::templatesKey, PanelCache::templateGroupsKey,
            PanelCache::hostsKey, PanelCache::panelSettingsKey, PanelCache::apiKeysKey,
            PanelCache::adminsKey, PanelCache::adminRolesKey, PanelCache::coresKey,
            PanelCache::clientTemplatesKey
        )
        for (key in keys) {
            PanelCache.put(key(session), "old")
            PanelCache.clear()
            PanelCache.put(key(other), "current")
            // A request that started before the switch can finish after clear().
            PanelCache.put(key(session), "late")
            assertNotEquals(key(session), key(other))
            assertNotEquals(key(session), key(sameUserNewToken))
            assertFalse(key(session).contains(session.token))
            assertEquals("current", PanelCache.get<String>(key(other)))
            assertNull(PanelCache.get<String>(key(sameUserNewToken)))
        }
    }

    @Test fun `clear wipes every range for the account`() {
        PanelCache.put(PanelCache.trafficKey(session, "LAST_7D"), listOf(TrafficPoint("t", 1)))
        PanelCache.clear()
        assertNull(PanelCache.get<Any>(PanelCache.trafficKey(session, "LAST_7D")))
    }
}
