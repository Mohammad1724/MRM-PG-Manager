package com.mrm.pgmanager.data.model

/** The app default is 80% of the device font size; the user's choice scales that default. */
object AppFontScale {
    const val MIN_PERCENT = 80
    const val MAX_PERCENT = 180
    const val DEFAULT_PERCENT = 100
    const val STEP_PERCENT = 10
    private const val BASE_PERCENT = 80
    const val SYSTEM_PERCENT = 10000 / BASE_PERCENT
    private const val LEGACY_MIN_PERCENT = 80
    private const val LEGACY_MAX_PERCENT = 140

    fun normalize(percent: Int): Int = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)

    fun applyTo(systemScale: Float, percent: Int): Float {
        val normalized = normalize(percent)
        return if (normalized == SYSTEM_PERCENT) systemScale
        else systemScale * (normalized * BASE_PERCENT / 10000f)
    }

    /** Convert the old device-relative percentage to the closest new app-relative percentage. */
    fun fromLegacyPercent(percent: Int): Int {
        val legacy = percent.coerceIn(LEGACY_MIN_PERCENT, LEGACY_MAX_PERCENT)
        return normalize((legacy * 100 + BASE_PERCENT / 2) / BASE_PERCENT)
    }

    /** Approximate the current size in the old device-relative format for older backups/apps. */
    fun toLegacyPercent(percent: Int): Int = (normalize(percent) * BASE_PERCENT + 50) / 100
}
