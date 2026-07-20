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

data class Group(val id: Int, val name: String)

enum class UserFilter { ALL, ACTIVE, NEAR_LIMIT, EXPIRED, DISABLED }
enum class UserSort { NAME, USAGE, EXPIRY, CREATED }
enum class ViewMode { GRID, COMPACT_LIST, MICRO_LIST }

data class UserEditorValues(val username: String, val value: Double, val note: String = "", val hwidLimit: Int? = null, val groupIds: List<Int> = emptyList())

enum class AppTab(val labelFa: String, val icon: String) {
    USERS("کاربران", "👥"),
    HOSTS("هاست‌ها", "🌐")
}

data class PanelHost(
    val id: Long,
    val remark: String,
    val address: List<String>,
    val inboundTag: String,
    val port: Int?,
    val sni: List<String>,
    val host: List<String>,
    val path: String?,
    val security: String,
    val fingerprint: String,
    val alpn: List<String>,
    val allowInsecure: Boolean,
    val isDisabled: Boolean,
    val priority: Int
)

data class PanelHostEditValues(
    val remark: String,
    val address: List<String>,
    val inboundTag: String,
    val port: Int?,
    val sni: List<String>,
    val host: List<String>,
    val path: String?,
    val security: String,
    val fingerprint: String,
    val alpn: List<String>,
    val allowInsecure: Boolean,
    val isDisabled: Boolean,
    val priority: Int
)
