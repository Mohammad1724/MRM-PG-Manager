package com.mrm.pgmanager.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.MonitoringSettings
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.storage.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MonitoringWorkerSessionTest {
    private lateinit var context: Context
    private lateinit var store: SessionStore
    private lateinit var server: MockWebServer
    private lateinit var first: Session
    private lateinit var second: Session

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("monitoring-worker-session-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        SessionStore.resetPrefsForTests()
        SessionStore.prefsFactory = { prefs }
        store = SessionStore(context)
        server = MockWebServer().apply { start() }
        first = Session(server.url("/").toString().trimEnd('/'), "token-a", "alice")
        second = first.copy(token = "token-b", username = "bob")
        store.save(first)
        store.saveMonitoringSettings(MonitoringSettings(notificationsEnabled = false, offlineCacheEnabled = true))
        PanelApi.allowCleartextLoopbackForTests = true
    }

    @After fun tearDown() {
        PanelApi.allowCleartextLoopbackForTests = false
        SessionStore.resetPrefsForTests()
        server.shutdown()
    }

    private fun worker() = TestListenableWorkerBuilder<MonitoringWorker>(context).build()

    @Test fun `a late system response cannot start work for an old account`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"cpu_usage":1}""").setHeadersDelay(1, TimeUnit.SECONDS))
        val result = async(Dispatchers.IO) { worker().doWork() }
        assertEquals("/api/system", server.takeRequest(5, TimeUnit.SECONDS)?.path)

        store.setActive(second)

        assertEquals(ListenableWorker.Result.success(), withTimeout(5_000) { result.await() })
        assertEquals(second, store.read())
        assertNull(store.readStatsCache(first))
        assertEquals(1, server.requestCount)
    }

    @Test fun `cancelling monitoring closes a blocked system request`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val result = async(Dispatchers.IO) { worker().doWork() }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        withTimeout(3_000) { result.cancelAndJoin() }
        assertTrue(result.isCancelled)
        assertEquals(first, store.read())
        assertNull(store.readExpiredAccount())
    }

    @Test fun `missing cpu does not reset an existing alert or break monitoring`() = runBlocking {
        store.saveMonitoringSettings(store.readMonitoringSettings().copy(
            notificationsEnabled = true, notifyLimited = false, notifyExpired = false,
            notifyNearLimit = false, notifyNearExpiry = false, notifyNodeOffline = false,
            offlineCacheEnabled = false, notifySystemHealth = true
        ))
        store.saveAlertFlag(first, "cpu", true)
        server.enqueue(MockResponse().setBody("""{"total_user":2,"mem_total":1,"disk_total":1}"""))

        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertTrue(store.readAlertFlag(first, "cpu"))
        assertTrue(store.readStatsCache(first)!!.first.cpuUsage.isNaN())
        assertEquals(2, store.readStatsCache(first)!!.first.totalUsers)
    }

    @Test fun `an active unauthorized session is expired once`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertNull(store.read())
        assertEquals(first.baseUrl, store.readExpiredAccount()?.baseUrl)
        assertEquals(first.username, store.readExpiredAccount()?.username)
        assertEquals(1, server.requestCount)
    }

    @Test fun `a late 401 does not expire the new account`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setHeadersDelay(1, TimeUnit.SECONDS))
        val result = async(Dispatchers.IO) { worker().doWork() }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))

        store.setActive(second)

        assertEquals(ListenableWorker.Result.success(), withTimeout(5_000) { result.await() })
        assertEquals(second, store.read())
        assertNull(store.readExpiredAccount())
    }

    @Test fun `a late users response is not saved after switching accounts`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"cpu_usage":1}"""))
        server.enqueue(MockResponse().setBody("""{"users":[]}""").setHeadersDelay(1, TimeUnit.SECONDS))
        val result = async(Dispatchers.IO) { worker().doWork() }
        assertEquals("/api/system", server.takeRequest(5, TimeUnit.SECONDS)?.path)
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))

        store.setActive(second)

        assertEquals(ListenableWorker.Result.success(), withTimeout(5_000) { result.await() })
        assertNull(store.readUsersCache(first))
        assertEquals(second, store.read())
    }
}
