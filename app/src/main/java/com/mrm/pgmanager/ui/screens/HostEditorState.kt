package com.mrm.pgmanager.ui.screens

import androidx.lifecycle.ViewModel
import com.mrm.pgmanager.data.model.PanelHost
import com.mrm.pgmanager.data.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Kept in memory across activity recreation; host JSON and credentials are not saved in a Bundle. */
internal class HostEditorViewModel : ViewModel() {
    val state = HostEditorState()
}

internal data class ActiveHostEditor(
    val requestId: Long,
    val session: Session,
    val initial: PanelHost,
    val draft: HostDraft,
    val saving: Boolean = false,
    val error: String? = null
)

internal data class HostSaveNotice(val requestId: Long, val session: Session, val created: Boolean)
internal data class HostEditorSnapshot(val active: ActiveHostEditor? = null, val notice: HostSaveNotice? = null)

/** Called on the main thread. Request identities isolate results from older sessions and editors. */
internal class HostEditorState {
    private var nextRequestId = 0L
    private val mutableSnapshot = MutableStateFlow(HostEditorSnapshot())
    val snapshot: StateFlow<HostEditorSnapshot> = mutableSnapshot.asStateFlow()
    val active: ActiveHostEditor? get() = snapshot.value.active
    val notice: HostSaveNotice? get() = snapshot.value.notice

    fun bind(session: Session) {
        val current = snapshot.value
        mutableSnapshot.value = current.copy(
            active = current.active?.takeIf { it.session == session },
            notice = current.notice?.takeIf { it.session == session }
        )
    }

    fun open(session: Session, host: PanelHost) {
        if (active?.session == session && active?.saving == true) return
        val editor = ActiveHostEditor(++nextRequestId, session, host, HostDraft.from(host))
        val previousNotice = notice?.takeIf { it.session == session }
        mutableSnapshot.value = HostEditorSnapshot(active = editor, notice = previousNotice)
    }

    fun update(requestId: Long, draft: HostDraft) {
        val current = active ?: return
        if (current.requestId == requestId && !current.saving) {
            mutableSnapshot.value = snapshot.value.copy(active = current.copy(draft = draft, error = null))
        }
    }

    fun beginSave(requestId: Long): ActiveHostEditor? {
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
        mutableSnapshot.value = HostEditorSnapshot(
            notice = HostSaveNotice(requestId, current.session, current.initial.id == 0)
        )
    }

    fun dismiss(requestId: Long) {
        val current = active ?: return
        if (current.requestId == requestId && !current.saving) mutableSnapshot.value = snapshot.value.copy(active = null)
    }

    fun consumeNotice(requestId: Long) {
        if (notice?.requestId == requestId) mutableSnapshot.value = snapshot.value.copy(notice = null)
    }
}
