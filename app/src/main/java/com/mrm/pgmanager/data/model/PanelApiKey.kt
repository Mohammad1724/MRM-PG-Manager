package com.mrm.pgmanager.data.model

/**
 * یک کلیدِ API پنل — `GET /api/api_keys` (مجوزِ `api_keys.read`).
 *
 * ### چرا این کلیدها در اپ مهم‌اند
 * ورودِ اپ با کلیدِ API تنها راهی است که **منقضی نمی‌شود** و ورکرِ پس‌زمینه را
 * زنده نگه می‌دارد (توکنِ JWT پنل پیش‌فرض ۲۴ ساعته است). تا امروز ساختن/باطل‌کردنِ
 * کلید فقط از پنلِ وب ممکن بود؛ یعنی اگر ادمین دسترسی به وب نداشت، مانیتورینگِ
 * پس‌زمینه‌اش یک روز پس از نصب می‌مُرد.
 *
 * `trimmed` همان نمایشِ ماسک‌شدهٔ پنل است (`pg_key_…abcd`)؛ متنِ کاملِ کلید فقط
 * یک‌بار — در پاسخِ ساخت یا چرخش — برمی‌گردد و در اپ ذخیره **نمی‌شود**.
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
    val inheritPermissions: Boolean = true
) {
    /** فقط کلیدهای فعال و منقضی‌نشده می‌توانند احراز هویت کنند. */
    val isActive: Boolean get() = status == ACTIVE && !isExpired

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
data class CreatedApiKey(val key: String, val item: PanelApiKey)
