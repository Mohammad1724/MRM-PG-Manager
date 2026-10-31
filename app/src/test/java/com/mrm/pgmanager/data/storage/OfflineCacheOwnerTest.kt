package com.mrm.pgmanager.data.storage

import com.mrm.pgmanager.data.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OfflineCacheOwnerTest {
    @Test fun `identity includes server username and credential without exposing the token`() {
        val session = Session("https://panel.example", "credential-a", "admin")
        val owner = offlineCacheOwner(session)
        assertEquals(64, owner.length)
        assertEquals(owner, offlineCacheOwner(session.copy()))
        assertNotEquals(owner, offlineCacheOwner(session.copy(baseUrl = "https://other.example")))
        assertNotEquals(owner, offlineCacheOwner(session.copy(username = "other")))
        assertNotEquals(owner, offlineCacheOwner(session.copy(token = "credential-b")))
        assertFalse(owner.contains(session.token))
    }

    @Test fun `length prefixes avoid ambiguous identity boundaries`() {
        assertNotEquals(
            offlineCacheOwner(Session("ab", "d", "c")),
            offlineCacheOwner(Session("a", "d", "bc"))
        )
    }
}
