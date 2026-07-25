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

data class UserEditorValues(val username: String, val value: Double, val note: String = "", val hwidLimit: Int? = null, val groupIds: List<Int> = emptyList())
