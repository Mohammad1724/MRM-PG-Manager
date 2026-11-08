package com.mrm.pgmanager.ui.screens

import com.mrm.pgmanager.data.model.PanelCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreEditorStateTest {
    private val core = PanelCore(
        id = 5, name = "Main", type = "future-backend",
        excludeInboundTags = listOf("old", "known"), fallbacksInboundTags = listOf("fallback")
    )
    private val draft = CoreEditorDraft.from(core)

    @Test fun `dirty state compares tag sets and keeps unknown types`() {
        assertEquals("future-backend", draft.type)
        assertFalse(draft.isDirty(core))
        assertFalse(draft.copy(exclude = setOf("known", "old")).isDirty(core))
        assertTrue(draft.copy(name = "Changed").isDirty(core))
        assertTrue(draft.copy(type = "").isDirty(core))
        assertTrue(draft.copy(exclude = setOf("known")).isDirty(core))
        assertTrue(draft.copy(fallbacks = emptySet()).isDirty(core))
        assertTrue(draft.copy(restartNodes = true).isDirty(core))
    }

    @Test fun `untouched whitespace in an existing name does not make the editor dirty`() {
        val spaced = core.copy(name = "  Main  ")
        assertFalse(CoreEditorDraft.from(spaced).isDirty(spaced))
    }

    @Test fun `name validation counts unicode code points like the panel`() {
        assertEquals(CoreNameError.EMPTY, draft.copy(name = "  ").nameError())
        assertNull(draft.copy(name = "a".repeat(256)).nameError())
        assertEquals(CoreNameError.TOO_LONG, draft.copy(name = "a".repeat(257)).nameError())
        assertNull(draft.copy(name = "😀".repeat(256)).nameError())
        assertEquals(CoreNameError.TOO_LONG, draft.copy(name = "😀".repeat(257)).nameError())
    }

    @Test fun `tags that the panel would drop or split require explicit removal`() {
        assertTrue(draft.copy(exclude = setOf("")).hasUnstorableTags())
        assertTrue(draft.copy(fallbacks = setOf("a,b")).hasUnstorableTags())
        assertFalse(draft.copy(exclude = setOf("null", "  "), fallbacks = emptySet()).hasUnstorableTags())
    }

    @Test fun `wireguard rejects tag selections without silently removing them`() {
        val wg = draft.copy(type = "wg")
        assertTrue(wg.wireGuardHasTags())
        assertFalse(wg.copy(exclude = emptySet(), fallbacks = emptySet()).wireGuardHasTags())
        assertFalse(draft.wireGuardHasTags())
    }

    @Test fun `each tag collection obeys the panel's serialized length limit`() {
        assertFalse(draft.copy(exclude = setOf("a".repeat(2048))).tagsTooLong())
        assertTrue(draft.copy(exclude = setOf("a".repeat(2049))).tagsTooLong())
        assertTrue(draft.copy(fallbacks = setOf("a".repeat(2048), "b")).tagsTooLong())
        assertFalse(draft.copy(exclude = setOf("😀".repeat(2048))).tagsTooLong())
    }
}
