package com.mrm.pgmanager.data.model

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
    var groupNames: List<String> = emptyList()
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
enum class CountMetric(val apiName: String, val label: String) {
    ONLINE("online", "کاربران آنلاین"),
    EXPIRED("expired", "کاربران منقضی"),
    LIMITED("limited", "کاربران محدود")
}
data class UserTemplateItem(
    val id: Int,
    val name: String,
    val dataLimit: Long? = null,
    /** مدت انقضای تمپلت بر حسب ثانیه */
    val expireDuration: Long? = null
)

enum class UserFilter { ALL, ACTIVE, NEAR_LIMIT, EXPIRED, DISABLED, DEBTOR }
enum class UserSort { NAME, USAGE, EXPIRY, CREATED }
enum class ViewMode { GRID, COMPACT_LIST, MICRO_LIST }

data class DebtorInfo(
    val username: String,
    val baseUrl: String,
    val amount: Long,
    val currency: String = "تومان",
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
