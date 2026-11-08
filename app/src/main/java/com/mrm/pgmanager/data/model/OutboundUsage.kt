package com.mrm.pgmanager.data.model

import org.json.JSONObject

private fun addTraffic(a: Long, b: Long): Long {
    require(a >= 0 && b >= 0 && a <= Long.MAX_VALUE - b) { "Outbound usage exceeds the supported range" }
    return a + b
}

/** Traffic recorded by a node for one outbound tag over the requested period. */
data class OutboundUsage(val tag: String, val points: List<NodeTrafficPoint>) {
    val uplink: Long get() = points.fold(0L) { total, point -> addTraffic(total, point.uplink) }
    val downlink: Long get() = points.fold(0L) { total, point -> addTraffic(total, point.downlink) }
    val total: Long get() = addTraffic(uplink, downlink)

    companion object {
        fun fromResponse(root: JSONObject): List<OutboundUsage> {
            val stats = root.optJSONObject("stats") ?: throw IllegalArgumentException("Invalid outbound usage response")
            fun traffic(point: JSONObject, name: String): Long {
                val value = when (val raw = point.opt(name)) {
                    is Int -> raw.toLong()
                    is Long -> raw
                    else -> throw IllegalArgumentException("Invalid outbound traffic")
                }
                require(value >= 0) { "Invalid outbound traffic" }
                return value
            }
            val result = stats.keys().asSequence().map { tag ->
                val series = stats.optJSONArray(tag) ?: throw IllegalArgumentException("Invalid outbound usage series")
                val points = (0 until series.length()).map { index ->
                    val point = series.optJSONObject(index) ?: throw IllegalArgumentException("Invalid outbound usage point")
                    val timestamp = (point.opt("period_start") as? String)?.takeIf { it.isNotBlank() }
                        ?: throw IllegalArgumentException("Invalid outbound usage period")
                    NodeTrafficPoint(timestamp, traffic(point, "uplink"), traffic(point, "downlink"))
                }.sortedBy { it.timestamp }
                OutboundUsage(tag, points)
            }.sortedByDescending { it.total }.toList()
            // Validate totals before exposing any series to the UI.
            result.forEach { it.total }
            merge(result)
            return result
        }

        fun merge(usage: List<OutboundUsage>): List<TrafficPoint> {
            val totals = sortedMapOf<String, Long>()
            usage.forEach { item ->
                item.points.forEach { point ->
                    totals[point.timestamp] = addTraffic(totals[point.timestamp] ?: 0L, addTraffic(point.uplink, point.downlink))
                }
            }
            return totals.map { TrafficPoint(it.key, it.value) }
        }
    }
}
