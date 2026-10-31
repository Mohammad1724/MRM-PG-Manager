package com.mrm.pgmanager.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WireGuardIpPagingTest {
    private val ips = (1..10_000).map { "2001:db8::$it" }

    @Test fun `large server lists are rendered in bounded pages`() {
        val first = pageWireGuardIps(ips, "", 0)
        assertEquals(20, first.ips.size)
        assertEquals(10_000, first.total)
        assertEquals(0, first.start)
        assertEquals(20, first.end)
        assertEquals(499, first.lastPage)

        val last = pageWireGuardIps(ips, "", 499)
        assertEquals(9_980, last.start)
        assertEquals(10_000, last.end)
        assertEquals("2001:db8::10000", last.ips.last())
    }

    @Test fun `ip search returns the matching addresses and clamps old pages`() {
        val result = pageWireGuardIps(ips, "::9999", 500)
        assertEquals(listOf("2001:db8::9999"), result.ips)
        assertEquals(1, result.total)
        assertEquals(0, result.page)
    }

    @Test fun `a matching tag or subnet leaves the supplied ips visible`() {
        val result = pageWireGuardIps(ips, "wg-interface", -1)
        assertEquals(10_000, result.total)
        assertEquals(0, result.page)
        assertEquals(ips.take(20), result.ips)
    }

    @Test fun `empty and shortened lists remain safe after refresh`() {
        val empty = pageWireGuardIps(emptyList(), "", 5)
        assertTrue(empty.ips.isEmpty())
        assertEquals(0, empty.start)
        assertEquals(0, empty.end)
        val shortened = pageWireGuardIps(ips.take(22), "", 500)
        assertEquals(1, shortened.page)
        assertEquals(2, shortened.ips.size)
    }
}
