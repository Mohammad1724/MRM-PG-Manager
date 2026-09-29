package com.mrm.pgmanager.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.AdminSelf
import com.mrm.pgmanager.data.model.PermissionScope
import com.mrm.pgmanager.data.model.Session

/**
 * مجوزهای ادمینِ واردشده (RBAC پنل v5) — یک‌بار پس از ورود از `GET /api/admin` گرفته می‌شود.
 *
 * صفحه‌ها با [can]/[scopeAll] تصمیم می‌گیرند دکمه‌ای را نشان بدهند یا نه، تا ادمینِ
 * محدود (مثلاً نقشِ operator) با دکمه‌هایی روبه‌رو نشود که در نهایت ۴۰۳ می‌گیرند.
 *
 * وقتی مجوزها را *نمی‌دانیم* (خطای شبکه، پنلِ قدیمی) همه‌چیز مجاز فرض می‌شود؛ پنل
 * خودش آخرین حرف را می‌زند و خطای ۴۰۳ با پیامِ خوانا نشان داده می‌شود. این‌طور هیچ
 * قابلیتی بی‌دلیل پنهان نمی‌شود.
 *
 * `current` یک State کامپوز است؛ هر کامپوزبلی که [can] را صدا بزند، با رسیدنِ پاسخِ
 * پنل خودکار دوباره ترسیم می‌شود.
 */
object AdminAccess {
    var current: AdminSelf? by mutableStateOf(null)
        private set

    /** نشستی که مجوزهای فعلی برای آن گرفته شده؛ با سوئیچِ حساب باید دوباره خوانده شود. */
    private var loadedFor: String? = null

    fun reset() {
        current = null
        loadedFor = null
    }

    /** مجوزها را برای این نشست می‌گیرد (اگر قبلاً برای همین نشست گرفته شده، کاری نمی‌کند). */
    suspend fun refresh(session: Session, force: Boolean = false) {
        val key = "${session.baseUrl}|${session.username}|${session.token.hashCode()}"
        if (!force && loadedFor == key) return
        val admin = runCatching { PanelApi.currentAdmin(session) }.getOrNull()
        current = admin
        loadedFor = if (admin != null) key else null
    }

    /** آیا `resource.action` مجاز است؟ نامعلوم = مجاز. */
    fun can(resource: String, action: String): Boolean = current?.can(resource, action) ?: true

    /** آیا `resource.action` با دامنهٔ «همهٔ کاربران» مجاز است (نه فقط کاربرانِ خودِ ادمین)؟ */
    fun scopeAll(resource: String, action: String): Boolean =
        (current?.scope(resource, action) ?: PermissionScope.ALL) >= PermissionScope.ALL
}
