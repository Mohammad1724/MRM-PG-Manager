package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.CoreType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreConfigTagsTest {
    @Test fun `reads only this core's inbound tags and preserves their spelling`() {
        val config = """{"inbounds":[{"tag":"local-a"},{"tag":"local-b"},{"tag":"local-a"},{"tag":" "},{"port":123}]}"""
        assertEquals(listOf("local-a", "local-b"), coreConfigInboundTags(config, CoreType.XRAY))
        assertEquals(emptyList<String>(), coreConfigInboundTags("{}", CoreType.XRAY))
    }

    @Test fun `wireguard uses its interface name`() {
        assertEquals(listOf("wg-main"), coreConfigInboundTags("""{"interface_name":"wg-main"}""", CoreType.WG))
        assertEquals(emptyList<String>(), coreConfigInboundTags("""{"interface_name":"wg-main"}""", null))
        assertEquals(emptyList<String>(), coreConfigInboundTags("{}", CoreType.WG))
    }

    @Test fun `unsupported structure is not treated as an empty valid list`() {
        assertNull(coreConfigInboundTags("not json", CoreType.XRAY))
        assertNull(coreConfigInboundTags("""{"inbounds":{"tag":"x"}}""", CoreType.XRAY))
    }
}
