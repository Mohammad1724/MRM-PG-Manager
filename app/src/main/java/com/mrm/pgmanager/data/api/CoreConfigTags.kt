package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.CoreType
import org.json.JSONArray
import org.json.JSONObject

/** Read selectable names from this core's config, not from the global inbound list. */
internal fun coreConfigInboundTags(config: String, type: String?): List<String>? = try {
    val root = JSONObject(config)
    val inboundValue = root.opt("inbounds")
    if (type == CoreType.WG) {
        listOfNotNull((root.opt("interface_name") as? String)?.takeIf { it.isNotBlank() })
    } else when (inboundValue) {
        null, JSONObject.NULL -> emptyList()
        is JSONArray -> (0 until inboundValue.length()).mapNotNull { index ->
            (inboundValue.optJSONObject(index)?.opt("tag") as? String)?.takeIf { it.isNotBlank() }
        }.distinct()
        else -> null
    }
} catch (_: Exception) {
    null
}
