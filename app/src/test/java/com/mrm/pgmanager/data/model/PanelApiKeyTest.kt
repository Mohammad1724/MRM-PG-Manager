package com.mrm.pgmanager.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelApiKeyTest {
    @Test fun `legacy keys without a reported quota retain their previous status`() {
        val key = PanelApiKey(id = 1, name = "legacy")

        assertTrue(key.isEnabled)
        assertTrue(key.isActive)
        assertFalse(key.isRequestLimitReached)
        assertFalse(key.isRequestCountUnknown)
    }

    @Test fun `reaching or exceeding a quota makes an enabled key inactive`() {
        for (count in listOf(5L, 6L)) {
            val key = PanelApiKey(id = 1, name = "limited", maxRequests = 5, requestCount = count)
            assertTrue(key.isEnabled)
            assertTrue(key.isRequestLimitReached)
            assertFalse(key.isActive)
        }
    }

    @Test fun `a limited key with an unknown count is not reported active`() {
        val key = PanelApiKey(id = 1, name = "unknown", maxRequests = 5)

        assertTrue(key.isEnabled)
        assertTrue(key.isRequestCountUnknown)
        assertFalse(key.isRequestLimitReached)
        assertFalse(key.isActive)
    }

    @Test fun `an unlimited key with a count is not exhausted`() {
        val key = PanelApiKey(id = 1, name = "unlimited", requestCount = 5)

        assertTrue(key.isEnabled)
        assertFalse(key.isRequestLimitReached)
        assertTrue(key.isActive)
    }

    @Test fun `expiry does not change the administrative switch`() {
        val key = PanelApiKey(id = 1, name = "expired", isExpired = true)

        assertTrue(key.isEnabled)
        assertFalse(key.isActive)
    }

    @Test fun `new request limits fit the portable SQL integer range`() {
        val max = ApiKeyQuota.MAX_PORTABLE_REQUESTS
        assertTrue(ApiKeyQuota(null, false).isValid())
        assertTrue(ApiKeyQuota(1, false).isValid())
        assertTrue(ApiKeyQuota(max, true).isValid())
        assertFalse(ApiKeyQuota(0, false).isValid())
        assertFalse(ApiKeyQuota(-1, false).isValid(-1))
        assertFalse(ApiKeyQuota(max + 1, false).isValid())
        assertTrue(ApiKeyQuota(max + 1, true).isValid(existingMaxRequests = max + 1))
        assertFalse(ApiKeyQuota(max + 2, true).isValid(existingMaxRequests = max + 1))
    }

    @Test fun `disabled status is independent of usage`() {
        val key = PanelApiKey(id = 1, name = "disabled", status = PanelApiKey.DISABLED, maxRequests = 5, requestCount = 5)

        assertFalse(key.isEnabled)
        assertTrue(key.isRequestLimitReached)
        assertFalse(key.isActive)
    }
}
