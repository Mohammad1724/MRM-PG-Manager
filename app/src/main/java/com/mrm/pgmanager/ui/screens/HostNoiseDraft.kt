package com.mrm.pgmanager.ui.screens

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** A noise row retains its source JSON, including fields that this version cannot edit. */
internal data class HostNoiseItem(
    val type: String = "rand",
    val packet: String = "",
    val packetIsArray: Boolean = false,
    val delay: String = "",
    val applyTo: String = "ip",
    private val source: String? = null
) {
    override fun toString(): String = "HostNoiseItem(type=$type, packet=<redacted>, delay=<redacted>, applyTo=$applyTo)"

    private fun original(): HostNoiseItem? = source?.let { fromJson(JSONObject(it)) }

    fun problem(): String? {
        val old = original()
        if (type != old?.type && type !in listOf("rand", "str", "base64", "hex")) return "type"
        if (applyTo != old?.applyTo && applyTo !in listOf("ip", "ipv4", "ipv6")) return "apply_to"
        if ((packet != old?.packet || packetIsArray != old.packetIsArray) && packetIsArray && parsePacketArray(packet) == null) return "packet"
        if (delay != old?.delay && delay.isNotEmpty() && !Regex("[0-9]{1,16}(-[0-9]{1,16})?").matches(delay)) return "delay"
        return null
    }

    fun toJson(): JSONObject {
        require(problem() == null) { "Invalid noise settings" }
        val old = original()
        val body = source?.let(::JSONObject) ?: JSONObject()
        if (type != old?.type) body.put("type", type)
        if (applyTo != old?.applyTo) body.put("apply_to", applyTo)
        if (packet != old?.packet || packetIsArray != old.packetIsArray) {
            body.put("packet", if (packetIsArray) requireNotNull(parsePacketArray(packet)) else packet)
        }
        if (delay != old?.delay) body.put("delay", delay)
        return body
    }

    companion object {
        fun fromJson(value: JSONObject): HostNoiseItem? {
            val type = value.opt("type") as? String ?: return null
            val packetValue = value.opt("packet")
            val packet = when (packetValue) {
                null, JSONObject.NULL -> ""
                is String -> packetValue
                is JSONArray -> if (validPacketArray(packetValue)) packetValue.toString() else return null
                else -> return null
            }
            val delayValue = value.opt("delay")
            val delay = when (delayValue) {
                null, JSONObject.NULL -> ""
                is String -> delayValue
                is Int, is Long -> delayValue.toString()
                else -> return null
            }
            val applyToValue = value.opt("apply_to")
            val applyTo = when (applyToValue) {
                null, JSONObject.NULL -> "ip"
                is String -> applyToValue
                else -> return null
            }
            return HostNoiseItem(type, packet, packetValue is JSONArray, delay, applyTo, value.toString())
        }
    }
}

/** Numeric array packets are explicit; ordinary strings are never silently converted to arrays. */
private fun parsePacketArray(text: String): JSONArray? = runCatching {
    val tokener = JSONTokener(text)
    val array = tokener.nextValue() as? JSONArray
    if (array != null && validPacketArray(array) && tokener.nextClean() == '\u0000') array else null
}.getOrNull()

private fun validPacketArray(array: JSONArray): Boolean = (0 until array.length()).all {
    val number = array.opt(it)
    (number is Int) || (number is Long && number in Int.MIN_VALUE..Int.MAX_VALUE)
}

internal data class HostNoiseList(val supported: Boolean, val items: List<HostNoiseItem>)

internal fun readHostNoise(raw: JSONObject, parsed: Boolean): HostNoiseList {
    if (!parsed) return HostNoiseList(false, emptyList())
    val parent = raw.opt("noise_settings")
    if (parent == null || parent === JSONObject.NULL) return HostNoiseList(true, emptyList())
    if (parent !is JSONObject) return HostNoiseList(false, emptyList())
    val value = parent.opt("xray")
    if (value == null || value === JSONObject.NULL) return HostNoiseList(true, emptyList())
    if (value !is JSONArray) return HostNoiseList(false, emptyList())
    val result = mutableListOf<HostNoiseItem>()
    for (index in 0 until value.length()) {
        val item = value.opt(index) as? JSONObject ?: return HostNoiseList(false, emptyList())
        result += HostNoiseItem.fromJson(item) ?: return HostNoiseList(false, emptyList())
    }
    return HostNoiseList(true, result)
}
