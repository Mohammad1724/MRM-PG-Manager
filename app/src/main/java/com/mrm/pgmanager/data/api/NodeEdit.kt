package com.mrm.pgmanager.data.api

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.util.Locale

/** An editor snapshot retains hashes of secrets, never the original credentials. */
internal data class NodeSnapshot(
    val id: Int,
    val name: String,
    val address: String,
    val port: Int,
    val apiPort: Int,
    val coefficient: Double,
    val connectionType: String,
    val keepAlive: Int,
    val coreId: Int?,
    val dataLimit: Long,
    val resetStrategy: String,
    val resetTime: Int,
    val defaultTimeout: Int,
    val internalTimeout: Int,
    val status: String,
    val certificateHash: String?,
    val apiKeyHash: String?,
    val proxyHash: String?
) {
    override fun toString(): String = "NodeSnapshot(id=$id)"
}

internal enum class NodeProxyEdit { KEEP, REPLACE, CLEAR }
internal enum class NodeStatusEdit { KEEP, ENABLE, DISABLE }

/** Secrets only live in this in-memory draft and are intentionally omitted from toString. */
internal data class NodeDraft(
    val name: String = "",
    val address: String = "",
    val port: String = "62050",
    val apiPort: String = "62051",
    val coefficient: String = "1.0",
    val connectionType: String = "grpc",
    val certificate: String = "",
    val keepAlive: String = "60",
    val coreId: String = "1",
    val apiKey: String = "",
    val dataLimit: String = "0",
    val resetStrategy: String = "no_reset",
    val resetTime: String = "-1",
    val defaultTimeout: String = "10",
    val internalTimeout: String = "15",
    val proxy: String = "",
    val proxyEdit: NodeProxyEdit = NodeProxyEdit.KEEP,
    val statusEdit: NodeStatusEdit = NodeStatusEdit.KEEP
) {
    override fun toString(): String = "NodeDraft(redacted)"

    companion object {
        fun from(node: NodeSnapshot) = NodeDraft(
            name = node.name, address = node.address, port = node.port.toString(),
            apiPort = node.apiPort.toString(), coefficient = node.coefficient.toString(),
            connectionType = node.connectionType, keepAlive = node.keepAlive.toString(),
            coreId = node.coreId?.toString().orEmpty(), dataLimit = node.dataLimit.toString(),
            resetStrategy = node.resetStrategy, resetTime = node.resetTime.toString(),
            defaultTimeout = node.defaultTimeout.toString(), internalTimeout = node.internalTimeout.toString()
        )
    }
}

internal enum class NodeDraftError {
    NAME, ADDRESS, PORT, API_PORT, COEFFICIENT, CONNECTION, CERTIFICATE, KEEP_ALIVE,
    CORE, API_KEY, DATA_LIMIT, RESET_STRATEGY, RESET_TIME, DEFAULT_TIMEOUT, INTERNAL_TIMEOUT, PROXY
}

private val nodeUuidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
private val nodeProxyPattern = Regex("^(http|https|socks4|socks5)://([^\\s:@]+:[^\\s:@]+@)?[a-zA-Z0-9.\\-]+:\\d{1,5}$")
private val nodeCertificatePattern = Regex("-----BEGIN CERTIFICATE-----[\\s\\S]*?-----END CERTIFICATE-----")
private val nodePrivateKeyPattern = Regex("-----BEGIN [^-]*PRIVATE KEY-----")
private val nodeResetLimits = mapOf("day" to 86_400, "week" to 604_800, "month" to 2_678_400, "year" to 31_536_000)

private fun isNodeCertificate(value: String): Boolean {
    val cert = value.trim()
    if (cert.length > 2048 || !nodeCertificatePattern.containsMatchIn(cert) || nodePrivateKeyPattern.containsMatchIn(cert)) return false
    return runCatching {
        CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(cert.toByteArray(Charsets.UTF_8)))
    }.isSuccess
}

