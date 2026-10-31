package com.mrm.pgmanager.ui.screens

import androidx.lifecycle.ViewModel
import com.mrm.pgmanager.data.api.NodeDraft
import com.mrm.pgmanager.data.api.NodeSnapshot
import com.mrm.pgmanager.data.api.validationError
import com.mrm.pgmanager.data.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Credentials and drafts remain in memory and are never written to saved instance state. */
internal class NodeEditorViewModel : ViewModel() {
    val state = NodeEditorState()
}

internal data class ActiveNodeEditor(
    val requestId: Long,
    val session: Session,
    val nodeId: Int?,
    val initial: NodeSnapshot? = null,
    val draft: NodeDraft? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val blocked: Boolean = false,
    val error: String? = null
) {
    override fun toString(): String = "ActiveNodeEditor(requestId=$requestId, nodeId=$nodeId, loading=$loading, saving=$saving)"
}

internal data class NodeSaveNotice(val requestId: Long, val session: Session, val created: Boolean) {
    override fun toString(): String = "NodeSaveNotice(requestId=$requestId, created=$created)"
}
internal enum class NodeAction { DELETE, SYNC, RECONNECT }
internal data class NodeActionRequest(val requestId: Long, val session: Session, val nodeId: Int, val action: NodeAction) {
    override fun toString(): String = "NodeActionRequest(requestId=$requestId, nodeId=$nodeId, action=$action)"
}
internal data class NodeActionNotice(val request: NodeActionRequest, val error: String? = null)
internal data class NodeEditorSnapshot(
    val active: ActiveNodeEditor? = null,
    val notice: NodeSaveNotice? = null,
    val action: NodeActionRequest? = null,
    val actionNotice: NodeActionNotice? = null
)

/** Called on the main thread; stale async results cannot modify another editor or account. */
internal class NodeEditorState {
    private var nextRequestId = 0L
    private val mutableSnapshot = MutableStateFlow(NodeEditorSnapshot())
    val snapshot: StateFlow<NodeEditorSnapshot> = mutableSnapshot.asStateFlow()
    val active: ActiveNodeEditor? get() = snapshot.value.active

    fun bind(session: Session) {
        val value = snapshot.value
        mutableSnapshot.value = value.copy(
            active = value.active?.takeIf { it.session == session },
            notice = value.notice?.takeIf { it.session == session },
            action = value.action?.takeIf { it.session == session },
            actionNotice = value.actionNotice?.takeIf { it.request.session == session }
        )
    }

    fun open(session: Session, nodeId: Int?): ActiveNodeEditor? {
        if ((active?.session == session && active?.saving == true) || snapshot.value.action?.session == session) return null
        val next = ActiveNodeEditor(
            requestId = ++nextRequestId, session = session, nodeId = nodeId,
            draft = if (nodeId == null) NodeDraft() else null, loading = nodeId != null
        )
        mutableSnapshot.value = snapshot.value.copy(active = next, notice = snapshot.value.notice?.takeIf { it.session == session })
        return next
    }

    fun beginLoad(requestId: Long): ActiveNodeEditor? {
        val current = active ?: return null
        if (current.requestId != requestId || current.loading || current.saving || current.nodeId == null) return null
        val loading = current.copy(loading = true, error = null)
        mutableSnapshot.value = snapshot.value.copy(active = loading)
        return loading
    }

    fun finishLoad(requestId: Long, initial: NodeSnapshot) {
        val current = active ?: return
        if (current.requestId != requestId || !current.loading || current.nodeId != initial.id) return
        mutableSnapshot.value = snapshot.value.copy(active = current.copy(
            initial = initial, draft = NodeDraft.from(initial), loading = false, error = null
        ))
    }

    fun failLoad(requestId: Long, message: String) {
        val current = active ?: return
        if (current.requestId == requestId && current.loading) {
            mutableSnapshot.value = snapshot.value.copy(active = current.copy(loading = false, error = message))
        }
    }

    fun update(requestId: Long, draft: NodeDraft) {
        val current = active ?: return
        if (current.requestId == requestId && !current.loading && !current.saving && !current.blocked) {
            mutableSnapshot.value = snapshot.value.copy(active = current.copy(draft = draft, error = null))
        }
    }

    fun beginSave(requestId: Long): ActiveNodeEditor? {
        val current = active ?: return null
        if (current.requestId != requestId || current.loading || current.saving || current.blocked || current.draft == null) return null
        if (current.draft.validationError(current.nodeId == null, current.initial) != null) return null
        val saving = current.copy(saving = true, error = null)
        mutableSnapshot.value = snapshot.value.copy(active = saving)
        return saving
    }

    fun failSave(requestId: Long, message: String, blocked: Boolean) {
        val current = active ?: return
        if (current.requestId == requestId && current.saving) {
            mutableSnapshot.value = snapshot.value.copy(active = current.copy(saving = false, blocked = blocked, error = message))
        }
    }

    fun finishSave(requestId: Long) {
        val current = active ?: return
        if (current.requestId == requestId && current.saving) {
            mutableSnapshot.value = snapshot.value.copy(active = null, notice = NodeSaveNotice(requestId, current.session, current.nodeId == null))
        }
    }

    fun dismiss(requestId: Long) {
        val current = active ?: return
        if (current.requestId == requestId && !current.saving) mutableSnapshot.value = snapshot.value.copy(active = null)
    }

    fun consumeNotice(requestId: Long) {
        if (snapshot.value.notice?.requestId == requestId) mutableSnapshot.value = snapshot.value.copy(notice = null)
    }

    fun beginAction(session: Session, nodeId: Int, action: NodeAction): NodeActionRequest? {
        if (nodeId <= 0 || snapshot.value.action != null || active?.saving == true) return null
        val request = NodeActionRequest(++nextRequestId, session, nodeId, action)
        mutableSnapshot.value = snapshot.value.copy(action = request, actionNotice = null)
        return request
    }

    fun finishAction(request: NodeActionRequest, error: String? = null) {
        if (snapshot.value.action?.requestId == request.requestId) {
            mutableSnapshot.value = snapshot.value.copy(action = null, actionNotice = NodeActionNotice(request, error))
        }
    }

    fun consumeActionNotice(requestId: Long) {
        if (snapshot.value.actionNotice?.request?.requestId == requestId) {
            mutableSnapshot.value = snapshot.value.copy(actionNotice = null)
        }
    }
}
