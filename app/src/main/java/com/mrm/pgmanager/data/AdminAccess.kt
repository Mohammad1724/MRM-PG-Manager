package com.mrm.pgmanager.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mrm.pgmanager.data.api.PanelApi
import com.mrm.pgmanager.data.cache.PanelCache
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
    private data class VerifiedAccess(val session: Session, val admin: AdminSelf)

    // Publish identity and permissions together so Compose observes a session change
    // even when both accounts return structurally identical admin data.
    private var verified: VerifiedAccess? by mutableStateOf(null)
    val current: AdminSelf? get() = verified?.admin
    internal var state: AdminAccessState by mutableStateOf(AdminAccessState.Unknown)
        private set

    private val lock = Any()
    private var generation = 0L

    fun reset() {
        synchronized(lock) {
            generation++
            verified = null
            state = AdminAccessState.Unknown
        }
    }

    /** A stale or cancelled request cannot install permissions from a different account. */
    suspend fun refresh(session: Session, force: Boolean = false) {
        refreshWith(session, force, PanelApi::currentAdmin)
    }

    internal suspend fun refreshWith(session: Session, force: Boolean = false, load: suspend (Session) -> AdminSelf) {
        val request = synchronized(lock) {
            if (!force && verified?.session == session) return
            generation++
            verified = null
            state = AdminAccessState.Loading
            generation
        }
        try {
            val admin = load(session)
            val context = currentCoroutineContext()
            context.ensureActive()
            synchronized(lock) {
                if (request == generation && context.isActive) {
                    verified = VerifiedAccess(session, admin)
                    // Revoke cached sensitive data even if the corresponding screen is not mounted.
                    if (!admin.can("settings", "read")) PanelCache.invalidate(PanelCache.panelSettingsKey(session))
                    if (!admin.can("api_keys", "read")) PanelCache.invalidate(PanelCache.apiKeysKey(session))
                    // Group scopes can change even when the same credential remains active.
                    PanelApi.invalidateGroupNameAccess(session)
                    state = AdminAccessState.Ready
                }
            }
        } catch (error: CancellationException) {
            synchronized(lock) { if (request == generation) state = AdminAccessState.Unknown }
            throw error
        } catch (_: Exception) {
            val context = currentCoroutineContext()
            synchronized(lock) {
                if (request == generation) state = if (context.isActive) AdminAccessState.Unavailable else AdminAccessState.Unknown
            }
            context.ensureActive()
        }
    }

    fun can(resource: String, action: String): Boolean = current?.can(resource, action) ?: false

    /** Permissions from another session must never authorize this session's data or responses. */
    fun canFor(session: Session, resource: String, action: String): Boolean {
        val access = verified
        return access?.session == session && access.admin.can(resource, action)
    }

    fun scopeAll(resource: String, action: String): Boolean =
        (current?.scope(resource, action) ?: PermissionScope.NONE) >= PermissionScope.ALL
}
