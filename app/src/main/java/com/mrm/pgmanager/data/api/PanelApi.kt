package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.utils.DateLogic
import com.mrm.pgmanager.data.model.BulkCreateResult
import com.mrm.pgmanager.data.model.CountMetric
import com.mrm.pgmanager.data.model.Group
import com.mrm.pgmanager.data.model.PanelNode
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.UserTemplateItem
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.StatsRange
import com.mrm.pgmanager.data.model.SystemStats
import com.mrm.pgmanager.data.model.TrafficPoint
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
        .retryOnConnectionFailure(true)
        .connectionPool(okhttp3.ConnectionPool(5, 30, TimeUnit.SECONDS))
        .addInterceptor { chain ->
            // برای متدهای امن (GET/PUT/DELETE) در صورتِ خطای شبکه‌ای، تا ۳ بار retry می‌کنیم.
            // POST retry نمی‌شود (برای جلوگیری از ساختِ کاربرِ تکراری).
            val request = chain.request()
            if (request.method == "POST") return@addInterceptor chain.proceed(request)
            var lastError: java.io.IOException? = null
            for (attempt in 0 until 3) {
                try {
                    return@addInterceptor chain.proceed(request)
                } catch (e: java.io.IOException) {
                    lastError = e
                    if (attempt < 2) Thread.sleep(400)
                }
            }
            throw lastError ?: java.io.IOException("retry exhausted")
        }
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun baseUrl(input: String): String {
        val trimmed = input.trim()
        require(trimmed.isNotBlank()) { "آدرس پنل را وارد کنید" }
        val prepared = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        val uri = runCatching { URI(prepared) }.getOrNull()
            ?: error("آدرس پنل معتبر نیست")
        require(!uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) { "آدرس پنل معتبر نیست" }
        // اپ با usesCleartextTraffic=false ساخته شده؛ http بدونِ پیامِ واضح در لایهٔ شبکه fail می‌شد.
        require(!uri.scheme.equals("http", ignoreCase = true)) {
            "اتصال http پشتیبانی نمی‌شود؛ لطفاً از https استفاده کنید"
        }
        // مسیر رو به‌طور پیش‌فرض حذف می‌کنیم (کاربر معمولاً آدرسِ داشبورد/کامل وارد می‌کند و این از 405 جلوگیری می‌کند).
        // فقط اگر آدرسِ کاملِ API (دارای /api) داده شده باشد، پیشوندِ قبل از /api را نگه می‌داریم (پشتیبانی از subpath).
        val rawPath = uri.rawPath.orEmpty()
        val basePath = when {
            rawPath.contains("/api/") -> rawPath.substring(0, rawPath.indexOf("/api/")).trimEnd('/')
            rawPath.endsWith("/api") -> rawPath.substring(0, rawPath.length - 4).trimEnd('/')
            else -> ""
        }
        return buildString {
            append(uri.scheme); append("://"); append(uri.host)
            if (uri.port != -1) append(":${uri.port}")
            if (basePath.isNotEmpty()) append(basePath)
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

    suspend fun systemStats(session: Session): SystemStats = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, "${session.baseUrl}/api/system").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("System stats failed: ${response.code}")
            val o = JSONObject(response.body?.string() ?: "{}")
            SystemStats(
                uptimeSeconds = o.optLong("uptime_seconds"), memTotal = o.optLong("mem_total"), memUsed = o.optLong("mem_used"),
                diskTotal = o.optLong("disk_total"), diskUsed = o.optLong("disk_used"), cpuCores = o.optInt("cpu_cores"), cpuUsage = o.optDouble("cpu_usage").toFloat(),
                totalUsers = o.optInt("total_user"), onlineUsers = o.optInt("online_users"), activeUsers = o.optInt("active_users"),
                expiredUsers = o.optInt("expired_users"), limitedUsers = o.optInt("limited_users"), disabledUsers = o.optInt("disabled_users"), onHoldUsers = o.optInt("on_hold_users"),
                incomingBandwidth = o.optLong("incoming_bandwidth"), outgoingBandwidth = o.optLong("outgoing_bandwidth")
            )
        }
    }

    /** null بودن مقدار هر node در API یعنی نود در دسترس نیست. */
    suspend fun nodeOnlineStates(session: Session): Map<Int, Boolean> = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, "${session.baseUrl}/api/nodes/realtime_stats").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Node stats failed: ${response.code}")
            val root = JSONObject(response.body?.string() ?: "{}")
            root.keys().asSequence().associate { key -> (key.toIntOrNull() ?: -1) to !root.isNull(key) }.filterKeys { it > 0 }
        }
    }

    /**
     * مصرفِ ترافیک همهٔ کاربران در یک بازهٔ زمانی.
     * پنل `period` را از میان minute/hour/day/month می‌پذیرد و `start`/`end` را
     * به‌صورت ISO-8601 آگاه از timezone. اگر `nodeId` بدهیم فقط همان نود لحاظ می‌شود.
     */
    suspend fun trafficUsage(
        session: Session,
        range: StatsRange = StatsRange.LAST_24H,
        nodeId: Int? = null
    ): List<TrafficPoint> = withContext(Dispatchers.IO) {
        val url = buildString {
            append(session.baseUrl); append("/api/users/usage")
            append("?period="); append(range.period)
            append("&start="); append(URLEncoder.encode(range.startIso(), "UTF-8"))
            if (nodeId != null) { append("&node_id="); append(nodeId) }
        }
        val request = requestBuilder(session, url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Traffic usage failed: ${response.code}")
            val root = JSONObject(response.body?.string() ?: "{}")
            val stats = root.optJSONObject("stats") ?: return@use emptyList()
            val totals = linkedMapOf<String, Long>()
            stats.keys().forEach { key ->
                val arr = stats.optJSONArray(key) ?: return@forEach
                for (i in 0 until arr.length()) { val p = arr.optJSONObject(i) ?: continue; val time = p.optString("period_start"); totals[time] = (totals[time] ?: 0L) + p.optLong("total_traffic") }
            }
            totals.entries.sortedBy { it.key }.map { TrafficPoint(it.key, it.value) }
        }
    }

    /**
     * نمودارِ تعدادِ کاربران بر اساس یک متریک (online / expired / limited).
     * اندپوینت: `GET /api/users/counts/{metric}` — پاسخ همان ساختارِ usage است
     * ولی به‌جای total_traffic فیلدِ count دارد.
     */
    suspend fun userCountMetric(
        session: Session,
        metric: CountMetric = CountMetric.ONLINE,
        range: StatsRange = StatsRange.LAST_24H
    ): List<TrafficPoint> = withContext(Dispatchers.IO) {
        val url = buildString {
            append(session.baseUrl); append("/api/users/counts/"); append(metric.apiName)
            append("?period="); append(range.period)
            append("&start="); append(URLEncoder.encode(range.startIso(), "UTF-8"))
        }
        val request = requestBuilder(session, url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("User count failed: ${response.code}")
            val root = JSONObject(response.body?.string() ?: "{}")
            val stats = root.optJSONObject("stats") ?: return@use emptyList()
            val totals = linkedMapOf<String, Long>()
            stats.keys().forEach { key ->
                val arr = stats.optJSONArray(key) ?: return@forEach
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    val time = p.optString("period_start")
                    // پنل بسته به متریک ممکن است count یا total را برگرداند.
                    val v = if (p.has("count")) p.optLong("count") else p.optLong("total")
                    totals[time] = maxOf(totals[time] ?: 0L, v)
                }
            }
            totals.entries.sortedBy { it.key }.map { TrafficPoint(it.key, it.value) }
        }
    }

    /** فهرست نودها برای فیلترِ نمودارها. */
    suspend fun nodes(session: Session): List<PanelNode> = withContext(Dispatchers.IO) {
        runCatching {
            val req = requestBuilder(session, "${session.baseUrl}/api/nodes").get().build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@runCatching emptyList<PanelNode>()
                val body = res.body?.string() ?: "{}"
                val arr = runCatching { JSONObject(body).optJSONArray("nodes") }.getOrNull()
                    ?: runCatching { org.json.JSONArray(body) }.getOrNull()
                    ?: return@runCatching emptyList<PanelNode>()
                List(arr.length()) { i ->
                    val n = arr.getJSONObject(i)
                    PanelNode(id = n.optInt("id"), name = n.optString("name", "Node #${n.optInt("id")}"))
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun users(session: Session): List<PanelUser> = withContext(Dispatchers.IO) {
        // صفحه‌بندی: تا زمانی که یک صفحه کامل (limitتایی) برنگردد ادامه می‌دهیم.
        // بدون load_sub (سنگین: پنل برای هر کاربر subscription URL می‌سازد)؛ لینک اشتراک فقط در صورت نیاز lazy واکشی می‌شود.
        val all = mutableListOf<PanelUser>()
        var offset = 0
        val limit = 1000
        while (true) {
            val request = requestBuilder(session, "${session.baseUrl}/api/users?offset=$offset&limit=$limit").get().build()
            val chunk = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Request failed: ${response.code}")
                val obj = JSONObject(response.body?.string() ?: error("Empty users response"))
                val arr = obj.getJSONArray("users")
                List(arr.length()) { i -> parseUser(arr.getJSONObject(i)) }
            }
            all.addAll(chunk)
            if (chunk.size < limit) break
            offset += limit
            // محافظ: اگر پنل total را برگرداند و به آن رسیدیم، متوقف شو (جلوگیری از حلقهٔ بی‌نهایت)
            if (offset > 1_000_000) break
        }
        // نام گروه‌ها در پاسخ لیست کاربران نیست (پنل group_names را exclude می‌کند)؛
        // پس با یک واکشی سبک از /api/groups/simple نگاشت id→name انجام می‌دهیم.
        val groupMap = runCatching { groups(session) }.getOrDefault(emptyList()).associate { it.id to it.name }
        if (groupMap.isNotEmpty()) {
            all.forEach { u ->
                if (u.groupNames.isEmpty() && u.groupIds.isNotEmpty()) {
                    u.groupNames = u.groupIds.mapNotNull { groupMap[it] }
                }
            }
        }
        all
    }

    /** واکشی تکی یک کاربر (با subscription_url) — برای دریافت لینک اشتراک فقط در صورت نیاز. */
    suspend fun user(session: Session, username: String): PanelUser = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, userUrl(session, username)).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("User fetch failed: ${response.code}")
            parseUser(JSONObject(response.body?.string() ?: error("Empty user response")))
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
        if (hwidLimit != null) body.put("hwid_limit", hwidLimit)  // 0 = نامحدود
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
                        if (ts < 1_000_000_000_000L) ts * 1000 else ts // Convert seconds to ms if needed
                    } catch (e2: Exception) {
                        0L
                    }
                }
                if (onlineTime > 0L && now - onlineTime < 300_000L) { // 5 minutes
                    isOnline = true
                }
            }
        }

        return PanelUser(
            id = user.optLong("id", 0L),
            username = user.getString("username"),
            status = user.optString("status", "unknown"),
            usedTraffic = user.optLong("used_traffic", 0L),
            dataLimit = user.optLong("data_limit", 0L),
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
        // ابتدا endpoint ساده؛ در صورتِ ناموفق‌بودن، fallback می‌زند.
        val simple: List<Group>? = runCatching {
            val req = requestBuilder(session, "${session.baseUrl}/api/groups/simple?limit=200").get().build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@runCatching null
                val obj = JSONObject(res.body?.string() ?: "{}")
                val arr = obj.optJSONArray("groups") ?: obj.optJSONArray("items") ?: return@runCatching null
                List(arr.length()) { i ->
                    val g = arr.getJSONObject(i)
                    Group(id = g.optInt("id"), name = g.optString("name"))
                }
            }
        }.getOrNull()
        if (simple != null) return@withContext simple
        // fallback: endpoint کامل
        runCatching {
            val req = requestBuilder(session, "${session.baseUrl}/api/groups").get().build()
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

    suspend fun userTemplates(session: Session): List<UserTemplateItem> = withContext(Dispatchers.IO) {
        // فقط endpoint کامل صدا زده می‌شود: فرم ویرایش به data_limit و expire_duration نیاز دارد
        // که در پاسخِ /simple وجود ندارد. (قبلاً هر دو صدا زده می‌شد و نتیجهٔ simple دور ریخته می‌شد.)
        val reqFull = requestBuilder(session, "${session.baseUrl}/api/user_templates").get().build()
        client.newCall(reqFull).execute().use { res ->
            if (!res.isSuccessful) error("بارگذاریِ تمپلت‌ها ناموفق بود: ${res.code}")
            val arr = org.json.JSONArray(res.body?.string() ?: "[]")
            List(arr.length()) { i ->
                val t = arr.getJSONObject(i)
                UserTemplateItem(
                    id = t.optInt("id"),
                    name = t.optString("name", "تمپلت #${t.optInt("id")}"),
                    dataLimit = if (t.has("data_limit") && !t.isNull("data_limit")) t.optLong("data_limit") else null,
                    expireDuration = if (t.has("expire_duration") && !t.isNull("expire_duration")) t.optLong("expire_duration") else null
                )
            }
        }
    }

    suspend fun createUserFromTemplate(session: Session, username: String, templateId: Int, note: String = "") = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("username", username)
            put("user_template_id", templateId)
            if (note.isNotBlank()) put("note", note)
        }
        executeJson(requestBuilder(session, "${session.baseUrl}/api/user/from_template").post(body.toString().toRequestBody(jsonType)).build())
    }

    /**
     * ساخت گروهیِ کاربران از روی تمپلت — **سمت سرور** (`POST /api/users/bulk/from_template`).
     * به‌جای N درخواستِ جداگانه، یک درخواست می‌فرستد؛ خیلی سریع‌تر و بدون
     * ریسکِ نیمه‌کاره ماندن.
     *
     * @param count تعداد کاربر (پنل حداکثر ۵۰۰ را می‌پذیرد)
     * @param sequential true → نام‌های ترتیبی با پیشوندِ [username]؛ false → نامِ تصادفی
     * @param startNumber شمارهٔ شروع در حالتِ ترتیبی
     * @return تعداد ساخته‌شده به‌همراه لینک‌های اشتراک
     */
    suspend fun bulkCreateUsersFromTemplate(
        session: Session,
        templateId: Int,
        count: Int,
        sequential: Boolean,
        username: String? = null,
        startNumber: Int? = null,
        note: String = ""
    ): BulkCreateResult = withContext(Dispatchers.IO) {
        require(count in 1..500) { "تعداد باید بین ۱ تا ۵۰۰ باشد" }
        val body = JSONObject().apply {
            put("user_template_id", templateId)
            put("count", count)
            put("strategy", if (sequential) "sequence" else "random")
            // قرارداد پنل: در حالتِ random باید username تهی باشد.
            if (sequential) {
                put("username", username ?: error("نامِ پایه برای حالتِ ترتیبی لازم است"))
                if (startNumber != null) put("start_number", startNumber)
            } else {
                put("username", JSONObject.NULL)
            }
            if (note.isNotBlank()) put("note", note)
        }
        val request = requestBuilder(session, "${session.baseUrl}/api/users/bulk/from_template")
            .post(body.toString().toRequestBody(jsonType)).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val details = response.body?.string()?.take(250).orEmpty()
                error("Bulk create failed: ${response.code} $details")
            }
            val o = JSONObject(response.body?.string() ?: "{}")
            val urls = o.optJSONArray("subscription_urls")
            BulkCreateResult(
                created = o.optInt("created", urls?.length() ?: 0),
                subscriptionUrls = if (urls == null) emptyList() else List(urls.length()) { urls.optString(it) }
            )
        }
    }

    suspend fun modifyUserFromTemplate(session: Session, username: String, templateId: Int, note: String = "") = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("user_template_id", templateId)
            if (note.isNotBlank()) put("note", note)
        }
        executeJson(requestBuilder(session, "${session.baseUrl}/api/user/from_template/${URLEncoder.encode(username, "UTF-8")}").put(body.toString().toRequestBody(jsonType)).build())
    }

    suspend fun bulkDeleteUsers(session: Session, userIds: Set<Long>) = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { put("ids", org.json.JSONArray(userIds)) }
        executeJson(requestBuilder(session, "${session.baseUrl}/api/users/bulk/delete").post(body.toString().toRequestBody(jsonType)).build())
    }

    suspend fun bulkResetUsersUsage(session: Session, userIds: Set<Long>) = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { put("ids", org.json.JSONArray(userIds)) }
        executeJson(requestBuilder(session, "${session.baseUrl}/api/users/bulk/reset").post(body.toString().toRequestBody(jsonType)).build())
    }

    suspend fun bulkDisableUsers(session: Session, userIds: Set<Long>) = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { put("ids", org.json.JSONArray(userIds)) }
        executeJson(requestBuilder(session, "${session.baseUrl}/api/users/bulk/disable").post(body.toString().toRequestBody(jsonType)).build())
    }

    suspend fun bulkEnableUsers(session: Session, userIds: Set<Long>) = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { put("ids", org.json.JSONArray(userIds)) }
        executeJson(requestBuilder(session, "${session.baseUrl}/api/users/bulk/enable").post(body.toString().toRequestBody(jsonType)).build())
    }

    suspend fun bulkApplyTemplate(session: Session, userIds: Set<Long>, templateId: Int, note: String = "") = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("ids", org.json.JSONArray(userIds))
            put("user_template_id", templateId)
            if (note.isNotBlank()) put("note", note)
        }
        executeJson(requestBuilder(session, "${session.baseUrl}/api/users/bulk/apply_template").post(body.toString().toRequestBody(jsonType)).build())
    }

    private fun gbToBytes(value: Double): Long = (value * 1024 * 1024 * 1024).toLong()
    // تاریخ انقضا را به‌صورت «اکنون + N روز» می‌فرستیم (سازگار با رفتار پنل و بدون خطای گردکردن).
    // تاریخ امروز/گذشته → 0 (نامحدود) تا کاربر ناخواسته «منقضی» ساخته نشود.
    private fun expireValue(date: String): Any = DateLogic.expireValue(date)
}
