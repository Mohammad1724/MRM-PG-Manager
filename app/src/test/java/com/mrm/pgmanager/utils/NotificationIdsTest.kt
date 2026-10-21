package com.mrm.pgmanager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * تستِ خالصِ [NotificationIds] — تضمینِ هم‌گراییِ ID بین موتور پیش‌زمینه و ورکر
 * (رفع F2/F3/F4/F5): دو موتور برای یک رویداد باید دقیقاً به یک ID برسند تا
 * NotificationManager انتشار مجدد را جایگزین کند، نه اینکه اعلان دوم انباشته شود.
 */
class NotificationIdsTest {

    @Test fun `fixed health and lifecycle ids stay stable`() {
        assertEquals(5101, NotificationIds.CPU)
        assertEquals(5102, NotificationIds.RAM)
        assertEquals(5103, NotificationIds.DISK)
        assertEquals(5104, NotificationIds.PANEL_OFFLINE)
        assertEquals(5105, NotificationIds.SESSION_EXPIRED)
        assertEquals(5106, NotificationIds.CAPACITY)
    }

    @Test fun `user event id matches the shared string scheme used by both engines`() {
        // ورکر historically از (kind + user.id).hashCode() استفاده می‌کرد؛ مرجع مشترک
        // باید همان مقدار را بدهد تا اعلان‌های قدیمیِ در سایه یکتا بمانند.
        assertEquals(("limited42").hashCode(), NotificationIds.userEvent("limited", 42L))
        assertEquals(("near_expiry7").hashCode(), NotificationIds.userEvent("near_expiry", 7L))
    }

    @Test fun `different alert kinds for the same user never share an id`() {
        val kinds = listOf("limited", "expired", "near_limit", "near_expiry")
        val ids = kinds.map { NotificationIds.userEvent(it, 99L) }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun `same kind for two users produces different ids`() {
        assertNotEquals(
            NotificationIds.userEvent("limited", 1L),
            NotificationIds.userEvent("limited", 2L)
        )
    }

    @Test fun `node online and offline never collide, including large spaced ids`() {
        // سناریوی D9: idهائی با فاصله‌ی زیاد؛ طرح هشی نباید هیچ برخوردی بسازد.
        val smallOffline = NotificationIds.nodeOffline(1)
        val largeOnline = NotificationIds.nodeOnline(201)
        val largeOffline = NotificationIds.nodeOffline(201)
        assertNotEquals(smallOffline, largeOnline)
        assertNotEquals(largeOffline, largeOnline)
        // پایداری: فراخوانی مکرر همان نتیجه.
        assertEquals(smallOffline, NotificationIds.nodeOffline(1))
    }

    @Test fun `debtor overdue id is identical for foreground sweep and worker`() {
        val expected = "debtor_overdue_alice".hashCode()
        assertEquals(expected, NotificationIds.debtorOverdue("alice"))
        assertNotEquals(
            NotificationIds.debtorRegistered("alice"),
            NotificationIds.debtorOverdue("alice")
        )
    }

    @Test fun `user action id is deterministic for the same title and message`() {
        assertEquals(
            NotificationIds.userAction("Done", "Reset traffic"),
            NotificationIds.userAction("Done", "Reset traffic")
        )
        assertNotEquals(
            NotificationIds.userAction("Done", "Reset traffic"),
            NotificationIds.userAction("Done", "Reset days")
        )
    }
}
