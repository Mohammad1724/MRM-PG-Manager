package com.mrm.pgmanager.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.model.AdminSelf
import com.mrm.pgmanager.data.model.PermissionScope
import com.mrm.pgmanager.data.model.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

internal enum class AdminAccessState { Unknown, Loading, Ready, Unavailable }

/** The panel is authoritative; unknown permissions must not be presented as granted. */
object AdminAccess {
    var current: AdminSelf? by mutableStateOf(null)
        private set
    internal var state: AdminAccessState by mutableStateOf(AdminAccessState.Unknown)
        private set

    private var loadedFor: Session? = null
    private var generation = 0L

    fun reset() {
        generation++
        current = null
        loadedFor = null
        state = AdminAccessState.Unknown
    }

    /** A stale or cancelled request cannot install permissions from a different account. */
    suspend fun refresh(session: Session, force: Boolean = false) {
        refreshWith(session, force, PanelApi::currentAdmin)
    }

    internal suspend fun refreshWith(session: Session, force: Boolean = false, load: suspend (Session) -> AdminSelf) {
        if (!force && loadedFor == session && current != null) return
        val request = ++generation
        current = null
        loadedFor = null
        state = AdminAccessState.Loading
        try {
            val admin = load(session)
            currentCoroutineContext().ensureActive()
            if (request == generation) {
                current = admin
                loadedFor = session
                state = AdminAccessState.Ready
            }
        } catch (error: CancellationException) {
            if (request == generation) state = AdminAccessState.Unknown
            throw error
        } catch (_: Exception) {
            val active = currentCoroutineContext().isActive
            if (request == generation) state = if (active) AdminAccessState.Unavailable else AdminAccessState.Unknown
            currentCoroutineContext().ensureActive()
        }
    }

    fun can(resource: String, action: String): Boolean = current?.can(resource, action) ?: false

    fun scopeAll(resource: String, action: String): Boolean =
        (current?.scope(resource, action) ?: PermissionScope.NONE) >= PermissionScope.ALL
}
