package com.mrm.pgmanager.ui.screens

import com.mrm.pgmanager.data.api.HostEditMerge
import com.mrm.pgmanager.data.api.HostFieldChange
import com.mrm.pgmanager.data.model.HostValidation
import com.mrm.pgmanager.data.model.PanelHost
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

internal enum class HostFieldType { TEXT, SET, LIST, INTEGER, BOOLEAN, NULLABLE_BOOLEAN, CHOICE }

internal data class HostFieldSpec(
    val key: String,
    val type: HostFieldType,
    val default: String = "",
    val emptyIsNull: Boolean = true,
    val min: Int? = null,
    val max: Int? = null,
    val maxLength: Int? = null,
    val pattern: Regex? = null
) {
    val path: List<String> = key.split('.')

    fun read(root: JSONObject): String {
        val current = HostEditMerge.editorValue(root, path) ?: return default
        if (current === JSONObject.NULL) return default
        return when (type) {
            HostFieldType.SET, HostFieldType.LIST -> if (current is JSONArray) {
                (0 until current.length()).mapNotNull { current.opt(it) as? String }.joinToString(", ")
            } else default
            HostFieldType.BOOLEAN, HostFieldType.NULLABLE_BOOLEAN -> if (current is Boolean) current.toString() else default
            else -> current.toString()
        }
    }

    fun isSame(left: String, right: String): Boolean = when (type) {
        HostFieldType.SET -> hostStringValues(left).toSet() == hostStringValues(right).toSet()
        HostFieldType.LIST -> hostStringValues(left) == hostStringValues(right)
        HostFieldType.INTEGER -> left.trim() == right.trim()
        else -> left == right
    }

    fun isValid(value: String): Boolean {
        val trimmed = value.trim()
        if (type == HostFieldType.NULLABLE_BOOLEAN && value !in listOf("", "true", "false")) return false
        if (type == HostFieldType.INTEGER && trimmed.isEmpty() && !emptyIsNull) return false
        if (type == HostFieldType.INTEGER && trimmed.isNotEmpty()) {
            val number = trimmed.toIntOrNull() ?: return false
            if (min != null && number < min || max != null && number > max) return false
        }
        if (maxLength != null && value.length > maxLength) return false
        if (pattern != null && trimmed.isNotEmpty() && !pattern.matches(value)) return false
        return true
    }

    fun jsonValue(value: String): Any? = when (type) {
        HostFieldType.SET, HostFieldType.LIST -> hostStringValues(value).let {
            if (it.isEmpty() && emptyIsNull) null else JSONArray(it)
        }
        HostFieldType.INTEGER -> value.trim().takeIf { it.isNotEmpty() }?.toInt()
        HostFieldType.BOOLEAN -> value == "true"
        HostFieldType.NULLABLE_BOOLEAN -> when (value) {
            "true" -> true
            "false" -> false
            else -> null
        }
        HostFieldType.TEXT, HostFieldType.CHOICE -> if (value.isBlank() && emptyIsNull) null else value
    }
}

internal fun hostStringValues(value: String): List<String> = value.split(',', '\n')
    .map(String::trim).filter(String::isNotEmpty).distinct()

private val hostRangePattern = Regex("[0-9]{1,16}(-[0-9]{1,16})?")

/** Only fields with a real editor are listed. Everything else remains untouched. */
internal val hostTcpReasons = listOf(
    "OK", "Created", "Accepted", "Non-Authoritative Information", "No Content", "Reset Content", "Partial Content",
    "Multiple Choices", "Moved Permanently", "Found", "See Other", "Not Modified", "Use Proxy", "Temporary Redirect",
    "Permanent Redirect", "Bad Request", "Unauthorized", "Payment Required", "Forbidden", "Not Found", "Method Not Allowed",
    "Not Acceptable", "Proxy Authentication Required", "Request Timeout", "Conflict", "Gone", "Length Required",
    "Precondition Failed", "Payload Too Large", "URI Too Long", "Unsupported Media Type", "Range Not Satisfiable",
    "Expectation Failed", "I'm a teapot", "Misdirected Request", "Unprocessable Entity", "Locked", "Failed Dependency",
    "Too Early", "Upgrade Required", "Precondition Required", "Too Many Requests", "Request Header Fields Too Large",
    "Unavailable For Legal Reasons", "Internal Server Error", "Not Implemented", "Bad Gateway", "Service Unavailable",
    "Gateway Timeout", "HTTP Version Not Supported"
)

