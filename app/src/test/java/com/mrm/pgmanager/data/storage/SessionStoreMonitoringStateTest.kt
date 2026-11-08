package com.mrm.pgmanager.data.storage

import android.content.Context
import com.mrm.pgmanager.data.model.Session
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionStoreMonitoringStateTest {
    private lateinit var store: SessionStore
    private val first = Session("https://panel.example", "pg_key_first", "alice")
    private val second = Session("https://panel.example", "pg_key_second", "bob")

    @Before fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("monitoring-state-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        SessionStore.resetPrefsForTests()
        SessionStore.prefsFactory = { preferences }
        store = SessionStore(context)
        store.save(first)
    }

    @After fun tearDown() { SessionStore.resetPrefsForTests() }

    @Test fun `states flags and counters do not cross account or credential boundaries`() {
        store.saveNotificationStates(first, mapOf(1L to "active|20|false"))
        store.saveNodeStates(first, mapOf(1 to true))
        store.saveAlertFlag(first, "cpu", true)
        store.saveAlertCount(first, "panel_offline", 2)
        assertEquals(mapOf(1L to "active|20|false"), store.readNotificationStates(first))
        assertEquals(mapOf(1 to true), store.readNodeStates(first))
        assertTrue(store.readAlertFlag(first, "cpu"))
        assertEquals(2, store.readAlertCount(first, "panel_offline"))

        store.save(second)
        assertEquals(emptyMap<Long, String>(), store.readNotificationStates(second))
        assertEquals(emptyMap<Int, Boolean>(), store.readNodeStates(second))
        assertFalse(store.readAlertFlag(second, "cpu"))
        assertEquals(0, store.readAlertCount(second, "panel_offline"))
        store.saveNodeStates(second, mapOf(1 to false))

        val rotated = second.copy(token = "pg_key_rotated")
        store.save(rotated)
        assertEquals(emptyMap<Int, Boolean>(), store.readNodeStates(rotated))
    }

    @Test fun `late writes from the old session cannot overwrite the active baseline`() {
        store.save(second)
        store.saveNotificationStates(second, mapOf(1L to "active|10|false"))
        store.saveNodeStates(second, mapOf(4 to true))
        store.saveAlertFlag(second, "cpu", true)
        store.saveAlertCount(second, "panel_offline", 1)

        store.saveNotificationStates(first, mapOf(1L to "limited|100|true"))
        store.saveNodeStates(first, mapOf(4 to false))
        store.saveAlertFlag(first, "cpu", false)
        store.saveAlertCount(first, "panel_offline", 9)

        assertEquals(mapOf(1L to "active|10|false"), store.readNotificationStates(second))
        assertEquals(mapOf(4 to true), store.readNodeStates(second))
        assertTrue(store.readAlertFlag(second, "cpu"))
        assertEquals(1, store.readAlertCount(second, "panel_offline"))
    }

    @Test fun `unowned legacy baselines are retained but not attributed to an account`() {
        store.prefs.edit()
            .putString("notification_user_states", "{\"1\":\"limited|100|true\"}")
            .putString("node_states", "{\"1\":false}")
            .putBoolean("alert_cpu", true)
            .apply()
        assertEquals(emptyMap<Long, String>(), store.readNotificationStates(first))
        assertEquals(emptyMap<Int, Boolean>(), store.readNodeStates(first))
        assertFalse(store.readAlertFlag(first, "cpu"))
        store.saveNodeStates(first, mapOf(1 to true))
        assertEquals("{\"1\":false}", store.prefs.getString("node_states", null))
    }
}
