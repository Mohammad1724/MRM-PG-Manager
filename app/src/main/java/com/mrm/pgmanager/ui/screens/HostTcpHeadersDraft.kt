package com.mrm.pgmanager.ui.screens

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal data class HostTcpHeader(val name: String, val values: List<String>) {
    override fun toString(): String = "HostTcpHeader(name=$name, values=<redacted>)"
}

/** Each HTTP request/response header map is edited and conflict-checked as one unit. */
internal data class HostTcpHeadersDraft(
    val headers: List<HostTcpHeader>,
    val supported: Boolean,
    val touched: Boolean = false
) {
    fun withHeaders(value: List<HostTcpHeader>): HostTcpHeadersDraft = copy(headers = value, touched = true)
    fun sameValues(other: HostTcpHeadersDraft): Boolean =
        headers.size == other.headers.size && headers.associate { it.name to it.values } == other.headers.associate { it.name to it.values }

    fun problem(original: HostTcpHeadersDraft): String? {
        if (!touched) return null
        if (!supported || !original.supported) return "tcp_headers_unsupported"
        if (sameValues(original)) return null
        if (headers.any { !validHeaderName(it.name) }) return "http_header_name"
        val names = headers.map { it.name.lowercase(Locale.ROOT) }
        if (names.distinct().size != names.size) return "http_header_duplicate"
        if (headers.any { row -> row.values.any { !validHeaderValue(it) } }) return "http_header_value"
        return null
    }

    fun jsonValue(): JSONObject? = if (headers.isEmpty()) null else JSONObject().apply {
        headers.forEach { put(it.name, JSONArray(it.values)) }
    }
}

internal fun readTcpHeaders(root: JSONObject, parsed: Boolean, direction: String): HostTcpHeadersDraft {
    if (!parsed) return HostTcpHeadersDraft(emptyList(), false)
    var current: Any = root
    for (part in listOf("transport_settings", "tcp_settings", direction)) {
        if (current !is JSONObject) return HostTcpHeadersDraft(emptyList(), false)
        val next = current.opt(part)
        if (next == null || next === JSONObject.NULL) return HostTcpHeadersDraft(emptyList(), true)
        current = next
    }
    if (current !is JSONObject) return HostTcpHeadersDraft(emptyList(), false)
    val raw = current.opt("headers")
    if (raw == null || raw === JSONObject.NULL) return HostTcpHeadersDraft(emptyList(), true)
    if (raw !is JSONObject) return HostTcpHeadersDraft(emptyList(), false)
    val headers = mutableListOf<HostTcpHeader>()
    for (key in raw.keys().asSequence()) {
        val values = raw.opt(key) as? JSONArray ?: return HostTcpHeadersDraft(emptyList(), false)
        val strings = (0 until values.length()).map { values.opt(it) as? String ?: return HostTcpHeadersDraft(emptyList(), false) }
        headers += HostTcpHeader(key, strings)
    }
    return HostTcpHeadersDraft(headers, true)
}
