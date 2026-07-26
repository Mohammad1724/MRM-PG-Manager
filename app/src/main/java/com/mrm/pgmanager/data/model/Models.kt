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
    val groupNames: List<String> = emptyList()
)

data class SystemStats(
    val uptimeSeconds: Long = 0,
    val memTotal: Long = 0,
    val memUsed: Long = 0,
    val diskTotal: Long = 0,
    val diskUsed: Long = 0,
    val cpuCores: Int = 0,
    val cpuUsage: Float = 0f,
    val totalUsers: Int = 0,
    val onlineUsers: Int = 0,
    val activeUsers: Int = 0,
    val expiredUsers: Int = 0,
    val limitedUsers: Int = 0,
    val disabledUsers: Int = 0,
    val onHoldUsers: Int = 0,
    val incomingBandwidth: Long = 0,
    val outgoingBandwidth: Long = 0
)

data class TrafficPoint(val timestamp: String, val totalTraffic: Long)

data class Group(val id: Int, val name: String)
data class UserTemplateItem(
    val id: Int,
    val name: String,
    val dataLimit: Long? = null,
    /** مدت انقضای تمپلت بر حسب ثانیه */
    val expireDuration: Long? = null
)

enum class UserFilter { ALL, ACTIVE, NEAR_LIMIT, EXPIRED, DISABLED }
enum class UserSort { NAME, USAGE, EXPIRY, CREATED }
enum class ViewMode { GRID, COMPACT_LIST, MICRO_LIST }

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
