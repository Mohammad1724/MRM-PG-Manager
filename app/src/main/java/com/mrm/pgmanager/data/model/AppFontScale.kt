package com.mrm.pgmanager.data.model

/** The app's font size is a bounded percentage of the device font size. */
object AppFontScale {
    const val MIN_PERCENT = 80
    const val MAX_PERCENT = 140
    const val DEFAULT_PERCENT = 100
    const val STEP_PERCENT = 10

    fun normalize(percent: Int): Int = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)

    fun applyTo(systemScale: Float, percent: Int): Float {
        val normalized = normalize(percent)
        return if (normalized == DEFAULT_PERCENT) systemScale else systemScale * (normalized / 100f)
    }
}
