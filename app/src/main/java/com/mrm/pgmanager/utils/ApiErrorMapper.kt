package com.mrm.pgmanager.utils

import android.content.Context
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.api.SessionExpiredException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * دستهٔ خطا — همان هشت خانواده‌ای که فاز ۵ لازم دارد (B1/B2 در سند UX).
 * `LOCAL_*` برای خرابی‌های محلی (فایل/SAF) است تا پیامِ «پنل در دسترس نیست»
 * برای خطای ذخیره‌سازی به کاربر نشان داده نشود.
 */
enum class ApiErrorKind {
    OFFLINE, TIMEOUT, TLS, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, VALIDATION, SERVER,
    LOCAL_PERMISSION, LOCAL_STORAGE, UNKNOWN
}

/** منشأ خطا — تعیین می‌کند «IOException» را شبکه بفهمیم یا فایل. */
enum class ErrorOrigin { NETWORK, LOCAL }

/**
 * **نگاشتِ مرکزیِ خطا → پیامِ انسانی (فاز ۵.۱)**
 *
 * چرا: پیش از این ۲۶ محل در ۹ فایل، `it.message` خام را در UI می‌ریختند و
 * کاربرِ فارسی‌زبان پیام‌های `Failed to connect to /10.0.0.5:8000` یا
 * `Request failed: 403 {"detail":"Not enough permissions"}` را می‌دید.
 *
 * قاعده‌ها:
 *  - هرگز متنِ خام به‌عنوانِ *پیام اصلی* نمایش داده نمی‌شود؛ متنِ خام فقط در
 *    «جزئیات فنی» (نگه‌داشتنِ اسنک) دیده می‌شود.
 *  - برای ۴xx اعتبارسنجی، پیامِ خودِ پنل (فیلد `detail`) **حفظ** می‌شود چون
 *    دقیق و قابل‌اقدام است («حجم باید عدد باشد» …).
 *  - ۴۰۱ نشست، پیامِ خودش را دارد تا مسیرِ «ورود دوباره» روشن بماند.
 *
 * کدهای HTTP از پیامِ PanelApi استخراج می‌شوند: الگوی ثابتِ `«… : <code> <detail>»`
 * (مثلاً `Request failed: 403 {"detail":"…"}`) — پس تغییرِ پیام‌ها در PanelApi
 * نباید این قرارداد را بشکند.
 */
object ApiErrorMapper {

    private val httpCode = Regex("""(?:^|[^\d])(\d{3})(?:[^\d]|$)""")
    private val jsonDetail = Regex("""\{\s*"detail"\s*:\s*"((?:[^"\\]|\\.)*)"""")

    // ── تشخیص (تابعِ خالص — قابلِ تست روی JVM) ───────────────────────────

    /** کدِ HTTP اگر خطا از پاسخِ پنل آمده باشد؛ در غیرِ این صورت `null`. */
    fun httpCodeOf(e: Throwable?): Int? {
        val message = e?.message ?: return null
        return httpCode.find(message)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 400..599 }
    }

    fun kindOf(e: Throwable?, origin: ErrorOrigin = ErrorOrigin.NETWORK): ApiErrorKind {
        if (e == null) return ApiErrorKind.UNKNOWN
        if (e is SessionExpiredException) return ApiErrorKind.UNAUTHORIZED

        if (origin == ErrorOrigin.LOCAL) {
            return when (e) {
                is SecurityException -> ApiErrorKind.LOCAL_PERMISSION
                is FileNotFoundException, is IOException -> ApiErrorKind.LOCAL_STORAGE
                else -> ApiErrorKind.UNKNOWN
            }
        }

        when (e) {
            is UnknownHostException -> return ApiErrorKind.OFFLINE
            is SocketTimeoutException -> return ApiErrorKind.TIMEOUT
            is SSLException -> return ApiErrorKind.TLS
        }

        // OkHttp خطاهای شبکه را IOException عمومی می‌دهد (مثل «retry exhausted» یعنی
        // هیچ نودی پاسخ نداد). این‌ها به کاربر «دوباره تلاش کن» می‌گویند، نه «خطای سرور».
        if (e is IOException) {
            val message = e.message.orEmpty()
            return if (message.contains("timeout", true)) ApiErrorKind.TIMEOUT else ApiErrorKind.OFFLINE
        }

        if (PanelApi.isUnauthorized(e)) return ApiErrorKind.UNAUTHORIZED
        val code = httpCodeOf(e) ?: return ApiErrorKind.UNKNOWN
        return when (code) {
            401 -> ApiErrorKind.UNAUTHORIZED
            403 -> ApiErrorKind.FORBIDDEN
            404 -> ApiErrorKind.NOT_FOUND
            in 400..499 -> ApiErrorKind.VALIDATION
            else -> ApiErrorKind.SERVER
        }
    }

    /** خطاهای گذرا «تلاش دوباره» می‌گیرند؛ بقیه باید کاربر را به اقدامِ درست ببرند. */
    fun isTransient(kind: ApiErrorKind): Boolean =
        kind == ApiErrorKind.OFFLINE || kind == ApiErrorKind.TIMEOUT || kind == ApiErrorKind.SERVER

