package com.mrm.pgmanager.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * اعتبارسنجیِ فرم‌های «زیرساختِ پنل» — همان قواعدی که پنل در `CreateHost` و
 * `ClientTemplateBase` اعمال می‌کند، ولی پیش از رفتن به شبکه. هدف این است که
 * کاربر پیامِ روشنِ فارسی ببیند، نه ۴۲۲ خامِ pydantic.
 */
class InfraValidationTest {

    // ── هاست ─────────────────────────────────────────────────────────────

    @Test fun `host form accepts a normal host`() {
        assertNull(HostValidation.validate("Germany — TLS", listOf("de.example.com"), "443"))
        // پورتِ خالی یعنی «پیش‌فرضِ inbound».
        assertNull(HostValidation.validate("x", listOf("a.com"), ""))
        // چند آدرس با فاصله‌های اضافه.
        assertNull(HostValidation.validate("x", listOf("a.com", " b.com "), "0"))
    }

    @Test fun `host remark cannot be empty or whitespace`() {
        assertEquals(HostValidation.ERR_REMARK, HostValidation.validate("", listOf("a.com"), "443"))
        assertEquals(HostValidation.ERR_REMARK, HostValidation.validate("   ", listOf("a.com"), "443"))
    }

    @Test fun `host needs at least one non-empty address`() {
        assertEquals(HostValidation.ERR_ADDRESS, HostValidation.validate("x", emptyList(), "443"))
        assertEquals(
            HostValidation.ERR_ADDRESS,
            HostValidation.validate("x", listOf("", "  "), "443")
        )
    }

    @Test fun `host port must be a number in range`() {
        assertEquals(HostValidation.ERR_PORT, HostValidation.validate("x", listOf("a.com"), "abc"))
        assertEquals(HostValidation.ERR_PORT, HostValidation.validate("x", listOf("a.com"), "65536"))
        assertEquals(HostValidation.ERR_PORT, HostValidation.validate("x", listOf("a.com"), "-1"))
        assertNull(HostValidation.validate("x", listOf("a.com"), "65535"))
    }

    @Test fun `host security values mirror the panel enum`() {
        assertEquals(
            listOf("inbound_default", "none", "tls"),
            HostSecurity.ALL
        )
    }

    // ── قالبِ کلاینت ─────────────────────────────────────────────────────

    @Test fun `client template accepts a normal template`() {
        assertNull(ClientTemplateValidation.validate("my-clash", "proxy-groups: []"))
    }

    @Test fun `client template name is trimmed and bounded`() {
        assertEquals(ClientTemplateValidation.ERR_NAME, ClientTemplateValidation.validate("  ", "x"))
        assertEquals(
            ClientTemplateValidation.ERR_NAME_LONG,
            ClientTemplateValidation.validate("a".repeat(ClientTemplateValidation.MAX_NAME + 1), "x")
        )
        assertNull(ClientTemplateValidation.validate("a".repeat(ClientTemplateValidation.MAX_NAME), "x"))
    }

    @Test fun `client template content cannot be blank`() {
        assertEquals(ClientTemplateValidation.ERR_CONTENT, ClientTemplateValidation.validate("n", ""))
        assertEquals(ClientTemplateValidation.ERR_CONTENT, ClientTemplateValidation.validate("n", "   \n "))
    }

    /** پنل فقط همین ۶۴ کاراکتر را می‌پذیرد (`Field(max_length=64)`). */
    @Test fun `client template max name matches the panel`() {
        assertEquals(64, ClientTemplateValidation.MAX_NAME)
    }

    // ── انواعِ کور ───────────────────────────────────────────────────────

    @Test fun `core types mirror the panel enum`() {
        assertEquals(listOf("xray", "wg", "mtproto", "singbox"), CoreType.ALL)
    }
}
