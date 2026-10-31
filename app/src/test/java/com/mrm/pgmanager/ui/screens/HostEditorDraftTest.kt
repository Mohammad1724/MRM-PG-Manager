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

    @Test fun `xudp aliases can be read edited and created without dropping sibling fields`() {
        val raw = JSONObject(original.raw).put("mux_settings", JSONObject().put("xray", JSONObject()
            .put("xudpConcurrency", 8).put("xudpProxyUDP443", "allow").put("future", "keep")))
        val host = original.copy(raw = raw.toString())
        val draft = HostDraft.from(host)
        assertEquals("8", draft.field("mux_settings.xray.xudp_concurrency"))
        assertEquals("allow", draft.field("mux_settings.xray.xudp_proxy_udp_443"))
        val edited = draft.withField("mux_settings.xray.xudp_concurrency", "15")
            .withField("mux_settings.xray.xudp_proxy_udp_443", "skip")
        assertNull(edited.validation(host))
        val result = HostEditMerge.merge(host.raw, host.raw, host.id, edited.changes(host))
            .getJSONObject("mux_settings").getJSONObject("xray")
        assertEquals(15, result.getInt("xudp_concurrency"))
        assertEquals(15, result.getInt("xudpConcurrency"))
        assertEquals("skip", result.getString("xudp_proxy_udp_443"))
        assertEquals("skip", result.getString("xudpProxyUDP443"))
        assertEquals("keep", result.getString("future"))
        val blank = PanelHost(id = 0, remark = "")
        val create = HostDraft.from(blank).copy(remark = "new", addressesText = "example.com", inboundTag = "vless")
            .withField("mux_settings.xray.xudp_concurrency", "7")
            .withField("mux_settings.xray.xudp_proxy_udp_443", "allow")
        val body = JSONObject()
        HostEditMerge.apply(body, create.changes(blank))
        assertEquals(7, body.getJSONObject("mux_settings").getJSONObject("xray").getInt("xudp_concurrency"))
        assertEquals("allow", body.getJSONObject("mux_settings").getJSONObject("xray").getString("xudp_proxy_udp_443"))
    }

    @Test fun `xhttp nullable switches and range settings preserve defaults and reject invalid edits`() {
        val draft = HostDraft.from(original)
        assertEquals("", draft.field("transport_settings.xhttp_settings.no_grpc_header"))
        val changed = draft.withField("transport_settings.xhttp_settings.no_grpc_header", "false")
            .withField("transport_settings.xhttp_settings.x_padding_obfs_mode", "true")
            .withField("transport_settings.xhttp_settings.x_padding_bytes", "100-200")
            .withField("transport_settings.xhttp_settings.sc_max_each_post_bytes", "1024")
        assertNull(changed.validation(original))
        val xhttp = HostEditMerge.merge(original.raw, original.raw, original.id, changed.changes(original))
            .getJSONObject("transport_settings").getJSONObject("xhttp_settings")
        assertFalse(xhttp.getBoolean("no_grpc_header"))
        assertTrue(xhttp.getBoolean("x_padding_obfs_mode"))
        assertEquals("100-200", xhttp.getString("x_padding_bytes"))
        assertEquals("1024", xhttp.getString("sc_max_each_post_bytes"))
        assertEquals("transport_settings.xhttp_settings.x_padding_bytes", changed
            .withField("transport_settings.xhttp_settings.x_padding_bytes", "1-2-3").validation(original)?.field)
        assertEquals("transport_settings.xhttp_settings.session_id_table", draft
            .withField("transport_settings.xhttp_settings.session_id_table", "غيرASCII").validation(original)?.field)
    }

    @Test fun `masque xmux validates ordered bounded ranges and mutually exclusive positive limits`() {
        val draft = HostDraft.from(original)
        val prefix = "transport_settings.masque_settings.xmux."
        assertEquals(prefix + "max_connections", draft.withField(prefix + "max_connections", "8-2").validation(original)?.field)
        assertEquals(prefix + "c_max_reuse_times", draft.withField(prefix + "c_max_reuse_times", "2147483648").validation(original)?.field)
        assertEquals("masque_xmux_conflict", draft.withField(prefix + "max_connections", "1-3")
            .withField(prefix + "max_concurrency", "8").validation(original)?.code)
        assertNull(draft.withField(prefix + "max_connections", "0").withField(prefix + "max_concurrency", "8-16").validation(original))
    }

    @Test fun `noise edits retain integer arrays unknown fields and sibling settings`() {
        val noise = JSONArray().put(JSONObject().put("type", "hex").put("packet", JSONArray(listOf(1, 2)))
            .put("delay", 10).put("apply_to", "ipv4").put("future", "keep"))
        val raw = JSONObject(original.raw).put("noise_settings", JSONObject().put("xray", noise).put("future", "sibling"))
        val host = original.copy(raw = raw.toString())
        val draft = HostDraft.from(host)
        assertTrue(draft.noiseSupported)
        assertTrue(draft.noise.single().packetIsArray)
        assertEquals("[1,2]", draft.noise.single().packet)
        assertTrue(draft.changes(host).isEmpty())
        val edited = draft.withNoise(listOf(draft.noise.single().copy(applyTo = "ipv6"), HostNoiseItem(packet = "[3,4]")))
        assertNull(edited.validation(host))
        val result = HostEditMerge.merge(host.raw, host.raw, host.id, edited.changes(host)).getJSONObject("noise_settings")
        assertEquals("sibling", result.getString("future"))
        val rows = result.getJSONArray("xray")
        assertEquals(2, rows.length())
        assertEquals(1, rows.getJSONObject(0).getJSONArray("packet").getInt(0))
        assertEquals(10, rows.getJSONObject(0).getInt("delay"))
        assertEquals("keep", rows.getJSONObject(0).getString("future"))
        assertEquals("ipv6", rows.getJSONObject(0).getString("apply_to"))
        assertEquals("[3,4]", rows.getJSONObject(1).getString("packet"))
        val cleared = draft.withNoise(emptyList())
        assertNull(cleared.changes(host).single().value)
    }

    @Test fun `invalid and unsupported noise is never silently rewritten`() {
        val draft = HostDraft.from(original)
        val invalidArray = draft.withNoise(listOf(HostNoiseItem(packet = "[1, 2] trailing", packetIsArray = true)))
        assertEquals("noise_packet", invalidArray.validation(original)?.code)
        assertEquals("noise_delay", draft.withNoise(listOf(HostNoiseItem(delay = "-1"))).validation(original)?.code)
        assertThrows(IllegalArgumentException::class.java) { invalidArray.changes(original) }
        val bad = listOf<Any>("unexpected", JSONArray(listOf("unexpected")), JSONObject().put("xray", JSONArray().put(42)))
        bad.forEach { value ->
            val host = original.copy(raw = JSONObject(original.raw).put("noise_settings", value).toString())
            val unsupported = HostDraft.from(host)
            assertFalse(unsupported.noiseSupported)
            assertEquals(listOf("remark"), unsupported.copy(remark = "changed").changes(host).map { it.path.single() })
            assertEquals("noise_unsupported", unsupported.withNoise(listOf(HostNoiseItem())).validation(host)?.code)
        }
    }

    @Test fun `noise list changes detect concurrent additions`() {
        val first = JSONObject(original.raw).put("noise_settings", JSONObject().put("xray", JSONArray()
            .put(JSONObject().put("type", "rand").put("packet", "10-20").put("delay", "5").put("apply_to", "ip"))))
        val host = original.copy(raw = first.toString())
        val draft = HostDraft.from(host)
        val latest = JSONObject(host.raw)
        latest.getJSONObject("noise_settings").getJSONArray("xray")
            .put(JSONObject().put("type", "str").put("packet", "server").put("apply_to", "ip"))
        assertThrows(com.mrm.pgmanager.data.api.HostEditConflictException::class.java) {
            HostEditMerge.merge(host.raw, latest.toString(), host.id, draft.withNoise(emptyList()).changes(host))
        }
    }

    @Test fun `final mask json is optional guarded and preserves unknown shapes`() {
        val draft = HostDraft.from(original)
        assertTrue(draft.finalMaskSupported)
        assertTrue(draft.finalMaskText.contains("unknown"))
        assertTrue(draft.changes(original).isEmpty())
        assertEquals("final_mask_invalid", draft.withFinalMask("{broken").validation(original)?.code)
        assertEquals("final_mask_invalid", draft.withFinalMask("{} trailing").validation(original)?.code)
        val edited = draft.withFinalMask("""{"tcp":[],"future":"kept"}""")
        assertNull(edited.validation(original))
        assertEquals("kept", (edited.changes(original).single().value as JSONObject).getString("future"))
        val latest = JSONObject(original.raw).put("final_mask_settings", JSONObject().put("server", true))
        assertThrows(com.mrm.pgmanager.data.api.HostEditConflictException::class.java) {
            HostEditMerge.merge(original.raw, latest.toString(), original.id, edited.changes(original))
        }
        val unsupportedHost = original.copy(raw = JSONObject(original.raw).put("final_mask_settings", "unsupported").toString())
        val unsupported = HostDraft.from(unsupportedHost)
        assertFalse(unsupported.finalMaskSupported)
        assertEquals("final_mask_unsupported", unsupported.withFinalMask("{}").validation(unsupportedHost)?.code)
        assertTrue(unsupported.copy(remark = "updated").changes(unsupportedHost).none { it.path == listOf("final_mask_settings") })
    }


    @Test fun `enabling one mux client deliberately disables the other two only on request`() {
        val raw = JSONObject(original.raw).put("mux_settings", JSONObject()
            .put("xray", JSONObject().put("enabled", false))
            .put("sing_box", JSONObject().put("enable", true).put("protocol", "yamux"))
            .put("clash", JSONObject().put("enable", true).put("future", "keep")))
        val host = original.copy(raw = raw.toString())
        val initial = HostDraft.from(host)
        assertTrue(initial.changes(host).isEmpty())
        val changed = initial.withMuxEnabled("mux_settings.xray.enabled", true)
        assertEquals("true", changed.field("mux_settings.xray.enabled"))
        assertEquals("false", changed.field("mux_settings.sing_box.enable"))
        assertEquals("false", changed.field("mux_settings.clash.enable"))
        val mux = HostEditMerge.merge(host.raw, host.raw, host.id, changed.changes(host)).getJSONObject("mux_settings")
        assertTrue(mux.getJSONObject("xray").getBoolean("enabled"))
        assertFalse(mux.getJSONObject("sing_box").getBoolean("enable"))
        assertFalse(mux.getJSONObject("clash").getBoolean("enable"))
        assertEquals("yamux", mux.getJSONObject("sing_box").getString("protocol"))
        assertEquals("keep", mux.getJSONObject("clash").getString("future"))
        val disabled = initial.withMuxEnabled("mux_settings.sing_box.enable", false)
        assertEquals("true", disabled.field("mux_settings.clash.enable"))
    }


    @Test fun `TCP header arrays are edited as whole maps without splitting commas`() {
        val raw = JSONObject(original.raw)
        raw.put("transport_settings", JSONObject().put("tcp_settings", JSONObject()
            .put("header", "http")
            .put("request", JSONObject().put("version", "1.1").put("method", "GET")
                .put("headers", JSONObject().put("Accept", JSONArray(listOf("text/html,application/json", "second")))
                    .put("Empty", JSONArray())))
            .put("future", "keep")))
        val host = original.copy(raw = raw.toString())
        val baseline = HostDraft.from(host)
        assertTrue(baseline.tcpRequestHeaders.supported)
        assertTrue(baseline.changes(host).isEmpty())
        val renamed = baseline.tcpRequestHeaders.headers.map { if (it.name == "Accept") it.copy(name = "Accept-Language") else it }
        val draft = baseline.withTcpHeaders("request", renamed)
            .withField("transport_settings.tcp_settings.request.method", "POST")
            .withField("transport_settings.tcp_settings.response.status", "201")
            .withField("transport_settings.tcp_settings.response.reason", "Created")
        assertNull(draft.validation(host))
        val tcp = HostEditMerge.merge(host.raw, host.raw, host.id, draft.changes(host))
            .getJSONObject("transport_settings").getJSONObject("tcp_settings")
        val headers = tcp.getJSONObject("request").getJSONObject("headers")
        assertEquals("text/html,application/json", headers.getJSONArray("Accept-Language").getString(0))
        assertEquals("second", headers.getJSONArray("Accept-Language").getString(1))
        assertEquals(0, headers.getJSONArray("Empty").length())
        assertEquals("POST", tcp.getJSONObject("request").getString("method"))
        assertEquals("201", tcp.getJSONObject("response").getString("status"))
        assertEquals("Created", tcp.getJSONObject("response").getString("reason"))
        assertEquals("keep", tcp.getString("future"))
        val reverted = draft.withTcpHeaders("request", baseline.tcpRequestHeaders.headers)
            .withField("transport_settings.tcp_settings.request.method", baseline.field("transport_settings.tcp_settings.request.method"))
            .withField("transport_settings.tcp_settings.response.status", baseline.field("transport_settings.tcp_settings.response.status"))
            .withField("transport_settings.tcp_settings.response.reason", baseline.field("transport_settings.tcp_settings.response.reason"))
        assertFalse(reverted.isDirtyComparedTo(baseline))
        assertTrue(reverted.changes(host).isEmpty())
    }

    @Test fun `TCP headers validate modified entries and preserve unsupported formats`() {
        val draft = HostDraft.from(original)
        assertEquals("http_header_name", draft.withTcpHeaders("request", listOf(HostTcpHeader("Bad Name", listOf("a")))).validation(original)?.code)
        assertEquals("http_header_duplicate", draft.withTcpHeaders("request", listOf(HostTcpHeader("A", listOf("a")), HostTcpHeader("a", listOf("b")))).validation(original)?.code)
        assertEquals("http_header_value", draft.withTcpHeaders("request", listOf(HostTcpHeader("A", listOf("a\rb")))).validation(original)?.code)
        assertEquals("transport_settings.tcp_settings.response.status", draft.withField("transport_settings.tcp_settings.response.status", "").validation(original)?.field)
        assertEquals("transport_settings.tcp_settings.response.reason", draft.withField("transport_settings.tcp_settings.response.reason", "invalid").validation(original)?.field)
        val raw = JSONObject(original.raw).put("transport_settings", JSONObject().put("tcp_settings", JSONObject()
            .put("request", JSONObject().put("headers", JSONObject().put("X", "not-an-array")))))
        val host = original.copy(raw = raw.toString())
        val unsupported = HostDraft.from(host)
        assertFalse(unsupported.tcpRequestHeaders.supported)
        assertEquals("tcp_headers_unsupported", unsupported.withTcpHeaders("request", listOf(HostTcpHeader("X", listOf("a")))).validation(host)?.code)
        val merged = HostEditMerge.merge(host.raw, host.raw, host.id, unsupported.copy(remark = "renamed").changes(host))
        assertEquals("not-an-array", merged.getJSONObject("transport_settings").getJSONObject("tcp_settings").getJSONObject("request").getJSONObject("headers").getString("X"))
    }

    @Test fun `TCP header map conflicts on concurrent changes and removal sends null`() {
        val raw = JSONObject(original.raw).put("transport_settings", JSONObject().put("tcp_settings", JSONObject()
            .put("request", JSONObject().put("headers", JSONObject().put("X", JSONArray(listOf("a")))))))
        val host = original.copy(raw = raw.toString())
        val draft = HostDraft.from(host).withTcpHeaders("request", emptyList())
        val change = draft.changes(host).single()
        assertNull(change.value)
        val latest = JSONObject(raw.toString())
        latest.getJSONObject("transport_settings").getJSONObject("tcp_settings").getJSONObject("request")
            .getJSONObject("headers").put("Y", JSONArray(listOf("server")))
        assertThrows(com.mrm.pgmanager.data.api.HostEditConflictException::class.java) {
            HostEditMerge.merge(host.raw, latest.toString(), host.id, listOf(change))
        }
        val result = HostEditMerge.merge(host.raw, host.raw, host.id, listOf(change))
        assertTrue(result.getJSONObject("transport_settings").getJSONObject("tcp_settings").getJSONObject("request").isNull("headers"))
    }

}
