package com.mrm.pgmanager.data.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsEditMergeTest {
    @Test fun `rebase preserves concurrent unknown and nested fields`() {
        val base = JSONObject("""{"title":"old","nested":{"x":1,"y":2},"unknown":"old"}""")
        val draft = JSONObject(base.toString()).apply { getJSONObject("nested").put("x", 3) }
        val latest = JSONObject(base.toString()).apply {
            getJSONObject("nested").put("y", 9)
            put("unknown", "fresh")
            put("new", true)
        }
        val result = SettingsEditMerge.merge(base, draft, latest, latest)
        assertTrue(result.changed)
        assertEquals(3, result.complete.getJSONObject("nested").getInt("x"))
        assertEquals(9, result.complete.getJSONObject("nested").getInt("y"))
        assertEquals("fresh", result.complete.getString("unknown"))
        assertTrue(result.complete.getBoolean("new"))
        assertEquals(setOf("nested"), result.changes.keys().asSequence().toSet())
        assertEquals(setOf("x"), result.changes.getJSONObject("nested").keys().asSequence().toSet())
    }

    @Test fun `edited field changed concurrently is rejected without including values in errors`() {
        val base = JSONObject("""{"nested":{"secret":"old-secret"}}""")
        val draft = JSONObject("""{"nested":{"secret":"mine-secret"}}""")
        val latest = JSONObject("""{"nested":{"secret":"server-secret"}}""")
        val error = assertThrows(SettingsEditConflictException::class.java) {
            SettingsEditMerge.merge(base, draft, latest, latest)
        }
        assertEquals(listOf("nested.secret"), error.fields)
        assertFalse(error.message.orEmpty().contains("server-secret"))
        assertFalse(error.message.orEmpty().contains("mine-secret"))
    }

    @Test fun `matching server edit requires no write`() {
        val base = JSONObject("""{"a":1}""")
        val changed = JSONObject("""{"a":2}""")
        val result = SettingsEditMerge.merge(base, changed, changed, changed)
        assertFalse(result.changed)
        assertEquals(0, result.changes.length())
    }

    @Test fun `explicit null clears while missing removes`() {
        val base = JSONObject("""{"a":1,"b":2}""")
        val draft = JSONObject("""{"a":null}""")
        val result = SettingsEditMerge.merge(base, draft, base, base)
        assertTrue(result.complete.has("a"))
        assertTrue(result.complete.isNull("a"))
        assertFalse(result.complete.has("b"))
    }

    @Test fun `confirmation checks changed nested values nulls and removals`() {
        val base = JSONObject("""{"nested":{"x":1,"untouched":2},"a":1,"b":2}""")
        val draft = JSONObject("""{"nested":{"x":3,"untouched":2},"a":null}""")
        val result = SettingsEditMerge.merge(base, draft, base, base)
        assertTrue(SettingsEditMerge.changesApplied(result, JSONObject("""{"nested":{"x":3,"untouched":9},"a":null}""")))
        assertFalse(SettingsEditMerge.changesApplied(result, JSONObject("""{"nested":{"x":1},"a":null}""")))
        assertFalse(SettingsEditMerge.changesApplied(result, JSONObject("""{"nested":{"x":3},"a":null,"b":2}""")))
        assertFalse(SettingsEditMerge.changesApplied(result, JSONObject("""{"nested":{"x":3}}""")))
    }

    @Test fun `arrays conflict as a whole`() {
        val base = JSONObject("""{"variables":[{"key":"A","value":"one"}]}""")
        val draft = JSONObject("""{"variables":[{"key":"A","value":"mine"}]}""")
        val latest = JSONObject("""{"variables":[{"key":"A","value":"theirs"}]}""")
        assertThrows(SettingsEditConflictException::class.java) { SettingsEditMerge.merge(base, draft, latest, latest) }
    }

    @Test fun `invalid fresh parent is rejected instead of replacing it`() {
        val base = JSONObject("""{"nested":{"a":1}}""")
        val draft = JSONObject("""{"nested":{"a":2}}""")
        val latest = JSONObject("""{"nested":"unsupported"}""")
        assertThrows(SettingsEditConflictException::class.java) { SettingsEditMerge.merge(base, draft, latest, latest) }
    }
}
