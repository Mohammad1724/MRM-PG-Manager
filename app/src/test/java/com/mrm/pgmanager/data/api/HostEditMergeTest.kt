package com.mrm.pgmanager.data.api

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HostEditMergeTest {
    private val original = """{"id":7,"remark":"original","address":["a","b"],
        "status":["active","on_hold"],"subscription_templates":{"xray":4,"other":"keep"},
        "mux_settings":{"xray":{"enabled":true,"xudpConcurrency":9,"xudpProxyUDP443":"allow"}}}"""

    @Test fun `nested update keeps new server siblings and normalizes response aliases for put`() {
        val latest = JSONObject(original)
        latest.put("remark", "server remark")
        latest.getJSONObject("subscription_templates").put("other", "new")
        val result = HostEditMerge.merge(original, latest.toString(), 7, listOf(
            HostFieldChange(listOf("subscription_templates", "xray"), null)
        ))
        assertEquals("server remark", result.getString("remark"))
        assertTrue(result.getJSONObject("subscription_templates").isNull("xray"))
        assertEquals("new", result.getJSONObject("subscription_templates").getString("other"))
        val xray = result.getJSONObject("mux_settings").getJSONObject("xray")
        assertEquals(9, xray.getInt("xudp_concurrency"))
        assertEquals("allow", xray.getString("xudp_proxy_udp_443"))
        assertTrue(xray.getBoolean("enabled"))
    }

    @Test fun `set ordering does not create a conflict`() {
        val latest = JSONObject(original).put("status", JSONArray(listOf("on_hold", "active")))
        val result = HostEditMerge.merge(original, latest.toString(), 7, listOf(
            HostFieldChange("status", JSONArray(listOf("active")))
        ))
        assertEquals(1, result.getJSONArray("status").length())
        assertEquals("active", result.getJSONArray("status").getString(0))
    }

    @Test fun `actual same field conflict fails rather than overwriting`() {
        val latest = JSONObject(original).put("remark", "server")
        val error = assertThrows(HostEditConflictException::class.java) {
            HostEditMerge.merge(original, latest.toString(), 7, listOf(HostFieldChange("remark", "mine")))
        }
        assertEquals(listOf("remark"), error.fields)
    }

    @Test fun `matching server edit is not a conflict`() {
        val latest = JSONObject(original).put("remark", "mine")
        val result = HostEditMerge.merge(original, latest.toString(), 7, listOf(HostFieldChange("remark", "mine")))
        assertEquals("mine", result.getString("remark"))
    }

    @Test fun `an invalid parent and ambiguous aliases are rejected`() {
        val invalid = JSONObject(original).put("subscription_templates", "unsupported")
        assertThrows(HostEditConflictException::class.java) {
            HostEditMerge.merge(original, invalid.toString(), 7, listOf(
                HostFieldChange(listOf("subscription_templates", "xray"), 5)
            ))
        }
        val ambiguous = JSONObject(original)
        ambiguous.getJSONObject("mux_settings").getJSONObject("xray").put("xudp_concurrency", 20)
        assertThrows(HostEditConflictException::class.java) {
            HostEditMerge.merge(original, ambiguous.toString(), 7, listOf(HostFieldChange("remark", "mine")))
        }
    }

    @Test fun `header maps conflict as a whole and never erase concurrent additions`() {
        val first = JSONObject(original).put("http_headers", JSONObject().put("X-One", "one"))
        val latest = JSONObject(first.toString()).put("priority", 4)
        latest.getJSONObject("http_headers").put("X-Server", "added")
        val mine = JSONObject().put("X-One", "edited")
        val conflict = assertThrows(HostEditConflictException::class.java) {
            HostEditMerge.merge(first.toString(), latest.toString(), 7, listOf(HostFieldChange("http_headers", mine)))
        }
        assertEquals(listOf("http_headers"), conflict.fields)
        val deletion = assertThrows(HostEditConflictException::class.java) {
            HostEditMerge.merge(first.toString(), latest.toString(), 7, listOf(HostFieldChange("http_headers", null)))
        }
        assertEquals(listOf("http_headers"), deletion.fields)
        val otherEdit = HostEditMerge.merge(first.toString(), latest.toString(), 7, listOf(HostFieldChange("remark", "new")))
        assertEquals("added", otherEdit.getJSONObject("http_headers").getString("X-Server"))
        assertEquals(4, otherEdit.getInt("priority"))
    }

    @Test fun `invalid snapshots and overlapping edits cannot produce a put body`() {
        assertThrows(IllegalArgumentException::class.java) {
            HostEditMerge.merge("{}", original, 7, listOf(HostFieldChange("remark", "mine")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HostEditMerge.merge(original, original, 7, listOf(
                HostFieldChange("subscription_templates", JSONObject()),
                HostFieldChange(listOf("subscription_templates", "xray"), 5)
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HostEditMerge.merge(original, original, 7, listOf(HostFieldChange("id", 9)))
        }
    }

    @Test fun `missing or null nested parent can be created without replacing siblings`() {
        val first = """{"id":7,"remark":"old","subscription_templates":null}"""
        val latest = """{"id":7,"remark":"old","subscription_templates":{"future":"preserved"}}"""
        val result = HostEditMerge.merge(first, latest, 7, listOf(
            HostFieldChange(listOf("subscription_templates", "xray"), 3)
        ))
        assertEquals(3, result.getJSONObject("subscription_templates").getInt("xray"))
        assertEquals("preserved", result.getJSONObject("subscription_templates").getString("future"))
        assertFalse(result.has("address"))
    }
}
