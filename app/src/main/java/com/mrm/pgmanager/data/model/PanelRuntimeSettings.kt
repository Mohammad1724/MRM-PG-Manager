package com.mrm.pgmanager.data.model

import org.json.JSONObject

private fun JSONObject.exactInt(name: String): Int? = when (val value = opt(name)) {
    is Int -> value
    is Long -> value.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
    else -> null
}

/** Runtime settings introduced in PasarGuard 5.6. Absent fields are not inferred on older panels. */
data class PanelRuntimeSettings(
    val recordNodeUsage: Boolean,
    val recordNodeStats: Boolean,
    val resetUsageCleanChartData: Boolean,
    val recordOutboundUsage: Boolean,
    val failThreshold: Int,
    val recoverThreshold: Int,
    val probeTimeout: Int,
    val maxBackoff: Int,
    val stopNodesOnShutdown: Boolean,
    val raw: JSONObject
) {
    fun toJson(): JSONObject {
        val body = JSONObject(raw.toString())
        val usage = body.optJSONObject("usage") ?: JSONObject().also { body.put("usage", it) }
        usage.put("record_node_usage", recordNodeUsage)
        usage.put("record_node_stats", recordNodeStats)
        usage.put("reset_usage_clean_chart_data", resetUsageCleanChartData)
        usage.put("record_outbound_usage", recordOutboundUsage)
        val health = body.optJSONObject("node_health") ?: JSONObject().also { body.put("node_health", it) }
        health.put("fail_threshold", failThreshold)
        health.put("recover_threshold", recoverThreshold)
        health.put("probe_timeout", probeTimeout)
        health.put("max_backoff", maxBackoff)
        body.put("stop_nodes_on_shutdown", stopNodesOnShutdown)
        return body
    }

    fun isValid(): Boolean = failThreshold >= 1 && recoverThreshold >= 1 && probeTimeout >= 1 && maxBackoff >= 0

    companion object {
        fun from(general: JSONObject?): PanelRuntimeSettings? {
            val usage = general?.optJSONObject("usage") ?: return null
            val health = general.optJSONObject("node_health") ?: return null
            // A partial or malformed response cannot safely establish 5.6 capabilities.
            return PanelRuntimeSettings(
                recordNodeUsage = usage.opt("record_node_usage") as? Boolean ?: return null,
                recordNodeStats = usage.opt("record_node_stats") as? Boolean ?: return null,
                resetUsageCleanChartData = usage.opt("reset_usage_clean_chart_data") as? Boolean ?: return null,
                recordOutboundUsage = usage.opt("record_outbound_usage") as? Boolean ?: return null,
                failThreshold = health.exactInt("fail_threshold") ?: return null,
                recoverThreshold = health.exactInt("recover_threshold") ?: return null,
                probeTimeout = health.exactInt("probe_timeout") ?: return null,
                maxBackoff = health.exactInt("max_backoff") ?: return null,
                stopNodesOnShutdown = general.opt("stop_nodes_on_shutdown") as? Boolean ?: return null,
                raw = general
            ).takeIf { it.isValid() }
        }
    }
}

/** Null retention keeps the corresponding data; zero expired-user days means immediate eligibility. */
data class PanelCleanupSettings(
    val expiredUsersRetentionDays: Int?,
    val includeLimitedUsers: Boolean,
    val usageHistoryRetentionDays: Int?,
    val nodeStatsRetentionDays: Int?,
    val usageRollupHourlyAfterDays: Int?,
    val usageRollupHalfDayAfterDays: Int?,
    val usageRollupDailyAfterDays: Int?,
    val raw: JSONObject
) {
    fun toJson(): JSONObject = JSONObject(raw.toString())
        .put("expired_users_retention_days", expiredUsersRetentionDays ?: JSONObject.NULL)
        .put("include_limited_users", includeLimitedUsers)
        .put("usage_history_retention_days", usageHistoryRetentionDays ?: JSONObject.NULL)
        .put("node_stats_retention_days", nodeStatsRetentionDays ?: JSONObject.NULL)
        .put("usage_rollup_hourly_after_days", usageRollupHourlyAfterDays ?: JSONObject.NULL)
        .put("usage_rollup_half_day_after_days", usageRollupHalfDayAfterDays ?: JSONObject.NULL)
        .put("usage_rollup_daily_after_days", usageRollupDailyAfterDays ?: JSONObject.NULL)

    fun isValid(): Boolean {
        if (expiredUsersRetentionDays != null && expiredUsersRetentionDays !in 0..36_500) return false
        val positive = listOf(
            usageHistoryRetentionDays, nodeStatsRetentionDays, usageRollupHourlyAfterDays,
            usageRollupHalfDayAfterDays, usageRollupDailyAfterDays
        )
        if (positive.any { it != null && it !in 1..36_500 }) return false
        val rollups = listOfNotNull(usageRollupHourlyAfterDays, usageRollupHalfDayAfterDays, usageRollupDailyAfterDays)
        return rollups.zipWithNext().all { (finer, coarser) -> finer < coarser }
    }

    companion object {
        fun from(o: JSONObject?): PanelCleanupSettings? {
            o ?: return null
            val nullableNames = listOf(
                "expired_users_retention_days", "usage_history_retention_days", "node_stats_retention_days",
                "usage_rollup_hourly_after_days", "usage_rollup_half_day_after_days", "usage_rollup_daily_after_days"
            )
            if (nullableNames.any { !o.has(it) || (!o.isNull(it) && o.exactInt(it) == null) }) return null
            val includeLimited = o.opt("include_limited_users") as? Boolean ?: return null
            return PanelCleanupSettings(
                expiredUsersRetentionDays = o.exactInt("expired_users_retention_days"),
                includeLimitedUsers = includeLimited,
                usageHistoryRetentionDays = o.exactInt("usage_history_retention_days"),
                nodeStatsRetentionDays = o.exactInt("node_stats_retention_days"),
                usageRollupHourlyAfterDays = o.exactInt("usage_rollup_hourly_after_days"),
                usageRollupHalfDayAfterDays = o.exactInt("usage_rollup_half_day_after_days"),
                usageRollupDailyAfterDays = o.exactInt("usage_rollup_daily_after_days"),
                raw = o
            ).takeIf { it.isValid() }
        }
    }
}
