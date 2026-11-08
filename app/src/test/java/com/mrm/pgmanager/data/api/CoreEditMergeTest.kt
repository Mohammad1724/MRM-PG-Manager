package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.PanelCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CoreEditMergeTest {
    private val original = PanelCore(
        id = 7, name = "Main", type = "xray", excludeInboundTags = listOf("old", "known"),
        fallbacksInboundTags = listOf("fallback"), config = "{\"inbounds\":[]}"
    )

    private fun edit() = CoreEditValues(
        name = original.name, type = original.type,
        excludeInboundTags = original.excludeInboundTags, fallbacksInboundTags = original.fallbacksInboundTags
    )

    @Test fun `unedited fields follow the latest server values`() {
        val latest = original.copy(
            name = "Remote", type = "wg", excludeInboundTags = listOf("new"),
            fallbacksInboundTags = listOf("remote-fallback"), config = "{\"new\":true}"
        )
        val merged = CoreEditMerge.merge(original, latest, edit())
        assertEquals("Remote", merged.name)
        assertEquals("wg", merged.type)
        assertEquals(listOf("new"), merged.excludeInboundTags)
        assertEquals(listOf("remote-fallback"), merged.fallbacksInboundTags)
    }

    @Test fun `distinct edits merge and tags are compared as unordered sets`() {
        val latest = original.copy(name = "Remote", excludeInboundTags = listOf("known", "old"))
        val merged = CoreEditMerge.merge(original, latest, edit().copy(excludeInboundTags = listOf("known", "extra")))
        assertEquals("Remote", merged.name)
        assertEquals(setOf("known", "extra"), merged.excludeInboundTags.toSet())
        assertEquals(setOf("fallback"), merged.fallbacksInboundTags.toSet())
    }

    @Test fun `concurrent edits to the same field are rejected without exposing values`() {
        val latest = original.copy(name = "Remote", fallbacksInboundTags = listOf("other"))
        val error = assertThrows(CoreEditConflictException::class.java) {
            CoreEditMerge.merge(original, latest, edit().copy(name = "Local", fallbacksInboundTags = listOf("new")))
        }
        assertEquals(listOf("name", "fallbacks_inbound_tags"), error.fields)
        assertEquals(false, error.message.orEmpty().contains("Remote"))
        assertEquals(false, error.message.orEmpty().contains("Local"))
    }

    @Test fun `untouched names with surrounding whitespace are never normalized as an edit`() {
        val spaced = original.copy(name = "  Main  ")
        val latest = spaced.copy(name = "Server")
        val unchanged = edit().copy(name = spaced.name)
        assertEquals("Server", CoreEditMerge.merge(spaced, latest, unchanged).name)
        assertEquals("  Main  ", CoreEditMerge.merge(spaced, spaced, unchanged).name)
        assertEquals("Edited", CoreEditMerge.merge(spaced, spaced, unchanged.copy(name = " Edited ")).name)
    }

    @Test fun `identical concurrent edit is accepted`() {
        val latest = original.copy(name = "Shared")
        assertEquals("Shared", CoreEditMerge.merge(original, latest, edit().copy(name = "Shared")).name)
    }

    @Test fun `mismatched identities are never merged`() {
        assertThrows(IllegalArgumentException::class.java) {
            CoreEditMerge.merge(original, original.copy(id = 8), edit())
        }
        assertThrows(IllegalArgumentException::class.java) {
            CoreEditMerge.merge(original.copy(id = 0), original.copy(id = 0), edit())
        }
    }
}
