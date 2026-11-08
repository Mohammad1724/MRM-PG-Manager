package com.mrm.pgmanager.data.storage

import android.content.Context
import com.mrm.pgmanager.data.model.Session
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class SessionStoreSessionTest {
    private lateinit var store: SessionStore
    private val first = Session("https://panel.example", "token-a", "alice")
    private val second = first.copy(username = "bob", token = "token-b")

    @Before fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("session-transition-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        SessionStore.resetPrefsForTests()
        SessionStore.prefsFactory = { prefs }
        store = SessionStore(context)
    }

    @After fun tearDown() = SessionStore.resetPrefsForTests()

    @Test fun `an old response cannot expire the new account`() {
        store.save(first)
        store.save(second)

        assertFalse(store.expireActive(first))
        assertEquals(second, store.read())
        assertNull(store.readExpiredAccount())
        assertTrue(store.expireActive(second))
        assertNull(store.read())
        assertEquals(second, store.readExpiredAccount())
        assertEquals(setOf(first, second), store.readAccounts().toSet())
    }

    @Test fun `a rotated credential cannot be expired by its previous credential`() {
        store.save(first)
        val rotated = first.copy(token = "token-rotated")
        store.save(rotated)

        assertFalse(store.expireActive(first))
        assertEquals(rotated, store.read())
    }

    @Test fun `switch and old expiration are atomic across store instances`() {
        val otherStore = SessionStore(RuntimeEnvironment.getApplication())
        val executor = Executors.newFixedThreadPool(2)
        try {
            repeat(100) {
                store.setActive(first)
                val start = CountDownLatch(1)
                val expiry = executor.submit { start.await(); store.expireActive(first) }
                val switch = executor.submit { start.await(); otherStore.setActive(second) }
                start.countDown()
                expiry.get(5, TimeUnit.SECONDS)
                switch.get(5, TimeUnit.SECONDS)
                assertEquals(second, store.read())
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