internal val HOST_EDITABLE_FIELDS = listOf(
    HostFieldSpec("priority", HostFieldType.INTEGER, emptyIsNull = false),
    HostFieldSpec("host", HostFieldType.SET, emptyIsNull = false),
    HostFieldSpec("path", HostFieldType.TEXT),
    HostFieldSpec("random_user_agent", HostFieldType.BOOLEAN, default = "false"),
    HostFieldSpec("sni", HostFieldType.SET, emptyIsNull = false),
    HostFieldSpec("alpn", HostFieldType.LIST, emptyIsNull = false),
    HostFieldSpec("fingerprint", HostFieldType.CHOICE, emptyIsNull = false),
    HostFieldSpec("cipher_suites", HostFieldType.TEXT),
    HostFieldSpec("allowinsecure", HostFieldType.NULLABLE_BOOLEAN),
    HostFieldSpec("use_sni_as_host", HostFieldType.BOOLEAN, default = "false"),
    HostFieldSpec("pinned_peer_cert_sha256", HostFieldType.TEXT, maxLength = 128),
    HostFieldSpec("verify_peer_cert_by_name", HostFieldType.SET, emptyIsNull = false),
    HostFieldSpec("vless_route", HostFieldType.TEXT, pattern = Regex("[0-9a-fA-F]{4}")),
    HostFieldSpec("ech.xray.config_list", HostFieldType.TEXT),
    HostFieldSpec("ech.xray.query_strategy", HostFieldType.CHOICE),
    HostFieldSpec("ech.mihomo.config", HostFieldType.TEXT),
    HostFieldSpec("ech.mihomo.query_server_name", HostFieldType.TEXT, maxLength = 255),
    HostFieldSpec("ech.sing_box.config", HostFieldType.TEXT),
    HostFieldSpec("ech.sing_box.query_server_name", HostFieldType.TEXT, maxLength = 255),
    HostFieldSpec("transport_settings.xhttp_settings.mode", HostFieldType.CHOICE),
    HostFieldSpec("transport_settings.xhttp_settings.no_grpc_header", HostFieldType.NULLABLE_BOOLEAN),
    HostFieldSpec("transport_settings.xhttp_settings.x_padding_obfs_mode", HostFieldType.NULLABLE_BOOLEAN),
    HostFieldSpec("transport_settings.xhttp_settings.x_padding_bytes", HostFieldType.TEXT, pattern = hostRangePattern),
    HostFieldSpec("transport_settings.xhttp_settings.sc_max_each_post_bytes", HostFieldType.TEXT, pattern = hostRangePattern),
    HostFieldSpec("transport_settings.xhttp_settings.sc_min_posts_interval_ms", HostFieldType.TEXT, pattern = hostRangePattern),
    HostFieldSpec("transport_settings.xhttp_settings.x_padding_key", HostFieldType.TEXT),
    HostFieldSpec("transport_settings.xhttp_settings.x_padding_header", HostFieldType.TEXT),
    HostFieldSpec("transport_settings.xhttp_settings.x_padding_placement", HostFieldType.CHOICE),
    HostFieldSpec("transport_settings.xhttp_settings.x_padding_method", HostFieldType.CHOICE),
    HostFieldSpec("transport_settings.xhttp_settings.uplink_http_method", HostFieldType.TEXT),
    HostFieldSpec("transport_settings.xhttp_settings.uplink_data_placement", HostFieldType.CHOICE),
    HostFieldSpec("transport_settings.xhttp_settings.uplink_data_key", HostFieldType.TEXT),
    HostFieldSpec("transport_settings.xhttp_settings.uplink_chunk_size", HostFieldType.TEXT, pattern = hostRangePattern),
    HostFieldSpec("transport_settings.xhttp_settings.session_placement", HostFieldType.CHOICE),
    HostFieldSpec("transport_settings.xhttp_settings.session_key", HostFieldType.TEXT),
    HostFieldSpec("transport_settings.xhttp_settings.session_id_table", HostFieldType.TEXT, pattern = Regex("[ -~]*")),
    HostFieldSpec("transport_settings.xhttp_settings.session_id_length", HostFieldType.TEXT, pattern = hostRangePattern),
    HostFieldSpec("transport_settings.xhttp_settings.seq_placement", HostFieldType.CHOICE),
    HostFieldSpec("transport_settings.xhttp_settings.seq_key", HostFieldType.TEXT),
    HostFieldSpec("transport_settings.xhttp_settings.download_settings", HostFieldType.INTEGER, min = 0),
    HostFieldSpec("transport_settings.xhttp_settings.xmux.max_concurrency", HostFieldType.TEXT, pattern = hostRangePattern),
    HostFieldSpec("transport_settings.xhttp_settings.xmux.max_connections", HostFieldType.TEXT, pattern = hostRangePattern),
    HostFieldSpec("transport_settings.xhttp_settings.xmux.c_max_reuse_times", HostFieldType.TEXT, pattern = hostRangePattern),
    HostFieldSpec("transport_settings.xhttp_settings.xmux.h_max_reusable_secs", HostFieldType.TEXT, pattern = hostRangePattern),
    HostFieldSpec("transport_settings.xhttp_settings.xmux.h_max_request_times", HostFieldType.TEXT, pattern = hostRangePattern),
    HostFieldSpec("transport_settings.xhttp_settings.xmux.h_keep_alive_period", HostFieldType.INTEGER),
    HostFieldSpec("transport_settings.tcp_settings.header", HostFieldType.CHOICE, emptyIsNull = false, pattern = Regex("(?:|none|http)")),
    HostFieldSpec("transport_settings.tcp_settings.request.version", HostFieldType.CHOICE, default = "1.1", emptyIsNull = false, pattern = Regex("(?:1\\.[01]|2\\.0|3\\.0)")),
    HostFieldSpec("transport_settings.tcp_settings.request.method", HostFieldType.CHOICE, default = "GET", emptyIsNull = false, pattern = Regex("(?:GET|POST|PUT|DELETE|HEAD|OPTIONS|PATCH|TRACE|CONNECT)")),
    HostFieldSpec("transport_settings.tcp_settings.response.version", HostFieldType.CHOICE, default = "1.1", emptyIsNull = false, pattern = Regex("(?:1\\.[01]|2\\.0|3\\.0)")),
    HostFieldSpec("transport_settings.tcp_settings.response.status", HostFieldType.TEXT, default = "200", emptyIsNull = false, pattern = Regex("[1-5][0-9]{2}")),
    HostFieldSpec("transport_settings.tcp_settings.response.reason", HostFieldType.CHOICE, default = "OK", emptyIsNull = false, pattern = Regex(hostTcpReasons.joinToString("|", "(?:", ")") { Regex.escape(it) }, RegexOption.IGNORE_CASE)),
    HostFieldSpec("transport_settings.kcp_settings.mtu", HostFieldType.INTEGER),
    HostFieldSpec("transport_settings.kcp_settings.tti", HostFieldType.INTEGER),
    HostFieldSpec("transport_settings.kcp_settings.uplink_capacity", HostFieldType.INTEGER),
    HostFieldSpec("transport_settings.kcp_settings.downlink_capacity", HostFieldType.INTEGER),
    HostFieldSpec("transport_settings.kcp_settings.congestion", HostFieldType.NULLABLE_BOOLEAN),
    HostFieldSpec("transport_settings.kcp_settings.read_buffer_size", HostFieldType.INTEGER),
    HostFieldSpec("transport_settings.kcp_settings.write_buffer_size", HostFieldType.INTEGER),
    HostFieldSpec("transport_settings.masque_settings.remote_dns", HostFieldType.LIST),
    HostFieldSpec("transport_settings.masque_settings.xmux.max_concurrency", HostFieldType.TEXT, pattern = hostRangePattern),
    HostFieldSpec("transport_settings.masque_settings.xmux.max_connections", HostFieldType.TEXT, pattern = hostRangePattern),
    HostFieldSpec("transport_settings.masque_settings.xmux.c_max_reuse_times", HostFieldType.TEXT, pattern = hostRangePattern),
    HostFieldSpec("transport_settings.masque_settings.xmux.h_max_reusable_secs", HostFieldType.TEXT, pattern = hostRangePattern),
    HostFieldSpec("transport_settings.masque_settings.xmux.h_keep_alive_period", HostFieldType.INTEGER),
    HostFieldSpec("transport_settings.grpc_settings.multi_mode", HostFieldType.BOOLEAN, default = "false"),
    HostFieldSpec("transport_settings.grpc_settings.idle_timeout", HostFieldType.INTEGER),
    HostFieldSpec("transport_settings.grpc_settings.health_check_timeout", HostFieldType.INTEGER),
    HostFieldSpec("transport_settings.grpc_settings.permit_without_stream", HostFieldType.BOOLEAN, default = "false"),
    HostFieldSpec("transport_settings.grpc_settings.initial_windows_size", HostFieldType.INTEGER),
    HostFieldSpec("transport_settings.websocket_settings.heartbeatPeriod", HostFieldType.INTEGER),
    HostFieldSpec("fragment_settings.xray.packets", HostFieldType.TEXT, emptyIsNull = false, pattern = Regex(":?tlshello|[0-9-]{1,16}")),
    HostFieldSpec("fragment_settings.xray.length", HostFieldType.TEXT, emptyIsNull = false, pattern = Regex("[0-9-]{1,16}")),
    HostFieldSpec("fragment_settings.xray.interval", HostFieldType.TEXT, emptyIsNull = false, pattern = Regex("[0-9-]{1,16}")),
    HostFieldSpec("fragment_settings.sing_box.fragment", HostFieldType.BOOLEAN, default = "false"),
    HostFieldSpec("fragment_settings.sing_box.fragment_fallback_delay", HostFieldType.TEXT, emptyIsNull = false, pattern = Regex("[0-9]+ms")),
    HostFieldSpec("fragment_settings.sing_box.record_fragment", HostFieldType.BOOLEAN, default = "false"),
    HostFieldSpec("mux_settings.xray.enabled", HostFieldType.BOOLEAN, default = "false"),
    HostFieldSpec("mux_settings.xray.concurrency", HostFieldType.INTEGER),
    HostFieldSpec("mux_settings.xray.xudp_concurrency", HostFieldType.INTEGER),
    HostFieldSpec("mux_settings.xray.xudp_proxy_udp_443", HostFieldType.CHOICE, default = "reject", emptyIsNull = false),
    HostFieldSpec("mux_settings.sing_box.enable", HostFieldType.BOOLEAN, default = "false"),
    HostFieldSpec("mux_settings.sing_box.protocol", HostFieldType.CHOICE, default = "smux", emptyIsNull = false),
    HostFieldSpec("mux_settings.sing_box.max_connections", HostFieldType.INTEGER),
    HostFieldSpec("mux_settings.sing_box.min_streams", HostFieldType.INTEGER),
    HostFieldSpec("mux_settings.sing_box.max_streams", HostFieldType.INTEGER),
    HostFieldSpec("mux_settings.sing_box.padding", HostFieldType.BOOLEAN, default = "false"),
    HostFieldSpec("mux_settings.clash.enable", HostFieldType.BOOLEAN, default = "false"),
    HostFieldSpec("mux_settings.clash.protocol", HostFieldType.CHOICE, default = "smux", emptyIsNull = false),
    HostFieldSpec("mux_settings.clash.max_connections", HostFieldType.INTEGER),
    HostFieldSpec("mux_settings.clash.min_streams", HostFieldType.INTEGER),
    HostFieldSpec("mux_settings.clash.max_streams", HostFieldType.INTEGER),
    HostFieldSpec("mux_settings.clash.padding", HostFieldType.BOOLEAN, default = "false"),
    HostFieldSpec("mux_settings.clash.statistic", HostFieldType.BOOLEAN, default = "false"),
    HostFieldSpec("mux_settings.clash.only_tcp", HostFieldType.BOOLEAN, default = "false"),
    HostFieldSpec("wireguard_overrides.allowed_ips", HostFieldType.LIST),
    HostFieldSpec("wireguard_overrides.mtu", HostFieldType.INTEGER, min = 576, max = 9000),
    HostFieldSpec("wireguard_overrides.keepalive_seconds", HostFieldType.INTEGER, min = 0, max = 86400),
    HostFieldSpec("wireguard_overrides.reserved", HostFieldType.TEXT, maxLength = 64),
    HostFieldSpec("wireguard_overrides.dns", HostFieldType.LIST)
)

