package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.SseDecoder
import okhttp3.Call
import okhttp3.Response
import okio.BufferedSource
import java.io.IOException

/**
 * اتصالِ بازِ «جریانِ لاگِ نود» — `GET /api/node/{id}/logs`.
 *
 * ### چرا یک شیءِ جدا و نه یک تابعِ suspend ساده
 * این endpoint مثلِ بقیه پاسخِ JSON نمی‌دهد؛ یک جریانِ `text/event-stream` است که
 * تا بستنِ اتصال باز می‌ماند. کوروتینِ خواننده روی `readUtf8Line()` **بلاک** است و
 * لغوشدنِ کوروتین آن بلوک را نمی‌شکند؛ تنها چیزی که بیدارش می‌کند بستنِ خودِ
 * سوکت است. پس عمرِ اتصال باید دستِ صاحبِ صفحه باشد: `DisposableEffect.onDispose`
 * همین شیء را `close()` می‌کند و خواننده با `IOException` بیدار می‌شود. اگر این را
 * پشتِ یک تابعِ suspend پنهان می‌کردیم، بستنِ صفحه سوکتی باز می‌گذاشت تا اولین
 * پینگِ پنل (۱۵ ثانیه بعد) — روی گوشی یعنی باتری و یک اتصالِ نصفه روی سرور.
 *
 * ### چرا `readTimeout = 0`
 * لاگِ نود می‌تواند چند دقیقه ساکت بماند. تایم‌اوتِ خواندنِ معمولیِ اپ (۲۰ ثانیه)
 * هر جریانِ ساکت را «خطا» می‌خواند. بسته‌شدنِ واقعی یا رفتنِ شبکه همچنان با
 * `IOException` می‌رسد (و TCP keepalive پنل هم پینگِ SSE می‌فرستد).
 *
 * @param closed `true` به‌محضِ `close()` — حلقۀ خواننده با آن تشخیص می‌دهد که
 *   `IOException`ِ بعدی خطا نیست، رفتنِ کاربر است.
 */
internal class NodeLogConnection(
    private val call: Call,
    private val response: Response
) {
    @Volatile
    var closed: Boolean = false
        private set

    private val source: BufferedSource? = response.body?.source()
    private val decoder = SseDecoder()

    /**
     * رویدادِ بعدی (یک یا چند خطِ لاگ)، یا `null` وقتی سرور جریان را بست **یا**
     * وقتی خودمان بسته‌ایم.
     *
     * بلاک‌کننده است؛ فقط از نخِ IO صدا زده شود. `null` بعد از [close] عمدی است:
     * خواننده باید مثلِ پایانِ عادی رفتار کند، نه مثلِ خطا.
     */
    @Throws(IOException::class)
    fun nextEvent(): String? {
        if (closed) return null
        val src = source ?: return null
        while (true) {
            // `try` فقط دورِ خودِ خواندن است، نه دورِ حلقه: بلوکی که با `close()`
            // می‌شکند همین `readUtf8Line()` است. okio بعد از بستنِ منبع دو جور
            // رفتار می‌کند — «canceled» (IOException از خودِ Call) و
            // `IllegalStateException("closed")` از source — و هیچ‌کدام خطای جریان
            // نیست اگر خودمان بسته باشیم؛ وگرنه هر خروجِ صفحه یک کرش بود.
            val line = try {
                src.readUtf8Line() ?: return null
            } catch (e: Exception) {
                if (closed) return null
                throw e
            }
            val event = decoder.feed(line)
            if (event != null) return event
        }
    }

    /** بستنِ بی‌خطر و چندباره‌پذیر — سوکت را می‌بندد و خواننده را بیدار می‌کند. */
    fun close() {
        closed = true
        decoder.reset()
        runCatching { response.close() }
        call.cancel()
    }
}
