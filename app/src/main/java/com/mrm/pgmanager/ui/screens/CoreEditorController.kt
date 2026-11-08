package com.mrm.pgmanager.ui.screens

import androidx.lifecycle.ViewModel
import com.mrm.pgmanager.data.model.PanelCore
import com.mrm.pgmanager.data.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Core configs and credentials stay in memory, never in saved instance state. */
internal class CoreEditorViewModel : ViewModel() {
    val state = CoreEditorState()
}

internal data class ActiveCoreEditor(
    val requestId: Long,
    val session: Session,
    val initial: PanelCore,
    val draft: CoreEditorDraft,
    val saving: Boolean = false,
    val error: String? = null
) {
    override fun toString(): String = "ActiveCoreEditor(requestId=$requestId, saving=$saving)"
}

internal data class CoreSaveNotice(val requestId: Long, val session: Session) {
    override fun toString(): String = "CoreSaveNotice(requestId=$requestId)"
}
internal data class CoreEditorSnapshot(val active: ActiveCoreEditor? = null, val notice: CoreSaveNotice? = null)

/** Called on the main thread; request identities isolate old sessions and results. */
internal class CoreEditorState {
    private var nextRequestId = 0L
    private val mutableSnapshot = MutableStateFlow(CoreEditorSnapshot())
    val snapshot: StateFlow<CoreEditorSnapshot> = mutableSnapshot.asStateFlow()
    val active: ActiveCoreEditor? get() = snapshot.value.active
    val notice: CoreSaveNotice? get() = snapshot.value.notice

    fun bind(session: Session) {
        val current = snapshot.value
        mutableSnapshot.value = current.copy(
            active = current.active?.takeIf { it.session == session },
            notice = current.notice?.takeIf { it.session == session }
        )
    }

    fun open(session: Session, core: PanelCore) {
        if (active?.session == session && active?.saving == true) return
        val editor = ActiveCoreEditor(++nextRequestId, session, core, CoreEditorDraft.from(core))
        mutableSnapshot.value = CoreEditorSnapshot(editor, notice?.takeIf { it.session == session })
    }

    fun update(requestId: Long, draft: CoreEditorDraft) {
        val current = active ?: return
        if (current.requestId == requestId && !current.saving) {
            mutableSnapshot.value = snapshot.value.copy(active = current.copy(draft = draft, error = null))
        }
    }

    fun beginSave(requestId: Long): ActiveCoreEditor? {
        val current = active ?: return null
        if (current.requestId != requestId || current.saving) return null
        val saving = current.copy(saving = true, error = null)
        mutableSnapshot.value = snapshot.value.copy(active = saving)
        return saving
    }

    fun failSave(requestId: Long, message: String) {
        val current = active ?: return
        if (current.requestId == requestId && current.saving) {
            mutableSnapshot.value = snapshot.value.copy(active = current.copy(saving = false, error = message))
        }
    }

    fun finishSave(requestId: Long) {
        val current = active ?: return
        if (current.requestId != requestId || !current.saving) return
        mutableSnapshot.value = CoreEditorSnapshot(notice = CoreSaveNotice(requestId, current.session))
    }

    fun dismiss(requestId: Long) {
        val current = active ?: return
        if (current.requestId == requestId && !current.saving) mutableSnapshot.value = snapshot.value.copy(active = null)
    }

    fun consumeNotice(requestId: Long) {
        if (notice?.requestId == requestId) mutableSnapshot.value = snapshot.value.copy(notice = null)
    }
}
