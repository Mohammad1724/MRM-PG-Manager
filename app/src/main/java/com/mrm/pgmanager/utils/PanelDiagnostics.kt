package com.mrm.pgmanager.utils

import com.mrm.pgmanager.data.api.PanelApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

/**
 * تشخیصِ گام‌به‌گامِ اتصال به پنل — از روی **همان گوشی** که اپ رویش اجرا می‌شود.
 *
 * چرا لازم شد: خطای «ارتباطِ امن برقرار نشد» روی یک پنلِ self-hosted چهار علتِ
 * کاملاً متفاوت دارد (DNS مسموم، پورتِ بسته، هندشیکِ TLS که پاسخ نمی‌گیرد، یا
 * پاسخِ HTTP) و از بیرون هیچ‌کدام قابلِ تفکیک نیست — نه برای ما و نه برای کاربر.
 * این ابزار همان مسیری را که اپ می‌رود، قدم‌به‌قدم می‌پیماید و نتیجهٔ هر قدم را
 * با جزئیاتِ فنی برمی‌گرداند:
 *
 *  ۱. **آدرس** — نرمال‌سازیِ همان قاعدهٔ اپ (حذفِ مسیر/فرگمنت، اجبارِ https).
 *  ۲. **DNS** — آی‌پی‌هایی که سیستمِ گوشی برمی‌گرداند (مسمومیتِ DNS اینجا لو می‌رود).
 *  ۳. **TCP** — اتصالِ خام به هر آی‌پی و پورت (پورتِ بسته/فیلترشده اینجا لو می‌رود).
 *  ۴. **TLS** — هندشیکِ واقعی + پروتکلِ نهایی + گواهی (SAN/صادرکننده/انقضا). اگر
 *     زنجیره ناقص یا نامِ میزبان ناهمخوان باشد، **همین‌جا** با متنِ دقیق معلوم می‌شود.
 *  ۵. **HTTP** — یک `GET /api/admin` بی‌اعتبارنامه؛ پاسخِ ۴۰۱/۴۰۳ یعنی «سرور سالم
 *     است و فقط اعتبارنامه لازم دارد» — همان چیزی که برای ورود می‌خواهیم.
 *
 * همه‌چیز با تایم‌اوتِ کوتاه اجرا می‌شود و هیچ‌وقت استثنا بیرون نمی‌دهد؛ خروجی
 * فهرستی از قدم‌هاست که هر کدام «موفق/ناموفق + توضیح» دارد.
 */
object PanelDiagnostics {

    data class Step(val label: String, val ok: Boolean, val detail: String)

    private const val TCP_TIMEOUT_MS = 6_000
    private const val TLS_TIMEOUT_MS = 10_000
    private const val HTTP_TIMEOUT_MS = 12_000

    suspend fun run(address: String): List<Step> = withContext(Dispatchers.IO) {
        val steps = mutableListOf<Step>()

        // ۱) آدرس
        val base = try {
            PanelApi.normalizedBaseForDiagnostics(address)
        } catch (t: Throwable) {
            steps += Step("آدرس", false, t.message ?: "آدرس نامعتبر است")
            return@withContext steps
        }
        steps += Step("آدرس", true, base)

        val uri = runCatching { URI(base) }.getOrNull()
        val host = uri?.host
        if (host.isNullOrBlank()) {
            steps += Step("آدرس", false, "میزبان (host) خوانده نشد")
            return@withContext steps
        }
        val port = if (uri.port != -1) uri.port else 443

        // ۲) DNS
        val ips = try {
            InetAddress.getAllByName(host).map { it.hostAddress ?: it.toString() }.distinct()
        } catch (t: Throwable) {
            steps += Step("DNS", false, "${t::class.java.simpleName}: ${t.message ?: "نام حل نشد"}")
            return@withContext steps
        }
        steps += Step("DNS", true, ips.joinToString("، "))

        // ۳) TCP — روی همهٔ آی‌پی‌ها امتحان می‌کنیم تا اگر یکی مسدود بود معلوم شود.
        var anyTcp = false
        val tcpDetails = ips.map { ip ->
            val started = System.currentTimeMillis()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), TCP_TIMEOUT_MS)
                    anyTcp = true
                    "$ip: اتصال برقرار (${System.currentTimeMillis() - started}ms)"
                }
            } catch (t: Throwable) {
                "$ip: ${t::class.java.simpleName}"
            }
        }
        steps += Step("TCP", anyTcp, tcpDetails.joinToString("\n"))

        if (!anyTcp) return@withContext steps     // بدونِ TCP، TLS/HTTP بی‌معناست

        // ۴) TLS — هندشیکِ واقعی با گواهی و پروتکل
        val tlsDetail = try {
            val ssl = (SSLContext.getDefault().socketFactory).createSocket(host, port) as javax.net.ssl.SSLSocket
            ssl.soTimeout = TLS_TIMEOUT_MS
            // روی SSLSocketِ دستی، بررسیِ نامِ میزبان پیش‌فرض **خاموش** است؛ روشنش
            // می‌کنیم تا ناهمخوانیِ SAN با دامنه (خطای رایجِ گواهیِ اشتباه) هم لو برود.
            ssl.sslParameters = ssl.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            ssl.startHandshake()
            val protocol = ssl.session.protocol
            val cert = runCatching { ssl.session.peerCertificates.firstOrNull() as? X509Certificate }.getOrNull()
            val info = buildString {
                append("پروتکل ").append(protocol)
                if (cert != null) {
                    append(" · صادرکننده: ").append(cert.issuerX500Principal.name.take(80))
                    val sans = runCatching { cert.subjectAlternativeNames }.getOrNull()
                    val names = sans?.mapNotNull { row -> (row.getOrNull(1) as? String) }?.take(3)
                    if (!names.isNullOrEmpty()) append(" · SAN: ").append(names.joinToString(", "))
                    append(" · تا ").append(cert.notAfter)
                }
            }
            ssl.close()
            Step("TLS", true, info)
        } catch (t: Throwable) {
            // متنِ دقیقِ علت (زنجیره/نامِ میزبان/انقضا) معمولاً در cause نشسته است.
            val cause = t.cause
            Step(
                "TLS", false,
                listOfNotNull(
                    "${t::class.java.simpleName}: ${t.message}".trim(),
                    cause?.let { it::class.java.simpleName + ": " + (it.message ?: "") }
                ).joinToString("\n")
            )
        }
        steps += tlsDetail

        // ۵) HTTP — پاسخِ سرور به یک درخواستِ واقعی
        val httpDetail = try {
            val req = Request.Builder()
                .url("$base/api/admin")
                .get()
                .build()
            val shortClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(TCP_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(HTTP_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .build()
            shortClient.newCall(req).execute().use { resp ->
                Step(
                    "HTTP", true,
                    "پاسخ ${resp.code}" + when (resp.code) {
                        401, 403 -> " — سرور سالم است (فقط اعتبارنامه لازم دارد)"
                        in 200..299 -> " — سرور پاسخ داد"
                        404 -> " — مسیرِ API پیدا نشد (آدرس/مسیر را بررسی کنید)"
                        else -> ""
                    }
                )
            }
        } catch (t: Throwable) {
            val cause = t.cause
            Step(
                "HTTP", false,
                listOfNotNull(
                    "${t::class.java.simpleName}: ${t.message}".trim(),
                    cause?.let { it::class.java.simpleName + ": " + (it.message ?: "") }
                ).joinToString("\n")
            )
        }
        steps += httpDetail

        steps
    }
}
