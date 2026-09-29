package com.mrm.pgmanager.data.model

import com.mrm.pgmanager.R

data class Session(val baseUrl: String, val token: String, val username: String) {
    /**
     * آیا «توکن» در واقع کلید API پنل است؟ (پیشوندِ `pg_key_`)
     * کلید API با هدرِ `X-Api-Key` فرستاده می‌شود و برخلافِ JWT (پیش‌فرض ۲۴ ساعته)
     * تا وقتی ادمین آن را باطل نکند معتبر می‌ماند — برای مانیتورینگِ پس‌زمینه ایده‌آل است.
     */
    val isApiKey: Boolean get() = token.startsWith(API_KEY_PREFIX)

    companion object {
        const val API_KEY_PREFIX = "pg_key_"
    }
}

/** دامنهٔ یک مجوزِ کاربری در نقشِ ادمین — همان `PermissionScope` پنل. */
object PermissionScope {
    const val NONE = 0
    const val OWN = 1
    const val ALL = 2
}

/**
 * ادمینِ واردشده و مجوزهایش — پاسخِ `GET /api/admin`.
 *
 * `permissions` نگاشتِ `resource → action → scope` است؛ در پنل مقدارِ هر action یا
 * `true` (مجاز، بدون scope → اینجا [PermissionScope.ALL]) یا `{"scope": N}` است و
 * نبودنش یعنی ممنوع. مالک (`role.is_owner`) از همهٔ بررسی‌ها معاف است.
 */
data class AdminSelf(
    val id: Int,
    val username: String,
    val isOwner: Boolean = false,
    val roleName: String = "",
    val permissions: Map<String, Map<String, Int>> = emptyMap(),
    /** `role.access.require_template`: ساختِ کاربر فقط از روی قالب مجاز است. */
    val requireTemplate: Boolean = false,
    val allowedTemplateIds: List<Int>? = null,
    val allowedGroupIds: List<Int>? = null,
    val canUseResetStrategy: Boolean = true,
    val canUseNextPlan: Boolean = true,
    val maxUsers: Int? = null,
    val totalUsers: Int = 0
) {
    /** بیشترین scope مجاز برای `resource.action`؛ مالک همیشه [PermissionScope.ALL]. */
    fun scope(resource: String, action: String): Int =
        if (isOwner) PermissionScope.ALL else permissions[resource]?.get(action) ?: PermissionScope.NONE

    fun can(resource: String, action: String): Boolean = scope(resource, action) > PermissionScope.NONE
}

data class PanelUser(
    val id: Long,
    val username: String,
    val status: String,
    val usedTraffic: Long,
    val dataLimit: Long,
    val expire: String?,
    val createdAt: String?,
    val subUrl: String = "",
    val onlineAt: String? = null,
    val isOnline: Boolean = false,
    val note: String? = null,
    val hwidLimit: Int? = null,
    val groupIds: List<Int> = emptyList(),
    var groupNames: List<String> = emptyList(),
    /** مصرفِ کل از ابتدا — با ریستِ مصرف صفر نمی‌شود. */
    val lifetimeUsedTraffic: Long = 0L,
    /** ادمینِ مالکِ کاربر (در پنل‌های چندادمینی). */
    val ownerAdmin: String? = null,
    /** پلنی که پس از تمام‌شدنِ پلنِ فعلی خودکار اعمال می‌شود. */
    val nextPlan: NextPlan? = null,
    /** فقط برای `on_hold`: مدت اعتبار (ثانیه) که با اولین اتصال کاربر شروع می‌شود. */
    val onHoldExpireDuration: Long? = null,
    /** فقط برای `on_hold`: اگر تا این زمان وصل نشود، پنل خودش فعالش می‌کند (اختیاری). */
    val onHoldTimeout: String? = null
) {
    /** مدت اعتبارِ on_hold به روز (گِرد به بالا)؛ null اگر کاربر on_hold نیست. */
    val onHoldDays: Int?
        get() = onHoldExpireDuration?.takeIf { status == "on_hold" && it > 0 }
            ?.let { ((it + 86_399L) / 86_400L).toInt() }
}

