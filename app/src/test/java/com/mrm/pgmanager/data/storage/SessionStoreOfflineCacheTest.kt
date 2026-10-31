package com.mrm.pgmanager.data.storage

import android.content.Context
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.SystemStats
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SessionStoreOfflineCacheTest {
    private lateinit var store: SessionStore
    private val first = Session("https://panel.example", "token-a", "admin")
    private val second = first.copy(username = "restricted", token = "token-b")
    private val user = PanelUser(1, "sample", "active", 0, 0, null, null)

    @Before fun setup() {
        SessionStore.resetPrefsForTests()
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("offline_cache_test", Context.MODE_PRIVATE).edit().clear().commit()
        SessionStore.prefsFactory = { it.getSharedPreferences("offline_cache_test", Context.MODE_PRIVATE) }
        store = SessionStore(context)
    }

    @After fun tearDown() { SessionStore.resetPrefsForTests() }

    @Test fun `user and system caches belong to the exact session`() {
        store.saveUsersCache(first, listOf(user))
        store.saveStatsCache(first, SystemStats(totalUsers = 1))
        assertEquals("sample", store.readUsersCache(first)?.first?.single()?.username)
        assertEquals(1, store.readStatsCache(first)?.first?.totalUsers)
        assertNotNull(store.usersCacheAgeMs(first))
        assertNull(store.readUsersCache(second))
        assertNull(store.readStatsCache(second))
        assertNull(store.usersCacheAgeMs(second))
        assertNull(store.readUsersCache(first.copy(token = "rotated")))
        assertNull(store.readStatsCache(first.copy(baseUrl = "https://other.example")))
    }

    @Test fun `legacy cache with unknown owner is not exposed`() {
        store.prefs.edit().putString("users_cache", "[]").putLong("users_cache_ts", 1)
            .putString("stats_cache", "{}").putLong("stats_cache_ts", 1).commit()
        assertNull(store.readUsersCache(first))
        assertNull(store.readStatsCache(first))
        assertNull(store.usersCacheAgeMs(first))
    }

    @Test fun `new owner replaces rather than inheriting a previous cache`() {
        store.saveUsersCache(first, listOf(user))
        store.saveUsersCache(second, listOf(user.copy(username = "second")))
        assertNull(store.readUsersCache(first))
        assertEquals("second", store.readUsersCache(second)?.first?.single()?.username)
    }
}
