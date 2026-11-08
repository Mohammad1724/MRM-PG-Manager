package com.mrm.pgmanager.data.api

import android.util.JsonReader
import android.util.JsonToken
import java.io.StringReader
import java.math.BigInteger

internal data class InboundDetail(val tag: String, val protocol: String, val network: String?)

/** BigInteger preserves the full IPv6 pool size reported by the panel. */
internal data class WireGuardSubnet(
    val subnet: String,
    val interfaceTags: List<String>,
    val capacity: BigInteger,
    val used: BigInteger,
    val free: BigInteger,
    val freeIps: List<String>
)

private inline fun <T> readInfraArray(raw: String, readItem: (JsonReader) -> T): List<T> {
    try {
        JsonReader(StringReader(raw)).use { reader ->
            val result = mutableListOf<T>()
            reader.beginArray()
            while (reader.hasNext()) result.add(readItem(reader))
            reader.endArray()
            require(reader.peek() == JsonToken.END_DOCUMENT)
            return result
        }
    } catch (_: Exception) {
        throw IllegalArgumentException("Invalid infrastructure response")
    }
}

private fun JsonReader.stringValue(): String {
    require(peek() == JsonToken.STRING)
    return nextString()
}

private fun JsonReader.stringList(): List<String> {
    val values = mutableListOf<String>()
    beginArray()
    while (hasNext()) values.add(stringValue())
    endArray()
    return values
}

private fun JsonReader.nonNegativeInteger(): BigInteger {
    require(peek() == JsonToken.NUMBER)
    val text = nextString()
    // IPv6 needs at most 39 decimal digits; allow headroom without accepting unbounded numbers.
    require(text.length in 1..128 && text.all { it in '0'..'9' })
    return BigInteger(text)
}

internal fun parseInboundDetails(raw: String): List<InboundDetail> = readInfraArray(raw) { reader ->
    var tag: String? = null
    var protocol: String? = null
    var network: String? = null
    val seen = mutableSetOf<String>()
    reader.beginObject()
    while (reader.hasNext()) {
        val field = reader.nextName()
        require(seen.add(field))
        when (field) {
            "tag" -> tag = reader.stringValue()
            "protocol" -> protocol = reader.stringValue()
            "network" -> network = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.stringValue()
            else -> reader.skipValue()
        }
    }
    reader.endObject()
    InboundDetail(requireNotNull(tag), requireNotNull(protocol), network)
}

internal fun parseWireGuardSubnets(raw: String): List<WireGuardSubnet> = readInfraArray(raw) { reader ->
    var subnet: String? = null
    var tags: List<String>? = null
    var capacity: BigInteger? = null
    var used: BigInteger? = null
    var free: BigInteger? = null
    var freeIps: List<String>? = null
    val seen = mutableSetOf<String>()
    reader.beginObject()
    while (reader.hasNext()) {
        val field = reader.nextName()
        require(seen.add(field))
        when (field) {
            "subnet" -> subnet = reader.stringValue()
            "interface_tags" -> tags = reader.stringList()
            "capacity" -> capacity = reader.nonNegativeInteger()
            "used" -> used = reader.nonNegativeInteger()
            "free" -> free = reader.nonNegativeInteger()
            "free_ips" -> freeIps = reader.stringList()
            else -> reader.skipValue()
        }
    }
    reader.endObject()
    WireGuardSubnet(requireNotNull(subnet), requireNotNull(tags), requireNotNull(capacity),
        requireNotNull(used), requireNotNull(free), requireNotNull(freeIps))
}
