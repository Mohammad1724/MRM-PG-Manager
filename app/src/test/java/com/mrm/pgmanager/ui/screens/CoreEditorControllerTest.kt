package com.mrm.pgmanager.ui.screens

import com.mrm.pgmanager.data.model.PanelCore
import com.mrm.pgmanager.data.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreEditorControllerTest {
    private val account = Session("https://panel.example", "test-token", "admin")
    private val core = PanelCore(id = 8, name = "Before", type = "xray", config = """{"sensitive":"example"}""")

    @Test fun `draft and in-flight save survive and cannot be modified or dismissed`() {
        val state = CoreEditorState()
        state.open(account, core)
        val id = requireNotNull(state.active).requestId
        val changed = requireNotNull(state.active).draft.copy(name = "After", exclude = setOf("tag"))
        state.update(id, changed)
        assertEquals(changed, state.active?.draft)
        assertNotNull(state.beginSave(id))
        assertNull(state.beginSave(id))
        state.update(id, changed.copy(name = "Too late"))
        state.dismiss(id)
        assertEquals(changed, state.active?.draft)
        state.failSave(id, "Failed")
        assertFalse(requireNotNull(state.active).saving)
        assertEquals(changed, state.active?.draft)
        assertNotNull(state.beginSave(id))
        state.finishSave(id)
        assertNull(state.active)
        assertEquals(id, state.notice?.requestId)
        state.consumeNotice(id)
        assertNull(state.notice)
    }

    @Test fun `old results cannot affect another session or editor`() {
        val state = CoreEditorState()
        state.open(account, core)
        val oldId = requireNotNull(state.active).requestId
        assertNotNull(state.beginSave(oldId))
        val other = Session("https://other.example", "other-test-token", "other")
        state.bind(other)
        state.open(other, core.copy(id = 9))
        val newId = requireNotNull(state.active).requestId
        state.finishSave(oldId)
        state.failSave(oldId, "Old error")
        assertEquals(newId, state.active?.requestId)
        assertNull(state.active?.error)
        assertNull(state.notice)
        assertNotNull(state.beginSave(newId))
        state.finishSave(newId)
        state.bind(account)
        assertNull(state.notice)
    }

    @Test fun `pending refresh survives opening the next editor and debug strings are redacted`() {
        val state = CoreEditorState()
        state.open(account, core)
        val first = requireNotNull(state.active).requestId
        assertFalse(state.active.toString().contains("test-token"))
        assertFalse(state.active.toString().contains("sensitive"))
        assertFalse(core.toString().contains("sensitive"))
        assertNotNull(state.beginSave(first))
        state.finishSave(first)
        assertFalse(state.notice.toString().contains("test-token"))
        state.open(account, core.copy(id = 9))
        assertEquals(first, state.notice?.requestId)
        state.consumeNotice(first)
        assertEquals(9, state.active?.initial?.id)
        assertTrue(state.notice == null)
    }
}