/**
 * ادمینِ پنل — برای بخشِ «ادمین‌ها» در داشبورد.
 * فقط ادمینی که دسترسیِ `admins:read` دارد می‌تواند این فهرست را بگیرد؛
 * برای بقیه پنل ۴۰۳ برمی‌گرداند و بخش پنهان می‌شود.
 */
data class PanelAdmin(
    val id: Int,
    val username: String,
    val totalUsers: Int = 0,
    val usedTraffic: Long = 0L,
    val dataLimit: Long? = null,
    val status: String = "active",
    val isOwner: Boolean = false
)

data class SystemStats(
    val uptimeSeconds: Long = 0L,
    val memTotal: Long = 0L,
    val memUsed: Long = 0L,
    val diskTotal: Long = 0L,
    val diskUsed: Long = 0L,
    val cpuCores: Int = 0,
    val cpuUsage: Float = 0f,
    val totalUsers: Int = 0,
    val onlineUsers: Int = 0,
    val activeUsers: Int = 0,
    val expiredUsers: Int = 0,
    val limitedUsers: Int = 0,
    val disabledUsers: Int = 0,
    val onHoldUsers: Int = 0,
    val incomingBandwidth: Long = 0L,
    val outgoingBandwidth: Long = 0L,
    /** نسخهٔ پنل (`version` در `GET /api/system`) — برای نمایش در تنظیمات و عیب‌یابی. */
    val version: String = ""
)

data class TrafficPoint(val timestamp: String, val totalTraffic: Long)

data class Group(val id: Int, val name: String)

/**
 * گروهِ کامل — برای صفحهٔ مدیریت گروه‌ها.
 * `Group` سبک (id+name) دست‌نخورده می‌ماند چون در انتخابگرِ گروهِ کاربران استفاده می‌شود.
 * پنل نام را بین ۳ تا ۶۴ کاراکتر می‌پذیرد و برای ساخت، حداقل یک inbound tag لازم است.
 */
data class GroupDetail(
    val id: Int,
    val name: String,
    val inboundTags: List<String> = emptyList(),
    val isDisabled: Boolean = false,
    val totalUsers: Int = 0
) {
    companion object {
        const val NAME_MIN = 3
        const val NAME_MAX = 64
    }
}

/** نتیجهٔ اعتبارسنجیِ فرمِ گروه — پیام خطا یا null اگر معتبر باشد. */
/**
 * قواعدِ نامِ کاربری — عیناً `UsernameValidatorMixin.validate_username` پنل:
 * ۳ تا ۱۲۸ کاراکتر، فقط `a-z A-Z 0-9 - _ @ .`، بدونِ دو کاراکترِ خاصِ پشتِ‌سرِ‌هم.
 * بررسیِ محلی فقط برای بازخوردِ فوری است؛ حرفِ آخر را پنل می‌زند (۴۲۲).
 */
object UsernameValidation {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 128
    const val ERR_LENGTH = "username_length"
    const val ERR_CHARS = "username_chars"
    const val ERR_CONSECUTIVE = "username_consecutive"

    private val allowed = Regex("^[a-zA-Z0-9\\-_@.]+$")
    private val consecutiveSpecials = Regex("[\\-_@.]{2,}")

    /** کلیدِ خطا یا null اگر معتبر باشد. */
    fun validate(raw: String): String? {
        val username = raw.trim()
        if (username.length !in MIN_LENGTH..MAX_LENGTH) return ERR_LENGTH
        if (!allowed.matches(username)) return ERR_CHARS
        if (consecutiveSpecials.containsMatchIn(username)) return ERR_CONSECUTIVE
        return null
    }

    fun isValid(raw: String): Boolean = validate(raw) == null
}

