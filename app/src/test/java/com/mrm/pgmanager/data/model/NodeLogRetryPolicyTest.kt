package com.mrm.pgmanager.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NodeLogRetryPolicyTest {
    @Test fun `backoff is bounded and increases between attempts`() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L),
            (1..NodeLogRetryPolicy.MaxAttempts).map(NodeLogRetryPolicy::delayMillis))
        assertThrows(IllegalArgumentException::class.java) { NodeLogRetryPolicy.delayMillis(0) }
        assertThrows(IllegalArgumentException::class.java) { NodeLogRetryPolicy.delayMillis(NodeLogRetryPolicy.MaxAttempts + 1) }
    }
}
