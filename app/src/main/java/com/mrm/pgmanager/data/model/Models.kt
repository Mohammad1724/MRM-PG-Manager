package com.mrm.pgmanager.data.model

import com.mrm.pgmanager.R

data class Session(val baseUrl: String, val token: String, val username: String)

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
    val ownerAdmin: String? = null
)

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
    val outgoingBandwidth: Long = 0L
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
data class PanelNode(val id: Int, val name: String)

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
enum class UserFilter(val panelStatus: String?) {
    ALL(null),
    ACTIVE("active"),
    EXPIRED("expired"),
    LIMITED("limited"),
    ON_HOLD("on_hold"),
    DISABLED("disabled"),
    NEAR_LIMIT(null),
    DEBTOR(null);

    /** آیا پنل می‌تواند این فیلتر را خودش اعمال کند؟ */
    val serverSide: Boolean get() = this == ALL || panelStatus != null
}

/**
 * پارامترهای جست‌وجوی سمتِ سرور — `GET /api/users`.
 * نام‌ها دقیقاً مطابقِ `UserListQuery` پنل‌اند.
 */
data class UserQuery(
    val search: String? = null,
    val status: String? = null,
    val groupId: Int? = null,
    /** مقدارهای مجاز پنل: `username`, `used_traffic`, `expire`, `created_at`… با `-` برای نزولی. */
    val sort: String? = null,
    val offset: Int = 0,
    val limit: Int = 60
)

/** یک صفحه از فهرستِ کاربران به‌همراه تعدادِ کلِ نتیجه. */
data class UsersPage(val users: List<PanelUser>, val total: Int)
/** ترتیبِ فهرست — به کلیدهای `sort` پنل نگاشت می‌شود. */
enum class UserSort(val panelSort: String) {
    NAME("username"),
    USAGE("-used_traffic"),
    EXPIRY("expire"),
    CREATED("-created_at")
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

data class UserEditorValues(val username: String, val value: Double, val note: String = "", val hwidLimit: Int? = null, val groupIds: List<Int> = emptyList())
