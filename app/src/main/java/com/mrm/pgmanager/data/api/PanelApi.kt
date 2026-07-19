package com.mrm.pgmanager.data.api

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

    suspend fun createUser(session: Session, username: String, limitGb: Double, expireIso: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("username", username).put("status", "active").put("data_limit", gbToBytes(limitGb)).put("expire", expireValue(expireIso))
        executeJson(requestBuilder(session, "${session.baseUrl}/api/user").post(body.toString().toRequestBody(jsonType)).build())
    }

    suspend fun modifyUser(session: Session, username: String, limitGb: Double, expireIso: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("data_limit", gbToBytes(limitGb)).put("expire", expireValue(expireIso))
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

    private fun parseUser(user: JSONObject) = PanelUser(
        id = user.optLong("id", 0L),
        username = user.getString("username"),
        status = user.optString("status", "unknown"),
        usedTraffic = user.optLong("used_traffic", 0),
        dataLimit = user.optLong("data_limit", 0),
        expire = if (user.isNull("expire")) null else user.optString("expire").takeIf { it != "null" && it != "0" },
        createdAt = if (user.isNull("created_at")) null else user.optString("created_at"),
        subUrl = user.optString("subscription_url", "").ifBlank { user.optString("sub_url", "") },
        onlineAt = if (user.isNull("online_at")) null else user.optString("online_at").takeIf { it != "null" },
        isOnline = user.optBoolean("online", false) || (user.optLong("online_at", 0L) > System.currentTimeMillis() / 1000 - 300)
    )

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