object GroupValidation {
    /** کلیدهای خطا؛ ترجمه در لایهٔ UI انجام می‌شود تا منطق قابل تست بماند. */
    const val ERR_NAME_SHORT = "name_short"
    const val ERR_NAME_LONG = "name_long"
    const val ERR_NO_INBOUND = "no_inbound"

    /** نامِ گروه را طبق قواعدِ پنل بررسی می‌کند (۳..۶۴ کاراکتر پس از trim). */
    fun validateName(raw: String): String? {
        val name = raw.trim()
        return when {
            name.length < GroupDetail.NAME_MIN -> ERR_NAME_SHORT
            name.length > GroupDetail.NAME_MAX -> ERR_NAME_LONG
            else -> null
        }
    }

    /** هنگام ساخت، پنل حداقل یک inbound tag می‌خواهد (GroupCreate). */
    fun validateInbounds(tags: List<String>, isCreate: Boolean): String? =
        if (isCreate && tags.isEmpty()) ERR_NO_INBOUND else null

    /** اعتبارسنجی کاملِ فرم؛ اولین خطا برگردانده می‌شود. */
    fun validate(name: String, tags: List<String>, isCreate: Boolean): String? =
        validateName(name) ?: validateInbounds(tags, isCreate)
}

/** نودِ پنل — برای فیلترِ نمودارهای آمار. */
/**
 * نودِ پنل.
 *
 * `status` یکی از مقادیرِ پنل است: connected / connecting / error / disabled /
 * limited. رشته نگه داشته شده تا اگر پنل حالتِ تازه‌ای اضافه کرد، اپ نترکد.
 */
data class PanelNode(
    val id: Int,
    val name: String,
    val address: String = "",
    val status: String = "",
    val message: String? = null,
    val xrayVersion: String? = null,
    val nodeVersion: String? = null,
    val uplink: Long = 0L,
    val downlink: Long = 0L
) {
    val isConnected: Boolean get() = status == "connected"
    val isDisabled: Boolean get() = status == "disabled"
}

/** آمارِ لحظه‌ایِ یک نود — `GET /api/nodes/realtime_stats`. */
data class NodeRealtime(
    val memTotal: Long = 0L,
    val memUsed: Long = 0L,
    val cpuCores: Int = 0,
    val cpuUsage: Float = 0f,
    val incomingSpeed: Long = 0L,
    val outgoingSpeed: Long = 0L,
    val uptimeSeconds: Long = 0L
)

/** یک دستگاهِ ثبت‌شدهٔ کاربر (HWID). */
data class UserDevice(
    val id: Int,
    val hwid: String,
    val deviceOs: String? = null,
    val osVersion: String? = null,
    val deviceModel: String? = null,
    val createdAt: String? = null,
    val lastUsedAt: String? = null
)

/**
 * «پلنِ بعدی» — وقتی حجم یا زمانِ کاربر تمام شود، پنل خودش این پلن را اعمال
 * می‌کند. یا از روی یک قالب، یا با حجم/مدتِ دستی.
 */
data class NextPlan(
    val templateId: Int? = null,
    val dataLimit: Long? = null,
    val expireSeconds: Long? = null,
    val addRemainingTraffic: Boolean = false
)

/** نتیجهٔ ساخت گروهیِ سمت‌سرور. */
data class BulkCreateResult(val created: Int, val subscriptionUrls: List<String> = emptyList())

/**
 * بازهٔ زمانیِ نمودارهای آمار.
 * `period` باید یکی از مقادیرِ مجازِ پنل باشد: minute | hour | day | month
 */
enum class StatsRange(val label: String, val period: String, private val seconds: Long) {
    LAST_1H("1h", "minute", 3_600L),
    LAST_6H("6h", "hour", 21_600L),
    LAST_24H("24h", "hour", 86_400L),
    LAST_3D("3d", "hour", 259_200L),
    LAST_7D("7d", "day", 604_800L),
    LAST_30D("30d", "day", 2_592_000L);

