package com.mrm.pgmanager.utils

import com.mrm.pgmanager.data.model.PanelUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * قراردادهای خروجیِ export — BUG_AUDIT #9 (شکستن ردیف CSV با newline داخل فیلد).
 */
class ExportUsersTest {

    private fun user(note: String?, username: String = "ali") = PanelUser(
        id = 1,
        username = username,
        status = "active",
        usedTraffic = 1024,
        dataLimit = 0,
        expire = "2026-12-31T23:59:59Z",
        createdAt = "2026-01-01T00:00:00Z",
        note = note
    )

    @Test fun `csv starts with BOM so Excel renders Persian correctly`() {
        val csv = usersToCsv(listOf(user(null)))
        assertTrue(csv.startsWith("\uFEFF"))
    }

    @Test fun `newline inside note does not create extra csv rows`() {
        val csv = usersToCsv(listOf(user("line1\nline2\r\nline3\rtail")))
        // هدر + یک ردیفِ داده = دو خط؛ بدونِ BUG_AUDIT #9 اینجا ۵ خط می‌شد.
        // `usersToCsv` بعد از آخرین ردیف هم newline می‌گذارد (متعارف برای CSV)، پس
        // `lines()` یک عنصرِ خالیِ آخر دارد و باید trim شود.
        assertEquals(2, csv.trimEnd('\n', '\r').lines().size)
        assertTrue(csv.contains("line1 line2 line3 tail"))
    }

    @Test fun `embedded quotes are doubled per RFC 4180`() {
        val csv = usersToCsv(listOf(user("he said \"hi\"")))
        assertTrue(csv.contains("\"he said \"\"hi\"\"\""))
    }

    @Test fun `each user yields exactly one data row`() {
        val csv = usersToCsv(listOf(user("a"), user("b"), user("c")))
        // یک هدر + سه کاربر، هیچ‌کدام note چندخطی ندارند.
        assertEquals(4, csv.trimEnd('\n', '\r').lines().size)
    }

    @Test fun `json export keeps newline inside note intact`() {
        // JSON برخلاف CSV باید محتوای خام را نگه دارد (org.json خودش escape می‌کند).
        val json = usersToJson(listOf(user("line1\nline2")))
        assertTrue(json.contains("line1\\nline2"))
        assertFalse(json.contains("line1\nline2"))
    }
}
