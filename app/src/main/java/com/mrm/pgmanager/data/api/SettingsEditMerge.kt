package com.mrm.pgmanager.data.api

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/** Field names only: settings may contain credentials and must not be logged. */
internal class SettingsEditConflictException(val fields: List<String>) :
    IllegalStateException("Settings changed on the server: ${fields.joinToString()}")

internal class SettingsUnavailableException : IllegalStateException("The settings section is unavailable")
internal class SettingsWriteUnconfirmedException : IllegalStateException("Settings update could not be confirmed")
internal class SettingsMaskedSecretException : IllegalStateException("A masked setting cannot be safely preserved")

/**
 * Rebase intentional edits onto a fresh section. This detects changes made before
 * the read; the settings API has no ETag or atomic compare-and-swap, so a write
 * between that read and the PUT can still be lost.
 */
internal object SettingsEditMerge {
    private object Missing
    private data class Change(val path: List<String>, val before: Any, val after: Any)
    data class Result(
        val complete: JSONObject,
        val changes: JSONObject,
        val changed: Boolean,
        val removedPaths: List<List<String>>
    )

    fun merge(base: JSONObject, draft: JSONObject, latest: JSONObject, latestRaw: JSONObject): Result {
        val edits = mutableListOf<Change>()
        collect(base, draft, emptyList(), edits)
        val conflicts = edits.filter { edit ->
            val current = valueAt(latest, edit.path)
            !same(edit.before, current) && !same(edit.after, current)
        }.map { it.path.joinToString(".") }
        if (conflicts.isNotEmpty()) throw SettingsEditConflictException(conflicts)

        val effective = edits.filter { edit -> !same(edit.after, valueAt(latest, edit.path)) }
        val complete = JSONObject(latestRaw.toString())
        val changes = JSONObject()
        effective.forEach { edit ->
            writeAt(complete, edit.path, edit.after)
            writeAt(changes, edit.path, edit.after)
        }
        return Result(complete, changes, effective.isNotEmpty(),
            effective.filter { it.after === Missing }.map { it.path })
    }

    /** Verify edited fields against the server response without comparing unchanged siblings. */
    fun changesApplied(result: Result, response: JSONObject): Boolean =
        containsChanges(result.changes, response) && result.removedPaths.all { valueAt(response, it) === Missing }

    private fun containsChanges(changes: JSONObject, response: JSONObject): Boolean =
        changes.keys().asSequence().all { key ->
            val expected = read(changes, key)
            val actual = read(response, key)
            if (expected is JSONObject) actual is JSONObject && containsChanges(expected, actual)
            else same(expected, actual)
        }

    private fun collect(base: Any, draft: Any, path: List<String>, edits: MutableList<Change>) {
        if (same(base, draft)) return
        if (base is JSONObject && draft is JSONObject) {
            val names = (base.keys().asSequence().toSet() + draft.keys().asSequence().toSet()).sorted()
            names.forEach { name ->
                collect(read(base, name), read(draft, name), path + name, edits)
            }
        } else {
            require(path.isNotEmpty()) { "Invalid settings edit" }
            edits.add(Change(path, base, draft))
        }
    }

    private fun read(obj: JSONObject, name: String): Any =
        if (obj.has(name)) obj.opt(name) ?: JSONObject.NULL else Missing

    private fun valueAt(root: JSONObject, path: List<String>): Any {
        var value: Any = root
        for (part in path) {
            if (value !is JSONObject) return Missing
            value = read(value, part)
        }
        return value
    }

    private fun writeAt(root: JSONObject, path: List<String>, value: Any) {
        var current = root
        for (part in path.dropLast(1)) {
            val child = read(current, part)
            current = when {
                child is JSONObject -> child
                child === Missing || child === JSONObject.NULL -> JSONObject().also { current.put(part, it) }
                else -> throw SettingsEditConflictException(listOf(path.joinToString(".")))
            }
        }
        if (value === Missing) current.remove(path.last()) else current.put(path.last(), value)
    }

    private fun same(a: Any, b: Any): Boolean {
        if (a === Missing || b === Missing) return a === b
        if (a === JSONObject.NULL || b === JSONObject.NULL) return a === JSONObject.NULL && b === JSONObject.NULL
        if (a is JSONObject && b is JSONObject) {
            val keys = a.keys().asSequence().toSet()
            return keys == b.keys().asSequence().toSet() && keys.all { same(read(a, it), read(b, it)) }
        }
        if (a is JSONArray && b is JSONArray) {
            return a.length() == b.length() && (0 until a.length()).all {
                same(a.opt(it) ?: JSONObject.NULL, b.opt(it) ?: JSONObject.NULL)
            }
        }
        if (a is Number && b is Number) {
            return runCatching { BigDecimal(a.toString()).compareTo(BigDecimal(b.toString())) == 0 }.getOrDefault(false)
        }
        return a == b
    }
}
