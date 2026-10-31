package com.mrm.pgmanager.data

import com.mrm.pgmanager.data.model.AdminSelf
import com.mrm.pgmanager.data.model.PermissionScope
import com.mrm.pgmanager.data.model.Session
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdminAccessTest {
    private val first = Session("https://one.example.test", "pg_key_first", "first")
    private val second = Session("https://two.example.test", "pg_key_second", "second")
    private val owner = AdminSelf(id = 1, username = "first", isOwner = true)
    private val reader = AdminSelf(id = 2, username = "second", permissions = mapOf(
        "nodes" to mapOf("read" to PermissionScope.OWN)
    ))

    @Before fun before() = AdminAccess.reset()
    @After fun after() = AdminAccess.reset()

    @Test fun `unknown and unavailable permissions fail closed`() = runBlocking {
        assertFalse(AdminAccess.can("nodes", "delete"))
        assertFalse(AdminAccess.scopeAll("nodes", "read"))
        AdminAccess.refreshWith(first) { throw IOException("offline") }
        assertEquals(AdminAccessState.Unavailable, AdminAccess.state)
        assertNull(AdminAccess.current)
        assertFalse(AdminAccess.can("nodes", "read"))
    }

    @Test fun `verified permissions and scope are respected`() = runBlocking {
        AdminAccess.refreshWith(second) { reader }
        assertEquals(AdminAccessState.Ready, AdminAccess.state)
        assertTrue(AdminAccess.can("nodes", "read"))
        assertFalse(AdminAccess.scopeAll("nodes", "read"))
        assertFalse(AdminAccess.can("nodes", "delete"))
        AdminAccess.refreshWith(first) { owner }
        assertTrue(AdminAccess.scopeAll("nodes", "read"))
    }

    @Test fun `stale refresh cannot replace a different account`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val old = launch { AdminAccess.refreshWith(first) { started.complete(Unit); release.await(); owner } }
        started.await()
        AdminAccess.reset()
        AdminAccess.refreshWith(second) { reader }
        release.complete(Unit)
        old.join()
        assertEquals("second", AdminAccess.current?.username)
        assertFalse(AdminAccess.can("nodes", "delete"))
    }

    @Test fun `cancellation is propagated without allowing actions`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val loading = launch { AdminAccess.refreshWith(first) { started.complete(Unit); CompletableDeferred<Unit>().await(); owner } }
        started.await()
        loading.cancelAndJoin()
        assertEquals(AdminAccessState.Unknown, AdminAccess.state)
        assertFalse(AdminAccess.can("nodes", "delete"))
    }
}