    /** زمانِ شروع به‌صورت ISO-8601 در UTC. */
    fun startIso(): String = java.time.Instant.now().minusSeconds(seconds).toString()
}

/** متریکِ نمودار «تعداد کاربران» — مطابق `UserCountMetric` در پنل. */
enum class CountMetric(val apiName: String, @androidx.annotation.StringRes val labelRes: Int) {
    ONLINE("online", R.string.metric_online),
    EXPIRED("expired", R.string.metric_expired),
    LIMITED("limited", R.string.metric_limited)
}
data class UserTemplateItem(
    val id: Int,
    val name: String,
    val dataLimit: Long? = null,
    /** مدت انقضای تمپلت بر حسب ثانیه */
    val expireDuration: Long? = null,
    /** سقفِ تعداد دستگاه (HWID). null یعنی نامحدود. */
    val hwidLimit: Int? = null,
    /** پیشوندِ نامِ کاربری که پنل هنگام ساخت اضافه می‌کند. */
    val usernamePrefix: String? = null,
    /** پسوندِ نامِ کاربری. */
    val usernameSuffix: String? = null,
    /** گروه‌هایی که کاربرِ ساخته‌شده از این تمپلت به آن‌ها می‌پیوندد. */
    val groupIds: List<Int> = emptyList(),
    /** وضعیتِ اولیهٔ کاربر: active یا on_hold. */
    val status: String? = null,
    /** استراتژیِ ریستِ حجم: no_reset / day / week / month / year. */
    val dataLimitResetStrategy: String = TemplateOptions.RESET_NO_RESET,
    /** مهلتِ فعال‌سازی برای وضعیتِ on_hold، بر حسب ثانیه. */
    val onHoldTimeout: Long? = null,
    /** ریستِ مصرف هنگام اعمالِ تمپلت. */
    val resetUsages: Boolean? = null,
    val isDisabled: Boolean? = null,
    /** روشِ رمزنگاریِ Shadowsocks در extra_settings. */
    val ssMethod: String? = null
) {
    companion object {
        const val NAME_MAX = 64
        /** پنل `max_length=20` روی پیشوند و پسوند می‌گذارد. */
        const val AFFIX_MAX = 20
        /** سقفِ expire_duration در پنل (MAX_ON_HOLD_EXPIRE_DURATION_SECONDS). */
        const val MAX_EXPIRE_SECONDS = 2_147_483_647L
    }
}

/**
 * مقادیرِ مجازِ enumهای تمپلت — دقیقاً مطابقِ پنل.
 * رشته‌ای نگه داشته شده‌اند تا افزوده‌شدنِ مقدارِ جدید در پنل باعثِ crash نشود.
 */
object TemplateOptions {
    const val STATUS_ACTIVE = "active"
    const val STATUS_ON_HOLD = "on_hold"
    val STATUSES = listOf(STATUS_ACTIVE, STATUS_ON_HOLD)

    const val RESET_NO_RESET = "no_reset"
    val RESET_STRATEGIES = listOf(RESET_NO_RESET, "day", "week", "month", "year")

    val SS_METHODS = listOf(
        "aes-128-gcm",
        "aes-256-gcm",
        "chacha20-ietf-poly1305",
        "xchacha20-poly1305"
    )
}

/**
 * اعتبارسنجیِ فرمِ تمپلت — آینهٔ قواعدِ پنل (`app/models/user_template.py`
 * و `UserValidator.validate_username`). کلیدِ خطا برمی‌گرداند؛ ترجمه در UI.
 */
object TemplateValidation {
    const val ERR_NAME_EMPTY = "tpl_name_empty"
    const val ERR_NAME_LONG = "tpl_name_long"
    const val ERR_NO_GROUP = "tpl_no_group"
    const val ERR_AFFIX_LONG = "tpl_affix_long"
    const val ERR_AFFIX_CHARS = "tpl_affix_chars"
    const val ERR_AFFIX_CONSECUTIVE = "tpl_affix_consecutive"
    const val ERR_EXPIRE_RANGE = "tpl_expire_range"
    const val ERR_DATA_NEGATIVE = "tpl_data_negative"

