package com.mrm.pgmanager.data.model

/** A bounded backoff for lost SSE streams; permanent failures are not retried. */
internal object NodeLogRetryPolicy {
    const val MaxAttempts = 5

    fun delayMillis(attempt: Int): Long {
        require(attempt in 1..MaxAttempts)
        return 1_000L shl (attempt - 1)
    }
}
