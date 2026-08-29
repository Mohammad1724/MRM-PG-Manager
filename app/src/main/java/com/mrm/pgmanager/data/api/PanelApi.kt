package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.utils.DateLogic
import com.mrm.pgmanager.data.model.BulkCreateResult
import com.mrm.pgmanager.data.model.CountMetric
import com.mrm.pgmanager.data.model.Group
import com.mrm.pgmanager.data.model.GroupDetail
import com.mrm.pgmanager.data.model.PanelAdmin
import com.mrm.pgmanager.data.model.NextPlan
import com.mrm.pgmanager.data.model.NodeRealtime
import com.mrm.pgmanager.data.model.PanelNode
import com.mrm.pgmanager.data.model.UserDevice
import com.mrm.pgmanager.data.model.PanelUser
import com.mrm.pgmanager.data.model.UserQuery
import com.mrm.pgmanager.data.model.UsersPage
import com.mrm.pgmanager.data.model.UserTemplateItem
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.model.StatsRange
import com.mrm.pgmanager.data.model.SystemStats
import com.mrm.pgmanager.data.model.TemplateOptions
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
        require(trimmed.isNotBlank()) { "Panel address is required" }
        val prepared = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        val uri = runCatching { URI(prepared) }.getOrNull()
            ?: error("Invalid URL")
        require(!uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) { "Invalid URL" }
        // اپ با usesCleartextTraffic=false ساخته شده؛ http بدونِ پیامِ واضح در لایهٔ شبکه fail می‌شد.
        require(!uri.scheme.equals("http", ignoreCase = true)) {
            "Cleartext http is not supported, use https"
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
     * مصرفِ ترافیکِ **یک کاربرِ مشخص** در یک بازهٔ زمانی.
     * اندپوینت: `GET /api/user/{username}/usage` (پیشوندِ روتر مفرد است).
     * پاسخ همان ساختارِ usageِ گروهی است: `{stats: {user_id: [{period_start, total_traffic}]}}`
     * ولی چون فقط یک کاربر است، معمولاً یک کلید بیشتر ندارد.
     *
     * برخلافِ [trafficUsage] اینجا کلیدها را جمع نمی‌کنیم بلکه همه را در یک سری ادغام
     * می‌کنیم تا اگر پنل به تفکیکِ نود پاسخ داد هم درست کار کند.
     */
    suspend fun userTrafficUsage(
        session: Session,
        username: String,
        range: StatsRange = StatsRange.LAST_7D
    ): List<TrafficPoint> = withContext(Dispatchers.IO) {
        val url = buildString {
            append(userUrl(session, username)); append("/usage")
            append("?period="); append(range.period)
            append("&start="); append(URLEncoder.encode(range.startIso(), "UTF-8"))
        }
        val request = requestBuilder(session, url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("User usage failed: ${response.code}")
            val root = JSONObject(response.body?.string() ?: "{}")
            val stats = root.optJSONObject("stats") ?: return@use emptyList()
            val totals = linkedMapOf<String, Long>()
            stats.keys().forEach { key ->
                val arr = stats.optJSONArray(key) ?: return@forEach
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    val time = p.optString("period_start")
                    totals[time] = (totals[time] ?: 0L) + p.optLong("total_traffic")
                }
            }
            totals.entries.sortedBy { it.key }.map { TrafficPoint(it.key, it.value) }
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
                    PanelNode(
                        id = n.optInt("id"),
                        name = n.optString("name", "Node #${n.optInt("id")}"),
                        address = n.optString("address", ""),
                        status = n.optString("status", ""),
                        message = if (n.isNull("message")) null else n.optString("message").takeIf { it.isNotBlank() },
                        xrayVersion = if (n.isNull("xray_version")) null else n.optString("xray_version").takeIf { it.isNotBlank() },
                        nodeVersion = if (n.isNull("node_version")) null else n.optString("node_version").takeIf { it.isNotBlank() },
                        uplink = n.optLong("uplink", 0L),
                        downlink = n.optLong("downlink", 0L)
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * آمارِ لحظه‌ایِ نودها — `GET /api/nodes/realtime_stats`.
     * کلیدِ null یعنی نود در دسترس نیست (همان چیزی که [nodeOnlineStates] هم
     * از رویش تصمیم می‌گیرد).
     */
    suspend fun nodeRealtimeStats(session: Session): Map<Int, NodeRealtime> = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, "${session.baseUrl}/api/nodes/realtime_stats").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Node stats failed: ${response.code}")
            val root = JSONObject(response.body?.string() ?: "{}")
            buildMap {
                root.keys().forEach { key ->
                    val id = key.toIntOrNull() ?: return@forEach
                    val o = root.optJSONObject(key) ?: return@forEach
                    put(
                        id,
                        NodeRealtime(
                            memTotal = o.optLong("mem_total"),
                            memUsed = o.optLong("mem_used"),
                            cpuCores = o.optInt("cpu_cores"),
                            cpuUsage = o.optDouble("cpu_usage", 0.0).toFloat(),
                            incomingSpeed = o.optLong("incoming_bandwidth_speed"),
                            outgoingSpeed = o.optLong("outgoing_bandwidth_speed"),
                            uptimeSeconds = o.optLong("uptime")
                        )
                    )
                }
            }
        }
    }

    /** تلاشِ دوبارهٔ اتصال به یک نود — `POST /api/node/{id}/reconnect`. */
    suspend fun reconnectNode(session: Session, nodeId: Int) = withContext(Dispatchers.IO) {
        executeJson(
            requestBuilder(session, "${session.baseUrl}/api/node/$nodeId/reconnect")
                .post("".toRequestBody(jsonType)).build()
        )
    }

    /** دستگاه‌های ثبت‌شدهٔ کاربر — `GET /api/user/{user_id}/hwids`. */
    suspend fun userDevices(session: Session, userId: Long): List<UserDevice> = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, "${session.baseUrl}/api/user/$userId/hwids").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HWID list failed: ${response.code}")
            val root = JSONObject(response.body?.string() ?: "{}")
            val arr = root.optJSONArray("hwids") ?: return@use emptyList()
            List(arr.length()) { i ->
                val d = arr.getJSONObject(i)
                UserDevice(
                    id = d.optInt("id"),
                    hwid = d.optString("hwid"),
                    deviceOs = d.optString("device_os").takeIf { it.isNotBlank() && it != "null" },
                    osVersion = d.optString("os_version").takeIf { it.isNotBlank() && it != "null" },
                    deviceModel = d.optString("device_model").takeIf { it.isNotBlank() && it != "null" },
                    createdAt = d.optString("created_at").takeIf { it.isNotBlank() && it != "null" },
                    lastUsedAt = d.optString("last_used_at").takeIf { it.isNotBlank() && it != "null" }
                )
            }
        }
    }

    /** حذفِ یک دستگاه — `DELETE /api/user/{user_id}/hwids/{hwid}`. */
    suspend fun deleteUserDevice(session: Session, userId: Long, hwid: String) = withContext(Dispatchers.IO) {
        executeJson(
            requestBuilder(
                session,
                "${session.baseUrl}/api/user/$userId/hwids/${URLEncoder.encode(hwid, "UTF-8")}"
            ).delete().build()
        )
    }

    /** پاک‌کردنِ همهٔ دستگاه‌ها — `POST /api/user/{user_id}/hwids/reset`. */
    suspend fun resetUserDevices(session: Session, userId: Long) = withContext(Dispatchers.IO) {
        executeJson(
            requestBuilder(session, "${session.baseUrl}/api/user/$userId/hwids/reset")
                .post("".toRequestBody(jsonType)).build()
        )
    }

    /** فعال‌کردنِ فوریِ پلنِ بعدی — `POST /api/user/{username}/active_next`. */
    suspend fun activateNextPlan(session: Session, username: String) = withContext(Dispatchers.IO) {
        executeJson(
            requestBuilder(session, "${userUrl(session, username)}/active_next")
                .post("".toRequestBody(jsonType)).build()
        )
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

    /**
     * یک صفحه از فهرستِ کاربران با فیلترِ سمتِ سرور — `GET /api/users`.
     *
     * چرا مهم است: تا امروز اپ **همهٔ** کاربران را می‌گرفت و بعد در گوشی
     * جست‌وجو/فیلتر/مرتب می‌کرد. روی پنلی با چند هزار کاربر یعنی چند مگابایت
     * دانلود در هر رفرش و کندیِ محسوس. پنل خودش `search`, `status`, `group`,
     * `sort` و صفحه‌بندی دارد؛ حالا از همان‌ها استفاده می‌شود و فقط همان چند ده
     * کاربری که روی صفحه دیده می‌شوند از شبکه می‌آیند.
     *
     * نام‌ها دقیقاً مطابق `UserListQuery` پنل‌اند: `group` نامِ مستعارِ
     * `group_ids` است و `sort` با پیشوندِ `-` نزولی می‌شود.
     */
    suspend fun usersPage(session: Session, query: UserQuery): UsersPage = withContext(Dispatchers.IO) {
        val url = buildString {
            append(session.baseUrl); append("/api/users")
            append("?offset="); append(query.offset)
            append("&limit="); append(query.limit)
            query.search?.takeIf { it.isNotBlank() }?.let {
                append("&search="); append(URLEncoder.encode(it, "UTF-8"))
            }
            query.status?.let { append("&status="); append(it) }
            query.groupId?.let { append("&group="); append(it) }
            query.sort?.let { append("&sort="); append(URLEncoder.encode(it, "UTF-8")) }
        }
        val request = requestBuilder(session, url).get().build()
        val page = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Request failed: ${response.code}")
            val obj = JSONObject(response.body?.string() ?: error("Empty users response"))
            val arr = obj.getJSONArray("users")
            UsersPage(List(arr.length()) { i -> parseUser(arr.getJSONObject(i)) }, obj.optInt("total", arr.length()))
        }
        attachGroupNames(session, page.users)
        page
    }

    /**
     * پاسخِ فهرستِ کاربران `group_names` ندارد (پنل عمداً حذفش می‌کند)، پس نگاشتِ
     * id→name را از یک واکشیِ سبک می‌گیریم و روی کاربرها می‌نشانیم.
     */
    private suspend fun attachGroupNames(session: Session, users: List<PanelUser>) {
        if (users.none { it.groupNames.isEmpty() && it.groupIds.isNotEmpty() }) return
        val groupMap = runCatching { groups(session) }.getOrDefault(emptyList()).associate { it.id to it.name }
        if (groupMap.isEmpty()) return
        users.forEach { u ->
            if (u.groupNames.isEmpty() && u.groupIds.isNotEmpty()) {
                u.groupNames = u.groupIds.mapNotNull { groupMap[it] }
            }
        }
    }

    /**
     * افزودن یا برداشتنِ گروه برای چند کاربر یک‌جا —
     * `POST /api/groups/bulk/add` و `POST /api/groups/bulk/remove`.
     *
     * هشدارِ مهم: اگر `users` خالی بماند پنل عملیات را روی **همهٔ کاربران**
     * اجرا می‌کند؛ برای همین اینجا فهرستِ خالی اصلاً درخواست نمی‌فرستد.
     */
    suspend fun bulkGroupMembership(
        session: Session,
        groupIds: Set<Int>,
        userIds: Set<Long>,
        add: Boolean
    ) = withContext(Dispatchers.IO) {
        if (groupIds.isEmpty() || userIds.isEmpty()) return@withContext
        val body = JSONObject().apply {
            put("group_ids", org.json.JSONArray(groupIds.toList()))
            put("users", org.json.JSONArray(userIds.toList()))
        }
        val path = if (add) "add" else "remove"
        executeJson(
            requestBuilder(session, "${session.baseUrl}/api/groups/bulk/$path")
                .post(body.toString().toRequestBody(jsonType)).build()
        )
    }

    /** واکشی تکی یک کاربر (با subscription_url) — برای دریافت لینک اشتراک فقط در صورت نیاز. */
    suspend fun user(session: Session, username: String): PanelUser = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, userUrl(session, username)).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("User fetch failed: ${response.code}")
            parseUser(JSONObject(response.body?.string() ?: error("Empty user response")))
        }
    }

    suspend fun createUser(session: Session, username: String, limitGb: Double, expireIso: String, note: String = "", hwidLimit: Int? = null, groupIds: List<Int> = emptyList(), nextPlan: NextPlan? = null, resetStrategy: String? = null, autoDeleteDays: Int? = null) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("username", username).put("status", "active").put("data_limit", gbToBytes(limitGb)).put("expire", expireValue(expireIso))
        if (note.isNotBlank()) body.put("note", note)
        if (hwidLimit != null && hwidLimit > 0) body.put("hwid_limit", hwidLimit)
        if (groupIds.isNotEmpty()) body.put("group_ids", org.json.JSONArray(groupIds))
        nextPlanJson(nextPlan)?.let { body.put("next_plan", it) }
        resetStrategy?.let { body.put("data_limit_reset_strategy", it) }
        autoDeleteDays?.let { body.put("auto_delete_in_days", it) }
        executeJson(requestBuilder(session, "${session.baseUrl}/api/user").post(body.toString().toRequestBody(jsonType)).build())
    }

    suspend fun modifyUser(session: Session, username: String, limitGb: Double, expireIso: String, note: String = "", hwidLimit: Int? = null, groupIds: List<Int>? = null, nextPlan: NextPlan? = null, resetStrategy: String? = null, autoDeleteDays: Int? = null) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("data_limit", gbToBytes(limitGb)).put("expire", expireValue(expireIso))
        if (note.isNotBlank()) body.put("note", note)
        if (hwidLimit != null) body.put("hwid_limit", hwidLimit)  // 0 = نامحدود
        if (groupIds != null) body.put("group_ids", org.json.JSONArray(groupIds))
        // null یعنی «دست نزن»؛ قالبِ خالی یعنی «پاکش کن».
        if (nextPlan != null) body.put("next_plan", nextPlanJson(nextPlan) ?: JSONObject.NULL)
        resetStrategy?.let { body.put("data_limit_reset_strategy", it) }
        // null یعنی «دست نزن»، عددِ صفر یعنی «حذفِ خودکار را بردار».
        autoDeleteDays?.let { body.put("auto_delete_in_days", if (it > 0) it else JSONObject.NULL) }
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

    /** بدنهٔ `next_plan`؛ اگر قالب و حجم و مدت هر سه خالی باشند null برمی‌گرداند. */
    private fun nextPlanJson(plan: NextPlan?): JSONObject? {
        if (plan == null) return null
        if (plan.templateId == null && plan.dataLimit == null && plan.expireSeconds == null) return null
        return JSONObject().apply {
            plan.templateId?.let { put("user_template_id", it) }
            plan.dataLimit?.let { put("data_limit", it) }
            plan.expireSeconds?.let { put("expire", it) }
            put("add_remaining_traffic", plan.addRemainingTraffic)
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

        // Check online_at field - could be ISO string, naive datetime, or timestamp
        if (!user.isNull("online_at")) {
            onlineAtStr = user.optString("online_at").takeIf { it != "null" && it.isNotBlank() }
            if (onlineAtStr != null) {
                val now = System.currentTimeMillis()
                val onlineTime = try {
                    // Try ISO instant first: "2024-01-15T10:30:00Z"
                    java.time.Instant.parse(onlineAtStr.replace(" ", "T")).toEpochMilli()
                } catch (e: Exception) {
                    try {
                        // Try LocalDateTime without Z (panel may return naive): "2024-01-15T10:30:00"
                        java.time.LocalDateTime.parse(onlineAtStr.replace(" ", "T")).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } catch (e2: Exception) {
                        try {
                            // Try timestamp (seconds or milliseconds)
                            val ts = onlineAtStr.toLong()
                            if (ts < 1_000_000_000_000L) ts * 1000 else ts
                        } catch (e3: Exception) {
                            0L
                        }
                    }
                }
                if (onlineTime > 0L && now - onlineTime < 300_000L) {
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
            groupNames = groupNames,
            lifetimeUsedTraffic = user.optLong("lifetime_used_traffic", 0L),
            nextPlan = user.optJSONObject("next_plan")?.let { np ->
                NextPlan(
                    templateId = if (np.isNull("user_template_id")) null else np.optInt("user_template_id"),
                    dataLimit = if (np.isNull("data_limit")) null else np.optLong("data_limit"),
                    expireSeconds = if (np.isNull("expire")) null else np.optLong("expire"),
                    addRemainingTraffic = np.optBoolean("add_remaining_traffic", false)
                )
            },
            // `admin` یک آبجکتِ AdminBase است؛ فقط نامش را نگه می‌داریم.
            ownerAdmin = user.optJSONObject("admin")?.optString("username")
                ?.takeIf { it.isNotBlank() && it != "null" }
        )
    }

    /**
     * فهرستِ ادمین‌های پنل — `GET /api/admins`.
     *
     * فقط ادمینی که مجوزِ `admins:read` دارد می‌تواند بگیرد؛ برای بقیه پنل ۴۰۳
     * می‌دهد و صداکننده باید بخش را پنهان کند (نه اینکه خطا نشان بدهد).
     */
    suspend fun admins(session: Session): List<PanelAdmin> = withContext(Dispatchers.IO) {
        val all = mutableListOf<PanelAdmin>()
        var offset = 0
        val limit = 100
        while (true) {
            val request = requestBuilder(session, "${session.baseUrl}/api/admins?limit=$limit&offset=$offset").get().build()
            val chunk = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Admins failed: ${response.code}")
                val body = response.body?.string().orEmpty()
                val arr = runCatching { JSONObject(body).optJSONArray("admins") }.getOrNull()
                    ?: runCatching { org.json.JSONArray(body) }.getOrNull()
                    ?: return@use emptyList<PanelAdmin>()
                List(arr.length()) { i ->
                    val a = arr.getJSONObject(i)
                    PanelAdmin(
                        id = a.optInt("id"),
                        username = a.optString("username"),
                        totalUsers = a.optInt("total_users", 0),
                        usedTraffic = a.optLong("used_traffic", 0L),
                        dataLimit = if (a.isNull("data_limit")) null else a.optLong("data_limit").takeIf { it > 0L },
                        status = a.optString("status", "active"),
                        isOwner = a.optJSONObject("role")?.optBoolean("is_owner", false) ?: false
                    )
                }
            }
            all.addAll(chunk)
            if (chunk.size < limit) break
            offset += limit
            if (offset > 10_000) break
        }
        all
    }

    /**
     * باطل‌کردنِ لینکِ اشتراکِ کاربر — `POST /api/user/{username}/revoke_sub`.
     *
     * توکنِ اشتراک عوض می‌شود، پس لینکِ قبلی (و هر کسی که آن را دارد) از کار
     * می‌افتد و کاربر باید لینکِ تازه را بگیرد. تنها راهِ درستِ واکنش به لو رفتنِ
     * لینک است.
     */
    suspend fun revokeSubscription(session: Session, username: String): PanelUser = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, "${userUrl(session, username)}/revoke_sub")
            .post("".toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Revoke failed: ${response.code}")
            parseUser(JSONObject(response.body?.string() ?: error("Empty revoke response")))
        }
    }

    suspend fun groups(session: Session): List<Group> = withContext(Dispatchers.IO) {
        // صفحه‌بندی: پنل‌های بزرگ ممکن است بیش از ۲۰۰ گروه داشته باشند
        val all = mutableListOf<Group>()
        var offset = 0
        val limit = 200
        while (true) {
            val chunk: List<Group>? = runCatching {
                val req = requestBuilder(session, "${session.baseUrl}/api/groups/simple?limit=$limit&offset=$offset").get().build()
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
            if (chunk == null) break
            all.addAll(chunk)
            if (chunk.size < limit) break
            offset += limit
            if (offset > 10_000) break
        }
        if (all.isNotEmpty()) return@withContext all
        // fallback: endpoint کامل با صفحه‌بندی
        all.clear()
        offset = 0
        while (true) {
            val chunk = runCatching {
                val req = requestBuilder(session, "${session.baseUrl}/api/groups?limit=$limit&offset=$offset").get().build()
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
            all.addAll(chunk)
            if (chunk.size < limit) break
            offset += limit
            if (offset > 10_000) break
        }
        all
    }

    // ─────────────────────────────────────────────────────────────
    //  گروه‌ها — CRUD کامل
    //
    //  ⚠️ نکتهٔ مسیرها: پیشوندِ روترِ پنل «/api/group» (مفرد) است ولی
    //  فهرست‌گرفتن روی «s» سوار می‌شود. یعنی:
    //    ساخت    POST   /api/group
    //    فهرست   GET    /api/groups
    //    جزئیات  GET    /api/group/{id}
    //    ویرایش  PUT    /api/group/{id}
    //    حذف     DELETE /api/group/{id}
    //  اشتباه‌گرفتنِ مفرد/جمع باعث 404 می‌شود.
    // ─────────────────────────────────────────────────────────────

    /** فهرستِ کاملِ گروه‌ها همراه با inbound tags و تعداد کاربر — با صفحه‌بندی. */
    suspend fun groupsDetailed(session: Session): List<GroupDetail> = withContext(Dispatchers.IO) {
        val all = mutableListOf<GroupDetail>()
        var offset = 0
        val limit = 200
        while (true) {
            val req = requestBuilder(session, "${session.baseUrl}/api/groups?limit=$limit&offset=$offset").get().build()
            val chunk = client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) error("Load groups failed: ${res.code}")
                val obj = JSONObject(res.body?.string() ?: "{}")
                val arr = obj.optJSONArray("groups") ?: obj.optJSONArray("items") ?: org.json.JSONArray()
                List(arr.length()) { i -> parseGroupDetail(arr.getJSONObject(i)) }
            }
            all.addAll(chunk)
            if (chunk.size < limit) break
            offset += limit
            if (offset > 10_000) break
        }
        all
    }

    /** تگ‌های inbound موجود در پنل — برای انتخاب در فرمِ گروه. */
    suspend fun inboundTags(session: Session): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val req = requestBuilder(session, "${session.baseUrl}/api/inbounds").get().build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@runCatching emptyList<String>()
                val raw = res.body?.string().orEmpty()
                // پنل list[str] برمی‌گرداند؛ ولی برای مقاومت، حالتِ آبجکت هم پوشش داده شده.
                val trimmed = raw.trim()
                val arr = if (trimmed.startsWith("[")) org.json.JSONArray(trimmed)
                else JSONObject(trimmed.ifBlank { "{}" }).optJSONArray("inbounds") ?: org.json.JSONArray()
                (0 until arr.length()).mapNotNull { i ->
                    when (val item = arr.opt(i)) {
                        is String -> item.takeIf { it.isNotBlank() }
                        is JSONObject -> item.optString("tag").takeIf { it.isNotBlank() }
                        else -> null
                    }
                }.distinct()
            }
        }.getOrDefault(emptyList())
    }

    /** ساخت گروه. پنل حداقل یک inbound tag می‌خواهد. */
    suspend fun createGroup(session: Session, name: String, inboundTags: List<String>, isDisabled: Boolean = false) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", name.trim())
            .put("inbound_tags", org.json.JSONArray(inboundTags))
            .put("is_disabled", isDisabled)
        executeJson(requestBuilder(session, "${session.baseUrl}/api/group").post(body.toString().toRequestBody(jsonType)).build())
    }

    /** ویرایش گروه (PUT روی مسیرِ مفرد + شناسه). */
    suspend fun modifyGroup(session: Session, groupId: Int, name: String, inboundTags: List<String>, isDisabled: Boolean) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", name.trim())
            .put("inbound_tags", org.json.JSONArray(inboundTags))
            .put("is_disabled", isDisabled)
        executeJson(requestBuilder(session, "${session.baseUrl}/api/group/$groupId").put(body.toString().toRequestBody(jsonType)).build())
    }

    /** حذف گروه. پنل 204 برمی‌گرداند (بدنهٔ خالی). */
    suspend fun deleteGroup(session: Session, groupId: Int) = withContext(Dispatchers.IO) {
        val req = requestBuilder(session, "${session.baseUrl}/api/group/$groupId").delete().build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) {
                val details = res.body?.string()?.take(250).orEmpty()
                error("Delete group failed: ${res.code} $details")
            }
        }
    }

    /** تبدیل JSON گروه به مدل. نامِ کلیدها مطابق GroupResponse پنل است. */
    internal fun parseGroupDetail(g: JSONObject): GroupDetail {
        val tags = mutableListOf<String>()
        if (!g.isNull("inbound_tags")) {
            val arr = g.optJSONArray("inbound_tags")
            if (arr != null) for (i in 0 until arr.length()) {
                arr.optString(i).takeIf { it.isNotBlank() }?.let { tags.add(it) }
            }
        }
        return GroupDetail(
            id = g.optInt("id"),
            name = g.optString("name"),
            inboundTags = tags,
            isDisabled = g.optBoolean("is_disabled", false),
            totalUsers = g.optInt("total_users", 0)
        )
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
            if (!res.isSuccessful) error("Templates request failed: ${res.code}")
            // ⚠️ برخلافِ /api/groups که آبجکتِ {groups,total} می‌دهد،
            // این endpoint آرایهٔ خام برمی‌گرداند.
            val arr = org.json.JSONArray(res.body?.string() ?: "[]")
            List(arr.length()) { i -> parseUserTemplate(arr.getJSONObject(i)) }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  تمپلت‌های کاربر — CRUD کامل
    //
    //  ⚠️ همان تلهٔ مفرد/جمعِ گروه‌ها، ولی با underscore:
    //    ساخت    POST   /api/user_template      (۲۰۱)
    //    فهرست   GET    /api/user_templates     ← آرایهٔ خام
    //    جزئیات  GET    /api/user_template/{id}
    //    ویرایش  PUT    /api/user_template/{id}
    //    حذف     DELETE /api/user_template/{id} (۲۰۴)
    // ─────────────────────────────────────────────────────────────

    /** تبدیل JSON تمپلت به مدل. کلیدها مطابقِ UserTemplateResponse پنل. */
    internal fun parseUserTemplate(t: JSONObject): UserTemplateItem {
        val gids = mutableListOf<Int>()
        if (!t.isNull("group_ids")) {
            t.optJSONArray("group_ids")?.let { a -> for (i in 0 until a.length()) gids.add(a.optInt(i)) }
        }
        val method = if (t.isNull("extra_settings")) null
        else t.optJSONObject("extra_settings")?.optString("method")?.takeIf { it.isNotBlank() }
        return UserTemplateItem(
            id = t.optInt("id"),
            name = t.optString("name", "Template #${t.optInt("id")}"),
            dataLimit = if (t.isNull("data_limit")) null else t.optLong("data_limit"),
            expireDuration = if (t.isNull("expire_duration")) null else t.optLong("expire_duration"),
            hwidLimit = if (t.isNull("hwid_limit")) null else t.optInt("hwid_limit"),
            usernamePrefix = if (t.isNull("username_prefix")) null else t.optString("username_prefix").takeIf { it.isNotBlank() },
            usernameSuffix = if (t.isNull("username_suffix")) null else t.optString("username_suffix").takeIf { it.isNotBlank() },
            groupIds = gids,
            status = if (t.isNull("status")) null else t.optString("status").takeIf { it.isNotBlank() },
            dataLimitResetStrategy = t.optString("data_limit_reset_strategy", TemplateOptions.RESET_NO_RESET)
                .ifBlank { TemplateOptions.RESET_NO_RESET },
            onHoldTimeout = if (t.isNull("on_hold_timeout")) null else t.optLong("on_hold_timeout"),
            resetUsages = if (t.isNull("reset_usages")) null else t.optBoolean("reset_usages"),
            isDisabled = if (t.isNull("is_disabled")) null else t.optBoolean("is_disabled"),
            ssMethod = method
        )
    }

    /**
     * بدنهٔ مشترکِ ساخت و ویرایش.
     * فیلدهای اختیاریِ خالی **حذف** می‌شوند نه اینکه null فرستاده شوند —
     * پنل برای غایب‌بودن مقدارِ پیش‌فرض می‌گذارد.
     */
    private fun templateBody(t: UserTemplateItem, includeGroups: Boolean = true): JSONObject {
        val b = JSONObject()
        b.put("name", t.name.trim())
        if (includeGroups) b.put("group_ids", org.json.JSONArray(t.groupIds))
        b.put("data_limit", t.dataLimit ?: JSONObject.NULL)
        b.put("expire_duration", t.expireDuration ?: JSONObject.NULL)
        b.put("hwid_limit", t.hwidLimit ?: JSONObject.NULL)
        b.put("username_prefix", t.usernamePrefix?.trim()?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
        b.put("username_suffix", t.usernameSuffix?.trim()?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
        b.put("data_limit_reset_strategy", t.dataLimitResetStrategy)
        t.status?.let { b.put("status", it) }
        // on_hold_timeout فقط وقتی معنی دارد که وضعیت on_hold باشد.
        if (t.status == TemplateOptions.STATUS_ON_HOLD) {
            b.put("on_hold_timeout", t.onHoldTimeout ?: JSONObject.NULL)
        }
        t.resetUsages?.let { b.put("reset_usages", it) }
        t.isDisabled?.let { b.put("is_disabled", it) }
        t.ssMethod?.let { b.put("extra_settings", JSONObject().put("method", it)) }
        return b
    }

    /** ساخت تمپلت. پنل حداقل یک گروه می‌خواهد و ۲۰۱ برمی‌گرداند. */
    suspend fun createUserTemplate(session: Session, template: UserTemplateItem) = withContext(Dispatchers.IO) {
        val body = templateBody(template)
        executeJson(
            requestBuilder(session, "${session.baseUrl}/api/user_template")
                .post(body.toString().toRequestBody(jsonType)).build()
        )
    }

    /** ویرایش تمپلت (PUT روی مسیرِ مفرد + شناسه). */
    suspend fun modifyUserTemplate(session: Session, templateId: Int, template: UserTemplateItem) = withContext(Dispatchers.IO) {
        val body = templateBody(template)
        executeJson(
            requestBuilder(session, "${session.baseUrl}/api/user_template/$templateId")
                .put(body.toString().toRequestBody(jsonType)).build()
        )
    }

    /** حذف تمپلت. پنل ۲۰۴ با بدنهٔ خالی برمی‌گرداند. */
    suspend fun deleteUserTemplate(session: Session, templateId: Int) = withContext(Dispatchers.IO) {
        val req = requestBuilder(session, "${session.baseUrl}/api/user_template/$templateId").delete().build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) {
                val details = res.body?.string()?.take(250).orEmpty()
                error("Delete template failed: ${res.code} $details")
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
        require(count in 1..500) { "Count must be between 1 and 500" }
        val body = JSONObject().apply {
            put("user_template_id", templateId)
            put("count", count)
            put("strategy", if (sequential) "sequence" else "random")
            // قرارداد پنل: در حالتِ random باید username تهی باشد.
            if (sequential) {
                put("username", username ?: error("A base name is required for sequential mode"))
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

    /**
     * افزودن/کاستنِ زمان برای چند کاربر — `POST /api/users/bulk/expire`.
     * `amount` بر حسب **ثانیه** است؛ منفی یعنی کم کن.
     *
     * توجه: این اندپوینت برخلافِ بقیهٔ bulkها کلیدش `users` است نه `ids`،
     * و اگر خالی بماند پنل روی *همهٔ* کاربران اعمالش می‌کند — پس خالی نمی‌فرستیم.
     */
    suspend fun bulkAddDays(session: Session, userIds: Set<Long>, days: Int) = withContext(Dispatchers.IO) {
        if (userIds.isEmpty() || days == 0) return@withContext
        val body = JSONObject().apply {
            put("amount", days.toLong() * 86_400L)
            put("users", org.json.JSONArray(userIds))
        }
        executeJson(
            requestBuilder(session, "${session.baseUrl}/api/users/bulk/expire")
                .post(body.toString().toRequestBody(jsonType)).build()
        )
    }

    /** افزودن/کاستنِ حجم برای چند کاربر — `POST /api/users/bulk/data_limit` (بایت). */
    suspend fun bulkAddData(session: Session, userIds: Set<Long>, gb: Double) = withContext(Dispatchers.IO) {
        if (userIds.isEmpty() || gb == 0.0) return@withContext
        val body = JSONObject().apply {
            put("amount", (gb * 1_073_741_824.0).toLong())
            put("users", org.json.JSONArray(userIds))
        }
        executeJson(
            requestBuilder(session, "${session.baseUrl}/api/users/bulk/data_limit")
                .post(body.toString().toRequestBody(jsonType)).build()
        )
    }

    /** باطل‌کردنِ لینکِ اشتراکِ چند کاربر — `POST /api/users/bulk/revoke_sub`. */
    suspend fun bulkRevokeSubs(session: Session, userIds: Set<Long>) = withContext(Dispatchers.IO) {
        if (userIds.isEmpty()) return@withContext
        val body = JSONObject().apply { put("ids", org.json.JSONArray(userIds)) }
        executeJson(
            requestBuilder(session, "${session.baseUrl}/api/users/bulk/revoke_sub")
                .post(body.toString().toRequestBody(jsonType)).build()
        )
    }

    /**
     * کاربرانی که هدفِ پاک‌سازی‌اند — `GET /api/users/expired?target=…`.
     * `target` یکی از expired / limited / on_hold / disabled. پاسخ آرایه‌ای از
     * نام‌های کاربری است، پس می‌شود قبل از حذف نشانشان داد.
     */
    suspend fun cleanupCandidates(session: Session, target: String = "expired"): List<String> =
        withContext(Dispatchers.IO) {
            val request = requestBuilder(session, "${session.baseUrl}/api/users/expired?target=$target").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Expired list failed: ${response.code}")
                val arr = org.json.JSONArray(response.body?.string() ?: "[]")
                List(arr.length()) { i -> arr.optString(i) }
            }
        }

    /** حذفِ همان فهرست — `DELETE /api/users/expired?target=…`. */
    suspend fun deleteCleanupCandidates(session: Session, target: String = "expired") = withContext(Dispatchers.IO) {
        executeJson(
            requestBuilder(session, "${session.baseUrl}/api/users/expired?target=$target").delete().build()
        )
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