    /** پنل نامِ خالی را رد می‌کند و ستونِ دیتابیس `String(64)` است. */
    fun validateName(raw: String): String? {
        val name = raw.trim()
        return when {
            name.isEmpty() -> ERR_NAME_EMPTY
            name.length > UserTemplateItem.NAME_MAX -> ERR_NAME_LONG
            else -> null
        }
    }

    /**
     * پیشوند/پسوندِ نامِ کاربری. پنل با `len_check=false, accept_null=true`
     * صدا می‌زند: خالی مجاز است، ولی اگر مقدار داشته باشد باید
     * `^[a-zA-Z0-9-_@.]+$` باشد و دو کاراکترِ خاصِ پشت‌سرهم نداشته باشد.
     */
    fun validateAffix(raw: String?): String? {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return null
        if (v.length > UserTemplateItem.AFFIX_MAX) return ERR_AFFIX_LONG
        if (!v.all { it.isLetterOrDigit() && it.code < 128 || it in "-_@." }) return ERR_AFFIX_CHARS
        val special = "-_@."
        for (i in 0 until v.length - 1) {
            if (v[i] in special && v[i + 1] in special) return ERR_AFFIX_CONSECUTIVE
        }
        return null
    }

    /** ساخت حداقل یک گروه می‌خواهد (`ListValidator.not_null_list`). */
    fun validateGroups(groupIds: List<Int>): String? =
        if (groupIds.isEmpty()) ERR_NO_GROUP else null

    /** `expire_duration` باید بینِ ۰ و MAX باشد. */
    fun validateExpire(seconds: Long?): String? {
        if (seconds == null) return null
        return if (seconds < 0 || seconds > UserTemplateItem.MAX_EXPIRE_SECONDS) ERR_EXPIRE_RANGE else null
    }

    /** `data_limit` باید ≥ ۰ باشد. */
    fun validateDataLimit(bytes: Long?): String? {
        if (bytes == null) return null
        return if (bytes < 0) ERR_DATA_NEGATIVE else null
    }

    /**
     * اعتبارسنجیِ کاملِ فرم. [requireGroup] در حالتِ ساخت true است؛
     * در ویرایش پنل `group_ids` را nullable می‌پذیرد.
     */
    fun validateAll(
        name: String,
        groupIds: List<Int>,
        prefix: String?,
        suffix: String?,
        dataLimit: Long?,
        expireSeconds: Long?,
        requireGroup: Boolean = true
    ): String? = validateName(name)
        ?: (if (requireGroup) validateGroups(groupIds) else null)
        ?: validateAffix(prefix)
        ?: validateAffix(suffix)
        ?: validateDataLimit(dataLimit)
        ?: validateExpire(expireSeconds)
}

/**
 * فیلترهای فهرستِ کاربران.
 *
 * پنج‌تای اول را **پنل** فیلتر می‌کند (`status` روی `/api/users`)، پس فقط همان
 * کاربرها از شبکه می‌آیند. دوتای آخر مفهومِ محلی‌اند: «نزدیک به سقف» با درصدِ
 * دلخواهِ کاربر حساب می‌شود و «بدهکار» اصلاً در پنل وجود ندارد؛ برای آن دو،
 * فهرستِ کامل گرفته و در گوشی فیلتر می‌شود.
 */
