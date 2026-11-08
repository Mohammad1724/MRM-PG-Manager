package com.mrm.pgmanager.ui.screens

import com.mrm.pgmanager.data.api.NodeSnapshot
import com.mrm.pgmanager.data.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeEditorStateTest {
    private val one = Session("https://one.example.test", "fake-token-one", "admin")
    private val two = Session("https://two.example.test", "fake-token-two", "admin")
    private val node = NodeSnapshot(
        id = 7, name = "Node", address = "node.example.test", port = 62050, apiPort = 62051,
        coefficient = 1.0, connectionType = "grpc", keepAlive = 60, coreId = 1,
        dataLimit = 0, resetStrategy = "no_reset", resetTime = -1, defaultTimeout = 10,
        internalTimeout = 15, status = "connected", certificateHash = "hash-cert",
        apiKeyHash = "hash-key", proxyHash = null
    )

    @Test fun `old fetch cannot populate a new editor or session`() {
        val state = NodeEditorState()
        val first = state.open(one, 7)!!
        val second = state.open(two, 8)!!
        state.bind(two)
        state.finishLoad(first.requestId, node)
        assertEquals(second.requestId, state.active!!.requestId)
        assertNull(state.active!!.draft)
        assertTrue(state.active!!.loading)
    }

    @Test fun `save is retained while in flight and uncertain outcome prevents replay`() {
        val state = NodeEditorState()
        val editor = state.open(one, 7)!!
        state.finishLoad(editor.requestId, node)
        val draft = state.active!!.draft!!.copy(name = "Changed")
        state.update(editor.requestId, draft)
        assertNotNull(state.beginSave(editor.requestId))
        state.dismiss(editor.requestId)
        assertTrue(state.active!!.saving)
        assertNull(state.open(one, null))
        state.failSave(editor.requestId, "Unconfirmed", blocked = true)
        assertTrue(state.active!!.blocked)
        assertNull(state.beginSave(editor.requestId))
        state.update(editor.requestId, draft.copy(name = "Other"))
        assertEquals("Changed", state.active!!.draft!!.name)
        state.dismiss(editor.requestId)
        assertNull(state.active)
    }

    @Test fun `success and action results are scoped to their owning session`() {
        val state = NodeEditorState()
        val editor = state.open(one, 7)!!
        state.finishLoad(editor.requestId, node)
        state.update(editor.requestId, state.active!!.draft!!.copy(name = "Changed"))
        state.beginSave(editor.requestId)
        state.finishSave(editor.requestId)
        assertEquals(one, state.snapshot.value.notice!!.session)
        state.bind(two)
        assertNull(state.snapshot.value.notice)
        val action = state.beginAction(one, 7, NodeAction.SYNC)!!
        state.bind(two)
        state.finishAction(action)
        assertNull(state.snapshot.value.actionNotice)
    }

    @Test fun `node editor and action diagnostics do not print session tokens`() {
        val state = NodeEditorState()
        val editor = state.open(one, null)!!
        assertFalse(editor.toString().contains(one.token))
        state.dismiss(editor.requestId)
        val action = state.beginAction(one, 7, NodeAction.DELETE)!!
        assertFalse(action.toString().contains(one.token))
    }
}
