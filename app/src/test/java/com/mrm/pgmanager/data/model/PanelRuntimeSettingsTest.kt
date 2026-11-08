package com.mrm.pgmanager.data.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelRuntimeSettingsTest {
    private val general = JSONObject("""{
        "default_method":"aes-256-gcm", "stop_nodes_on_shutdown":true,
        "usage":{"record_node_usage":true,"record_node_stats":false,
                 "reset_usage_clean_chart_data":false,"record_outbound_usage":false,"unknown":12},
        "node_health":{"fail_threshold":3,"recover_threshold":2,"probe_timeout":5,"max_backoff":60}
    }""")

    @Test fun `legacy responses do not invent runtime or cleanup support`() {
        val settings = PanelSettings.from(JSONObject("""{"general":{},"subscription":{"rules":[]}}"""))
        assertNull(settings.runtime)
        assertNull(settings.cleanup)
        assertNull(settings.subscription.clientsLimit)
    }

    @Test fun `runtime parses 5_6 settings and retains unknown fields`() {
        val runtime = PanelRuntimeSettings.from(general)!!
        assertTrue(runtime.recordNodeUsage)
        assertFalse(runtime.recordOutboundUsage)
        assertTrue(runtime.stopNodesOnShutdown)
        val body = runtime.copy(recordOutboundUsage = true).toJson()
        assertTrue(body.getJSONObject("usage").getBoolean("record_outbound_usage"))
        assertEquals(12, body.getJSONObject("usage").getInt("unknown"))
        assertEquals("aes-256-gcm", body.getString("default_method"))
        assertEquals(60, body.getJSONObject("node_health").getInt("max_backoff"))
    }

    @Test fun `partial or malformed runtime and cleanup data are not treated as supported defaults`() {
        val partial = JSONObject(general.toString())
        partial.getJSONObject("usage").remove("record_node_usage")
        assertNull(PanelRuntimeSettings.from(partial))
        val malformed = JSONObject(general.toString())
        malformed.getJSONObject("node_health").put("probe_timeout", "invalid")
        assertNull(PanelRuntimeSettings.from(malformed))
        assertNull(PanelCleanupSettings.from(JSONObject("{}")))
        val badCleanup = JSONObject("""{
            "expired_users_retention_days":"invalid","include_limited_users":false,"usage_history_retention_days":null,
            "node_stats_retention_days":null,"usage_rollup_hourly_after_days":null,
            "usage_rollup_half_day_after_days":null,"usage_rollup_daily_after_days":null
        }""")
        assertNull(PanelCleanupSettings.from(badCleanup))
    }

    @Test fun `runtime numeric limits mirror the panel`() {
        val runtime = PanelRuntimeSettings.from(general)!!
        assertTrue(runtime.copy(maxBackoff = 0).isValid())
        assertFalse(runtime.copy(failThreshold = 0).isValid())
        assertFalse(runtime.copy(recoverThreshold = 0).isValid())
        assertFalse(runtime.copy(probeTimeout = 0).isValid())
        assertFalse(runtime.copy(maxBackoff = -1).isValid())
    }

    @Test fun `cleanup null zero and ordering retain their distinct meanings`() {
        val cleanup = PanelCleanupSettings.from(JSONObject("""{
            "expired_users_retention_days":null,"include_limited_users":false,"usage_history_retention_days":null,
            "node_stats_retention_days":null,"usage_rollup_hourly_after_days":null,
            "usage_rollup_half_day_after_days":null,"usage_rollup_daily_after_days":null
        }"""))!!
        assertNull(cleanup.expiredUsersRetentionDays)
        assertFalse(cleanup.includeLimitedUsers)
        assertTrue(cleanup.isValid())
        assertTrue(cleanup.copy(expiredUsersRetentionDays = 0).isValid())
        assertTrue(cleanup.copy(usageRollupHourlyAfterDays = 3, usageRollupDailyAfterDays = 10).isValid())
        assertFalse(cleanup.copy(usageHistoryRetentionDays = 0).isValid())
        assertFalse(cleanup.copy(expiredUsersRetentionDays = -1).isValid())
        assertFalse(cleanup.copy(nodeStatsRetentionDays = 36_501).isValid())
        assertFalse(cleanup.copy(usageRollupHourlyAfterDays = 10, usageRollupDailyAfterDays = 10).isValid())
        assertFalse(cleanup.copy(usageRollupHourlyAfterDays = 12, usageRollupHalfDayAfterDays = 5).isValid())
    }

    @Test fun `client limit appears only when reported by subscription`() {
        val settings = PanelSettings.from(JSONObject("""{"subscription":{"rules":[],"clients_limit":0}}"""))
        assertEquals(0, settings.subscription.clientsLimit)
        assertEquals(7, settings.subscription.copy(clientsLimit = 7).toJson().getInt("clients_limit"))
        val old = PanelSubscriptionSection.from(JSONObject("""{"rules":[]}"""))
        assertFalse(old.copy(clientsLimit = 7).toJson().has("clients_limit"))
    }
}
