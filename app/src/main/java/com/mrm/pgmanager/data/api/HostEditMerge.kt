package com.mrm.pgmanager.data.api

import org.json.JSONArray
import org.json.JSONObject

/** Minimal client-template information exposed by the panel's read_simple permission. */
internal data class HostXrayTemplate(val id: Int, val name: String)

/** A deliberate edit to one JSON field. A null value means an explicit JSON null. */
internal data class HostFieldChange(val path: List<String>, val value: Any?) {
    constructor(field: String, value: Any?) : this(listOf(field), value)
}

/** The server and the editor both changed at least one of the same fields. */
internal class HostEditConflictException(val fields: List<String>) :
    IllegalStateException("The host changed on the server: ${fields.joinToString()}")

/**
 * Builds the complete PUT body from a fresh server response and only the user's edits.
 * This is conflict detection, not an atomic compare-and-swap: the API has no version
 * precondition, so another write can still occur between the GET and the PUT.
 */
internal object HostEditMerge {
    private object Missing

    // The panel models these fields as sets and can return them in a different order.
    private val unorderedFields = setOf("address", "status", "host", "sni", "verify_peer_cert_by_name")

    fun merge(initialRaw: String, latestRaw: String, hostId: Int, changes: List<HostFieldChange>): JSONObject {
        val initial = JSONObject(initialRaw)
        val latest = JSONObject(latestRaw)
        require(initial.has("id") && initial.optInt("id", -1) == hostId) { "Invalid original host snapshot" }
        require(latest.has("id") && latest.optInt("id", -1) == hostId) { "Invalid latest host snapshot" }
        validate(changes)

        val conflicts = changes.filter { change ->
            val originalValue = get(initial, change.path)
            val latestValue = get(latest, change.path)
            val requestedValue = change.value ?: JSONObject.NULL
            !equivalent(originalValue, latestValue, change.path) &&
                !equivalent(requestedValue, latestValue, change.path)
        }.map { it.path.joinToString(".") }
        if (conflicts.isNotEmpty()) throw HostEditConflictException(conflicts)

        apply(latest, changes)
        normalizePutAliases(latest)
        return latest
    }

    /** The panel serializes these two fields as camelCase but accepts snake_case on PUT. */
    private fun normalizePutAliases(body: JSONObject) {
        val xray = body.optJSONObject("mux_settings")?.optJSONObject("xray") ?: return
        listOf(
            "xudpConcurrency" to "xudp_concurrency",
            "xudpProxyUDP443" to "xudp_proxy_udp_443"
        ).forEach { (responseName, requestName) ->
            if (xray.has(responseName)) {
                val value = xray.opt(responseName) ?: JSONObject.NULL
                if (xray.has(requestName) && !equivalent(value, xray.opt(requestName) ?: JSONObject.NULL, listOf(requestName))) {
                    throw HostEditConflictException(listOf("mux_settings.xray.$requestName"))
                }
                // Keep the response key as well for forks that may accept it.
                xray.put(requestName, value)
            }
        }
    }

    fun apply(body: JSONObject, changes: List<HostFieldChange>) {
        validate(changes)
        changes.forEach { change -> put(body, change.path, change.value ?: JSONObject.NULL) }
    }

    private fun validate(changes: List<HostFieldChange>) {
        require(changes.all { it.path.isNotEmpty() && it.path.none(String::isBlank) }) { "Invalid host field path" }
        require(changes.none { it.path.first() == "id" }) { "Host id cannot be changed" }
        require(changes.map { it.path }.distinct().size == changes.size) { "Duplicate host field path" }
        require(changes.none { a -> changes.any { b -> a !== b && b.path.size > a.path.size && b.path.take(a.path.size) == a.path } }) {
            "Overlapping host field paths"
        }
    }

    private fun get(root: JSONObject, path: List<String>): Any {
        var value: Any = root
        for (part in path) {
            if (value !is JSONObject || !value.has(part)) return Missing
            value = value.opt(part) ?: JSONObject.NULL
        }
        return value
    }

    private fun put(root: JSONObject, path: List<String>, value: Any) {
        var parent = root
        for (part in path.dropLast(1)) {
            val existing = if (parent.has(part)) parent.opt(part) else null
            parent = when {
                existing is JSONObject -> existing
                existing == null || existing === JSONObject.NULL -> JSONObject().also { parent.put(part, it) }
                else -> throw HostEditConflictException(listOf(path.joinToString(".")))
            }
        }
        parent.put(path.last(), value)
    }

    private fun equivalent(a: Any, b: Any, path: List<String>): Boolean {
        if (a === Missing || b === Missing) return a === b
        if (a === JSONObject.NULL || b === JSONObject.NULL) return a === JSONObject.NULL && b === JSONObject.NULL
        if (a is JSONObject && b is JSONObject) {
            val aKeys = a.keys().asSequence().toSet()
            val bKeys = b.keys().asSequence().toSet()
            return aKeys == bKeys && aKeys.all { key ->
                equivalent(a.opt(key) ?: JSONObject.NULL, b.opt(key) ?: JSONObject.NULL, path + key)
            }
        }
        if (a is JSONArray && b is JSONArray) {
            if (a.length() != b.length()) return false
            if (path.size == 1 && path.first() in unorderedFields) {
                val left = (0 until a.length()).map { a.opt(it) }
                val right = (0 until b.length()).map { b.opt(it) }
                if (left.all { it is String } && right.all { it is String }) return left.toSet() == right.toSet()
            }
            return (0 until a.length()).all { index ->
                equivalent(a.opt(index) ?: JSONObject.NULL, b.opt(index) ?: JSONObject.NULL, path + index.toString())
            }
        }
        if (a is Number && b is Number) return a.toDouble() == b.toDouble()
        return a == b
    }
}
