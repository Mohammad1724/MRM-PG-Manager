package com.mrm.pgmanager.ui.screens

import com.mrm.pgmanager.data.api.HostEditMerge
import com.mrm.pgmanager.data.model.PanelHost
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HostEditorDraftTest {
    private val original = PanelHost(
        id = 11,
        remark = "example",
        addresses = listOf("a.example", "b.example"),
        inboundTag = "vless",
        port = 443,
        security = "tls",
        isDisabled = false,
        status = listOf("active", "future_status"),
        raw = """{"id":11,"remark":"example","address":["a.example","b.example"],
            "inbound_tag":"vless","port":443,"security":"tls","is_disabled":false,
            "status":["active","future_status"],"priority":2,"alpn":["h2"],
            "subscription_templates":{"xray":7,"future":"keep"},
            "ech":{"xray":{"config_list":"abc","unknown":"keep"}},
            "wireguard_overrides":{"allowed_ips":["10.0.0.0/8"],"dns":["1.1.1.1"]},
            "fragment_settings":{"sing_box":{"fragment_fallback_delay":"500ms"}},
            "final_mask_settings":{"unknown":"keep"}}"""
    )

    @Test fun `untouched draft never rewrites basic or advanced fields`() {
        val draft = HostDraft.from(original)
        assertNull(draft.validation(original))
        assertTrue(draft.changes(original).isEmpty())
        assertEquals(7, draft.templateId)
        assertEquals(listOf("active", "future_status"), draft.status)
        assertEquals("abc", draft.field("ech.xray.config_list"))
    }

    @Test fun `status filter is independent from host disabled and keeps unknown statuses`() {
        val draft = HostDraft.from(original).copy(status = listOf("active", "future_status", "disabled"))
        val changes = draft.changes(original)
        assertEquals(listOf("status"), changes.map { it.path.single() })
        val values = changes.single().value as JSONArray
        assertEquals("future_status", values.getString(1))
        assertEquals("disabled", values.getString(2))
        assertFalse(draft.disabled)
        val baseline = HostDraft.from(original)
        assertFalse(baseline.copy(status = baseline.status.reversed()).isDirtyComparedTo(baseline))
    }

    @Test fun `template is only changed after an explicit selection`() {
        val draft = HostDraft.from(original)
        assertTrue(draft.copy(templateTouched = true).changes(original).isEmpty())
        val changes = draft.copy(templateId = null, templateTouched = true).changes(original)
        assertEquals(listOf("subscription_templates", "xray"), changes.single().path)
        val result = HostEditMerge.merge(original.raw, original.raw, original.id, changes)
        assertTrue(result.getJSONObject("subscription_templates").isNull("xray"))
        assertEquals("keep", result.getJSONObject("subscription_templates").getString("future"))
    }

    @Test fun `an unrecognized saved template is preserved until the user deliberately chooses`() {
        val raw = JSONObject(original.raw)
        raw.getJSONObject("subscription_templates").put("xray", "unrecognized")
        val host = original.copy(raw = raw.toString())
        val draft = HostDraft.from(host)
        assertNull(draft.templateId)
        assertTrue(HostDraft.hasUnrecognizedTemplate(host.raw))
        assertTrue(draft.copy(remark = "renamed").changes(host).none { it.path.first() == "subscription_templates" })
        assertEquals(listOf("subscription_templates", "xray"), draft.copy(templateTouched = true).changes(host).single().path)
    }

    @Test fun `nested settings merge independently and preserve siblings`() {
        val draft = HostDraft.from(original)
            .withField("ech.xray.config_list", "new")
            .withField("transport_settings.grpc_settings.multi_mode", "true")
            .withField("mux_settings.sing_box.protocol", "yamux")
            .withField("wireguard_overrides.dns", "")
        assertNull(draft.validation(original))
        val merged = HostEditMerge.merge(original.raw, original.raw, original.id, draft.changes(original))
        assertEquals("new", merged.getJSONObject("ech").getJSONObject("xray").getString("config_list"))
        assertEquals("keep", merged.getJSONObject("ech").getJSONObject("xray").getString("unknown"))
        assertTrue(merged.getJSONObject("transport_settings").getJSONObject("grpc_settings").getBoolean("multi_mode"))
        assertEquals("yamux", merged.getJSONObject("mux_settings").getJSONObject("sing_box").getString("protocol"))
        assertTrue(merged.getJSONObject("wireguard_overrides").isNull("dns"))
        assertEquals("keep", merged.getJSONObject("final_mask_settings").getString("unknown"))
    }

    @Test fun `priority supports zero and negatives but cannot clear an existing value`() {
        val draft = HostDraft.from(original)
        assertEquals("2", draft.field("priority"))
        assertEquals("priority", draft.withField("priority", "").validation(original)?.field)
        val changed = draft.withField("priority", "-1")
        assertNull(changed.validation(original))
        assertEquals(-1, changed.changes(original).single().value)
        val zero = draft.withField("priority", "0")
        assertNull(zero.validation(original))
        assertEquals(0, zero.changes(original).single().value)
        val blank = PanelHost(id = 0, remark = "")
        val create = HostDraft.from(blank).copy(remark = "new", addressesText = "example.com", inboundTag = "vless")
        assertNull(create.validation(blank))
        assertTrue(create.changes(blank).none { it.path == listOf("priority") })
    }

    @Test fun `certificate verification distinguishes inherited true and explicit false`() {
        val inherited = HostDraft.from(original)
        assertEquals("", inherited.field("allowinsecure"))
        assertTrue(inherited.changes(original).none { it.path == listOf("allowinsecure") })
        val verify = inherited.withField("allowinsecure", "false")
        assertEquals(false, verify.changes(original).single().value)
        val insecure = inherited.withField("allowinsecure", "true")
        assertEquals(true, insecure.changes(original).single().value)
        val explicitHost = original.copy(raw = JSONObject(original.raw).put("allowinsecure", true).toString())
        val clearOverride = HostDraft.from(explicitHost).withField("allowinsecure", "")
        assertNull(clearOverride.changes(explicitHost).single().value)
    }

    @Test fun `advanced invalid inputs are blocked and do not clear other values`() {
        val draft = HostDraft.from(original)
        assertEquals("wireguard_overrides.mtu", draft.withField("wireguard_overrides.mtu", "575").validation(original)?.field)
        assertEquals("wireguard_overrides.keepalive_seconds", draft.withField("wireguard_overrides.keepalive_seconds", "86401").validation(original)?.field)
        assertEquals("vless_route", draft.withField("vless_route", "xyz").validation(original)?.field)
        assertEquals("fragment_settings.sing_box.fragment_fallback_delay", draft.withField("fragment_settings.sing_box.fragment_fallback_delay", "4s").validation(original)?.field)
        assertEquals("transport_settings.grpc_settings.idle_timeout", draft.withField("transport_settings.grpc_settings.idle_timeout", "not a number").validation(original)?.field)
        assertNull(draft.withField("wireguard_overrides.mtu", "9000").validation(original))
        assertNull(draft.withField("vless_route", "01aF").validation(original))
    }

    @Test fun `xray fragment requires all three fields and can be cleared atomically`() {
        val draft = HostDraft.from(original).withField("fragment_settings.xray.packets", "tlshello")
        assertEquals("fragment_settings.xray.length", draft.validation(original)?.field)
        val complete = draft.withField("fragment_settings.xray.length", "100-200")
            .withField("fragment_settings.xray.interval", "10-20")
        assertNull(complete.validation(original))
        val merged = HostEditMerge.merge(original.raw, original.raw, original.id, complete.changes(original))
        assertEquals("tlshello", merged.getJSONObject("fragment_settings").getJSONObject("xray").getString("packets"))
        assertEquals("500ms", merged.getJSONObject("fragment_settings").getJSONObject("sing_box").getString("fragment_fallback_delay"))
        val savedHost = original.copy(raw = merged.toString())
        val cleared = HostDraft.from(savedHost)
            .withField("fragment_settings.xray.packets", "")
            .withField("fragment_settings.xray.length", "")
            .withField("fragment_settings.xray.interval", "")
        assertNull(cleared.validation(savedHost))
        assertEquals(listOf("fragment_settings", "xray"), cleared.changes(savedHost).single().path)
        assertNull(cleared.changes(savedHost).single().value)
    }

    @Test fun `clearing sing box fallback uses its non nullable empty default`() {
        val draft = HostDraft.from(original).withField("fragment_settings.sing_box.fragment_fallback_delay", "")
        val change = draft.changes(original).single()
        assertEquals("", change.value)
    }

    @Test fun `http headers are edited as a complete map and untouched values stay exact`() {
        val raw = JSONObject(original.raw).put("http_headers", JSONObject()
            .put("X-Future", " untouched ").put("User-Agent", "original"))
        val host = original.copy(raw = raw.toString())
        val draft = HostDraft.from(host)
        assertTrue(draft.httpHeadersSupported)
        assertTrue(draft.changes(host).isEmpty())
        val reordered = draft.withHttpHeaders(draft.httpHeaders.reversed())
        assertFalse(reordered.isDirtyComparedTo(draft))
        assertTrue(reordered.changes(host).isEmpty())
        val edited = draft.withHttpHeaders(draft.httpHeaders.map {
            if (it.name == "User-Agent") it.copy(value = "new agent") else it
        } + HostHttpHeader("X-New", ""))
        assertNull(edited.validation(host))
        val change = edited.changes(host).single()
        assertEquals(listOf("http_headers"), change.path)
        val headers = change.value as JSONObject
        assertEquals(" untouched ", headers.getString("X-Future"))
        assertEquals("new agent", headers.getString("User-Agent"))
        assertEquals("", headers.getString("X-New"))
        assertFalse(edited.toString().contains("new agent"))
        val merged = HostEditMerge.merge(host.raw, host.raw, host.id, listOf(change))
        assertEquals("keep", merged.getJSONObject("final_mask_settings").getString("unknown"))
        assertEquals(" untouched ", merged.getJSONObject("http_headers").getString("X-Future"))
    }

    @Test fun `explicit header deletion uses null and undo preserves original empty representation`() {
        val host = original.copy(raw = JSONObject(original.raw).put("http_headers", JSONObject().put("X-A", "a")).toString())
        val draft = HostDraft.from(host)
        val removed = draft.withHttpHeaders(emptyList())
        assertNull(removed.validation(host))
        assertNull(removed.changes(host).single().value)
        assertTrue(HostEditMerge.merge(host.raw, host.raw, host.id, removed.changes(host)).isNull("http_headers"))
        listOf(JSONObject.NULL, JSONObject()).forEach { existing ->
            val emptyHost = original.copy(raw = JSONObject(original.raw).put("http_headers", existing).toString())
            val emptyDraft = HostDraft.from(emptyHost)
            val undo = emptyDraft.withHttpHeaders(listOf(HostHttpHeader("X-Test", "a"))).withHttpHeaders(emptyList())
            assertTrue(undo.changes(emptyHost).isEmpty())
            assertFalse(undo.isDirtyComparedTo(emptyDraft))
        }
    }

    @Test fun `invalid header names duplicates and control characters block serialization`() {
        val draft = HostDraft.from(original)
        listOf("", "Bad Name", "Bad:Name", "caf\u00e9", "x\ny").forEach { name ->
            val bad = draft.withHttpHeaders(listOf(HostHttpHeader(name, "ok")))
            assertEquals("http_header_name", bad.validation(original)?.code)
            assertThrows(IllegalArgumentException::class.java) { bad.changes(original) }
        }
        val duplicate = draft.withHttpHeaders(listOf(HostHttpHeader("X-Test", "a"), HostHttpHeader("x-test", "b")))
        assertEquals("http_header_duplicate", duplicate.validation(original)?.code)
        assertThrows(IllegalArgumentException::class.java) { duplicate.changes(original) }
        listOf("bad\r\nvalue", "bad\u0000value", "bad\u007fvalue", "bad\u0085value").forEach { value ->
            val bad = draft.withHttpHeaders(listOf(HostHttpHeader("X-Test", value)))
            assertEquals("http_header_value", bad.validation(original)?.code)
            assertThrows(IllegalArgumentException::class.java) { bad.changes(original) }
        }
        val valid = draft.withHttpHeaders(listOf(HostHttpHeader("X-Foo_1", "value\twith tab")))
        assertNull(valid.validation(original))
        assertEquals("value\twith tab", (valid.changes(original).single().value as JSONObject).getString("X-Foo_1"))
        val tokenCharacters = "!#$%&'*+-.^_`|~"
        tokenCharacters.forEach { character ->
            assertNull(draft.withHttpHeaders(listOf(HostHttpHeader("X$character", "ok"))).validation(original))
        }
        (0..0x9f).map(Int::toChar).filter { it != '\t' && it.isISOControl() }.forEach { control ->
            assertEquals("http_header_value", draft.withHttpHeaders(listOf(HostHttpHeader("X-Test", "a${control}b"))).validation(original)?.code)
        }
    }

    @Test fun `unsupported header formats are read only and preserved when other fields change`() {
        listOf(JSONObject().put("X-Future", 42), JSONArray(listOf("x")), "unexpected", 4).forEach { value ->
            val host = original.copy(raw = JSONObject(original.raw).put("http_headers", value).toString())
            val draft = HostDraft.from(host)
            assertFalse(draft.httpHeadersSupported)
            val changes = draft.copy(remark = "updated").changes(host)
            assertEquals(listOf("remark"), changes.map { it.path.single() })
            val merged = HostEditMerge.merge(host.raw, host.raw, host.id, changes)
            assertEquals(JSONObject(host.raw).get("http_headers").toString(), merged.get("http_headers").toString())
            val attempted = draft.withHttpHeaders(listOf(HostHttpHeader("X-A", "a")))
            assertEquals("http_headers_unsupported", attempted.validation(host)?.code)
            assertThrows(IllegalArgumentException::class.java) { attempted.changes(host) }
        }
    }

    @Test fun `new host can send the real http headers map`() {
        val blank = PanelHost(id = 0, remark = "")
        val draft = HostDraft.from(blank).copy(remark = "new", addressesText = "example.com", inboundTag = "inbound")
            .withHttpHeaders(listOf(HostHttpHeader("X-First", "one"), HostHttpHeader("X-Second", "two")))
        assertNull(draft.validation(blank))
        val body = JSONObject()
        HostEditMerge.apply(body, draft.changes(blank))
        assertEquals("one", body.getJSONObject("http_headers").getString("X-First"))
        assertEquals("two", body.getJSONObject("http_headers").getString("X-Second"))
    }

    @Test fun `new hosts require an inbound while existing hosts are not silently changed`() {
        val blank = PanelHost(id = 0, remark = "")
        val create = HostDraft.from(blank).copy(remark = "new", addressesText = "a.example")
        assertEquals("inbound", create.validation(blank)?.code)
        assertNull(create.copy(inboundTag = "vless").validation(blank))
        val existingWithoutInbound = original.copy(inboundTag = null)
        assertNull(HostDraft.from(existingWithoutInbound).validation(existingWithoutInbound))
    }

    @Test fun `new host can carry actual independent status template and advanced settings`() {
        val blank = PanelHost(id = 0, remark = "")
        val draft = HostDraft.from(blank).copy(
            remark = "new", addressesText = "a.example", inboundTag = "vless", status = listOf("on_hold"),
            templateId = 9, templateTouched = true, disabled = true
        ).withField("ech.xray.query_strategy", "full")
        assertNull(draft.validation(blank))
        val changes = draft.changes(blank)
        val body = JSONObject().put("remark", draft.remark).put("address", JSONArray(draft.addresses))
        HostEditMerge.apply(body, changes)
        assertTrue(body.getBoolean("is_disabled"))
        assertEquals("on_hold", body.getJSONArray("status").getString(0))
        assertEquals(9, body.getJSONObject("subscription_templates").getInt("xray"))
        assertEquals("full", body.getJSONObject("ech").getJSONObject("xray").getString("query_strategy"))
    }
}
