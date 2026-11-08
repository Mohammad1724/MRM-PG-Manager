package com.mrm.pgmanager.ui.screens

import android.content.Context
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
import com.mrm.pgmanager.data.model.MonitoringSettings
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.storage.SessionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UsersSubscriptionFetchTest {
    private lateinit var server: MockWebServer
    private lateinit var session: Session
    private lateinit var store: SessionStore
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val user = PanelUser(
        id = 7L, username = "alpha", status = "active", usedTraffic = 0L, dataLimit = 0L,
        expire = null, createdAt = null
    )

    @Before fun setUp() {
        SessionStore.resetPrefsForTests()
        SessionStore.prefsFactory = { it.getSharedPreferences("share_fetch_test", Context.MODE_PRIVATE) }
        context.getSharedPreferences("share_fetch_test", Context.MODE_PRIVATE).edit().clear().commit()
        store = SessionStore(context)
        PanelCache.clear()
        server = MockWebServer().also { it.start() }
        PanelApi.allowCleartextLoopbackForTests = true
        session = Session(server.url("/").toString().trimEnd('/'), "test-token", "test-admin")
    }

    @After fun tearDown() {
        SessionStore.resetPrefsForTests()
        PanelApi.allowCleartextLoopbackForTests = false
        PanelCache.clear()
        server.shutdown()
    }

    @Test fun `existing link opens the sheet without a network request`() = runBlocking {
        val state = UsersUiState(this, context, session, store, MonitoringSettings(), {})
        val withLink = user.copy(subUrl = "https://example.test/sub/alpha")
        state.qrWithFetch(withLink)
        assertSame(withLink, state.qrUser)
        assertEquals(0, server.requestCount)
    }

    @Test fun `missing link fetches the selected user before opening the sheet`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":7,"username":"alpha","status":"active","subscription_url":"/sub/alpha"}"""))
        val state = UsersUiState(this, context, session, store, MonitoringSettings(), {})
        state.qrWithFetch(user)
        coroutineContext[Job]!!.children.toList().joinAll()
        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("/api/user/by-id/7", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        assertEquals("${session.baseUrl}/sub/alpha", state.qrUser?.subUrl)
        assertEquals(user.id, state.qrUser?.id)
    }

    @Test fun `failed fetch does not open an empty sheet`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        val state = UsersUiState(this, context, session, store, MonitoringSettings(), {})
        state.qrWithFetch(user)
        coroutineContext[Job]!!.children.toList().joinAll()
        assertNull(state.qrUser)
        assertEquals(1, server.requestCount)
    }
}
