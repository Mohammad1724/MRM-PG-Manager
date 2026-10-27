package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.SseDecoder
import okhttp3.Call
import okhttp3.Response
import okio.BufferedSource
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * اتصالِ بازِ «جریانِ لاگِ نود» — `GET /api/node/{id}/logs`.
 *
 * ### چرا یک شیءِ جدا و نه یک تابعِ suspend ساده
 * این endpoint مثلِ بقیه پاسخِ JSON نمی‌دهد؛ یک جریانِ `text/event-stream` است که
 * The reader can block in `readUtf8Line()`. Cancellation closes the owning call,
 * which interrupts the socket read, and cleanup closes the response on IO.
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

    /** Interrupt an in-flight read without waiting for response cleanup. */
    fun cancel() {
        closed = true
        call.cancel()
    }

    /** Close idempotently after interrupting the underlying call. */
    fun close() {
        cancel()
        runCatching { response.close() }
    }
}

/** Cancel an in-flight handshake and close a response that cannot be delivered to its caller. */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun connectNodeLog(call: Call, errorDetail: (String?) -> String): NodeLogConnection = kotlinx.coroutines.coroutineScope {
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        launch(Dispatchers.IO) {
            try {
                val response = call.execute()
                if (!response.isSuccessful) {
                    val message = response.use {
                        "Node logs failed: ${it.code} ${errorDetail(it.body?.string())}".trim()
                    }
                    error(message)
                }
                val connection = try {
                    NodeLogConnection(call, response)
                } catch (error: Exception) {
                    response.close()
                    throw error
                }
                continuation.resume(connection) {
                    // Cancellation callbacks may run on the UI thread; close the response on IO.
                    connection.cancel()
                    Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable { connection.close() })
                }
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }
}

/** Closing a cancelled reader must interrupt a blocked socket, not wait for another SSE event. */
private suspend fun nextNodeLogEvent(connection: NodeLogConnection): String? = kotlinx.coroutines.coroutineScope {
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { connection.cancel() }
        launch(Dispatchers.IO) {
            try {
                continuation.resume(connection.nextEvent())
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }
}

internal enum class NodeLogReadResult { Ended, Closed }

/** Read events on IO and distinguish a server EOF from an intentional close before cleanup. */
internal suspend fun readNodeLogEvents(
    connection: NodeLogConnection,
    onEvent: suspend (String) -> Unit
): NodeLogReadResult {
    try {
        while (true) {
            val event = try {
                nextNodeLogEvent(connection)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (connection.closed) return NodeLogReadResult.Closed
                throw error
            }
            if (event == null) {
                return if (connection.closed) NodeLogReadResult.Closed else NodeLogReadResult.Ended
            }
            onEvent(event)
        }
    } finally {
        withContext(NonCancellable + Dispatchers.IO) { connection.close() }
    }
}
