package com.mrm.pgmanager.ui.screens

import com.mrm.pgmanager.data.model.PanelHost
import com.mrm.pgmanager.data.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostEditorStateTest {
    private val account = Session("https://panel.example", "test-token", "admin")
    private val host = PanelHost(id = 8, remark = "before", addresses = listOf("example.com"))

    @Test fun `draft and in-flight save remain in retained state and duplicate saves are blocked`() {
        val state = HostEditorState()
        state.open(account, host)
        val requestId = requireNotNull(state.active).requestId
        val changed = requireNotNull(state.active).draft.copy(remark = "after")
        state.update(requestId, changed)
        assertEquals(changed, state.active?.draft)
        val operation = state.beginSave(requestId)
        assertNotNull(operation)
        assertTrue(requireNotNull(state.active).saving)
        assertNull(state.beginSave(requestId))
        state.update(requestId, changed.copy(remark = "late edit"))
        state.dismiss(requestId)
        assertEquals("after", state.active?.draft?.remark)
        state.failSave(requestId, "failed")
        assertFalse(requireNotNull(state.active).saving)
        assertEquals("failed", state.active?.error)
        assertNotNull(state.beginSave(requestId))
        state.finishSave(requestId)
        assertNull(state.active)
        assertEquals(false, state.notice?.created)
        state.consumeNotice(requestId)
        assertNull(state.notice)
    }

    @Test fun `http header edits survive a failed save and cannot change while saving`() {
        val state = HostEditorState()
        state.open(account, host)
        val requestId = requireNotNull(state.active).requestId
        val edited = requireNotNull(state.active).draft.withHttpHeaders(listOf(HostHttpHeader("X-Client", "value")))
        state.update(requestId, edited)
        assertNotNull(state.beginSave(requestId))
        state.update(requestId, edited.withHttpHeaders(emptyList()))
        state.dismiss(requestId)
        assertEquals(listOf(HostHttpHeader("X-Client", "value")), state.active?.draft?.httpHeaders)
        state.failSave(requestId, "failed")
        assertEquals(edited, state.active?.draft)
    }

    @Test fun `opening another editor does not discard a pending list refresh`() {
        val state = HostEditorState()
        state.open(account, host)
        val first = requireNotNull(state.active).requestId
        assertNotNull(state.beginSave(first))
        state.finishSave(first)
        state.open(account, host.copy(id = 9))
        assertEquals(first, state.notice?.requestId)
        state.consumeNotice(first)
        assertEquals(9, state.active?.initial?.id)
        assertNull(state.notice)
    }

    @Test fun `late results cannot change another account or a newer editor`() {
        val state = HostEditorState()
        state.open(account, host)
        val oldId = requireNotNull(state.active).requestId
        assertNotNull(state.beginSave(oldId))
        val other = Session("https://other.example", "other-test-token", "other")
        state.bind(other)
        state.open(other, PanelHost(id = 0, remark = ""))
        val newId = requireNotNull(state.active).requestId
        state.finishSave(oldId)
        state.failSave(oldId, "old error")
        assertEquals(newId, state.active?.requestId)
        assertNull(state.active?.error)
        assertNull(state.notice)
        assertNotNull(state.beginSave(newId))
        state.finishSave(newId)
        assertEquals(true, state.notice?.created)
        state.bind(account)
        assertNull(state.notice)
    }

    @Test fun `noise and TCP header drafts survive failed saves without crossing accounts`() {
        val state = HostEditorState()
        state.open(account, host)
        val id = requireNotNull(state.active).requestId
        val changed = requireNotNull(state.active).draft
            .withNoise(listOf(HostNoiseItem(packet = "1-5", delay = "3")))
            .withTcpHeaders("request", listOf(HostTcpHeader("X-Region", listOf("east,west", "other"))))
        state.update(id, changed)
        assertNotNull(state.beginSave(id))
        state.update(id, changed.withNoise(emptyList()))
        state.failSave(id, "failed")
        assertEquals(changed, state.active?.draft)
        val other = Session("https://other.example", "other-test-token", "other")
        state.bind(other)
        state.open(other, host.copy(id = 9))
        state.failSave(id, "stale")
        assertTrue(requireNotNull(state.active).draft.noise.isEmpty())
        assertTrue(requireNotNull(state.active).draft.tcpRequestHeaders.headers.isEmpty())
        assertNull(state.active?.error)
    }

}
