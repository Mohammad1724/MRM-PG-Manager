package com.mrm.pgmanager.data.api

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InfraReadModelsTest {
    @Test fun `inbounds preserve nullable networks and decode escaped tags`() {
        val rows = parseInboundDetails("""[{"tag":"t\"1","protocol":"vless","network":null},{"tag":"b","protocol":"trojan","network":"ws"}]""")
        assertEquals("t\"1", rows[0].tag)
        assertNull(rows[0].network)
        assertEquals("ws", rows[1].network)
    }

    @Test fun `wireguard capacity retains full ipv6 integer precision`() {
        val rows = parseWireGuardSubnets("""[{"subnet":"2001:db8::/64","interface_tags":["wg-a","wg-b"],"capacity":18446744073709551614,"used":3,"free":18446744073709551611,"free_ips":["2001:db8::4"],"future":{"x":1}}]""")
        assertEquals(BigInteger("18446744073709551614"), rows.single().capacity)
        assertEquals(BigInteger("18446744073709551611"), rows.single().free)
        assertEquals(listOf("wg-a", "wg-b"), rows.single().interfaceTags)
        assertEquals(listOf("2001:db8::4"), rows.single().freeIps)
    }

    @Test fun `empty arrays are valid but malformed shapes are not presented as empty`() {
        assertEquals(emptyList<InboundDetail>(), parseInboundDetails("[]"))
        assertEquals(emptyList<WireGuardSubnet>(), parseWireGuardSubnets("[]"))
        assertThrows(IllegalArgumentException::class.java) { parseInboundDetails("{}") }
        assertThrows(IllegalArgumentException::class.java) { parseInboundDetails("""[{"tag":"a"}]""") }
        assertThrows(IllegalArgumentException::class.java) { parseWireGuardSubnets("""[{"subnet":"x"}]""") }
        assertThrows(IllegalArgumentException::class.java) { parseWireGuardSubnets("[] trailing") }
    }

    @Test fun `duplicate and noninteger counters are rejected`() {
        val template = """[{"subnet":"x","interface_tags":[],"capacity":%s,"used":0,"free":0,"free_ips":[]}]"""
        for (invalid in listOf("-1", "1.5", "1e3", "\"10\"")) {
            assertThrows(IllegalArgumentException::class.java) { parseWireGuardSubnets(template.format(invalid)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseInboundDetails("""[{"tag":"a","tag":"b","protocol":"x"}]""")
        }
    }
}
