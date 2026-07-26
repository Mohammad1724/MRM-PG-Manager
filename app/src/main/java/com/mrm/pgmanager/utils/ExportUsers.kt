package com.mrm.pgmanager.utils

import com.mrm.pgmanager.data.model.PanelUser

/** ساخت CSV از فهرست کاربران؛ با BOM تا اکسل فارسی را درست نمایش دهد. */
fun usersToCsv(users: List<PanelUser>): String = buildString {
    append('\uFEFF')
    appendLine("username,status,used_bytes,limit_bytes,used_gb,limit_gb,expire,created_at,note")
    fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    users.forEach { u ->
        append(esc(u.username)); append(',')
        append(u.status); append(',')
        append(u.usedTraffic); append(',')
        append(u.dataLimit); append(',')
        append(String.format(java.util.Locale.US, "%.2f", u.usedTraffic / 1073741824.0)); append(',')
        append(String.format(java.util.Locale.US, "%.2f", u.dataLimit / 1073741824.0)); append(',')
        append(esc(u.expire ?: "")); append(',')
        append(esc(u.createdAt ?: "")); append(',')
        appendLine(esc(u.note ?: ""))
    }
}

/** ساخت JSON خوانا از فهرست کاربران؛ ساختار مشابه پاسخ API پنل. */
fun usersToJson(users: List<PanelUser>): String {
    val arr = org.json.JSONArray()
    users.forEach { u ->
        arr.put(org.json.JSONObject().apply {
            put("id", u.id)
            put("username", u.username)
            put("status", u.status)
            put("used_traffic", u.usedTraffic)
            put("data_limit", u.dataLimit)
            put("expire", u.expire ?: "")
            put("created_at", u.createdAt ?: "")
            put("online_at", u.onlineAt ?: "")
            put("is_online", u.isOnline)
            put("note", u.note ?: "")
            if (u.hwidLimit != null) put("hwid_limit", u.hwidLimit)
            put("group_ids", org.json.JSONArray(u.groupIds))
            put("group_names", org.json.JSONArray(u.groupNames))
            put("subscription_url", u.subUrl)
        })
    }
    return arr.toString(2)
}