enum class UserFilter(val panelStatus: String?, private val local: Boolean = false) {
    ALL(null),
    ACTIVE("active"),
    /** کاربرانِ آنلاین — پنل خودش با `online=true` (پنجرهٔ ۲ دقیقه‌ای) فیلتر می‌کند. */
    ONLINE(null),
    EXPIRED("expired"),
    LIMITED("limited"),
    ON_HOLD("on_hold"),
    DISABLED("disabled"),
    /** در آستانهٔ انقضا — پنل با `expire_after=now&expire_before=now+N` فیلتر می‌کند (N = [UserQuery.expiringWindowDays]). */
    EXPIRING_SOON(null),
    /** بدون سقفِ حجم — `no_data_limit=true`. */
    NO_LIMIT(null),
    /** بدون تاریخِ انقضا — `no_expire=true` (پنل کاربرانِ on_hold را خودش کنار می‌گذارد). */
    NO_EXPIRE(null),
    /** بدون هیچ گروهی — `no_group=true`؛ این‌ها عملاً هیچ اینباندی ندارند. */
    NO_GROUP(null),
    /** محلی: درصدِ مصرف از آستانهٔ تنظیمات گذشته. */
    NEAR_LIMIT(null, local = true),
    /** محلی: در دفترِ بدهکارانِ خودِ برنامه است. */
    DEBTOR(null, local = true);

    /** پارامترِ `online` پنل؛ فقط برای فیلترِ آنلاین `true` است. */
    val panelOnline: Boolean? get() = if (this == ONLINE) true else null
    val panelNoDataLimit: Boolean? get() = if (this == NO_LIMIT) true else null
    val panelNoExpire: Boolean? get() = if (this == NO_EXPIRE) true else null
    val panelNoGroup: Boolean? get() = if (this == NO_GROUP) true else null

    /** آیا پنل می‌تواند این فیلتر را خودش اعمال کند؟ (فقط بدهکار و لبِ مرز محلی‌اند) */
    val serverSide: Boolean get() = !local
}

/**
 * پارامترهای جست‌وجوی سمتِ سرور — `GET /api/users`.
 * نام‌ها دقیقاً مطابقِ `UserListQuery` پنل‌اند.
 */
data class UserQuery(
    val search: String? = null,
    val status: String? = null,
    /** `online=true` → فقط کاربرانی که در پنجرهٔ آنلاینِ پنل (۲ دقیقه) فعال بوده‌اند. */
    val online: Boolean? = null,
    val groupId: Int? = null,
    /** مقدارهای مجاز پنل: `username`, `used_traffic`, `expire`, `created_at`… با `-` برای نزولی. */
    val sort: String? = null,
    /** بازهٔ انقضا (ISO-8601 با ناحیهٔ زمانی؛ پنل `OptionalAwareDatetime` می‌خواهد). */
    val expireAfter: String? = null,
    val expireBefore: String? = null,
    /** `no_data_limit` / `no_expire` / `no_group` پنل — فقط وقتی true فرستاده می‌شوند. */
    val noDataLimit: Boolean? = null,
    val noExpire: Boolean? = null,
    val noGroup: Boolean? = null,
    /** نامِ ادمینِ مالک (`admin=`) — برای پنل‌های چندادمینی. */
    val admin: String? = null,
    val offset: Int = 0,
    val limit: Int = 60
) {
    companion object {
        /** کفِ بازهٔ «در آستانهٔ انقضا»؛ اگر آستانهٔ اعلانِ تنظیمات بزرگ‌تر باشد همان ملاک است. */
        const val MIN_EXPIRING_WINDOW_DAYS = 7

        fun expiringWindowDays(nearExpiryDays: Int): Int = maxOf(MIN_EXPIRING_WINDOW_DAYS, nearExpiryDays)

        /**
         * بازهٔ [now, now + days] به‌صورتِ ISO-8601 (UTC) برای `expire_after`/`expire_before`.
         * `expire_after=now` کاربرانِ از قبل منقضی را کنار می‌گذارد.
         */
        fun expiringWindow(days: Int, now: java.time.Instant = java.time.Instant.now()): Pair<String, String> {
            val start = now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
            val end = start.plus(days.toLong(), java.time.temporal.ChronoUnit.DAYS)
            return start.toString() to end.toString()
        }
    }
}

