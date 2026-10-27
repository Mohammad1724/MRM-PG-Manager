package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.utils.DateLogic
import com.mrm.pgmanager.data.model.AdminSelf
import com.mrm.pgmanager.data.model.PermissionScope
import com.mrm.pgmanager.data.model.BulkCreateResult
import com.mrm.pgmanager.data.model.CountMetric
import com.mrm.pgmanager.data.model.Group
import com.mrm.pgmanager.data.model.GroupDetail
import com.mrm.pgmanager.data.model.AdminRoleList
import com.mrm.pgmanager.data.model.PanelAdmin
import com.mrm.pgmanager.data.model.PanelAdminRole
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
import com.mrm.pgmanager.data.model.NodeUsage
import com.mrm.pgmanager.data.model.NodeTrafficPoint
import com.mrm.pgmanager.data.model.OnlineIp
import com.mrm.pgmanager.data.model.SubUpdate
import com.mrm.pgmanager.data.model.SubUpdateList
import com.mrm.pgmanager.data.model.UserRef
import com.mrm.pgmanager.data.model.PanelGeneralSettings
import com.mrm.pgmanager.data.model.OnHoldAction
import com.mrm.pgmanager.data.model.ClientTemplate
import com.mrm.pgmanager.data.model.PanelCore
import com.mrm.pgmanager.data.model.PanelHost
import com.mrm.pgmanager.data.model.PanelSettings
import com.mrm.pgmanager.data.model.PanelApiKey
import com.mrm.pgmanager.data.model.CreatedApiKey
import com.mrm.pgmanager.data.model.PanelGeneralSection
import com.mrm.pgmanager.data.model.PanelSubscriptionSection
import com.mrm.pgmanager.data.model.PanelHwidSection
import com.mrm.pgmanager.data.model.PanelNotificationSection
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

/**
 * پنل به درخواستِ احرازهویت‌شده ۴۰۱ داده: JWT منقضی/باطل شده (مثلاً پس از تغییرِ رمز)
 * یا کلید API غیرفعال/حذف شده است. پیام عمداً «401» را دارد تا کدهای قدیمی‌ای که
 * `message.contains("401")` می‌کنند هم همچنان درست کار کنند.
 */
class SessionExpiredException(message: String = "401 Unauthorized: session expired") : RuntimeException(message)

object PanelApi {
    /** تشخیصِ یکپارچهٔ «نشست منقضی شده» — به‌جای `message.contains("401")` پراکنده. */
    fun isUnauthorized(e: Throwable?): Boolean =
        e is SessionExpiredException || e?.message?.contains("401") == true

    /**
     * کلاینتی که **فقط TLS 1.2** حرف می‌زند.
     *
     * چرا لازم است: روی برخی پنل‌های self-hosted (و برخی واسط‌های شبکه) هندشیکِ
     * TLS 1.3 پاسخ نمی‌گیرد یا نیمه‌کاره رها می‌شود، در حالی که همان سرور با 1.2
     * بی‌مشکل جواب می‌دهد. تلاشِ آخرِ ورود با این کلاینت انجام می‌شود — بدونِ
     * اینکه امنیتِ مسیرِ عادی کم شود (پیش‌فرض همان TLS 1.3 می‌ماند).
     */
    private val clientTls12: OkHttpClient by lazy {
        client.newBuilder()
            // فقط TLS؛ CLEARTEXT عمداً نیست: این کلاینت فقط برای آخرین تلاشِ ورودِ
            // https به‌کار می‌رود و مبادلهٔ توکن روی متنِ ساده هرگز نباید ممکن باشد.
            // MODERN_TLS (TLS 1.3+) و COMPATIBLE_TLS (TLS 1.2 با رمزهای قدیمی‌تر)
            // با هم هندشیکِ سازگار با پنل‌های self-hosted را پوشش می‌دهند.
            .connectionSpecs(listOf(okhttp3.ConnectionSpec.MODERN_TLS, okhttp3.ConnectionSpec.COMPATIBLE_TLS))
            .build()
    }

    /** آدرسِ نرمال‌شده با همان قاعده‌ای که خودِ اپ استفاده می‌کند (برای ابزارِ تشخیصی). */
    internal fun normalizedBaseForDiagnostics(address: String): String = baseUrl(address)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(okhttp3.ConnectionPool(5, 30, TimeUnit.SECONDS))
        .addInterceptor { chain ->
            // ۴۰۱ برای درخواست‌های احرازهویت‌شده در یک نقطه به استثنای تایپ‌دار تبدیل می‌شود.
            // درخواستِ لاگین (بدون هدرِ auth) مستثناست تا «رمز اشتباه» با «نشست منقضی» قاطی نشود.
            val request = chain.request()
            val response = chain.proceed(request)
            val authenticated = request.header("Authorization") != null || request.header("X-Api-Key") != null
            if (response.code == 401 && authenticated) {
                response.close()
                throw SessionExpiredException()
            }
            response
        }
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

    /**
     * کلاینتِ جریان‌ها (لاگِ زندهٔ نود).
     *
     * دو تفاوت با [client]، هر دو از روی همان سازنده تا یک منبعِ حقیقت بماند:
     * `readTimeout = 0` چون لاگ می‌تواند دقایق ساکت بماند، و
     * `retryOnConnectionFailure(false)` چون تلاشِ دوبارهٔ OkHttp روی پاسخِ
     * نیمه‌کارهٔ streaming یعنی دو جریانِ هم‌زمان و خط‌های تکراری روی صفحه.
     * interceptorها (نگاشتِ ۴۰۱ به [SessionExpiredException]، retryِ متدهای امن)
     * سرِ جایشان می‌مانند.
     */
    private val streamClient: OkHttpClient by lazy {
        client.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    /** بدنهٔ خالیِ JSON برای POSTهایی که پارامتر ندارند (مثلِ چرخشِ کلیدِ API). */
    private const val EMPTY_JSON = "{}"

    /**
     * هدفِ «کاربرانِ بی‌فعال» — از PasarGuard **v5.5** به `ExpiredUsersQuery.target`
     * اضافه شد و برخلافِ بقیه، فیلترِ `expired_before` را **الزامی** می‌کند.
     *
     * عمداً **قبل از** [CLEANUP_TARGETS] تعریف شده: در Kotlin ارجاعِ رو‌به‌جلو در
     * مقداردهیِ اولیهٔ property مجاز نیست («Variable must be initialized») و
     * همین ترتیب یک بار CI را سرخ کرد.
     */
    internal const val CLEANUP_INACTIVE = "inactive"

    /** مقادیرِ مجازِ `target` برای پاک‌سازی — همان `ExpiredUsersQuery.target` پنل. */
    internal val CLEANUP_TARGETS = listOf("expired", "limited", "on_hold", "disabled", CLEANUP_INACTIVE)

    /** مقادیرِ مجازِ `on_hold_timeout_action` در پنل (`OnHoldTimeoutAction`، v5.5). */
    internal val ON_HOLD_ACTIONS = listOf(OnHoldAction.ACTIVATE, OnHoldAction.DISABLE, OnHoldAction.DELETE)

    /**
     * سقفِ ثانیه‌ایِ پنل برای `next_plan.expire` و `on_hold_expire_duration`
     * (`MAX_ON_HOLD_EXPIRE_DURATION_SECONDS = 2_147_483_647` در `app/models/validators.py`).
     *
     * از PasarGuard **v5.5** پنل این سقف را برای `next_plan` هم اعمال می‌کند
     * (`UserWithValidator.validate_next_plan`). بدونِ کلمپ، یک ورودیِ بزرگ (مثلاً
     * «۳۰۰۰۰۰ روز») به‌جای پیامِ روشن، ۴۲۲ خامِ pydantic می‌گرفت.
     */
    internal const val MAX_PLAN_SECONDS = 2_147_483_647L

    /**
     * فقط برای تست‌های واحد: MockWebServer روی `http://localhost` بالا می‌آید و مسیرِ
     * ورود (`login`/`loginWithApiKey`) از [baseUrl] می‌گذرد که http را رد می‌کند.
     * در اپ همیشه false است؛ حتی برای loopback، چون manifest cleartext را بسته است.
     */
    @androidx.annotation.VisibleForTesting
    internal var allowCleartextLoopbackForTests = false

    private fun baseUrl(input: String): String {
        val trimmed = input.trim()
        require(trimmed.isNotBlank()) { "Panel address is required" }
        val prepared = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        val uri = runCatching { URI(prepared) }.getOrNull()
            ?: error("Invalid URL")
        require(!uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) { "Invalid URL" }
        // اپ با usesCleartextTraffic=false ساخته شده؛ http بدونِ پیامِ واضح در لایهٔ شبکه fail می‌شد.
        val loopback = uri.host == "localhost" || uri.host == "127.0.0.1"
        require(!uri.scheme.equals("http", ignoreCase = true) || (allowCleartextLoopbackForTests && loopback)) {
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

    /**
     * مسیرِ پایهٔ یک کاربر.
     *
     * پنل از v3.2 مسیرهای `/api/user/by-id/{id}` را دارد و مسیرهای `/api/user/{username}`
     * را deprecated کرده (طبق docstring پنل در v6 حذف می‌شوند). چون اپ همین حالا هم به
     * مسیرهای v5 (`/disabled`، `users/counts`) وابسته است، by-id همیشه در دسترس است.
     * مسیرِ username فقط برای رکوردهای بدونِ id (مثلاً کشِ قدیمی) به‌عنوانِ پشتیبان می‌ماند.
     */
    private fun userUrl(session: Session, user: PanelUser): String =
        if (user.id > 0L) "${session.baseUrl}/api/user/by-id/${user.id}"
        else "${session.baseUrl}/api/user/${URLEncoder.encode(user.username, "UTF-8")}"

    /** هدرِ احراز هویت: کلید API با `X-Api-Key` (روشِ توصیه‌شدهٔ پنل)، JWT با `Bearer`. */
    private fun requestBuilder(session: Session, url: String): Request.Builder {
        val builder = Request.Builder().url(url)
        return if (session.isApiKey) builder.header("X-Api-Key", session.token)
        else builder.header("Authorization", "Bearer ${session.token}")
    }

    /**
     * پیامِ خطای خوانا از بدنهٔ پاسخ: پنل خطاها را به‌شکلِ `{"detail": "..."}` می‌دهد
     * (یا برای ۴۲۲ آرایه‌ای از `{loc, msg}`). به‌جای نشان‌دادنِ JSON خام، همان متن را برمی‌داریم.
     */
    internal fun errorDetail(body: String?): String {
        if (body.isNullOrBlank()) return ""
        return runCatching {
            val detail = JSONObject(body).opt("detail") ?: return@runCatching body.take(250)
            when (detail) {
                is String -> detail
                is org.json.JSONArray -> (0 until detail.length()).mapNotNull { i ->
                    val item = detail.optJSONObject(i) ?: return@mapNotNull detail.optString(i).takeIf { it.isNotBlank() }
                    val loc = item.optJSONArray("loc")?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }
                        ?.filterNot { it == "body" || it == "query" || it == "path" }?.joinToString(".")
                    listOfNotNull(loc?.takeIf { it.isNotBlank() }, item.optString("msg").takeIf { it.isNotBlank() }).joinToString(": ")
                }.joinToString("; ")
                is JSONObject -> detail.toString()
                else -> detail.toString()
            }
        }.getOrDefault(body.take(250)).take(300)
    }

    suspend fun login(address: String, username: String, password: String): Session = withContext(Dispatchers.IO) {
        require(username.isNotBlank() && password.isNotBlank()) { "Credentials required" }
        val base = baseUrl(address)
        val body = FormBody.Builder().add("username", username).add("password", password).build()
        val request = Request.Builder().url("$base/api/admin/token").post(body).build()
        // ورود idempotent است، پس تکرار بی‌خطر است — و خطای هندشیکِ TLS در عمل
        // گذرا است (فیلتر/واسطِ شبکه/مشکلِ 1.3). سه تلاش، بدونِ دو منبعِ حقیقت:
        //  ۱) کلاینتِ پیش‌فرض   ۲) دوباره بعد از ۴۰۰ms   ۳) با TLS 1.2
        // فقط `SSLException` را دوباره می‌کوشیم؛ ۴۰۱/۴۰۳ یعنی سرور جواب داده و
        // تکرار بی‌معناست.
        var lastTlsError: Throwable? = null
        val attempts = listOf(client to 0L, client to 400L, clientTls12 to 0L)
        for ((httpClient, delayMs) in attempts) {
            if (delayMs > 0) Thread.sleep(delayMs)
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Login failed: ${response.code} ${errorDetail(response.body?.string())}".trim())
                    val token = JSONObject(response.body?.string() ?: error("Empty login response")).getString("access_token")
                    return@withContext Session(base, token, username)
                }
            } catch (e: javax.net.ssl.SSLException) {
                lastTlsError = e
            }
        }
        throw lastTlsError ?: IllegalStateException("Login failed")
    }

