package com.mrm.pgmanager.data.api

import com.mrm.pgmanager.data.model.PanelCore

/** The panel stores tag sets as comma-separated strings and drops empty entries. */
internal fun coreTagCanRoundTrip(tag: String): Boolean = tag.isNotEmpty() && ',' !in tag

/** The core API accepts the whole configuration even when only a label is edited. */
internal data class CoreEditValues(
    val name: String,
    val type: String?,
    val excludeInboundTags: List<String>,
    val fallbacksInboundTags: List<String>
)

/** A request may have reached the server, but its result could not be confirmed. */
internal class CoreUpdateUnconfirmedException : IllegalStateException("Core update could not be confirmed")

/** Contains field names only, never configuration values. */
internal class CoreEditConflictException(val fields: List<String>) :
    IllegalStateException("The core changed on the server: ${fields.joinToString()}")

/**
 * Merge only deliberate edits into a fresh server snapshot. The panel has no version
 * precondition, so another writer can still change the core between GET and PUT.
 */
internal object CoreEditMerge {
    fun merge(initial: PanelCore, latest: PanelCore, edit: CoreEditValues): CoreEditValues {
        require(initial.id > 0 && initial.id == latest.id) { "Invalid core snapshot" }
        val conflicts = mutableListOf<String>()

        fun <T> choose(field: String, before: T, now: T, requested: T): T {
            if (requested == before) return now
            if (now != before && requested != now) conflicts += field
            return requested
        }

        val requestedName = if (edit.name == initial.name) initial.name else edit.name.trim()
        val name = choose("name", initial.name, latest.name, requestedName)
        val type = choose("type", initial.type, latest.type, edit.type)
        val exclude = choose(
            "exclude_inbound_tags", initial.excludeInboundTags.toSet(),
            latest.excludeInboundTags.toSet(), edit.excludeInboundTags.toSet()
        )
        val fallbacks = choose(
            "fallbacks_inbound_tags", initial.fallbacksInboundTags.toSet(),
            latest.fallbacksInboundTags.toSet(), edit.fallbacksInboundTags.toSet()
        )
        if (conflicts.isNotEmpty()) throw CoreEditConflictException(conflicts)
        return CoreEditValues(name, type, exclude.toList(), fallbacks.toList())
    }
}