/** یک صفحه از فهرستِ کاربران به‌همراه تعدادِ کلِ نتیجه. */
data class UsersPage(val users: List<PanelUser>, val total: Int)
/** ترتیبِ فهرست — به کلیدهای `sort` پنل نگاشت می‌شود. */
enum class UserSort(val panelSort: String) {
    NAME("username"),
    USAGE("-used_traffic"),
    EXPIRY("expire"),
    CREATED("-created_at"),
    /** آخرین فعالیت — تازه‌ترین `online_at` اول. */
    LAST_ONLINE("-online_at")
}
enum class ViewMode { GRID, COMPACT_LIST, MICRO_LIST }

data class DebtorInfo(
    val username: String,
    val baseUrl: String,
    val amount: Long,
    val currency: String = "",
    val markedAt: Long,
    val notes: String = "",
    val autoDisabled: Boolean = false,
    val userId: Long = 0L
) {
    fun isOverdue(afterHours: Int): Boolean {
        if (afterHours <= 0) return false
        val deadline = markedAt + afterHours * 3600_000L
        return System.currentTimeMillis() > deadline
    }
    fun overdueHours(afterHours: Int): Int {
        if (!isOverdue(afterHours)) return 0
        val diff = System.currentTimeMillis() - (markedAt + afterHours * 3600_000L)
        return (diff / 3600_000L).toInt()
    }
}

/** الگوی ساخت نام کاربری خودکار؛ هم برای دکمهٔ تصادفی فرم و هم برای ساخت گروهی. */
data class UsernamePattern(
    val prefix: String = "user",
    /** تعداد ارقام بخش تصادفی (۳ تا ۶). */
    val randomDigits: Int = 4,
    /** شروع شمارش در حالت ترتیبی. */
    val sequentialStart: Int = 1,
    /** true = ترتیبی (user-001)، false = تصادفی (user-4821). */
    val sequential: Boolean = false
) {
    fun sequentialName(index: Int): String = "$prefix-${(sequentialStart + index).toString().padStart(3, '0')}"
    fun randomName(): String {
        val d = randomDigits.coerceIn(1, 6)
        var lo = 1; for (i in 1 until d) lo *= 10
        var hi = lo * 10
        if (d == 1) { lo = 0; hi = 10 }
        val n = (lo until hi).random()
        val minLen = if (d == 1) 1 else d
        return "$prefix-${n.toString().padStart(minLen, '0')}"
    }
}

data class UserEditorValues(
    val username: String,
    val value: Double,
    val note: String = "",
    val hwidLimit: Int? = null,
    val groupIds: List<Int> = emptyList(),
    /**
     * پلنی که پس از تمام‌شدنِ این پلن خودکار جایش می‌نشیند.
     * `null` یعنی دست نزن؛ [NextPlan] با همهٔ فیلدهای خالی یعنی پاکش کن.
     */
    val nextPlan: NextPlan? = null,
    /** no_reset / day / week / month / year */
    val resetStrategy: String = TemplateOptions.RESET_NO_RESET,
    /** حذفِ خودکار پس از انقضا؛ null یعنی هرگز. */
    val autoDeleteDays: Int? = null,
    /**
     * وضعیتی که باید به پنل فرستاده شود: `on_hold` (با [onHoldExpireSeconds]) یا
     * `active` (برای بیرون‌آوردن از on_hold). null یعنی وضعیت را دست نزن؛
     * غیرفعال/فعال‌کردن همچنان از مسیر `/disabled` می‌رود.
     */
    val status: String? = null,
    /** مدت اعتبار پس از اولین اتصال (ثانیه) — فقط وقتی [status] برابر `on_hold` است. */
    val onHoldExpireSeconds: Long? = null,
    /** مهلت فعال‌سازی از الان (ثانیه)؛ 0 یعنی پاکش کن؛ null یعنی دست نزن. */
    val onHoldTimeoutSeconds: Long? = null
)
