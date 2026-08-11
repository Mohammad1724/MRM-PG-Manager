package com.mrm.pgmanager.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * این تست‌ها قرارداد با API پنل PasarGuard را قفل می‌کنند.
 * اگر کسی مقدارِ period یا نامِ متریک را عوض کند، پنل 422 برمی‌گرداند —
 * بهتر است همین‌جا شکست بخورد تا سرِ رانتایم.
 */
class StatsModelsTest {

    /** پنل فقط این چهار مقدار را می‌پذیرد (app/models/stats.py → Period). */
    @Test fun `every StatsRange uses a period the panel accepts`() {
        val allowed = setOf("minute", "hour", "day", "month")
        StatsRange.entries.forEach { range ->
            assertTrue("invalid period '${range.period}' on ${range.name}", range.period in allowed)
        }
    }

    @Test fun `startIso is a parsable instant in the past`() {
        StatsRange.entries.forEach { range ->
            val start = Instant.parse(range.startIso())
            assertTrue("${range.name} start should be in the past", start.isBefore(Instant.now()))
        }
    }

    /** بازهٔ بزرگ‌تر باید شروعِ دورتری داشته باشد. */
    @Test fun `ranges are ordered from shortest to longest`() {
        val starts = StatsRange.entries.map { Instant.parse(it.startIso()) }
        starts.zipWithNext().forEach { (shorter, longer) ->
            assertTrue("ranges out of order", longer.isBefore(shorter))
        }
    }

    /** مطابق `UserCountMetric` در app/models/stats.py */
    @Test fun `CountMetric names match the panel enum`() {
        assertEquals(
            setOf("online", "expired", "limited"),
            CountMetric.entries.map { it.apiName }.toSet()
        )
    }

    @Test fun `labels are present for the UI`() {
        StatsRange.entries.forEach { assertTrue(it.label.isNotBlank()) }
        CountMetric.entries.forEach { assertTrue(it.label.isNotBlank()) }
    }
}
