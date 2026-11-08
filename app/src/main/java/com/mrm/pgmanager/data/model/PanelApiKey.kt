package com.mrm.pgmanager.data.model

/**
 * A panel API key returned by `GET /api/api_keys` (requires `api_keys.read`).
 * API keys may expire or be exhausted by a request limit; background monitoring
 * must not assume that they work indefinitely.
 *
 * [trimmed] contains only the masked representation. The full secret is returned
 * once on creation or rotation and is never stored in this model.
 */
data class PanelApiKey(
    val id: Int,
    val name: String,
    val trimmed: String = "",
    val status: String = ACTIVE,
    val note: String? = null,
    val adminId: Int = 0,
    val createdAt: String? = null,
    val expireDate: String? = null,
    val isExpired: Boolean = false,
    val inheritPermissions: Boolean = true,
    val maxRequests: Long? = null,
    val requestCount: Long? = null,
    val deleteOnLimit: Boolean? = null,
    val requestLimitReported: Boolean = false,
    val requestLimitUnknown: Boolean = false
) {
    /** Only a complete response can prove that the quota fields are supported. */
    val supportsRequestLimits: Boolean
        get() = requestLimitReported && !requestLimitUnknown && requestCount != null && deleteOnLimit != null

    /** The administrative switch is independent of expiry and request exhaustion. */
    val isEnabled: Boolean get() = status == ACTIVE

    /** A missing limit means the server did not report an exhausted quota. */
    val isRequestLimitReached: Boolean
        get() = maxRequests != null && maxRequests > 0 && requestCount != null && requestCount >= maxRequests

    /** Do not present a limited key as usable when its count is unavailable. */
    val isRequestCountUnknown: Boolean get() = maxRequests != null && requestCount == null

    /** A display status, not a guarantee of server authorization. */
    val isActive: Boolean get() =
        isEnabled && !isExpired && !isRequestLimitReached && !isRequestCountUnknown && !requestLimitUnknown

    companion object {
        const val ACTIVE = "active"
        const val DISABLED = "disabled"
    }
}

/**
 * پاسخِ ساخت/چرخشِ کلید — `APIKeyCreateResponse` پنل.
 *
 * [key] فقط همین یک‌بار می‌آید؛ بعد از بستنِ دیالوگ دیگر قابلِ بازیابی نیست و
 * تنها راه، چرخشِ دوباره است.
 */
data class CreatedApiKey(
    val key: String,
    val item: PanelApiKey,
    val requestLimitConfirmed: Boolean = true
)

/** A null maximum removes the quota; the count is never reset by this edit. */
data class ApiKeyQuota(val maxRequests: Long?, val deleteOnLimit: Boolean) {
    /**
     * The panel stores this value in SQL Integer columns. The signed 32-bit limit
     * is portable across its supported databases. An unchanged, larger value
     * already stored by a permissive database may still be retained.
     */
    fun isValid(existingMaxRequests: Long? = null): Boolean =
        maxRequests == null || (maxRequests > 0 &&
            (maxRequests <= MAX_PORTABLE_REQUESTS || maxRequests == existingMaxRequests))

    companion object {
        const val MAX_PORTABLE_REQUESTS = 2_147_483_647L
    }
}