    // ── متن‌ها ───────────────────────────────────────────────────────────

    /** متنِ خام + نوعِ استثنا — فقط برای «جزئیات فنی»، هرگز به‌عنوانِ پیامِ اصلی. */
    fun technical(e: Throwable?): String? {
        val message = e?.message?.trim().orEmpty()
        if (message.isEmpty()) return null
        return (e?.javaClass?.simpleName?.let { "$it: " } ?: "") + message.take(300)
    }

    /**
     * پیامِ خودِ پنل برای خطاهای اعتبارسنجی (`{"detail":"…"}`).
     * با regex استخراج می‌شود (نه JSONObject) تا این تابع روی JVM ساده هم تست‌پذیر بماند.
     */
    fun panelDetail(e: Throwable?): String? {
        val message = e?.message ?: return null
        val raw = jsonDetail.find(message)?.groupValues?.get(1) ?: return null
        return raw.replace("\\\"", "\"").replace("\\n", " ").trim().takeIf { it.isNotBlank() }
    }

    /**
     * ریزِ علتِ شکستِ TLS — چهار حالتی که در عمل پیش می‌آید و **هرکدام درمانِ
     * جداگانه‌ای دارند**. پیامِ کلیِ «گواهی یا پروتکل را بررسی کنید» کاربر را
     * سرگردان می‌کرد؛ حالا اپ خودش می‌گوید کدام‌یک است.
     *
     * نکتهٔ عملی: پیامِ خودِ اندروید برای بعضی حالت‌ها در `cause` می‌نشیند، نه در
     * پیامِ استثنای اصلی؛ پس هر دو را می‌خوانیم.
     */
    enum class TlsReason { CHAIN, PROTOCOL, HOSTNAME, EXPIRED, UNKNOWN }

    fun tlsReason(e: Throwable?): TlsReason {
        val text = listOfNotNull(e?.message, e?.cause?.message, e?.cause?.cause?.message).joinToString(" ")
        return when {
            // گواهی درست است ولی سرور کلِ زنجیره را نمی‌فرستد — شایع‌ترین حالت بعد از
            // تمدیدِ گواهی یا تغییرِ پروکسی. مرورگرها واسطِ گم‌شده را خودشان دانلود
            // می‌کنند (AIA)، اندروید این کار را نمی‌کند.
            text.contains("Trust anchor", true) || text.contains("CertPathValidator", true) -> TlsReason.CHAIN
            // سرور روی این پورت TLS صحبت نمی‌کند (پنلِ http، پورتِ اشتباه، یا واسطِ شبکه).
            text.contains("wrong version number", true) || text.contains("protocol error", true) ||
                text.contains("protocol_version", true) || text.contains("handshake failure", true) -> TlsReason.PROTOCOL
            text.contains("No subject alternative", true) ||
                text.contains("Hostname", true) && text.contains("verif", true) -> TlsReason.HOSTNAME
            text.contains("expired", true) || text.contains("NotYetValid", true) ||
                text.contains("CertificateExpired", true) -> TlsReason.EXPIRED
            else -> TlsReason.UNKNOWN
        }
    }

    /** پیامِ نهاییِ کاربر — هرگز متنِ خام نیست. */
    fun friendly(context: Context, e: Throwable?, origin: ErrorOrigin = ErrorOrigin.NETWORK): String =
        when (kindOf(e, origin)) {
            ApiErrorKind.OFFLINE -> context.getString(R.string.err_offline_panel)
            ApiErrorKind.TIMEOUT -> context.getString(R.string.err_timeout_panel)
            ApiErrorKind.TLS -> when (tlsReason(e)) {
                TlsReason.CHAIN -> context.getString(R.string.err_tls_chain)
                TlsReason.PROTOCOL -> context.getString(R.string.err_tls_protocol)
                TlsReason.HOSTNAME -> context.getString(R.string.err_tls_hostname)
                TlsReason.EXPIRED -> context.getString(R.string.err_tls_expired)
                TlsReason.UNKNOWN -> context.getString(R.string.err_tls_panel)
            }
            ApiErrorKind.UNAUTHORIZED -> context.getString(R.string.us_session_expired)
            ApiErrorKind.FORBIDDEN -> context.getString(R.string.err_forbidden_panel)
            ApiErrorKind.NOT_FOUND -> context.getString(R.string.err_not_found_panel)
            ApiErrorKind.SERVER -> context.getString(R.string.err_server_fmt, httpCodeOf(e) ?: 500)
            ApiErrorKind.VALIDATION -> panelDetail(e)
                ?.let { context.getString(R.string.err_validation_fmt, it) }
                ?: context.getString(R.string.err_generic)
            ApiErrorKind.LOCAL_PERMISSION -> context.getString(R.string.err_local_permission)
            ApiErrorKind.LOCAL_STORAGE -> context.getString(R.string.err_local_storage)
            ApiErrorKind.UNKNOWN -> context.getString(R.string.err_generic)
        }
}
