package com.mrm.pgmanager.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserEditorFieldsTest {
    @Test fun `small positive limits never display as unlimited`() {
        assertEquals("", UserEditorFields.formatDataLimit(0))
        assertEquals("0.000976563", UserEditorFields.formatDataLimit(1_048_576))
        assertEquals("0.000000001", UserEditorFields.formatDataLimit(1))
        assertEquals("1.4", UserEditorFields.formatDataLimit(1_500_000_000))
    }

    @Test fun `explicit limits convert to bytes with bounded exact arithmetic`() {
        assertEquals(0L, UserEditorFields.parseDataLimit(""))
        assertEquals(1_073_741_824L, UserEditorFields.parseDataLimit("1"))
        assertEquals(1_503_238_554L, UserEditorFields.parseDataLimit("1.4"))
        assertNull(UserEditorFields.parseDataLimit("0.00000000001"))
        assertNull(UserEditorFields.parseDataLimit("-1"))
        assertNull(UserEditorFields.parseDataLimit("1.2.3"))
        assertNull(UserEditorFields.parseDataLimit("999999999999999999999999999999"))
    }

    @Test fun `device defaults zero and overflow remain distinct`() {
        assertTrue(UserEditorFields.validHwidLimit(""))
        assertTrue(UserEditorFields.validHwidLimit("0"))
        assertTrue(UserEditorFields.validHwidLimit("42"))
        assertFalse(UserEditorFields.validHwidLimit("-1"))
        assertFalse(UserEditorFields.validHwidLimit("2147483648"))
    }

    @Test fun `unchanged on hold duration keeps the exact seconds`() {
        assertEquals(10_800L, UserEditorFields.onHoldSeconds(10_800L, false, 1))
        assertEquals(86_400L, UserEditorFields.onHoldSeconds(10_800L, true, 1))
        assertEquals(172_800L, UserEditorFields.onHoldSeconds(null, false, 2))
        assertNull(UserEditorFields.onHoldSeconds(null, false, 0))
        assertNull(UserEditorFields.onHoldSeconds(null, true, 24_856))
    }

    @Test fun `activation deadline validates bounds without overflow`() {
        assertEquals(0L, UserEditorFields.timeoutSeconds(""))
        assertEquals(86_400L, UserEditorFields.timeoutSeconds("1"))
        assertEquals(2_147_472_000L, UserEditorFields.timeoutSeconds("24855"))
        assertNull(UserEditorFields.timeoutSeconds("24856"))
        assertNull(UserEditorFields.timeoutSeconds("9223372036854775807"))
        assertNull(UserEditorFields.timeoutSeconds("-1"))
    }
}