/** Check only values this client can round-trip, without exposing input in an error. */
internal fun NodeDraft.validationError(isNew: Boolean, initial: NodeSnapshot? = null): NodeDraftError? {
    val trimmedName = name.trim()
    if (trimmedName.isEmpty() || trimmedName.codePointCount(0, trimmedName.length) > 256) return NodeDraftError.NAME
    if (address.isBlank() || address.trim().length > 256) return NodeDraftError.ADDRESS
    if (port.toIntOrNull() !in 1..65535) return NodeDraftError.PORT
    if (apiPort.toIntOrNull() !in 1..65535) return NodeDraftError.API_PORT
    if (coefficient.toDoubleOrNull()?.let { it.isFinite() && it >= 0.0 } != true) return NodeDraftError.COEFFICIENT
    if (connectionType !in listOf("grpc", "rest") && connectionType != initial?.connectionType) return NodeDraftError.CONNECTION
    if ((isNew || certificate.isNotBlank()) && !isNodeCertificate(certificate)) return NodeDraftError.CERTIFICATE
    if (keepAlive.toIntOrNull()?.let { it >= 0 } != true) return NodeDraftError.KEEP_ALIVE
    if (coreId.isBlank()) {
        if (isNew || initial?.coreId != null) return NodeDraftError.CORE
    } else if (coreId.toIntOrNull()?.let { it > 0 } != true) return NodeDraftError.CORE
    if ((isNew || apiKey.isNotBlank()) && !nodeUuidPattern.matches(apiKey.trim())) return NodeDraftError.API_KEY
    if (dataLimit.toLongOrNull()?.let { it >= 0 } != true) return NodeDraftError.DATA_LIMIT
    if (resetStrategy !in (nodeResetLimits.keys + "no_reset") && resetStrategy != initial?.resetStrategy) return NodeDraftError.RESET_STRATEGY
    val reset = resetTime.toIntOrNull() ?: return NodeDraftError.RESET_TIME
    if (reset < -1 || (reset != -1 && nodeResetLimits[resetStrategy]?.let { reset >= it } == true)) return NodeDraftError.RESET_TIME
    if (defaultTimeout.toIntOrNull()?.let { it >= 3 } != true) return NodeDraftError.DEFAULT_TIMEOUT
    if (internalTimeout.toIntOrNull() !in 3..60) return NodeDraftError.INTERNAL_TIMEOUT
    val proxyValue = proxy.trim()
    if ((isNew && proxyValue.isNotEmpty()) || (!isNew && proxyEdit == NodeProxyEdit.REPLACE)) {
        if (proxyValue.length > 256 || !nodeProxyPattern.matches(proxyValue)) return NodeDraftError.PROXY
    }
    return null
}

private fun secretHash(value: String?): String? = value?.let {
    MessageDigest.getInstance("SHA-256").digest(it.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }
}

/** Fail closed on missing or malformed fields instead of overwriting them with defaults. */
internal fun parseNodeSnapshot(body: String, expectedId: Int): NodeSnapshot {
    try {
        val obj = JSONObject(body)
        fun text(key: String): String = obj.get(key) as? String ?: error("Invalid node field")
        fun int(key: String): Int {
            val number = obj.get(key) as? Number ?: error("Invalid node field")
            val long = number.toLong()
            require(long in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() && number.toDouble() == long.toDouble())
            return long.toInt()
        }
        fun long(key: String): Long {
            val number = obj.get(key) as? Number ?: error("Invalid node field")
            return number.toString().toLongOrNull() ?: error("Invalid node field")
        }
        fun nullableText(key: String): String? {
            require(obj.has(key))
            return if (obj.isNull(key)) null else text(key)
        }
        val id = int("id")
        require(id > 0 && id == expectedId)
        val coreId = if (obj.isNull("core_config_id")) {
            require(obj.has("core_config_id")); null
        } else int("core_config_id")
        val coefficient = (obj.get("usage_coefficient") as? Number)?.toDouble() ?: error("Invalid node field")
        require(coefficient.isFinite())
        return NodeSnapshot(
            id = id, name = text("name"), address = text("address"), port = int("port"),
            apiPort = int("api_port"), coefficient = coefficient, connectionType = text("connection_type"),
            keepAlive = int("keep_alive"), coreId = coreId, dataLimit = long("data_limit"),
            resetStrategy = text("data_limit_reset_strategy"), resetTime = int("reset_time"),
            defaultTimeout = int("default_timeout"), internalTimeout = int("internal_timeout"),
            status = text("status"), certificateHash = secretHash(text("server_ca")),
            apiKeyHash = secretHash(nullableText("api_key")), proxyHash = secretHash(nullableText("proxy_url"))
        )
    } catch (_: Exception) {
        throw IllegalArgumentException("Invalid node response")
    }
}

/** No values or credentials are included in conflict and uncertain-write messages. */
internal class NodeEditConflictException(val fields: List<String>) :
    IllegalStateException("Node changed on the server: ${fields.joinToString()}")
internal class NodeWriteUnconfirmedException : IllegalStateException("Node operation could not be confirmed")

