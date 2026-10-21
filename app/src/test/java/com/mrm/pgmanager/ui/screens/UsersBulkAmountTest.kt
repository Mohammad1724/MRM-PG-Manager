package com.mrm.pgmanager.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * تستِ تابع خالصِ پاک‌سازیِ ورودیِ دیالوگِ «افزودن/کاستنِ روز یا حجمِ گروهی» —
 * رفع U5 (استخراج از منطقِ درون‌خطیِ دیالوگ، بدون تغییرِ معنایی).
 *
 * قواعد: نرمال‌سازیِ ارقام فارسی/عربی، فقط رقم، یک منفیِ ابتدایی، و نقطهٔ اعشار
 * تنها در حالتِ حجم.
 */
class UsersBulkAmountTest {

    @Test fun `plain digits pass through for days and data`() {
        assertEquals("30", UsersUiState.sanitizeBulkAmount("30", "days"))
        assertEquals("50", UsersUiState.sanitizeBulkAmount("50", "data"))
    }

    @Test fun `persian and arabic digits normalize to latin`() {
        assertEquals("30", UsersUiState.sanitizeBulkAmount("۳۰", "days"))
        assertEquals("125", UsersUiState.sanitizeBulkAmount("١٢٥", "data"))
    }

    @Test fun `leading minus is preserved so admins can subtract`() {
        assertEquals("-7", UsersUiState.sanitizeBulkAmount("-7", "days"))
        assertEquals("-1.5", UsersUiState.sanitizeBulkAmount("-1.5", "data"))
    }

    @Test fun `minus anywhere but the first position is stripped`() {
        assertEquals("12", UsersUiState.sanitizeBulkAmount("1-2", "days"))
        // فقط منفیِ ابتدایی می‌ماند؛ منفیِ دوم حذف می‌شود.
        assertEquals("-5", UsersUiState.sanitizeBulkAmount("--5", "days"))
    }

    @Test fun `decimal dot allowed only for data kind`() {
        assertEquals("1.5", UsersUiState.sanitizeBulkAmount("1.5", "data"))
        // در حالتِ روز نقطه معنا ندارد و حذف می‌شود.
        assertEquals("15", UsersUiState.sanitizeBulkAmount("1.5", "days"))
    }

    @Test fun `letters and spaces are stripped`() {
        assertEquals("10", UsersUiState.sanitizeBulkAmount("10 gb", "data"))
        assertEquals("12", UsersUiState.sanitizeBulkAmount("روز ۱۲", "days"))
    }

    @Test fun `empty input stays empty`() {
        assertEquals("", UsersUiState.sanitizeBulkAmount("", "days"))
        assertEquals("", UsersUiState.sanitizeBulkAmount("abc", "data"))
    }

    @Test fun `behavior is unchanged for mixed invalid input`() {
        // منفیِ ابتدایی می‌ماند، نقطه در حالتِ حجم می‌ماند، حرف حذف می‌شود.
        assertEquals("-2.5", UsersUiState.sanitizeBulkAmount("-2.5x", "data"))
    }
}
