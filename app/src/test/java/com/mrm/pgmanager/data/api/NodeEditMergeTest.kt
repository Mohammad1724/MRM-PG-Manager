package com.mrm.pgmanager.data.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NodeEditMergeTest {
    private val fakeKey = "11111111-1111-4111-8111-111111111111"
    private val replacementKey = "22222222-2222-4222-8222-222222222222"

    private fun nodeJson(): JSONObject = JSONObject()
        .put("id", 7).put("name", "  Alpha  ").put("address", "node.example.test")
        .put("port", 62050).put("api_port", 63123).put("usage_coefficient", 1.25)
        .put("connection_type", "grpc").put("server_ca", "fake-certificate")
        .put("keep_alive", 60).put("core_config_id", 2).put("api_key", fakeKey)
        .put("data_limit", 9007199254740993L).put("data_limit_reset_strategy", "month")
        .put("reset_time", -1).put("default_timeout", 10).put("internal_timeout", 15)
        .put("proxy_url", "http://fake-user:fake-pass@example.test:1080").put("status", "connected")

    private fun snapshot(json: JSONObject = nodeJson()) = parseNodeSnapshot(json.toString(), 7)

    @Test fun `unchanged fields and credentials are omitted while inherited api port is preserved`() {
        val before = snapshot()
        val latest = snapshot(nodeJson().put("address", "other.example.test").put("api_port", 64000))
        val body = NodeEditMerge.update(before, latest, NodeDraft.from(before).copy(name = "Edited"))
        assertEquals(2, body.length())
        assertEquals("Edited", body.getString("name"))
        assertEquals(64000, body.getInt("api_port"))
        assertFalse(body.has("address"))
        assertFalse(body.has("api_key"))
        assertFalse(body.has("server_ca"))
        assertFalse(body.has("proxy_url"))
        assertFalse(body.has("data_limit"))
        assertEquals(9007199254740993L, before.dataLimit)
    }

    @Test fun `unchanged names are not silently trimmed`() {
        val before = snapshot()
        val body = NodeEditMerge.update(before, before, NodeDraft.from(before).copy(port = "12345"))
        assertFalse(body.has("name"))
        assertEquals(12345, body.getInt("port"))
    }

    @Test fun `colliding edits are rejected without disclosing values`() {
        val before = snapshot()
        val latest = snapshot(nodeJson().put("name", "Remote"))
        val error = assertThrows(NodeEditConflictException::class.java) {
            NodeEditMerge.update(before, latest, NodeDraft.from(before).copy(name = "Local"))
        }
        assertEquals(listOf("name"), error.fields)
        assertFalse(error.message.orEmpty().contains("Remote"))
        assertFalse(error.message.orEmpty().contains("Local"))
    }

    @Test fun `proxy removal is explicit and conflicts with a concurrent replacement`() {
        val before = snapshot()
        val draft = NodeDraft.from(before).copy(proxyEdit = NodeProxyEdit.CLEAR)
        val body = NodeEditMerge.update(before, before, draft)
        assertTrue(body.has("proxy_url"))
        assertTrue(body.isNull("proxy_url"))
        val latest = snapshot(nodeJson().put("proxy_url", "socks5://new.example.test:1080"))
        val error = assertThrows(NodeEditConflictException::class.java) { NodeEditMerge.update(before, latest, draft) }
        assertEquals(listOf("proxy_url"), error.fields)
        assertFalse(error.message.orEmpty().contains("fake-pass"))
    }

    @Test fun `replacement credential conflicts are detected using hashes only`() {
        val before = snapshot()
        val latest = snapshot(nodeJson().put("api_key", "33333333-3333-4333-8333-333333333333"))
        val draft = NodeDraft.from(before).copy(apiKey = replacementKey)
        val error = assertThrows(NodeEditConflictException::class.java) { NodeEditMerge.update(before, latest, draft) }
        assertEquals(listOf("api_key"), error.fields)
        assertFalse(before.toString().contains(fakeKey))
        assertFalse(draft.toString().contains(replacementKey))
        assertFalse(error.toString().contains(replacementKey))
    }

    @Test fun `reset update includes a fresh consistent strategy and time`() {
        val before = snapshot()
        val latest = snapshot(nodeJson().put("reset_time", 500))
        val draft = NodeDraft.from(before).copy(resetStrategy = "day")
        val body = NodeEditMerge.update(before, latest, draft)
        assertEquals("day", body.getString("data_limit_reset_strategy"))
        assertEquals(500, body.getInt("reset_time"))
    }

    @Test fun `invalid merged reset pair does not get sent`() {
        val before = snapshot()
        val latest = snapshot(nodeJson().put("reset_time", 100_000))
        assertThrows(IllegalArgumentException::class.java) {
            NodeEditMerge.update(before, latest, NodeDraft.from(before).copy(resetStrategy = "day"))
        }
    }

    @Test fun `node status is only changed deliberately`() {
        val before = snapshot()
        val connectedLater = snapshot(nodeJson().put("status", "connecting"))
        assertFalse(NodeEditMerge.update(before, connectedLater, NodeDraft.from(before)).has("status"))
        val body = NodeEditMerge.update(before, connectedLater, NodeDraft.from(before).copy(statusEdit = NodeStatusEdit.DISABLE))
        assertEquals("disabled", body.getString("status"))
    }

    @Test fun `unassigned core stays unassigned unless a valid id is selected`() {
        val before = snapshot(nodeJson().put("core_config_id", JSONObject.NULL))
        val draft = NodeDraft.from(before)
        assertFalse(NodeEditMerge.update(before, before, draft).has("core_config_id"))
        assertEquals(4, NodeEditMerge.update(before, before, draft.copy(coreId = "4")).getInt("core_config_id"))
    }

    @Test fun `missing required fields and mismatched ids fail closed`() {
        val missing = nodeJson().apply { remove("api_port") }
        assertThrows(IllegalArgumentException::class.java) { snapshot(missing) }
        assertThrows(IllegalArgumentException::class.java) { parseNodeSnapshot(nodeJson().toString(), 8) }
    }

    @Test fun `create body matches required node fields and validates real public certificate`() {
        val cert = javaClass.getResourceAsStream("/node-test-cert.pem")!!.bufferedReader().use { it.readText() }
        val draft = NodeDraft(name = "New", address = "node.example.test", certificate = cert, apiKey = fakeKey,
            proxy = "socks5://proxy.example.test:1080")
        assertEquals(null, draft.validationError(isNew = true))
        val body = NodeEditMerge.create(draft)
        assertEquals("New", body.getString("name"))
        assertEquals(62051, body.getInt("api_port"))
        assertEquals("grpc", body.getString("connection_type"))
        assertEquals(fakeKey, body.getString("api_key"))
        assertEquals("socks5://proxy.example.test:1080", body.getString("proxy_url"))
        assertNotNull(body.getString("server_ca"))
        assertEquals(NodeDraftError.CERTIFICATE,
            draft.copy(certificate = cert + "\n-----BEGIN EC PRIVATE KEY-----\ntest\n-----END EC PRIVATE KEY-----").validationError(true))
    }

    @Test fun `validation rejects unsafe or malformed numbers and proxy`() {
        val base = NodeDraft.from(snapshot())
        assertEquals(NodeDraftError.PORT, base.copy(port = "0").validationError(false, snapshot()))
        assertEquals(NodeDraftError.COEFFICIENT, base.copy(coefficient = "NaN").validationError(false, snapshot()))
        assertEquals(NodeDraftError.INTERNAL_TIMEOUT, base.copy(internalTimeout = "61").validationError(false, snapshot()))
        assertEquals(NodeDraftError.PROXY, base.copy(proxyEdit = NodeProxyEdit.REPLACE, proxy = "file://bad").validationError(false, snapshot()))
        assertEquals(NodeDraftError.API_KEY, base.copy(apiKey = "not-a-uuid").validationError(false, snapshot()))
    }
}
