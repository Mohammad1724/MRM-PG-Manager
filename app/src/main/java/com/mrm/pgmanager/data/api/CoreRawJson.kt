package com.mrm.pgmanager.data.api

import org.json.JSONObject
import java.util.ArrayDeque

/**
 * Slice an object-valued top-level field without parsing and reserializing its numbers.
 * The caller also parses the response as JSON; this scanner only locates the original
 * value and rejects ambiguous or malformed outer structure. No values enter an error.
 */
internal fun rawJsonObjectField(document: String, target: String): String? {
    var cursor = 0
    val seen = mutableSetOf<String>()
    var result: String? = null

    fun skipWhitespace() {
        while (cursor < document.length && document[cursor] in " \t\r\n") cursor++
    }

    fun skipString(): Boolean {
        if (cursor >= document.length || document[cursor] != '"') return false
        cursor++
        while (cursor < document.length) {
            when (document[cursor++]) {
                '"' -> return true
                '\\' -> {
                    if (cursor >= document.length) return false
                    cursor++
                }
                else -> if (document[cursor - 1] < ' ') return false
            }
        }
        return false
    }

    fun skipValue(): Boolean {
        if (cursor >= document.length) return false
        if (document[cursor] == '"') return skipString()
        if (document[cursor] == '{' || document[cursor] == '[') {
            val expected = ArrayDeque<Char>()
            while (cursor < document.length) {
                when (val char = document[cursor]) {
                    '"' -> if (!skipString()) return false
                    '{', '[' -> {
                        expected.addLast(if (char == '{') '}' else ']')
                        cursor++
                    }
                    '}', ']' -> {
                        if (expected.isEmpty() || expected.removeLast() != char) return false
                        cursor++
                        if (expected.isEmpty()) return true
                    }
                    else -> cursor++
                }
            }
            return false
        }
        val start = cursor
        while (cursor < document.length && document[cursor] !in ",}] \t\r\n") cursor++
        return cursor > start
    }

    skipWhitespace()
    if (cursor >= document.length || document[cursor++] != '{') return null
    skipWhitespace()
    if (cursor < document.length && document[cursor] == '}') return null

    while (cursor < document.length) {
        val keyStart = cursor
        if (!skipString()) return null
        val encodedKey = document.substring(keyStart, cursor)
        val key = try {
            JSONObject("{\"key\":$encodedKey}").getString("key")
        } catch (_: Exception) {
            return null
        }
        if (!seen.add(key)) return null
        skipWhitespace()
        if (cursor >= document.length || document[cursor++] != ':') return null
        skipWhitespace()
        val valueStart = cursor
        if (!skipValue()) return null
        if (key == target) {
            if (document[valueStart] != '{') return null
            result = document.substring(valueStart, cursor)
        }
        skipWhitespace()
        if (cursor >= document.length) return null
        when (document[cursor++]) {
            '}' -> {
                skipWhitespace()
                return if (cursor == document.length) result else null
            }
            ',' -> {
                skipWhitespace()
                if (cursor >= document.length || document[cursor] == '}') return null
            }
            else -> return null
        }
    }
    return null
}
