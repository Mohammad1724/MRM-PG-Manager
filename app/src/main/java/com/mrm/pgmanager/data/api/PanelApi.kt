package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.Group
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object PanelApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun baseUrl(input: String): String {
        val prepared = if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input"
        val uri = URI(prepared)
        require(!uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) { "Invalid URL" }
        return buildString {
            append(uri.scheme); append("://"); append(uri.host)
            if (uri.port != -1) append(":${uri.port}")
        }
    }

    private fun userUrl(session: Session, username: String): String =
        "${session.baseUrl}/api/user/${URLEncoder.encode(username, "UTF-8")}"

    private fun requestBuilder(session: Session, url: String): Request.Builder =
        Request.Builder().url(url).header("Authorization", "Bearer ${session.token}")

    suspend fun login(address: String, username: String, password: String): Session = withContext(Dispatchers.IO) {
        require(username.isNotBlank() && password.isNotBlank()) { "Credentials required" }
        val base = baseUrl(address)
        val body = FormBody.Builder().add("username", username).add("password", password).build()
        val request = Request.Builder().url("$base/api/admin/token").post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Login failed: ${response.code}")
            val token = JSONObject(response.body?.string() ?: error("Empty login response")).getString("access_token")
            Session(base, token, username)
        }
    }

    suspend fun users(session: Session): List<PanelUser> = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, "${session.baseUrl}/api/users?offset=0&limit=1000&load_sub=true").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Request failed: ${response.code}")
            val data = JSONObject(response.body?.string() ?: error("Empty users response")).getJSONArray("users")
            List(data.length()) { index -> parseUser(data.getJSONObject(index)) }
        }
    }

    suspend fun createUser(session: Session, username: String, limitGb: Double, expireIso: String, note: String = "", hwidLimit: Int? = null, groupIds: List<Int> = emptyList()) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("username", username).put("status", "active").put("data_limit", gbToBytes(limitGb)).put("expire", expireValue(expireIso))
        if (note.isNotBlank()) body.put("note", note)
        if (hwidLimit != null && hwidLimit > 0) body.put("hwid_limit", hwidLimit)
        if (groupIds.isNotEmpty()) body.put("group_ids", org.json.JSONArray(groupIds))
        executeJson(requestBuilder(session, "${session.baseUrl}/api/user").post(body.toString().toRequestBody(jsonType)).build())
    }

    suspend fun modifyUser(session: Session, username: String, limitGb: Double, expireIso: String, note: String = "", hwidLimit: Int? = null, groupIds: List<Int>? = null) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("data_limit", gbToBytes(limitGb)).put("expire", expireValue(expireIso))
        if (note.isNotBlank()) body.put("note", note)
        if (hwidLimit != null) body.put("hwid_limit", hwidLimit)
        if (groupIds != null) body.put("group_ids", org.json.JSONArray(groupIds))
        executeJson(requestBuilder(session, userUrl(session, username)).put(body.toString().toRequestBody(jsonType)).build())
    }

    suspend fun resetUsage(session: Session, username: String) = withContext(Dispatchers.IO) {
        executeJson(requestBuilder(session, "${userUrl(session, username)}/reset").post("".toRequestBody(jsonType)).build())
    }

    suspend fun setDisabled(session: Session, username: String, disabled: Boolean) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("disabled", disabled)
        executeJson(requestBuilder(session, "${userUrl(session, username)}/disabled").put(body.toString().toRequestBody(jsonType)).build())
    }

    suspend fun deleteUser(session: Session, username: String) = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, userUrl(session, username)).delete().build()
        client.newCall(request).execute().use { response -> if (!response.isSuccessful) error("Delete failed: ${response.code}") }
    }

    private fun executeJson(request: Request) {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val details = response.body?.string()?.take(250).orEmpty()
                error("Request failed: ${response.code} $details")
            }
        }
    }

    private fun parseUser(user: JSONObject): PanelUser {
        val groupIds = mutableListOf<Int>()
        val groupNames = mutableListOf<String>()
        if (!user.isNull("group_ids")) {
            val arr = user.optJSONArray("group_ids")
            if (arr != null) for (i in 0 until arr.length()) groupIds.add(arr.optInt(i))
        }
        if (!user.isNull("group_names")) {
            val arr = user.optJSONArray("group_names")
            if (arr != null) for (i in 0 until arr.length()) groupNames.add(arr.optString(i))
        }
        // fallback: groups array of objects
        if (groupIds.isEmpty() && !user.isNull("groups")) {
            val arr = user.optJSONArray("groups")
            if (arr != null) for (i in 0 until arr.length()) {
                val g = arr.optJSONObject(i)
                if (g != null) {
                    groupIds.add(g.optInt("id"))
                    groupNames.add(g.optString("name"))
                }
            }
        }

        // Parse online status - handle both boolean "online" and "online_at" string/ISO date
        var isOnline = user.optBoolean("online", false)
        var onlineAtStr: String? = null

        // Check online_at field - could be ISO string or timestamp
        if (!user.isNull("online_at")) {
            onlineAtStr = user.optString("online_at").takeIf { it != "null" && it.isNotBlank() }
            if (onlineAtStr != null) {
                // Try to parse as ISO date first, then as timestamp
                val now = System.currentTimeMillis()
                val onlineTime = try {
                    // Try ISO format: "2024-01-15T10:30:00Z"
                    java.time.Instant.parse(onlineAtStr.replace(" ", "T")).toEpochMilli()
                } catch (e: Exception) {
                    try {
                        // Try timestamp (seconds or milliseconds)
                        val ts = onlineAtStr.toLong()
                        if (ts < 1e12) ts * 1000 else ts // Convert seconds to ms if needed
                    } catch (e2: Exception) {
                        0L
                    }
                }
                if (onlineTime > 0 && now - onlineTime < 300_000) { // 5 minutes
                    isOnline = true
                }
            }
        }

        return PanelUser(
            id = user.optLong("id", 0L),
            username = user.getString("username"),
            status = user.optString("status", "unknown"),
            usedTraffic = user.optLong("used_traffic", 0),
            dataLimit = user.optLong("data_limit", 0),
            expire = if (user.isNull("expire")) null else user.optString("expire").takeIf { it != "null" && it != "0" },
            createdAt = if (user.isNull("created_at")) null else user.optString("created_at"),
            subUrl = user.optString("subscription_url", "").ifBlank { user.optString("sub_url", "") },
            onlineAt = onlineAtStr,
            isOnline = isOnline,
            note = if (user.isNull("note")) null else user.optString("note").takeIf { it.isNotBlank() && it != "null" },
            hwidLimit = if (user.isNull("hwid_limit")) null else user.optInt("hwid_limit").takeIf { it > 0 },
            groupIds = groupIds,
            groupNames = groupNames
        )
    }

    suspend fun groups(session: Session): List<Group> = withContext(Dispatchers.IO) {
        runCatching {
            val req = requestBuilder(session, "${session.baseUrl}/api/groups/simple?limit=200").get().build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@runCatching emptyList<Group>()
                val obj = JSONObject(res.body?.string() ?: "{}")
                val arr = obj.optJSONArray("groups") ?: obj.optJSONArray("items") ?: return@runCatching emptyList<Group>()
                List(arr.length()) { i ->
                    val g = arr.getJSONObject(i)
                    Group(id = g.optInt("id"), name = g.optString("name"))
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun onlineUserCount(session: Session): Int = withContext(Dispatchers.IO) {
        runCatching {
            val req = requestBuilder(session, "${session.baseUrl}/api/system/users").get().build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) JSONObject(res.body?.string() ?: "{}").optInt("online_users", 0) else 0
            }
        }.getOrDefault(0)
    }

    private fun gbToBytes(value: Double): Long = (value * 1024 * 1024 * 1024).toLong()
    private fun expireValue(date: String): Any = if (date.isBlank() || date == "null" || date == "0") 0 else "${date}T23:59:59Z"
}