internal data class HostDraftProblem(val code: String, val field: String? = null)
internal data class HostHttpHeader(val name: String, val value: String) {
    override fun toString(): String = "HostHttpHeader(name=$name, value=<redacted>)"
}

internal fun validHeaderName(name: String): Boolean = name.isNotEmpty() && name.all {
    it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "!#$%&'*+-.^_`|~"
}

internal fun validHeaderValue(value: String): Boolean = value.all { it == '\t' || !it.isISOControl() }

private fun validMasqueRange(value: String): Boolean {
    if (!hostRangePattern.matches(value)) return false
    val bounds = value.split('-').map { it.toLongOrNull() ?: return false }
    return bounds.first() <= bounds.last() && bounds.all { it <= Int.MAX_VALUE }
}

private fun parseHostObject(text: String): JSONObject? = runCatching {
    val tokener = JSONTokener(text)
    val value = tokener.nextValue() as? JSONObject
    if (value != null && tokener.nextClean() == '\u0000') value else null
}.getOrNull()

/** In-memory draft; the editor state holder retains it across activity recreation. */
internal data class HostDraft(
    val remark: String,
    val addressesText: String,
    val inboundTag: String?,
    val portText: String,
    val security: String,
    val disabled: Boolean,
    val status: List<String>,
    val templateId: Int?,
    val templateTouched: Boolean,
    val httpHeaders: List<HostHttpHeader>,
    val httpHeadersSupported: Boolean,
    val httpHeadersTouched: Boolean,
    val tcpRequestHeaders: HostTcpHeadersDraft,
    val tcpResponseHeaders: HostTcpHeadersDraft,
    val noise: List<HostNoiseItem>,
    val noiseSupported: Boolean,
    val noiseTouched: Boolean,
    val finalMaskText: String,
    val finalMaskSupported: Boolean,
    val finalMaskTouched: Boolean,
    val fields: Map<String, String>
) {
    val addresses: List<String> get() = hostStringValues(addressesText)
    val port: Int? get() = portText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()

    fun field(key: String): String = fields[key].orEmpty()
    fun withField(key: String, value: String): HostDraft = copy(fields = fields + (key to value))
    fun withMuxEnabled(key: String, enabled: Boolean): HostDraft {
        val keys = listOf("mux_settings.xray.enabled", "mux_settings.sing_box.enable", "mux_settings.clash.enable")
        require(key in keys) { "Unknown mux client" }
        val updates = if (enabled) keys.associateWith { (it == key).toString() } else mapOf(key to "false")
        return copy(fields = fields + updates)
    }
    fun withHttpHeaders(headers: List<HostHttpHeader>): HostDraft = copy(httpHeaders = headers, httpHeadersTouched = true)
    fun withTcpHeaders(direction: String, headers: List<HostTcpHeader>): HostDraft = when (direction) {
        "request" -> copy(tcpRequestHeaders = tcpRequestHeaders.withHeaders(headers))
        "response" -> copy(tcpResponseHeaders = tcpResponseHeaders.withHeaders(headers))
        else -> error("Unknown TCP direction")
    }
    fun withNoise(items: List<HostNoiseItem>): HostDraft = copy(noise = items, noiseTouched = true)
    fun withFinalMask(text: String): HostDraft = copy(finalMaskText = text, finalMaskTouched = true)

    override fun toString(): String = "HostDraft(<redacted>)"

    private fun sameHttpHeaders(original: HostDraft): Boolean =
        httpHeaders.size == original.httpHeaders.size &&
            httpHeaders.associate { it.name to it.value } == original.httpHeaders.associate { it.name to it.value }

    fun isDirtyComparedTo(original: HostDraft): Boolean {
        val sameHeaders = sameHttpHeaders(original)
        return copy(
            status = if (status.toSet() == original.status.toSet()) original.status else status,
            httpHeaders = if (sameHeaders) original.httpHeaders else httpHeaders,
            httpHeadersTouched = if (sameHeaders) original.httpHeadersTouched else httpHeadersTouched,
            tcpRequestHeaders = if (tcpRequestHeaders.sameValues(original.tcpRequestHeaders)) original.tcpRequestHeaders else tcpRequestHeaders,
            tcpResponseHeaders = if (tcpResponseHeaders.sameValues(original.tcpResponseHeaders)) original.tcpResponseHeaders else tcpResponseHeaders,
            noiseTouched = if (noise == original.noise) original.noiseTouched else noiseTouched,
            finalMaskTouched = if (finalMaskText == original.finalMaskText) original.finalMaskTouched else finalMaskTouched
        ) != original
    }

    private fun httpHeadersProblem(original: HostDraft): String? {
        if (!httpHeadersTouched) return null
        if (!httpHeadersSupported || !original.httpHeadersSupported) return "http_headers_unsupported"
        if (sameHttpHeaders(original)) return null
        if (httpHeaders.any { !validHeaderName(it.name) }) return "http_header_name"
        val names = httpHeaders.map { it.name.lowercase(Locale.ROOT) }
        if (names.distinct().size != names.size) return "http_header_duplicate"
        if (httpHeaders.any { !validHeaderValue(it.value) }) return "http_header_value"
        return null
    }

    private fun noiseProblem(original: HostDraft): String? {
        if (!noiseTouched) return null
        if (!noiseSupported || !original.noiseSupported) return "noise_unsupported"
        if (noise == original.noise) return null
        return noise.firstNotNullOfOrNull { it.problem() }?.let { "noise_$it" }
    }

    private fun finalMaskProblem(original: HostDraft): String? {
        if (!finalMaskTouched) return null
        if (!finalMaskSupported || !original.finalMaskSupported) return "final_mask_unsupported"
        if (finalMaskText.isBlank() || finalMaskText == original.finalMaskText) return null
        return if (parseHostObject(finalMaskText) == null) "final_mask_invalid" else null
    }

    fun validation(initial: PanelHost): HostDraftProblem? {
        HostValidation.validate(remark, addresses, portText)?.let { return HostDraftProblem(it) }
        if (initial.id == 0 && inboundTag.isNullOrBlank()) return HostDraftProblem("inbound")
        val original = from(initial)
        httpHeadersProblem(original)?.let { return HostDraftProblem(it, "http_headers") }
        tcpRequestHeaders.problem(original.tcpRequestHeaders)?.let { return HostDraftProblem(it, "tcp_request_headers") }
        tcpResponseHeaders.problem(original.tcpResponseHeaders)?.let { return HostDraftProblem(it, "tcp_response_headers") }
        noiseProblem(original)?.let { return HostDraftProblem(it, "noise_settings.xray") }
        finalMaskProblem(original)?.let { return HostDraftProblem(it, "final_mask_settings") }
        HOST_EDITABLE_FIELDS.forEach { spec ->
            val value = field(spec.key)
            if (!spec.isSame(value, original.field(spec.key)) && !spec.isValid(value)) {
                return HostDraftProblem("advanced", spec.key)
            }
        }
        val tcpStatus = "transport_settings.tcp_settings.response.status"
        if (field(tcpStatus) != original.field(tcpStatus) && !Regex("[1-5][0-9]{2}").matches(field(tcpStatus))) {
            return HostDraftProblem("advanced", tcpStatus)
        }
        val masque = "transport_settings.masque_settings.xmux."
        val maxConnections = field(masque + "max_connections")
        val maxConcurrency = field(masque + "max_concurrency")
        for (key in listOf("max_connections", "max_concurrency", "c_max_reuse_times", "h_max_reusable_secs")) {
            val value = field(masque + key)
            if (value != original.field(masque + key) && value.isNotEmpty() && !validMasqueRange(value)) {
                return HostDraftProblem("advanced", masque + key)
            }
        }
        if (maxConnections != original.field(masque + "max_connections") || maxConcurrency != original.field(masque + "max_concurrency")) {
            if ((maxConnections.substringAfterLast('-').toLongOrNull() ?: 0L) > 0L &&
                (maxConcurrency.substringAfterLast('-').toLongOrNull() ?: 0L) > 0L) {
                return HostDraftProblem("masque_xmux_conflict")
            }
        }
        val fragment = HOST_EDITABLE_FIELDS.filter { it.key.startsWith("fragment_settings.xray.") }
        val fragmentChanged = fragment.any { !it.isSame(field(it.key), original.field(it.key)) }
        if (fragmentChanged && fragment.any { field(it.key).isNotBlank() }) {
            fragment.firstOrNull { field(it.key).isBlank() }?.let { return HostDraftProblem("advanced", it.key) }
        }
        return null
    }

    fun changes(initial: PanelHost): List<HostFieldChange> {
        val original = from(initial)
        val changes = mutableListOf<HostFieldChange>()
        if (initial.id != 0) {
            if (remark.trim() != original.remark.trim()) changes += HostFieldChange("remark", remark.trim())
            if (addresses.toSet() != original.addresses.toSet()) changes += HostFieldChange("address", JSONArray(addresses))
            if (inboundTag != original.inboundTag) changes += HostFieldChange("inbound_tag", inboundTag)
            if (port != original.port) changes += HostFieldChange("port", port)
            if (security != original.security) changes += HostFieldChange("security", security)
        }
        if (disabled != original.disabled) changes += HostFieldChange("is_disabled", disabled)
        if (status.toSet() != original.status.toSet()) changes += HostFieldChange("status", JSONArray(status))
        if (templateTouched && (templateId != original.templateId || hasUnrecognizedTemplate(initial.raw))) {
            changes += HostFieldChange(listOf("subscription_templates", "xray"), templateId)
        }
        if (httpHeadersTouched) {
            require(httpHeadersProblem(original) == null) { "Invalid HTTP headers" }
            val current = httpHeaders.associate { it.name to it.value }
            val previous = original.httpHeaders.associate { it.name to it.value }
            if (current != previous) {
                val body = if (current.isEmpty()) null else JSONObject().apply { current.forEach { (key, value) -> put(key, value) } }
                // The whole map is a conflict unit so concurrent additions cannot be silently lost.
                changes += HostFieldChange("http_headers", body)
            }
        }
        listOf("request" to (tcpRequestHeaders to original.tcpRequestHeaders),
            "response" to (tcpResponseHeaders to original.tcpResponseHeaders)).forEach { (direction, pair) ->
            val (current, baseline) = pair
            if (current.touched) {
                require(current.problem(baseline) == null) { "Invalid TCP headers" }
                if (!current.sameValues(baseline)) {
                    changes += HostFieldChange(listOf("transport_settings", "tcp_settings", direction, "headers"), current.jsonValue())
                }
            }
        }
        if (noiseTouched) {
            require(noiseProblem(original) == null) { "Invalid noise settings" }
            if (noise != original.noise) {
                val value = if (noise.isEmpty()) null else JSONArray().apply { noise.forEach { put(it.toJson()) } }
                // List ordering and entries form one conflict unit, preserving concurrent additions.
                changes += HostFieldChange(listOf("noise_settings", "xray"), value)
            }
        }
        if (finalMaskTouched) {
            require(finalMaskProblem(original) == null) { "Invalid FinalMask settings" }
            if (finalMaskText != original.finalMaskText) {
                changes += HostFieldChange("final_mask_settings", if (finalMaskText.isBlank()) null else parseHostObject(finalMaskText))
            }
        }
        val fragment = HOST_EDITABLE_FIELDS.filter { it.key.startsWith("fragment_settings.xray.") }
        val clearFragment = fragment.all { field(it.key).isBlank() } &&
            fragment.any { !it.isSame(field(it.key), original.field(it.key)) }
        HOST_EDITABLE_FIELDS.forEach { spec ->
            val value = field(spec.key)
            if ((!clearFragment || spec !in fragment) && !spec.isSame(value, original.field(spec.key))) {
                changes += HostFieldChange(spec.path, spec.jsonValue(value))
            }
        }
        if (clearFragment) changes += HostFieldChange(listOf("fragment_settings", "xray"), null)
        return changes
    }

    companion object {
        fun from(host: PanelHost): HostDraft {
            val parsed = runCatching { JSONObject(host.raw) }.getOrNull()
            val raw = parsed ?: JSONObject()
            val savedHeaders = raw.opt("http_headers")
            val headerObject = savedHeaders as? JSONObject
            val headersSupported = parsed != null && (savedHeaders == null || savedHeaders === JSONObject.NULL ||
                headerObject != null && headerObject.keys().asSequence().all { headerObject.opt(it) is String })
            val headers = if (headersSupported && headerObject != null) {
                headerObject.keys().asSequence().map { HostHttpHeader(it, headerObject.getString(it)) }.toList()
            } else emptyList()
            val noise = readHostNoise(raw, parsed != null)
            val finalMask = raw.opt("final_mask_settings")
            val finalMaskSupported = parsed != null && (finalMask == null || finalMask === JSONObject.NULL || finalMask is JSONObject)
            val finalMaskText = if (finalMask is JSONObject) finalMask.toString(2) else ""
            val xray = raw.optJSONObject("subscription_templates")?.opt("xray")
            val templateId = when (xray) {
                is Number -> xray.toString().toIntOrNull()?.takeIf { it > 0 }
                is String -> xray.toIntOrNull()?.takeIf { it > 0 }
                else -> null
            }
            return HostDraft(
                remark = host.remark,
                addressesText = host.addresses.joinToString(", "),
                inboundTag = host.inboundTag,
                portText = host.port?.toString().orEmpty(),
                security = host.security,
                disabled = host.isDisabled,
                status = host.status.distinct(),
                templateId = templateId,
                templateTouched = false,
                httpHeaders = headers,
                httpHeadersSupported = headersSupported,
                httpHeadersTouched = false,
                tcpRequestHeaders = readTcpHeaders(raw, parsed != null, "request"),
                tcpResponseHeaders = readTcpHeaders(raw, parsed != null, "response"),
                noise = noise.items,
                noiseSupported = noise.supported,
                noiseTouched = false,
                finalMaskText = finalMaskText,
                finalMaskSupported = finalMaskSupported,
                finalMaskTouched = false,
                fields = HOST_EDITABLE_FIELDS.associate { it.key to it.read(raw) }
            )
        }

        fun hasUnrecognizedTemplate(raw: String): Boolean {
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: return true
            val parent = json.opt("subscription_templates") ?: return false
            if (parent === JSONObject.NULL) return false
            if (parent !is JSONObject) return true
            val value = parent.opt("xray") ?: return false
            if (value === JSONObject.NULL) return false
            return when (value) {
                is Number, is String -> value.toString().toIntOrNull()?.let { it <= 0 } ?: true
                else -> true
            }
        }
    }
}
