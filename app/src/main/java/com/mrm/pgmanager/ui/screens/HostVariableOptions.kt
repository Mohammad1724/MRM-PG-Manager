package com.mrm.pgmanager.ui.screens

/** Built-in names and descriptions are localized separately; the copied token stays unchanged. */
internal data class HostVariableOption(val key: String, val description: String) {
    val token: String get() = "{$key}"
}

internal fun filterHostVariables(options: List<HostVariableOption>, query: String): List<HostVariableOption> {
    val search = query.trim()
    if (search.isEmpty()) return options
    return options.filter {
        it.token.contains(search, ignoreCase = true) || it.description.contains(search, ignoreCase = true)
    }
}

/** Keep the original order when the compact guide uses two columns. */
internal fun hostVariableColumns(
    options: List<HostVariableOption>,
    twoColumns: Boolean
): List<List<HostVariableOption>> {
    if (!twoColumns || options.size < 2) return listOf(options)
    val midpoint = (options.size + 1) / 2
    return listOf(options.take(midpoint), options.drop(midpoint))
}

/** Narrow windows and large text remain single-column to avoid clipping. */
internal fun useTwoHostVariableColumns(widthDp: Float, fontScale: Float): Boolean =
    widthDp >= 260f && fontScale <= 1.3f
