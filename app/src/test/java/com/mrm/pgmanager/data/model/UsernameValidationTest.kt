package com.mrm.pgmanager.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** آینهٔ `UsernameValidatorMixin.validate_username` پنل (۳..۱۲۸، مجموعهٔ کاراکتر، بدونِ کاراکترِ خاصِ تکراری). */
class UsernameValidationTest {

    @Test fun `accepts panel-valid usernames`() {
        listOf("ali", "user_01", "a.b-c@d", "x".repeat(128), "MRM-2026").forEach {
            assertNull("should be valid: $it", UsernameValidation.validate(it))
        }
    }

    @Test fun `length must be 3 to 128`() {
        assertEquals(UsernameValidation.ERR_LENGTH, UsernameValidation.validate("ab"))
        assertEquals(UsernameValidation.ERR_LENGTH, UsernameValidation.validate(""))
        assertEquals(UsernameValidation.ERR_LENGTH, UsernameValidation.validate("x".repeat(129)))
        assertNull(UsernameValidation.validate("x".repeat(128)))
    }

    @Test fun `only alphanumerics and - _ @ dot are allowed`() {
        assertEquals(UsernameValidation.ERR_CHARS, UsernameValidation.validate("ali reza"))
        assertEquals(UsernameValidation.ERR_CHARS, UsernameValidation.validate("علی"))
        assertEquals(UsernameValidation.ERR_CHARS, UsernameValidation.validate("ali+1"))
        assertEquals(UsernameValidation.ERR_CHARS, UsernameValidation.validate("ali/1"))
    }

    @Test fun `consecutive special characters are rejected`() {
        assertEquals(UsernameValidation.ERR_CONSECUTIVE, UsernameValidation.validate("ali--reza"))
        assertEquals(UsernameValidation.ERR_CONSECUTIVE, UsernameValidation.validate("ali_.reza"))
        assertEquals(UsernameValidation.ERR_CONSECUTIVE, UsernameValidation.validate("a@@b"))
        assertNull(UsernameValidation.validate("a-b_c.d@e"))
    }

    @Test fun `surrounding whitespace is ignored`() {
        assertTrue(UsernameValidation.isValid("  ali  "))
    }
}
