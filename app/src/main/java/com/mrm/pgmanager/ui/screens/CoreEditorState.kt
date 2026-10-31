package com.mrm.pgmanager.ui.screens

import com.mrm.pgmanager.data.api.coreTagCanRoundTrip
import com.mrm.pgmanager.data.model.CoreType
import com.mrm.pgmanager.data.model.PanelCore

internal data class CoreEditorDraft(
    val name: String,
    val type: String,
    val exclude: Set<String>,
    val fallbacks: Set<String>,
    val restartNodes: Boolean = false
) {
    fun isDirty(initial: PanelCore): Boolean =
        name != initial.name || type.ifBlank { null } != initial.type ||
            exclude != initial.excludeInboundTags.toSet() || fallbacks != initial.fallbacksInboundTags.toSet() || restartNodes

    fun nameError(): CoreNameError? = when {
        name.isBlank() -> CoreNameError.EMPTY
        name.trim().codePointCount(0, name.trim().length) > 256 -> CoreNameError.TOO_LONG
        else -> null
    }

    fun hasUnstorableTags(): Boolean = (exclude + fallbacks).any { !coreTagCanRoundTrip(it) }

    fun wireGuardHasTags(): Boolean = type == CoreType.WG && (exclude.isNotEmpty() || fallbacks.isNotEmpty())

    fun tagsTooLong(): Boolean = listOf(exclude, fallbacks).any { tags ->
        val value = tags.joinToString(",")
        value.codePointCount(0, value.length) > 2048
    }

    companion object {
        fun from(core: PanelCore) = CoreEditorDraft(
            name = core.name, type = core.type.orEmpty(), exclude = core.excludeInboundTags.toSet(),
            fallbacks = core.fallbacksInboundTags.toSet()
        )
    }
}

internal enum class CoreNameError { EMPTY, TOO_LONG }
