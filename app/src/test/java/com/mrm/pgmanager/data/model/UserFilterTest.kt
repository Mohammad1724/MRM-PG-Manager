package com.mrm.pgmanager.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** نگاشتِ فیلترهای برنامه به پارامترهای `UserListQuery` پنل. */
class UserFilterTest {

    @Test fun `only near-limit and debtor are local filters`() {
        val local = UserFilter.values().filter { !it.serverSide }
        assertEquals(listOf(UserFilter.NEAR_LIMIT, UserFilter.DEBTOR), local)
    }

    @Test fun `boolean panel flags are set only by their own filter`() {
        assertTrue(UserFilter.NO_LIMIT.panelNoDataLimit == true)
        assertTrue(UserFilter.NO_EXPIRE.panelNoExpire == true)
        assertTrue(UserFilter.NO_GROUP.panelNoGroup == true)
        assertTrue(UserFilter.ONLINE.panelOnline == true)
        UserFilter.values().filter { it != UserFilter.NO_LIMIT }.forEach { assertNull(it.name, it.panelNoDataLimit) }
        UserFilter.values().filter { it != UserFilter.NO_EXPIRE }.forEach { assertNull(it.name, it.panelNoExpire) }
        UserFilter.values().filter { it != UserFilter.NO_GROUP }.forEach { assertNull(it.name, it.panelNoGroup) }
        assertNull(UserFilter.EXPIRING_SOON.panelStatus)
    }

    @Test fun `expiring window starts now and ends N days later in UTC`() {
        val now = java.time.Instant.parse("2026-09-29T10:15:30.250Z")
        val (after, before) = UserQuery.expiringWindow(7, now)
        assertEquals("2026-09-29T10:15:30Z", after)
        assertEquals("2026-10-06T10:15:30Z", before)
    }

    @Test fun `expiring window honours the settings threshold but never drops below the floor`() {
        assertEquals(7, UserQuery.expiringWindowDays(1))
        assertEquals(7, UserQuery.expiringWindowDays(7))
        assertEquals(15, UserQuery.expiringWindowDays(15))
    }

    @Test fun `last online sort maps to the panel's descending online_at key`() {
        assertEquals("-online_at", UserSort.LAST_ONLINE.panelSort)
        assertFalse(UserSort.values().map { it.panelSort }.toSet().size != UserSort.values().size)
    }
}