    /**
     * `GET /api/admin` با همان الگوی تلاشِ TLS که [login] دارد: کلاینت پیش‌فرض،
     * یک بار تکرار پس از ۴۰۰ms و در پایان کلاینت TLS 1.2. فقط `SSLException` تکرار
     * می‌شود؛ ۴۰۱ (که اینترسپتور آن را به [SessionExpiredException] تبدیل می‌کند)
     * فوراً بالا می‌رود چون تکرارش بی‌معناست.
     *
     * چرا لازم است: ورود با کلید API قبلاً فقط یک تلاش با کلاینت پیش‌فرض داشت،
     * پس روی پنل‌های self-hosted ای که هندشیک TLS 1.3 نیمه‌کاره می‌ماند، ورود
     * رمزی موفق می‌شد ولی ورود با کلید شکست می‌خورد (عدم تقارن L3 ممیزی لاگین).
     */
    private fun fetchAdminWithTlsFallback(session: Session): AdminSelf {
        var lastTlsError: Throwable? = null
        val attempts = listOf(client to 0L, client to 400L, clientTls12 to 0L)
        for ((httpClient, delayMs) in attempts) {
            if (delayMs > 0) Thread.sleep(delayMs)
            try {
                val request = requestBuilder(session, "${session.baseUrl}/api/admin").get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Admin fetch failed: ${response.code} ${errorDetail(response.body?.string())}".trim())
                    return parseAdminSelf(JSONObject(response.body?.string() ?: error("Empty admin response")))
                }
            } catch (e: javax.net.ssl.SSLException) {
                lastTlsError = e
            }
        }
        throw lastTlsError ?: IllegalStateException("Admin fetch failed")
    }

    /**
     * ورود با کلید API پنل (`pg_key_<uuid4>`؛ Settings → API Keys).
     *
     * پنل endpoint جداگانه‌ای برای اعتبارسنجیِ کلید ندارد؛ `GET /api/admin` را با
     * `X-Api-Key` می‌زنیم: اگر کلید معتبر باشد ادمینِ صاحبِ کلید برمی‌گردد و
     * نامِ او را برای نمایش در نشست نگه می‌داریم. کلیدِ نامعتبر/غیرفعال → ۴۰۱.
     */
    suspend fun loginWithApiKey(address: String, apiKey: String): Session = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        require(key.startsWith(Session.API_KEY_PREFIX) && key.length > Session.API_KEY_PREFIX.length) { "Invalid API key" }
        val base = baseUrl(address)
        val probe = Session(base, key, "")
        val admin = try {
            fetchAdminWithTlsFallback(probe)
        } catch (e: SessionExpiredException) {
            error("Login failed: 401 invalid API key")
        }
        Session(base, key, admin.username)
    }

    /** ادمینِ فعلی و مجوزهایش — `GET /api/admin` (برای همهٔ ادمین‌ها مجاز است). */
    suspend fun currentAdmin(session: Session): AdminSelf = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, "${session.baseUrl}/api/admin").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Admin fetch failed: ${response.code} ${errorDetail(response.body?.string())}".trim())
            parseAdminSelf(JSONObject(response.body?.string() ?: error("Empty admin response")))
        }
    }

    internal fun parseAdminSelf(a: JSONObject): AdminSelf {
        val role = a.optJSONObject("role")
        // همان پارسرِ نقش‌ها: یک نگاشت، یک شکل — تفاوتِ دو پیاده‌سازی اینجا
        // یعنی «چرا دکمه‌ام پنهان است» با «چرا برای همکارم پنهان است» یکی نباشد.
        val permissions = parsePermissionMap(role?.optJSONObject("permissions"))
        val access = role?.optJSONObject("access")
        val features = role?.optJSONObject("features")
        val limits = role?.optJSONObject("limits")
        fun intList(arr: org.json.JSONArray?): List<Int>? = arr?.let { (0 until it.length()).map { i -> it.optInt(i) } }
        return AdminSelf(
            id = a.optInt("id"),
            username = a.optString("username"),
            isOwner = role?.optBoolean("is_owner", false) ?: false,
            roleName = role?.optString("name").orEmpty(),
            permissions = permissions,
            requireTemplate = access?.optBoolean("require_template", false) ?: false,
            allowedTemplateIds = intList(access?.optJSONArray("allowed_template_ids")),
            allowedGroupIds = intList(access?.optJSONArray("allowed_group_ids")),
            canUseResetStrategy = features?.optBoolean("can_use_reset_strategy", true) ?: true,
            canUseNextPlan = features?.optBoolean("can_use_next_plan", true) ?: true,
            maxUsers = limits?.takeIf { !it.isNull("max_users") }?.optInt("max_users"),
            totalUsers = a.optInt("total_users", 0)
        )
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
                incomingBandwidth = o.optLong("incoming_bandwidth"), outgoingBandwidth = o.optLong("outgoing_bandwidth"),
                version = o.optString("version").takeIf { it != "null" }.orEmpty()
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
        user: PanelUser,
        range: StatsRange = StatsRange.LAST_7D
    ): List<TrafficPoint> = withContext(Dispatchers.IO) {
        val url = buildString {
            append(userUrl(session, user)); append("/usage")
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
     * ترافیکِ نودها به تفکیکِ نود — `GET /api/node/usage?group_by_node=true` (مجوزِ `nodes.stats`).
     * پاسخ: `{"stats": {"<node_id>": [{"period_start", "uplink", "downlink"}, …]}}`.
     * با `nodeId` فقط همان نود می‌آید؛ پنل فقط سطل‌های کاملِ زمانی را برمی‌گرداند.
     */
    suspend fun nodeUsage(
        session: Session,
        range: StatsRange = StatsRange.LAST_24H,
        nodeId: Int? = null
    ): List<NodeUsage> = withContext(Dispatchers.IO) {
        val url = buildString {
            append(session.baseUrl); append("/api/node/usage")
            append("?period="); append(range.period)
            append("&start="); append(URLEncoder.encode(range.startIso(), "UTF-8"))
            append("&group_by_node=true")
            if (nodeId != null) { append("&node_id="); append(nodeId) }
        }
        val request = requestBuilder(session, url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Node usage failed: ${response.code}")
            val root = JSONObject(response.body?.string() ?: "{}")
            val stats = root.optJSONObject("stats") ?: return@use emptyList()
            stats.keys().asSequence().mapNotNull { key ->
                val id = key.toIntOrNull() ?: return@mapNotNull null
                val arr = stats.optJSONArray(key) ?: return@mapNotNull null
                val points = (0 until arr.length()).mapNotNull { i ->
                    val p = arr.optJSONObject(i) ?: return@mapNotNull null
                    NodeTrafficPoint(p.optString("period_start"), p.optLong("uplink"), p.optLong("downlink"))
                }.sortedBy { it.timestamp }
                NodeUsage(id, points)
            }.sortedByDescending { it.total }.toList()
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
                    // راستی‌آزمایی با سورسِ پنل v5.4.1 (`crud/user.py:get_user_count_metric_stats`):
                    // بدونِ group_by_node/node_id پاسخ فقط یک کلیدِ تجمیعی (`-1`) دارد،
                    // پس در عمل یک مقدار در هر بازهٔ زمانی هست و maxOf با sum برابر است.
                    // اگر روزی group_by_node اضافه شد، شمارشِ کاربرانِ متمایز در چند نود
                    // نه با max درست می‌شود نه با sum (هم‌پوشانی دارد)؛ باید سراغ فیلدِ
                    // بالاسطحیِ `count_during_period` رفت. maxOf عمداً برای شکلِ فعلی نگه داشته شد.
                    totals[time] = maxOf(totals[time] ?: 0L, v)
                }
            }
            totals.entries.sortedBy { it.key }.map { TrafficPoint(it.key, it.value) }
        }
    }

    /**
     * فهرست نودها برای فیلترِ نمودارها — `GET /api/nodes` (مجوز `nodes.read`).
     *
     * ۴۰۳ عمداً به «لیست خالی» تبدیل می‌شود (نقش بدونِ مجوز، نودی برای دیدن ندارد)؛
     * ولی خطای گذرای شبکه باید پرتاب شود تا فراخوان لیستِ کش‌شده‌اش را نگه دارد و
     * صفحه «این پنل نودی ندارد»ِ دروغ نشان ندهد (هم‌راستا با رفع D3 داشبورد).
     */
    suspend fun nodes(session: Session): List<PanelNode> = withContext(Dispatchers.IO) {
        val req = requestBuilder(session, "${session.baseUrl}/api/nodes").get().build()
        client.newCall(req).execute().use { res ->
            if (res.code == 403) return@withContext emptyList()
            if (!res.isSuccessful) error("Nodes request failed: ${res.code} ${errorDetail(res.body?.string())}".trim())
            val body = res.body?.string() ?: "{}"
            val arr = runCatching { JSONObject(body).optJSONArray("nodes") }.getOrNull()
                ?: runCatching { org.json.JSONArray(body) }.getOrNull()
                ?: return@withContext emptyList<PanelNode>()
            // `sortedBy` پایدار است: نودهای بدونِ `sort_order` (همه `0`) ترتیبِ خودِ
            // پاسخ را نگه می‌دارند و بقیه همان‌جای پنل می‌نشینند.
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
                    sortOrder = n.optInt("sort_order", 0),
                    uplink = n.optLong("uplink", 0L),
                    downlink = n.optLong("downlink", 0L)
                )
            }.sortedBy { it.sortOrder }
        }
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

    // ── جریانِ لاگِ نود (SSE) ──────────────────────────────────────────────
    //
    // پنل لاگ را با `EventSourceResponse` می‌دهد (`GET /api/node/{id}/logs`،
    // مجوزِ `nodes.logs`)؛ خودِ نود ایجنتِ Go است و پنل لاگ را از آن می‌گیرد، پس
    // اپ هرگز مستقیم با نود حرف نمی‌زند. چون پاسخ «جریان» است نه JSON، این مسیر
    // کلاینتِ جدا و عمرِ مشخصی می‌خواهد (شرحش در `NodeLogStream.kt`).

    /**
     * بازکردنِ جریانِ لاگ — تا رسیدنِ پاسخ باز می‌ماند و بعد خواندنِ خط‌ها با
     * [NodeLogConnection.nextEvent] انجام می‌شود.
     *
     * `Accept: text/event-stream` عمداً فرستاده می‌شود: sse-starlette بدونِ آن هم
     * کار می‌کند، ولی با آن مطمئن می‌شویم هیچ واسطه‌ای (پروکسی/CDN) بدنه را
     * buffer نمی‌کند. `Cache-Control: no-cache` همان کار را برای واسطه‌های
     * بدجنس‌تر می‌کند — لاگِ کش‌شده یعنی دیدنِ دیروز به‌جای امروز.
     *
     * خطاها مثلِ بقیهٔ مسیرها `detail`ِ پنل را در message نگه می‌دارند تا
     * `ApiErrorMapper` بتواند ۴۰۳ «مجوزِ nodes.logs نداری» را خوانا نشان بدهد.
     */
    internal suspend fun openNodeLogs(session: Session, nodeId: Int): NodeLogConnection {
        val request = requestBuilder(session, "${session.baseUrl}/api/node/$nodeId/logs")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .get().build()
        return connectNodeLog(streamClient.newCall(request), ::errorDetail)
    }

    /** دستگاه‌های ثبت‌شدهٔ کاربر — `GET /api/user/{user_id}/hwids`. */
    /**
     * IPهای فعالِ کاربر روی همهٔ نودهای سالم — `GET /api/node/online_stats/{user_id}/ip` (مجوزِ `nodes.stats`).
     * پاسخ: `{"nodes": {"<node_id>": {"ips": {"1.2.3.4": 2}} | null}}`؛ نودهای بی‌پاسخ null می‌آیند و رد می‌شوند.
     */
    suspend fun userOnlineIps(session: Session, userId: Long): List<OnlineIp> = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, "${session.baseUrl}/api/node/online_stats/$userId/ip").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Online IPs failed: ${response.code}")
            val root = JSONObject(response.body?.string() ?: "{}")
            val nodes = root.optJSONObject("nodes") ?: return@use emptyList()
            nodes.keys().asSequence().flatMap { key ->
                val nodeId = key.toIntOrNull() ?: return@flatMap emptySequence()
                val ips = nodes.optJSONObject(key)?.optJSONObject("ips") ?: return@flatMap emptySequence()
                ips.keys().asSequence().map { ip -> OnlineIp(nodeId, ip, ips.optInt(ip)) }
            }.sortedWith(compareByDescending<OnlineIp> { it.connections }.thenBy { it.nodeId }.thenBy { it.ip }).toList()
        }
    }

    /**
     * تاریخچهٔ گرفتنِ لینکِ اشتراک — `GET /api/user/by-id/{user_id}/sub_update?offset=&limit=` (مجوزِ `users.read`).
     * پاسخ: `{"updates": [{"created_at", "user_agent", "ip", "hwid"}], "count": N}`.
     */
    suspend fun userSubUpdates(session: Session, userId: Long, limit: Int = 5, offset: Int = 0): SubUpdateList = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, "${session.baseUrl}/api/user/by-id/$userId/sub_update?offset=$offset&limit=$limit").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Sub updates failed: ${response.code}")
            val root = JSONObject(response.body?.string() ?: "{}")
            val arr = root.optJSONArray("updates")
            val updates = if (arr == null) emptyList() else List(arr.length()) { i ->
                val u = arr.getJSONObject(i)
                SubUpdate(
                    createdAt = u.optString("created_at"),
                    userAgent = u.optString("user_agent"),
                    ip = if (u.isNull("ip")) null else u.optString("ip").takeIf { it.isNotBlank() },
                    hwid = if (u.isNull("hwid")) null else u.optString("hwid").takeIf { it.isNotBlank() }
                )
            }
            SubUpdateList(updates, root.optInt("count", updates.size))
        }
    }

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
    suspend fun activateNextPlan(session: Session, user: PanelUser) = withContext(Dispatchers.IO) {
        executeJson(
            requestBuilder(session, "${userUrl(session, user)}/active_next")
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
                if (!response.isSuccessful) error("Request failed: ${response.code} ${errorDetail(response.body?.string())}".trim())
                val obj = JSONObject(response.body?.string() ?: error("Empty users response"))
                val arr = obj.getJSONArray("users")
                List(arr.length()) { i -> parseUser(arr.getJSONObject(i), session.baseUrl) }
            }
            all.addAll(chunk)
            if (chunk.size < limit) break
            offset += limit
            // محافظ: اگر پنل total را برگرداند و به آن رسیدیم، متوقف شو (جلوگیری از حلقهٔ بی‌نهایت)
            if (offset > 1_000_000) break
        }
        // نام گروه‌ها در پاسخ لیست کاربران نیست (پنل group_names را exclude می‌کند)؛
        // پس با یک واکشی سبک از /api/groups/simple نگاشت id→name انجام می‌دهیم.
        attachGroupNames(session, all)
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
            query.online?.let { append("&online="); append(it) }
            query.groupId?.let { append("&group="); append(it) }
            query.sort?.let { append("&sort="); append(URLEncoder.encode(it, "UTF-8")) }
            query.expireAfter?.let { append("&expire_after="); append(URLEncoder.encode(it, "UTF-8")) }
            query.expireBefore?.let { append("&expire_before="); append(URLEncoder.encode(it, "UTF-8")) }
            // پرچم‌های بولی فقط وقتی true معنا دارند؛ false همان پیش‌فرضِ پنل است.
            if (query.noDataLimit == true) append("&no_data_limit=true")
            if (query.noExpire == true) append("&no_expire=true")
            if (query.noGroup == true) append("&no_group=true")
            query.admin?.takeIf { it.isNotBlank() }?.let { append("&admin="); append(URLEncoder.encode(it, "UTF-8")) }
        }
        val request = requestBuilder(session, url).get().build()
        val page = client.newCall(request).execute().use { response ->
            // ۴۰۳ (نقشِ بدونِ users.read) و ۴۲۲ (پارامترِ نامعتبر) با پیامِ خودِ پنل، نه فقط عدد.
            if (!response.isSuccessful) error("Request failed: ${response.code} ${errorDetail(response.body?.string())}".trim())
            val obj = JSONObject(response.body?.string() ?: error("Empty users response"))
            val arr = obj.getJSONArray("users")
            UsersPage(List(arr.length()) { i -> parseUser(arr.getJSONObject(i), session.baseUrl) }, obj.optInt("total", arr.length()))
        }
        page.copy(users = attachGroupNames(session, page.users))
    }

    /**
     * پاسخِ فهرستِ کاربران `group_names` ندارد (پنل عمداً حذفش می‌کند)، پس نگاشتِ
     * id→name را از یک واکشیِ سبک می‌گیریم و روی کاربرها می‌نشانیم.
     */
    private suspend fun attachGroupNames(session: Session, users: List<PanelUser>): List<PanelUser> {
        if (users.none { it.groupNames.isEmpty() && it.groupIds.isNotEmpty() }) return users
        val groupMap = groupNameMap(session)
        if (groupMap.isEmpty()) return users
        // `PanelUser` تغییرناپذیر است (برای اینکه Compose بتواند کارت‌ها را رد کند)،
        // پس به‌جای دست‌کاریِ درجا، نسخهٔ کپی با نامِ گروه‌ها می‌سازیم.
        return users.map { u ->
            if (u.groupNames.isEmpty() && u.groupIds.isNotEmpty()) u.copy(groupNames = u.groupIds.mapNotNull { groupMap[it] })
            else u
        }
    }

    /**
     * نگاشتِ id→name گروه‌ها با کشِ کوتاهِ درون‌حافظه‌ای (به‌ازای هر پنل).
     *
     * هر صفحهٔ کاربران (و هر رفرشِ خودکار، که می‌تواند هر ۵ ثانیه باشد) به این
     * نگاشت نیاز دارد؛ بدونِ کش هر بار یک `GET /api/groups/simple` اضافه می‌رفت.
     * گروه‌ها به‌ندرت عوض می‌شوند، و ساخت/ویرایش/حذفِ گروه از همین اپ کش را باطل می‌کند.
     */
    private val groupNameCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Map<Int, String>>>()
    private const val GROUP_NAME_CACHE_MS = 60_000L

    private suspend fun groupNameMap(session: Session): Map<Int, String> {
        // nanoTime (یکنواخت و بدونِ وابستگی به اندروید — تست‌های JVM هم از این مسیر می‌گذرند).
        val now = System.nanoTime() / 1_000_000L
        groupNameCache[session.baseUrl]?.let { (at, map) -> if (now - at < GROUP_NAME_CACHE_MS && map.isNotEmpty()) return map }
        val map = runCatching { groups(session) }.getOrDefault(emptyList()).associate { it.id to it.name }
        if (map.isNotEmpty()) groupNameCache[session.baseUrl] = now to map
        return map
    }

    /** پس از ساخت/ویرایش/حذفِ گروه صدا زده می‌شود تا نامِ تازه فوراً روی کاربران بنشیند. */
    private fun invalidateGroupNames(session: Session) { groupNameCache.remove(session.baseUrl) }

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
        // عضویتِ گروهی هم نگاشت id→name روی کاربرها را عوض می‌کند؛ وگرنه بجِ گروه
        // روی کارت‌ها تا انقضای ۶۰ثانیه‌ای کش کهنه می‌ماند (همان رفتارِ CRUD گروه).
        invalidateGroupNames(session)
    }

    /** واکشی تکی یک کاربر (با subscription_url) — برای دریافت لینک اشتراک فقط در صورت نیاز. */
    suspend fun user(session: Session, user: PanelUser): PanelUser = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, userUrl(session, user)).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("User fetch failed: ${response.code}")
            parseUser(JSONObject(response.body?.string() ?: error("Empty user response")), session.baseUrl)
        }
    }

    /**
     * ساخت کاربر. اگر `status == "on_hold"` باشد، طبق قاعدهٔ پنل (`UserValidator.validate_status`)
     * به‌جای `expire` فقط `on_hold_expire_duration` (ثانیه، > 0) فرستاده می‌شود؛ شمارش اعتبار
     * با اولین اتصال کاربر شروع می‌شود.
     */
    suspend fun createUser(session: Session, username: String, limitGb: Double, expireIso: String, note: String = "", hwidLimit: Int? = null, groupIds: List<Int> = emptyList(), nextPlan: NextPlan? = null, resetStrategy: String? = null, autoDeleteDays: Int? = null, status: String? = null, onHoldExpireSeconds: Long? = null, onHoldTimeoutSeconds: Long? = null) = withContext(Dispatchers.IO) {
        val onHold = isOnHoldRequest(status, onHoldExpireSeconds)
        val body = JSONObject().put("username", username).put("status", if (onHold) "on_hold" else "active").put("data_limit", gbToBytes(limitGb))
        if (onHold) {
            body.put("on_hold_expire_duration", (onHoldExpireSeconds ?: 0L).coerceIn(0L, MAX_PLAN_SECONDS))
            // پنل عددِ صحیح را «ثانیه از الان» تعبیر می‌کند.
            if (onHoldTimeoutSeconds != null && onHoldTimeoutSeconds > 0L) body.put("on_hold_timeout", onHoldTimeoutSeconds)
        } else body.put("expire", expireValue(expireIso))
        if (note.isNotBlank()) body.put("note", note)
        if (hwidLimit != null && hwidLimit > 0) body.put("hwid_limit", hwidLimit)
        if (groupIds.isNotEmpty()) body.put("group_ids", org.json.JSONArray(groupIds))
        nextPlanJson(nextPlan)?.let { body.put("next_plan", it) }
        resetStrategy?.let { body.put("data_limit_reset_strategy", it) }
        autoDeleteDays?.let { body.put("auto_delete_in_days", it) }
        executeJson(requestBuilder(session, "${session.baseUrl}/api/user").post(body.toString().toRequestBody(jsonType)).build())
    }

    /**
     * ویرایش کاربر. `status` فقط در دو حالت فرستاده می‌شود:
     *  - `on_hold` → همراه `on_hold_expire_duration` و بدون `expire` (پنل خودش expire را پاک می‌کند)؛
     *  - `active` → برای بیرون‌آوردن کاربر از on_hold، همراه `expire` تازه.
     * در بقیهٔ حالت‌ها وضعیت دست نمی‌خورد تا وضعیت‌های محاسبه‌شدهٔ پنل (expired/limited) خراب نشود.
     *
     * قواعدِ «دست‌نزدن» (همه بر اساسِ `crud.modify_user` پنل v5.4.1):
     *  - `expireIso == null` → کلیدِ `expire` اصلاً فرستاده نمی‌شود (پنل تاریخِ فعلی را نگه می‌دارد).
     *    `""` همچنان یعنی «نامحدود» (expire = 0).
     *  - `note` همیشه فرستاده می‌شود، چون پنل `""` را «پاک کن» می‌فهمد و اگر کلید نیاید
     *    یادداشتِ قبلی می‌ماند — قبلاً امکانِ پاک‌کردنِ یادداشت وجود نداشت.
     *  - `next_plan`: پنل اگر این کلید در بدنه **نباشد**، پلنِ بعدیِ موجود را حذف می‌کند
     *    (`elif db_user.next_plan is not None: delete`). پس اگر صداکننده چیزی نداده،
     *    پلنِ فعلیِ خودِ کاربر دوباره فرستاده می‌شود تا با یک «تمدید» بی‌صدا از بین نرود.
     */
    suspend fun modifyUser(session: Session, user: PanelUser, limitGb: Double, expireIso: String?, note: String = "", hwidLimit: Int? = null, groupIds: List<Int>? = null, nextPlan: NextPlan? = null, resetStrategy: String? = null, autoDeleteDays: Int? = null, status: String? = null, onHoldExpireSeconds: Long? = null, onHoldTimeoutSeconds: Long? = null) = withContext(Dispatchers.IO) {
        val onHold = isOnHoldRequest(status, onHoldExpireSeconds)
        val body = JSONObject().put("data_limit", gbToBytes(limitGb))
        if (onHold) {
            body.put("status", "on_hold").put("on_hold_expire_duration", (onHoldExpireSeconds ?: 0L).coerceIn(0L, MAX_PLAN_SECONDS))
            // null یعنی «دست نزن»، 0 یعنی «مهلت را بردار»، عددِ مثبت یعنی «ثانیه از الان».
            onHoldTimeoutSeconds?.let { body.put("on_hold_timeout", it.coerceAtLeast(0L)) }
        } else {
            if (expireIso != null) body.put("expire", expireValue(expireIso))
            if (status == "active") body.put("status", "active")
        }
        body.put("note", note.trim())
        if (hwidLimit != null) body.put("hwid_limit", hwidLimit)  // 0 = نامحدود
        if (groupIds != null) body.put("group_ids", org.json.JSONArray(groupIds))
        // null یعنی «دست نزن» → پلنِ فعلیِ کاربر دوباره فرستاده می‌شود؛ قالبِ خالی یعنی «پاکش کن».
        val planToSend = nextPlan ?: user.nextPlan
        if (planToSend != null) body.put("next_plan", nextPlanJson(planToSend) ?: JSONObject.NULL)
        resetStrategy?.let { body.put("data_limit_reset_strategy", it) }
        // null یعنی «دست نزن»، عددِ صفر یعنی «حذفِ خودکار را بردار».
        autoDeleteDays?.let { body.put("auto_delete_in_days", if (it > 0) it else JSONObject.NULL) }
        executeJson(requestBuilder(session, userUrl(session, user)).put(body.toString().toRequestBody(jsonType)).build())
    }

    /** on_hold فقط وقتی معتبر است که مدتِ مثبت داشته باشد؛ در غیر این صورت مثل درخواستِ عادی رفتار می‌شود. */
    private fun isOnHoldRequest(status: String?, onHoldExpireSeconds: Long?): Boolean =
        status == "on_hold" && (onHoldExpireSeconds ?: 0L) > 0L

    suspend fun resetUsage(session: Session, user: PanelUser) = withContext(Dispatchers.IO) {
        executeJson(requestBuilder(session, "${userUrl(session, user)}/reset").post("".toRequestBody(jsonType)).build())
    }

    suspend fun setDisabled(session: Session, user: PanelUser, disabled: Boolean) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("disabled", disabled)
        executeJson(requestBuilder(session, "${userUrl(session, user)}/disabled").put(body.toString().toRequestBody(jsonType)).build())
    }

    suspend fun deleteUser(session: Session, user: PanelUser) = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, userUrl(session, user)).delete().build()
        client.newCall(request).execute().use { response -> if (!response.isSuccessful) error("Delete failed: ${response.code}") }
    }

    private fun executeJson(request: Request) {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val details = errorDetail(response.body?.string())
                error("Request failed: ${response.code} $details".trim())
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
            // کلمپ به سقفِ پنل (v5.5 این سقف را برای next_plan هم اعمال می‌کند).
            plan.expireSeconds?.let { put("expire", it.coerceIn(0L, MAX_PLAN_SECONDS)) }
            put("add_remaining_traffic", plan.addRemainingTraffic)
        }
    }

    /**
     * لینکِ اشتراک را مطلق می‌کند.
     *
     * پنل لینک را `f"{url_prefix}/{path}/{token}"` می‌سازد و اگر ادمین
     * `XRAY_SUBSCRIPTION_URL_PREFIX` را (که پیش‌فرضش خالی است) تنظیم نکرده باشد،
     * پاسخ `/sub/<token>` است — یک مسیرِ نسبی. داشبوردِ خودِ پنل همین‌جا
     * `window.location.origin` را جلوی آن می‌گذارد؛ اپ هم باید آدرسِ پنل را
     * بگذارد، وگرنه لینک/QR کپی‌شده برای مشتری بی‌فایده بود.
     */
    internal fun absoluteSubUrl(raw: String, baseUrl: String?): String {
        val url = raw.trim()
        if (url.isEmpty() || baseUrl.isNullOrBlank()) return url
        return when {
            url.startsWith("//") -> baseUrl.substringBefore("://", "https") + ":" + url
            url.startsWith("/") -> baseUrl.trimEnd('/') + url
            else -> url
        }
    }

    internal fun parseUser(user: JSONObject, baseUrl: String? = null): PanelUser {
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

        // وضعیتِ آنلاین: پنل فیلدِ boolean ندارد و فقط `online_at` می‌دهد؛ با همان پنجرهٔ
        // ۲ دقیقه‌ایِ پنل (DateLogic.ONLINE_WINDOW_MS) و پارسرِ مشترک (offset/Z، naive=UTC) حساب می‌شود.
        val onlineAtStr: String? = if (user.isNull("online_at")) null
            else user.optString("online_at").takeIf { it != "null" && it.isNotBlank() }
        val isOnline = user.optBoolean("online", false) || DateLogic.isOnline(onlineAtStr)

        return PanelUser(
            id = user.optLong("id", 0L),
            username = user.getString("username"),
            status = user.optString("status", "unknown"),
            usedTraffic = user.optLong("used_traffic", 0L),
            dataLimit = user.optLong("data_limit", 0L),
            expire = if (user.isNull("expire")) null else user.optString("expire").takeIf { it != "null" && it != "0" },
            createdAt = if (user.isNull("created_at")) null else user.optString("created_at"),
            subUrl = absoluteSubUrl(user.optString("subscription_url", "").ifBlank { user.optString("sub_url", "") }, baseUrl),
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
                ?.takeIf { it.isNotBlank() && it != "null" },
            onHoldExpireDuration = if (user.isNull("on_hold_expire_duration")) null else user.optLong("on_hold_expire_duration").takeIf { it > 0L },
            onHoldTimeout = if (user.isNull("on_hold_timeout")) null else user.optString("on_hold_timeout").takeIf { it.isNotBlank() && it != "null" },
            dataLimitResetStrategy = if (user.isNull("data_limit_reset_strategy")) null else user.optString("data_limit_reset_strategy").takeIf { it.isNotBlank() && it != "null" },
            autoDeleteDays = if (user.isNull("auto_delete_in_days")) null else user.optInt("auto_delete_in_days").takeIf { it > 0 },
            // PasarGuard v5.5: تعداد دستگاه در خودِ پاسخِ لیست می‌آید (بدونِ درخواستِ جداگانهٔ HWID).
            // `null` = عدد در دسترس نیست؛ صفر یک مقدارِ واقعی است («دستگاهی ثبت نشده»).
            hwidCount = if (user.isNull("hwid_count")) null else user.optInt("hwid_count").takeIf { it >= 0 }
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
                        isOwner = a.optJSONObject("role")?.optBoolean("is_owner", false) ?: false,
                        roleName = a.optJSONObject("role")?.optString("name").orEmpty(),
                        isDisabled = a.optBoolean("is_disabled", false) || a.optString("status") == "disabled",
                        isLimited = a.optBoolean("is_limited", false) || a.optString("status") == "limited"
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

    // ═══════════════════ نقش‌های ادمین (فازِ ۶.۲) ═══════════════════
    //
    // `GET /api/admin-roles?offset=&limit=` → `{"roles": [...], "total": N}`
    // (مجوزِ `admin_roles.read`). هر نقش `permissions` را به‌شکلِ
    // `resource → action → scope` می‌دهد؛ همان نگاشتی که هنگامِ بررسیِ
    // `AdminAccess.can` استفاده می‌شود. اینجا فقط **خواندنی** است.

    /**
     * فهرستِ نقش‌ها — یک صفحهٔ ۱۰۰تایی کافی است (نقش‌ها در عمل زیرِ ۲۰ عددند)
     * ولی `total` برگردانده می‌شود تا UI اگر جا ماند بگوید.
     */
    suspend fun adminRoles(session: Session, limit: Int = 100): AdminRoleList = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, "${session.baseUrl}/api/admin-roles?offset=0&limit=$limit")
            .get().build()
        val body = executeJsonForBody(request)
        val arr = body.optJSONArray("roles") ?: return@withContext AdminRoleList(emptyList(), 0)
        val roles = List(arr.length()) { i -> parseAdminRole(arr.getJSONObject(i)) }
        AdminRoleList(roles, body.optInt("total", roles.size))
    }

    private fun parseAdminRole(r: JSONObject): PanelAdminRole {
        val limits = r.optJSONObject("limits")
        return PanelAdminRole(
            id = r.optInt("id"),
            name = r.optString("name"),
            isOwner = r.optBoolean("is_owner", false),
            createdAt = optionalString(r, "created_at"),
            permissions = parsePermissionMap(r.optJSONObject("permissions")),
            maxUsers = limits?.let { if (it.isNull("max_users")) null else it.optInt("max_users") },
            hwidMode = r.optJSONObject("hwid")?.let { optionalString(it, "mode") }
        )
    }

    /**
     * نگاشتِ `resource → action → scope` از آبجکتِ `permissions` پنل.
     *
     * مقدارِ هر اکشن در پنل سه شکل دارد: `true` (مجازِ کامل)،
     * `{"scope": N}` (محدود) یا `null`/غایب (ممنوع). اکشن‌های ممنوع **حذف**
     * می‌شوند تا UI فقط چیزی را نشان بدهد که واقعاً مجاز است و نگاشتِ خالی
     * (نقشِ بی‌مجوز) هم بی‌دلیل کلید نسازد.
     */
    private fun parsePermissionMap(perms: JSONObject?): Map<String, Map<String, Int>> {
        if (perms == null) return emptyMap()
        val out = mutableMapOf<String, Map<String, Int>>()
        perms.keys().forEach { resource ->
            val actions = perms.optJSONObject(resource) ?: return@forEach
            val map = mutableMapOf<String, Int>()
            actions.keys().forEach { action ->
                val scope = when (val v = actions.opt(action)) {
                    is Boolean -> if (v) PermissionScope.ALL else PermissionScope.NONE
                    is JSONObject -> v.optInt("scope", PermissionScope.NONE)
                    is Number -> v.toInt()
                    else -> PermissionScope.NONE
                }
                if (scope > PermissionScope.NONE) map[action] = scope
            }
            if (map.isNotEmpty()) out[resource] = map
        }
        return out
    }

    /**
     * باطل‌کردنِ لینکِ اشتراکِ کاربر — `POST /api/user/{username}/revoke_sub`.
     *
     * توکنِ اشتراک عوض می‌شود، پس لینکِ قبلی (و هر کسی که آن را دارد) از کار
     * می‌افتد و کاربر باید لینکِ تازه را بگیرد. تنها راهِ درستِ واکنش به لو رفتنِ
     * لینک است.
     */
    suspend fun revokeSubscription(session: Session, user: PanelUser): PanelUser = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, "${userUrl(session, user)}/revoke_sub")
            .post("".toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Revoke failed: ${response.code}")
            parseUser(JSONObject(response.body?.string() ?: error("Empty revoke response")), session.baseUrl)
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
        invalidateGroupNames(session)
    }

    /** ویرایش گروه (PUT روی مسیرِ مفرد + شناسه). */
    suspend fun modifyGroup(session: Session, groupId: Int, name: String, inboundTags: List<String>, isDisabled: Boolean) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", name.trim())
            .put("inbound_tags", org.json.JSONArray(inboundTags))
            .put("is_disabled", isDisabled)
        executeJson(requestBuilder(session, "${session.baseUrl}/api/group/$groupId").put(body.toString().toRequestBody(jsonType)).build())
        invalidateGroupNames(session)
    }

    /** حذف گروه. پنل 204 برمی‌گرداند (بدنهٔ خالی). */
    suspend fun deleteGroup(session: Session, groupId: Int) = withContext(Dispatchers.IO) {
        val req = requestBuilder(session, "${session.baseUrl}/api/group/$groupId").delete().build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) {
                val details = errorDetail(res.body?.string())
                error("Delete group failed: ${res.code} $details".trim())
            }
        }
        invalidateGroupNames(session)
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
                subscriptionUrls = if (urls == null) emptyList() else List(urls.length()) { absoluteSubUrl(urls.optString(it), session.baseUrl) }
            )
        }
    }

    suspend fun modifyUserFromTemplate(session: Session, user: PanelUser, templateId: Int, note: String = "") = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("user_template_id", templateId)
            if (note.isNotBlank()) put("note", note)
        }
        // مسیرِ by-id از v3.2 هست؛ نسخهٔ username آن هم deprecated است.
        val url = if (user.id > 0L) "${session.baseUrl}/api/user/from_template/by-id/${user.id}"
            else "${session.baseUrl}/api/user/from_template/${URLEncoder.encode(user.username, "UTF-8")}"
        executeJson(requestBuilder(session, url).put(body.toString().toRequestBody(jsonType)).build())
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

    /**
     * پیش‌نمایشِ عملیاتِ گروهی — پنل از v5 `dry_run` دارد و **هیچ تغییری اعمال نمی‌کند**.
     *
     * `POST /api/users/bulk/{expire|data_limit}` با `dry_run=true` فقط
     * `BulkOperationDryRunResponse{affected_users}` را برمی‌گرداند.
     *
     * سمتِ پنل شمارش این‌گونه است (`app/db/crud/bulk.py`):
     *  - `expire` → کاربرانی که `expire` دارند (کاربرِ بدونِ تاریخِ انقضا بی‌اثر است)؛
     *  - `data_limit` → کاربرانی که حجمِ محدود دارند (`data_limit` صفر/تهی نیست).
     *
     * @return تعدادِ کاربرانِ متأثر؛ یا `-1` اگر پنل عدد را برنگرداند (نامعلوم).
     *   عددِ منفی هرگز به‌عنوان «صفر کاربر» نمایش داده نمی‌شود — UI متنِ «نامعلوم» می‌گذارد.
     */
    suspend fun bulkPreview(session: Session, userIds: Set<Long>, kind: String): Int = withContext(Dispatchers.IO) {
        if (userIds.isEmpty()) return@withContext 0
        val path = if (kind == "days") "expire" else "data_limit"
        val body = JSONObject().apply {
            put("amount", 0)
            put("users", org.json.JSONArray(userIds))
            put("dry_run", true)
        }
        val request = requestBuilder(session, "${session.baseUrl}/api/users/bulk/$path")
            .post(body.toString().toRequestBody(jsonType)).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Preview failed: ${response.code}")
            JSONObject(response.body?.string() ?: "{}").optInt("affected_users", -1)
        }
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
     *
     * `target` یکی از `expired` / `limited` / `on_hold` / `disabled` / **`inactive`**.
     * `inactive` از **PasarGuard v5.5** اضافه شده و برخلافِ بقیه بر اساسِ «آخرین
     * فعالیت» (online_at، و اگر هرگز آنلاین نشده created_at) فیلتر می‌کند؛ پنل برای
     * همین حالت `expired_before` را **الزامی** کرده است
     * (`ExpiredUsersQuery.validate_inactive_cutoff`) و بدونِ آن ۴۲۲ می‌دهد. پس همان
     * قاعده را سمتِ اپ هم اعتبارسنجی می‌کنیم تا خطا پیش از رفتن به شبکه روشن باشد.
     *
     * پاسخ آرایه‌ای از نام‌های کاربری است، پس می‌شود قبل از حذف نشانشان داد.
     */
    suspend fun cleanupCandidates(
        session: Session,
        target: String = "expired",
        expiredBefore: String? = null
    ): List<String> = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, cleanupUrl(session, target, expiredBefore)).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Expired list failed: ${response.code} ${errorDetail(response.body?.string())}".trim())
            val arr = org.json.JSONArray(response.body?.string() ?: "[]")
            List(arr.length()) { i -> arr.optString(i) }
        }
    }

    /** حذفِ همان فهرست — `DELETE /api/users/expired?target=…` (همان قاعدهٔ `inactive`). */
    suspend fun deleteCleanupCandidates(
        session: Session,
        target: String = "expired",
        expiredBefore: String? = null
    ) = withContext(Dispatchers.IO) {
        executeJson(requestBuilder(session, cleanupUrl(session, target, expiredBefore)).delete().build())
    }

    /** ساختِ URLِ پاک‌سازی با همان اعتبارسنجیِ پنل (`inactive` بدونِ مهلت = ۴۲۲). */
    private fun cleanupUrl(session: Session, target: String, expiredBefore: String?): String {
        require(target in CLEANUP_TARGETS) { "Unknown cleanup target: $target" }
        val cutoff = expiredBefore?.trim()?.takeIf { it.isNotEmpty() }
        require(target != CLEANUP_INACTIVE || cutoff != null) {
            "expired_before is required for the inactive target"
        }
        return buildString {
            append(session.baseUrl); append("/api/users/expired?target="); append(target)
            if (cutoff != null) {
                append("&expired_before="); append(URLEncoder.encode(cutoff, "UTF-8"))
            }
        }
    }

    /**
     * فهرستِ سبکِ کاربران — `GET /api/users/simple` (فقط `id` و `username`).
     *
     * برای جست‌وجوی «فقط یک نام» (بدهکارِ سررسیدشده در ورکر پس‌زمینه، یا پیدا کردنِ
     * شناسهٔ یک نام) از `/api/users` با همهٔ فیلدهای سنگین‌اش استفاده نمی‌کنیم؛
     * پنل برای همین کارکرد اندپوینتِ ارزان‌تر دارد و روی پنل‌های چند‌هزار‌کاربری
     * تفاضلِ پارس/شبکه محسوس است.
     */
    suspend fun usersSimple(
        session: Session,
        search: String? = null,
        offset: Int = 0,
        limit: Int = 50
    ): List<UserRef> = withContext(Dispatchers.IO) {
        require(limit in 1..1000) { "limit must be between 1 and 1000" }
        val url = buildString {
            append(session.baseUrl); append("/api/users/simple?offset="); append(offset)
            append("&limit="); append(limit)
            search?.takeIf { it.isNotBlank() }?.let {
                append("&search="); append(URLEncoder.encode(it, "UTF-8"))
            }
        }
        val request = requestBuilder(session, url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Simple users failed: ${response.code} ${errorDetail(response.body?.string())}".trim())
            val arr = JSONObject(response.body?.string() ?: "{}").optJSONArray("users") ?: return@use emptyList()
            List(arr.length()) { i ->
                val u = arr.getJSONObject(i)
                UserRef(id = u.optLong("id", 0L), username = u.optString("username"))
            }
        }
    }

    /**
     * ذخیرهٔ ترتیبِ نمایشِ نودها — `PUT /api/nodes/order` (**PasarGuard v5.5**،
     * مجوزِ `nodes.update`، بدنه `{"ordered_ids": [..]}`).
     *
     * پنل حداقل دو شناسه و بدونِ تکرار می‌خواهد (`ReorderRequest`)، پس همان قواعد
     * سمتِ اپ بررسی می‌شوند تا درخواستِ بی‌معنا اصلاً نرود.
     */
    suspend fun reorderNodes(session: Session, orderedIds: List<Int>) = withContext(Dispatchers.IO) {
        require(orderedIds.size >= 2) { "Reordering needs at least two nodes" }
        require(orderedIds.size == orderedIds.distinct().size) { "Node ids must be unique" }
        val body = JSONObject().put("ordered_ids", org.json.JSONArray(orderedIds))
        executeJson(
            requestBuilder(session, "${session.baseUrl}/api/nodes/order")
                .put(body.toString().toRequestBody(jsonType)).build()
        )
    }

    /**
     * تنظیماتِ عمومیِ پنل — `GET /api/settings/general` (مجوزِ `settings.read_general`).
     * فقط برای خواندنِ `on_hold_timeout_action` استفاده می‌شود (v5.5)؛ اگر نقش مجوز
     * نداشته باشد ۴۰۳ می‌دهد و صداکننده باید سکوت کند، نه اینکه خطا نشان دهد.
     */
    suspend fun generalSettings(session: Session): PanelGeneralSettings = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, "${session.baseUrl}/api/settings/general").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("General settings failed: ${response.code} ${errorDetail(response.body?.string())}".trim())
            val o = JSONObject(response.body?.string() ?: "{}")
            val action = o.optString("on_hold_timeout_action", OnHoldAction.ACTIVATE)
            PanelGeneralSettings(
                defaultMethod = o.optString("default_method", ""),
                // مقدارِ ناشناخته → پیش‌فرضِ پنل (`activate`) تا متنِ UI چیزی از خودش نسازد.
                onHoldTimeoutAction = if (action in ON_HOLD_ACTIONS) action else OnHoldAction.ACTIVATE
            )
        }
    }

    suspend fun bulkApplyTemplate(session: Session, userIds: Set<Long>, templateId: Int, note: String = "") = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("ids", org.json.JSONArray(userIds))
            put("user_template_id", templateId)
            if (note.isNotBlank()) put("note", note)
        }
        executeJson(requestBuilder(session, "${session.baseUrl}/api/users/bulk/apply_template").post(body.toString().toRequestBody(jsonType)).build())
    }

    // ═══════════════════ زیرساختِ پنل (فاز ۲) ═══════════════════
    //
    // سه گروهِ «دادهٔ سطحِ سرور» که تا امروز فقط از پنلِ وب قابلِ مدیریت بودند:
    // هاست‌ها (سازندهٔ لینکِ اشتراک)، کورها (کانفیگِ Xray/WireGuard/MTProto/sing-box) و
    // قالب‌های کلاینت (خروجیِ اشتراک). همه با مجوزِ خودِ پنل گیت شده‌اند و هر
    // خطای ۴۰۳/۴۲۲ عیناً با `detail` به UI می‌رسد (نقش‌های محدود در v5.5
    // سخت‌گیرانه‌تر شده‌اند).

    /**
     * فهرستِ هاست‌ها — `GET /api/hosts` (مجوزِ `hosts.read`).
     *
     * `limit=0` در پنل یعنی «همه»؛ هاست‌ها معمولاً چند ده‌تایی هستند و صفحه‌بندی
     * برایشان معنا ندارد. پاسخ آرایهٔ خالص است، ولی پوششِ `{"hosts": […]}` هم
     * تحمل می‌شود تا با فورک‌های پنل نشکند.
     */
    suspend fun hosts(session: Session): List<PanelHost> = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, "${session.baseUrl}/api/hosts?limit=0&offset=0").get().build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Load hosts failed: ${response.code} ${errorDetail(raw)}".trim())
            val trimmed = raw.trim()
            val arr = if (trimmed.startsWith("[")) org.json.JSONArray(trimmed)
            else JSONObject(trimmed.ifBlank { "{}" }).optJSONArray("hosts") ?: org.json.JSONArray()
            List(arr.length()) { i -> parseHost(arr.getJSONObject(i)) }
        }
    }

    /**
     * ساختِ هاست — `POST /api/host/` (مجوزِ `hosts.create`).
     *
     * فقط کمینهٔ لازم فرستاده می‌شود: پنل بقیهٔ فیلدها را با پیش‌فرضِ خودش
     * می‌سازد. `inbound_tag` باید در یکی از کورها موجود باشد، وگرنه پنل ۴۰۰
     * با پیامِ روشن می‌دهد — همان پیام به کاربر نشان داده می‌شود.
     */
    suspend fun createHost(
        session: Session,
        remark: String,
        addresses: List<String>,
        inboundTag: String? = null,
        port: Int? = null,
        security: String = "inbound_default"
    ): PanelHost = withContext(Dispatchers.IO) {
        require(remark.isNotBlank()) { "Remark cannot be empty" }
        val cleanAddresses = addresses.map { it.trim() }.filter { it.isNotEmpty() }
        require(cleanAddresses.isNotEmpty()) { "At least one address is required" }
        val body = JSONObject()
            .put("remark", remark.trim())
            .put("address", org.json.JSONArray(cleanAddresses))
            .put("security", security)
            .put("priority", 0)
            .put("is_disabled", false)
            .put("alpn", org.json.JSONArray())
        if (!inboundTag.isNullOrBlank()) body.put("inbound_tag", inboundTag)
        port?.let { body.put("port", it) }
        val response = executeJsonForBody(
            requestBuilder(session, "${session.baseUrl}/api/host/")
                .post(body.toString().toRequestBody(jsonType)).build()
        )
        parseHost(response)
    }

    /**
     * ویرایشِ هاست — `PUT /api/host/{id}` (مجوزِ `hosts.update`).
     *
     * پنل برای PUT **کلِ** بدنهٔ `CreateHost` را می‌خواهد؛ اگر فقط فیلدهای فرم را
     * بفرستیم، بقیه (transport/mux/fragment/ECH/…) به پیش‌فرض برمی‌گردند و
     * هاستی که با پنلِ وب ساخته شده بی‌سروصدا خراب می‌شود. پس بدنهٔ خامِ همان
     * ردیف گرفته و فقط کلیدهای تغییرکرده رویش merge می‌شوند؛ مقدارِ `null`
     * یعنی «کلید را بردار».
     */
    suspend fun modifyHost(session: Session, host: PanelHost, changes: Map<String, Any?>): PanelHost =
        withContext(Dispatchers.IO) {
            val body = runCatching { JSONObject(host.raw) }.getOrElse { JSONObject() }
            changes.forEach { (key, value) ->
                // `value == null` کافی نبود: فرم برای «این فیلد را پاک کن» مقدارِ
                // `JSONObject.NULL` می‌فرستد (چون `Map<String, Any?>` است و برای
                // رسیدن به کاتلینِ `null` باید کلید را حذف کنند) و نتیجه این بود که
                // بدنه `"port": null` می‌گرفت — کلید همچنان *هست* و پنل آن را
                // «صریحاً خالی» می‌فهمد، نه «پیش‌فرضِ inbound». حالا هر دو شکل
                // یعنی «کلید را بردار».
                if (value == null || value === JSONObject.NULL) body.remove(key) else body.put(key, value)
            }
            val response = executeJsonForBody(
                requestBuilder(session, "${session.baseUrl}/api/host/${host.id}")
                    .put(body.toString().toRequestBody(jsonType)).build()
            )
            parseHost(response)
        }

    /** حذفِ هاست — `DELETE /api/host/{id}` (مجوزِ `hosts.update`، پاسخ ۲۰۴). */
    suspend fun deleteHost(session: Session, hostId: Int) = withContext(Dispatchers.IO) {
        executeJson(requestBuilder(session, "${session.baseUrl}/api/host/$hostId").delete().build())
    }

    /**
     * فعال/غیرفعال‌کردنِ گروهیِ هاست‌ها — `POST /api/hosts/bulk/{enable|disable}`
     * (مجوزِ `hosts.update`). پنل تعدادِ انجام‌شده را در `count` می‌دهد؛ همان
     * برگردانده می‌شود تا UI «۳ هاست غیرفعال شد» بگوید، نه یک پیامِ کلی.
     */
    suspend fun setHostsDisabled(session: Session, ids: Set<Int>, disabled: Boolean): Int =
        withContext(Dispatchers.IO) {
            require(ids.isNotEmpty()) { "No hosts selected" }
            val body = JSONObject().put("ids", org.json.JSONArray(ids.toList()))
            val action = if (disabled) "disable" else "enable"
            val response = executeJsonForBody(
                requestBuilder(session, "${session.baseUrl}/api/hosts/bulk/$action")
                    .post(body.toString().toRequestBody(jsonType)).build()
            )
            if (response.isNull("count")) ids.size else response.optInt("count", ids.size)
        }

    /**
     * فهرستِ کورها — `GET /api/cores` (مجوزِ `cores.read`).
     * بدونِ `limit` همه برمی‌گردند (`CoreListQuery.limit = None`).
     */
    suspend fun cores(session: Session): List<PanelCore> = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, "${session.baseUrl}/api/cores").get().build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Load cores failed: ${response.code} ${errorDetail(raw)}".trim())
            val arr = JSONObject(raw.ifBlank { "{}" }).optJSONArray("cores") ?: org.json.JSONArray()
            List(arr.length()) { i -> parseCore(arr.getJSONObject(i)) }
        }
    }

    /**
     * ویرایشِ کور — `PUT /api/cores/{id}?restart_nodes=…` (مجوزِ `cores.update`).
     *
     * دو نکتهٔ قرارداد که اگر رعایت نشوند درخواست همیشه ۴۲۲ می‌گیرد:
     *  ۱. `restart_nodes` یک query paramِ **الزامی** است (بدونِ مقدارِ پیش‌فرض در پنل)؛
     *  ۲. بدنه باید `config` کامل را داشته باشد — پس [PanelCore.config] خام و
     *     دست‌نخورده دوباره فرستاده می‌شود و فقط فیلدهای فرم عوض می‌شوند.
     */
    suspend fun modifyCore(
        session: Session,
        core: PanelCore,
        name: String,
        type: String?,
        excludeInboundTags: List<String>,
        fallbacksInboundTags: List<String>,
        restartNodes: Boolean
    ): PanelCore = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "Core name cannot be empty" }
        val config = runCatching { JSONObject(core.config) }.getOrElse { JSONObject() }
        require(config.length() > 0) { "Core config cannot be empty" }
        val body = JSONObject()
            .put("name", name.trim())
            .put("config", config)
            .put("exclude_inbound_tags", org.json.JSONArray(excludeInboundTags))
            .put("fallbacks_inbound_tags", org.json.JSONArray(fallbacksInboundTags))
        if (type.isNullOrBlank()) body.put("type", JSONObject.NULL) else body.put("type", type)
        val url = "${session.baseUrl}/api/cores/${core.id}?restart_nodes=$restartNodes"
        parseCore(
            executeJsonForBody(
                requestBuilder(session, url).put(body.toString().toRequestBody(jsonType)).build()
            )
        )
    }

    /** ری‌استارتِ نودهای یک کور — `POST /api/cores/{id}/restart` (مجوزِ `cores.update`، ۲۰۴). */
    suspend fun restartCore(session: Session, coreId: Int) = withContext(Dispatchers.IO) {
        executeJson(
            requestBuilder(session, "${session.baseUrl}/api/cores/$coreId/restart")
                .post("".toRequestBody(jsonType)).build()
        )
    }

    /** حذفِ کور — `DELETE /api/cores/{id}?restart_nodes=…` (مجوزِ `cores.delete`، ۲۰۴). */
    suspend fun deleteCore(session: Session, coreId: Int, restartNodes: Boolean = false) =
        withContext(Dispatchers.IO) {
            executeJson(
                requestBuilder(session, "${session.baseUrl}/api/cores/$coreId?restart_nodes=$restartNodes")
                    .delete().build()
            )
        }

    /**
     * فهرستِ قالب‌های کلاینت — `GET /api/client_templates` (مجوزِ `client_templates.read`).
     * برخلافِ `/api/user_templates` پاسخ **پوشیده** است: `{"count": n, "templates": […]}`
     * (آن یکی آرایهٔ خالص بود) — همین تفاوت قبلاً منبعِ باگِ پارس بوده است.
     */
    suspend fun clientTemplates(session: Session): List<ClientTemplate> = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, "${session.baseUrl}/api/client_templates").get().build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Load client templates failed: ${response.code} ${errorDetail(raw)}".trim())
            val arr = JSONObject(raw.ifBlank { "{}" }).optJSONArray("templates") ?: org.json.JSONArray()
            List(arr.length()) { i -> parseClientTemplate(arr.getJSONObject(i)) }
        }
    }

    /** ساختِ قالبِ کلاینت — `POST /api/client_templates` (مجوزِ `client_templates.create`). */
    suspend fun createClientTemplate(
        session: Session,
        name: String,
        templateType: String,
        content: String,
        isDefault: Boolean = false
    ): ClientTemplate = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "Template name cannot be empty" }
        require(content.isNotBlank()) { "Template content cannot be empty" }
        val body = JSONObject()
            .put("name", name.trim())
            .put("template_type", templateType)
            .put("content", content)
            .put("is_default", isDefault)
        parseClientTemplate(
            executeJsonForBody(
                requestBuilder(session, "${session.baseUrl}/api/client_templates")
                    .post(body.toString().toRequestBody(jsonType)).build()
            )
        )
    }

    /**
     * ویرایشِ قالبِ کلاینت — `PUT /api/client_templates/{id}` (مجوزِ `client_templates.update`).
     *
     * `template_type` در پنل تغییرپذیر نیست (`ClientTemplateModify` فقط نام/محتوا/
     * پیش‌فرض را می‌پذیرد)، پس انتخابِ نوع فقط در فرمِ «ساخت» دیده می‌شود.
     */
    suspend fun modifyClientTemplate(
        session: Session,
        templateId: Int,
        name: String? = null,
        content: String? = null,
        isDefault: Boolean? = null
    ): ClientTemplate = withContext(Dispatchers.IO) {
        val body = JSONObject()
        name?.let { body.put("name", it.trim()) }
        content?.let { body.put("content", it) }
        isDefault?.let { body.put("is_default", it) }
        require(body.length() > 0) { "Nothing to update" }
        parseClientTemplate(
            executeJsonForBody(
                requestBuilder(session, "${session.baseUrl}/api/client_templates/$templateId")
                    .put(body.toString().toRequestBody(jsonType)).build()
            )
        )
    }

    /** حذفِ قالبِ کلاینت — `DELETE /api/client_templates/{id}` (پاسخ ۲۰۰/۲۰۴). */
    suspend fun deleteClientTemplate(session: Session, templateId: Int) = withContext(Dispatchers.IO) {
        executeJson(
            requestBuilder(session, "${session.baseUrl}/api/client_templates/$templateId").delete().build()
        )
    }

    // ═══════════════════ کلیدهای API (فازِ ۵.۲) ═══════════════════
    //
    // مسیرها (PasarGuard 5.5.1): `GET /api/api_keys`، `POST /api/api_key`،
    // `POST /api/api_key/{id}/revoke` (چرخش؛ متنِ تازه برمی‌گرداند)،
    // `PATCH /api/api_key/{id}` (نام/یادداشت/وضعیت) و `DELETE /api/api_key/{id}`.
    // متنِ کاملِ کلید فقط در پاسخِ ساخت/چرخش می‌آید و اینجا هم جایی ذخیره نمی‌شود.

    /** فهرستِ کلیدهای API — `GET /api/api_keys` (مجوزِ `api_keys.read`). */
    suspend fun apiKeys(session: Session, limit: Int = 200): List<PanelApiKey> = withContext(Dispatchers.IO) {
        val request = requestBuilder(session, "${session.baseUrl}/api/api_keys?offset=0&limit=$limit")
            .get().build()
        val body = executeJsonForBody(request)
        val arr = body.optJSONArray("api_keys") ?: return@withContext emptyList()
        List(arr.length()) { i -> parseApiKey(arr.getJSONObject(i)) }
    }

    /**
     * ساختِ کلید — `POST /api/api_key` (مجوزِ `api_keys.create`، پاسخ ۲۰۱).
     *
     * `expireDays = null` یعنی «بی‌انقضا» (`expire_date: null`). پنل تاریخِ گذشته را
     * رد می‌کند و ارقامِ مثبت را می‌پذیرد.
     */
    suspend fun createApiKey(
        session: Session,
        name: String,
        note: String = "",
        expireDays: Long? = null
    ): CreatedApiKey = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "API key name cannot be empty" }
        val body = JSONObject()
            .put("name", name.trim())
            .put("note", note.trim().ifBlank { JSONObject.NULL })
            .put("expire_date", expireDays?.let { DateLogic.isoFromNow(it) } ?: JSONObject.NULL)
        parseCreatedApiKey(
            executeJsonForBody(
                requestBuilder(session, "${session.baseUrl}/api/api_key")
                    .post(body.toString().toRequestBody(jsonType)).build()
            )
        )
    }

    /**
     * چرخشِ کلید — `POST /api/api_key/{id}/revoke`.
     *
     * اسمِ مسیر «revoke» است ولی رفتارش **چرخش** است: همان کلید با متنِ تازه
     * برمی‌گردد و کلیدِ قبلی بی‌درنگ از کار می‌افتد. برای «غیرفعال‌کردن» از
     * [setApiKeyDisabled] استفاده کنید.
     */
    suspend fun rotateApiKey(session: Session, keyId: Int): CreatedApiKey = withContext(Dispatchers.IO) {
        parseCreatedApiKey(
            executeJsonForBody(
                requestBuilder(session, "${session.baseUrl}/api/api_key/$keyId/revoke")
                    .post(EMPTY_JSON.toRequestBody(jsonType)).build()
            )
        )
    }

    /** فعال/غیرفعال‌کردنِ کلید — `PATCH /api/api_key/{id}` با `status`. */
    suspend fun setApiKeyDisabled(session: Session, keyId: Int, disabled: Boolean): PanelApiKey =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put(
                "status",
                if (disabled) PanelApiKey.DISABLED else PanelApiKey.ACTIVE
            )
            parseApiKey(
                executeJsonForBody(
                    requestBuilder(session, "${session.baseUrl}/api/api_key/$keyId")
                        .patch(body.toString().toRequestBody(jsonType)).build()
                )
            )
        }

    /** حذفِ کلید — `DELETE /api/api_key/{id}` (پاسخ ۲۰۴). */
    suspend fun deleteApiKey(session: Session, keyId: Int) = withContext(Dispatchers.IO) {
        executeJson(
            requestBuilder(session, "${session.baseUrl}/api/api_key/$keyId").delete().build()
        )
    }

    private fun parseApiKey(o: JSONObject): PanelApiKey = PanelApiKey(
        id = o.optInt("id"),
        name = o.optString("name"),
        trimmed = o.optString("api_key_trimmed"),
        status = o.optString("status", PanelApiKey.ACTIVE),
        note = optionalString(o, "note"),
        adminId = o.optInt("admin_id"),
        createdAt = optionalString(o, "created_at"),
        expireDate = optionalString(o, "expire_date"),
        isExpired = o.optBoolean("is_expired", false),
        inheritPermissions = o.optBoolean("inherit_permissions", true)
    )

    private fun parseCreatedApiKey(o: JSONObject): CreatedApiKey =
        CreatedApiKey(key = o.optString("api_key"), item = parseApiKey(o))

    // ═══════════════════ تنظیماتِ پنل (فازِ ۵) ═══════════════════
    //
    // پنل تنظیماتش را در بخش‌های مستقل نگه می‌دارد و `PUT /api/settings` هر بخشِ
    // فرستاده‌شده را **کامل** جایگزین می‌کند (`model_dump(exclude_none=True)`).
    // پس هر تابعِ ذخیره، بخشِ کامل می‌فرستد و برای فیلدهای ناشناخته بدنهٔ خامِ
    // قبلی را نگه می‌دارد (همان قاعدهٔ هاست‌ها در فاز ۲). مجوزها:
    // `settings.read` برای خواندن و `settings.update` برای نوشتن؛ ۴۰۳ با `detail`
    // خودِ پنل به UI می‌رسد.

    /** بخش‌های مجازِ `PUT /api/settings` — نگهبانِ محلی برای اینکه کلیدِ اشتباه نرود. */
    private val PANEL_SETTINGS_SECTIONS =
        setOf("general", "subscription", "hwid", "notification_settings")

    /** همهٔ تنظیماتِ پنل — `GET /api/settings` (مجوزِ `settings.read`). */
    suspend fun panelSettings(session: Session): PanelSettings = withContext(Dispatchers.IO) {
        PanelSettings.from(
            executeJsonForBody(requestBuilder(session, "${session.baseUrl}/api/settings").get().build())
        )
    }

    /**
     * نوشتنِ یک بخش — `PUT /api/settings` با `{section: body}`.
     *
     * پاسخ، `SettingsSchema` کاملِ تازه است؛ همان را برمی‌گردانیم تا UI مقدارِ
     * نرمال‌شدهٔ سرور را نشان بدهد (مثلاً `url_prefix` که پنل `/`های دو طرف را
     * می‌بُرد، یا `custom_variables` که از `general` به `subscription` منتقل می‌شود).
     */
    private fun putPanelSection(session: Session, section: String, body: JSONObject): PanelSettings {
        require(section in PANEL_SETTINGS_SECTIONS) { "Unknown settings section: $section" }
        val payload = JSONObject().put(section, body)
        val request = requestBuilder(session, "${session.baseUrl}/api/settings")
            .put(payload.toString().toRequestBody(jsonType)).build()
        return PanelSettings.from(executeJsonForBody(request))
    }

    suspend fun savePanelGeneral(session: Session, section: PanelGeneralSection): PanelSettings =
        withContext(Dispatchers.IO) { putPanelSection(session, "general", section.toJson()) }

    suspend fun savePanelSubscription(session: Session, section: PanelSubscriptionSection): PanelSettings =
        withContext(Dispatchers.IO) { putPanelSection(session, "subscription", section.toJson()) }

    suspend fun savePanelHwid(session: Session, section: PanelHwidSection): PanelSettings =
        withContext(Dispatchers.IO) { putPanelSection(session, "hwid", section.toJson()) }

    suspend fun savePanelNotifications(session: Session, section: PanelNotificationSection): PanelSettings =
        withContext(Dispatchers.IO) { putPanelSection(session, "notification_settings", section.toJson()) }

    // ── پارسِ زیرساخت ────────────────────────────────────────────────────

    private fun parseHost(o: JSONObject): PanelHost = PanelHost(
        id = o.optInt("id"),
        remark = o.optString("remark"),
        addresses = jsonStringList(o, "address"),
        inboundTag = optionalString(o, "inbound_tag"),
        port = if (o.isNull("port")) null else o.optInt("port"),
        sni = jsonStringList(o, "sni"),
        host = jsonStringList(o, "host"),
        path = optionalString(o, "path"),
        security = optionalString(o, "security") ?: "inbound_default",
        alpn = jsonStringList(o, "alpn"),
        fingerprint = optionalString(o, "fingerprint") ?: "none",
        isDisabled = o.optBoolean("is_disabled", false),
        priority = o.optInt("priority", 0),
        status = jsonStringList(o, "status"),
        useSniAsHost = o.optBoolean("use_sni_as_host", false),
        randomUserAgent = o.optBoolean("random_user_agent", false),
        raw = o.toString()
    )

    private fun parseCore(o: JSONObject): PanelCore = PanelCore(
        id = o.optInt("id"),
        name = o.optString("name"),
        type = optionalString(o, "type"),
        excludeInboundTags = jsonStringList(o, "exclude_inbound_tags"),
        fallbacksInboundTags = jsonStringList(o, "fallbacks_inbound_tags"),
        sortOrder = o.optInt("sort_order", 0),
        // کانفیگ رشتهٔ خام می‌ماند تا PUT بعدی عیناً همان را برگرداند.
        config = o.optJSONObject("config")?.toString() ?: "{}"
    )

    private fun parseClientTemplate(o: JSONObject): ClientTemplate = ClientTemplate(
        id = o.optInt("id"),
        name = o.optString("name"),
        templateType = o.optString("template_type"),
        content = o.optString("content"),
        isDefault = o.optBoolean("is_default", false),
        isSystem = o.optBoolean("is_system", false)
    )

    /**
     * رشتهٔ اختیاری از JSON. `optString` برای مقدارِ null رشتهٔ `"null"` می‌دهد؛
     * این تابع آن حالت و رشتهٔ خالی را به `null` تبدیل می‌کند (مثلِ `inbound_tag`).
     */
    private fun optionalString(o: JSONObject, key: String): String? =
        o.optString(key).takeIf { it.isNotBlank() && it != "null" }

    /** آرایهٔ رشته‌ها؛ کلیدِ غایب یا null → لیستِ خالی (نه خطا). */
    private fun jsonStringList(o: JSONObject, key: String): List<String> {
        val arr = o.optJSONArray(key) ?: return emptyList()
        return List(arr.length()) { i -> arr.optString(i) }.filter { it.isNotBlank() && it != "null" }
    }

    /** مثلِ [executeJson] ولی بدنهٔ پاسخ را هم برمی‌گرداند (برای POST/PUT که آبجکت می‌دهند). */
    private fun executeJsonForBody(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Request failed: ${response.code} ${errorDetail(raw)}".trim())
            }
            return runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrElse { JSONObject() }
        }
    }

    private fun gbToBytes(value: Double): Long = (value * 1024 * 1024 * 1024).toLong()
    // تاریخ انقضا را به‌صورت «اکنون + N روز» می‌فرستیم (سازگار با رفتار پنل و بدون خطای گردکردن).
    // تاریخ امروز/گذشته → 0 (نامحدود) تا کاربر ناخواسته «منقضی» ساخته نشود.
    private fun expireValue(date: String): Any = DateLogic.expireValue(date)
}
