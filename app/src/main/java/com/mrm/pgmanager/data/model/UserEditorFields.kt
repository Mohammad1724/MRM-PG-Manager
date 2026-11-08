package com.mrm.pgmanager.data.model

import java.math.BigDecimal
import java.math.RoundingMode

/** Conversions for user forms; unchanged server values must not be reconstructed from display text. */
internal object UserEditorFields {
    private val bytesPerGiB = BigDecimal.valueOf(1_073_741_824L)
    private val smallLimit = BigDecimal("0.005")
    const val MaxDurationSeconds = 2_147_483_647L

    fun formatDataLimit(bytes: Long): String {
        if (bytes <= 0L) return ""
        val value = BigDecimal.valueOf(bytes).divide(bytesPerGiB, 9, RoundingMode.HALF_UP)
        val display = if (value < smallLimit) value else value.setScale(2, RoundingMode.HALF_UP)
        return display.stripTrailingZeros().toPlainString()
    }

    /** Null means invalid; an empty field explicitly means unlimited. */
    fun parseDataLimit(text: String): Long? {
        val value = text.trim()
        if (value.isEmpty()) return 0L
        if (value.length > 64) return null
        val gigabytes = value.toBigDecimalOrNull() ?: return null
        if (gigabytes.signum() < 0) return null
        return try {
            val bytes = gigabytes.multiply(bytesPerGiB).setScale(0, RoundingMode.HALF_UP).longValueExact()
            if (gigabytes.signum() > 0 && bytes == 0L) null else bytes
        } catch (_: ArithmeticException) {
            null
        }
    }

    fun validHwidLimit(text: String): Boolean =
        text.isBlank() || text.toIntOrNull()?.let { it >= 0 } == true

    /** Null is invalid; an empty field means a zero timeout. */
    fun timeoutSeconds(days: String): Long? {
        if (days.isBlank()) return 0L
        val value = days.toLongOrNull() ?: return null
        if (value < 0L || value > MaxDurationSeconds / 86_400L) return null
        return value * 86_400L
    }

    /** Keep the exact server duration unless the displayed duration was deliberately edited. */
    fun onHoldSeconds(originalSeconds: Long?, durationEdited: Boolean, days: Int?): Long? {
        if (!durationEdited && originalSeconds != null && originalSeconds in 1L..MaxDurationSeconds) return originalSeconds
        return days?.let { timeoutSeconds(it.toString()) }?.takeIf { it > 0L }
    }
}