internal object NodeEditMerge {
    fun create(draft: NodeDraft): JSONObject {
        require(draft.validationError(isNew = true) == null) { "Invalid node draft" }
        return JSONObject().apply {
            put("name", draft.name.trim()); put("address", draft.address.trim())
            put("port", draft.port.toInt()); put("api_port", draft.apiPort.toInt())
            put("usage_coefficient", draft.coefficient.toDouble()); put("connection_type", draft.connectionType)
            put("server_ca", draft.certificate.trim()); put("keep_alive", draft.keepAlive.toInt())
            put("core_config_id", draft.coreId.toInt()); put("api_key", draft.apiKey.trim())
            put("data_limit", draft.dataLimit.toLong()); put("data_limit_reset_strategy", draft.resetStrategy)
            put("reset_time", draft.resetTime.toInt()); put("default_timeout", draft.defaultTimeout.toInt())
            put("internal_timeout", draft.internalTimeout.toInt())
            if (draft.proxy.isNotBlank()) put("proxy_url", draft.proxy.trim())
        }
    }

    /** Merge deliberate changes into a fresh GET. The server offers no atomic precondition. */
    fun update(initial: NodeSnapshot, latest: NodeSnapshot, draft: NodeDraft): JSONObject {
        require(initial.id > 0 && initial.id == latest.id) { "Invalid node snapshot" }
        require(draft.validationError(isNew = false, initial = initial) == null) { "Invalid node draft" }
        val body = JSONObject()
        val conflicts = mutableListOf<String>()
        fun <T> changed(field: String, before: T, now: T, requested: T): T {
            if (requested == before) return now
            if (now != before && now != requested) conflicts += field
            else if (now != requested) body.put(field, requested ?: JSONObject.NULL)
            return requested
        }
        changed("name", initial.name, latest.name, if (draft.name == initial.name) initial.name else draft.name.trim())
        changed("address", initial.address, latest.address, if (draft.address == initial.address) initial.address else draft.address.trim())
        changed("port", initial.port, latest.port, draft.port.toInt())
        changed("api_port", initial.apiPort, latest.apiPort, draft.apiPort.toInt())
        changed("usage_coefficient", initial.coefficient, latest.coefficient, draft.coefficient.toDouble())
        changed("connection_type", initial.connectionType, latest.connectionType, draft.connectionType)
        changed("keep_alive", initial.keepAlive, latest.keepAlive, draft.keepAlive.toInt())
        changed("core_config_id", initial.coreId, latest.coreId, draft.coreId.toIntOrNull())
        changed("data_limit", initial.dataLimit, latest.dataLimit, draft.dataLimit.toLong())
        val strategy = changed("data_limit_reset_strategy", initial.resetStrategy, latest.resetStrategy, draft.resetStrategy)
        val reset = changed("reset_time", initial.resetTime, latest.resetTime, draft.resetTime.toInt())
        changed("default_timeout", initial.defaultTimeout, latest.defaultTimeout, draft.defaultTimeout.toInt())
        changed("internal_timeout", initial.internalTimeout, latest.internalTimeout, draft.internalTimeout.toInt())

        fun secret(field: String, before: String?, now: String?, value: String?) {
            val desired = secretHash(value)
            if (now != before && now != desired) conflicts += field
            else if (now != desired) body.put(field, value ?: JSONObject.NULL)
        }
        if (draft.certificate.isNotBlank()) secret("server_ca", initial.certificateHash, latest.certificateHash, draft.certificate.trim())
        if (draft.apiKey.isNotBlank()) secret("api_key", initial.apiKeyHash, latest.apiKeyHash, draft.apiKey.trim())
        when (draft.proxyEdit) {
            NodeProxyEdit.KEEP -> Unit
            NodeProxyEdit.REPLACE -> secret("proxy_url", initial.proxyHash, latest.proxyHash, draft.proxy.trim())
            NodeProxyEdit.CLEAR -> secret("proxy_url", initial.proxyHash, latest.proxyHash, null)
        }
        if (draft.statusEdit != NodeStatusEdit.KEEP) {
            val enabled = draft.statusEdit == NodeStatusEdit.ENABLE
            val before = initial.status != "disabled"
            val now = latest.status != "disabled"
            if (now != before && now != enabled) conflicts += "status"
            else if (now != enabled) body.put("status", if (enabled) "connecting" else "disabled")
        }
        if (conflicts.isNotEmpty()) throw NodeEditConflictException(conflicts)
        // NodeModify inherits a non-null api_port default of 62051. Omitting it silently resets the server value.
        if (!body.has("api_port")) body.put("api_port", latest.apiPort)
        // The panel's reset validator needs both values whenever a non-default strategy is sent.
        if (body.has("data_limit_reset_strategy") || body.has("reset_time")) {
            require(reset >= -1 && (reset == -1 || nodeResetLimits[strategy]?.let { reset < it } != false)) {
                "Invalid merged reset time"
            }
            body.put("data_limit_reset_strategy", strategy)
            body.put("reset_time", reset)
        }
        return body
    }
}
