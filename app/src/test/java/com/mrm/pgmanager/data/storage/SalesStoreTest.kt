package com.mrm.pgmanager.data.storage

import com.mrm.pgmanager.data.model.SaleRecord
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * همان منطقِ serialize/deserialize که در SessionStore هست، ولی روی یک نگه‌دارندهٔ
 * ساده به‌جای EncryptedSharedPreferences (که در JVM در دسترس نیست).
 *
 * ⚠️ این کد آینهٔ `SessionStore.readSales/saveSales/addSale` است.
 * اگر آن‌ها را عوض کردید، این فایل را هم به‌روز کنید وگرنه تست چیزی را
 * می‌سنجد که دیگر در برنامه اجرا نمی‌شود.
 */
private class FakePrefs {
    var value: String = "[]"
}

private const val MAX_SALES = 2000

private fun save(p: FakePrefs, list: List<SaleRecord>) {
    val trimmed = list.sortedByDescending { it.soldAt }.take(MAX_SALES)
    val arr = JSONArray()
    trimmed.forEach { s ->
        arr.put(JSONObject().apply {
            put("id", s.id); put("username", s.username); put("baseUrl", s.baseUrl)
            put("amount", s.amount); put("currency", s.currency); put("days", s.days)
            put("soldAt", s.soldAt); put("note", s.note)
        })
    }
    p.value = arr.toString()
}

private fun read(p: FakePrefs): List<SaleRecord> = runCatching {
    val arr = JSONArray(p.value)
    val out = mutableListOf<SaleRecord>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val username = o.optString("username"); if (username.isBlank()) continue
        val baseUrl = o.optString("baseUrl"); if (baseUrl.isBlank()) continue
        val soldAt = o.optLong("soldAt", 0L); if (soldAt <= 0L) continue
        out.add(SaleRecord(
            id = o.optString("id").ifBlank { "$soldAt-$username" },
            username = username, baseUrl = baseUrl,
            amount = o.optLong("amount", 0L),
            currency = o.optString("currency", "تومان").ifBlank { "تومان" },
            days = o.optInt("days", 0), soldAt = soldAt, note = o.optString("note", "")
        ))
    }
    out
}.getOrDefault(emptyList())

private fun addSale(p: FakePrefs, s: SaleRecord) = save(p, listOf(s) + read(p).filterNot { it.id == s.id })

class SalesStoreTest {
    private fun rec(i: Int, amount: Long = 1000L, base: String = "https://p1") =
        SaleRecord("id$i", "user$i", base, amount, "تومان", 30, 1_700_000_000_000L + i * 1000L)

    @Test fun roundTripPreservesAllFields() {
        val p = FakePrefs()
        val s = SaleRecord("x1", "ali", "https://p", 125_000L, "تومان", 30, 1_755_000_000_000L, "نقدی")
        save(p, listOf(s))
        assertEquals(listOf(s), read(p))
    }

    @Test fun emptyAndCorruptInputAreSafe() {
        val p = FakePrefs()
        assertTrue(read(p).isEmpty())
        p.value = "not json at all"
        assertTrue(read(p).isEmpty())
        p.value = """[{"username":"","baseUrl":"b","soldAt":1}]"""
        assertTrue("blank username must be skipped", read(p).isEmpty())
        p.value = """[{"username":"a","baseUrl":"b","soldAt":0}]"""
        assertTrue("missing soldAt must be skipped", read(p).isEmpty())
    }

    @Test fun addSaleIsIdempotentOnSameId() {
        val p = FakePrefs()
        val s = rec(1)
        addSale(p, s); addSale(p, s)
        assertEquals(1, read(p).size)
    }

    @Test fun capIsEnforcedKeepingNewest() {
        val p = FakePrefs()
        save(p, (1..2100).map { rec(it) })
        val got = read(p)
        assertEquals(MAX_SALES, got.size)
        // جدیدترین باید بماند، قدیمی‌ترین برود
        assertEquals("id2100", got.first().id)
        assertFalse(got.any { it.id == "id1" })
    }

    @Test fun persianCurrencySurvivesJson() {
        val p = FakePrefs()
        save(p, listOf(rec(1)))
        assertEquals("تومان", read(p)[0].currency)
    }
}
