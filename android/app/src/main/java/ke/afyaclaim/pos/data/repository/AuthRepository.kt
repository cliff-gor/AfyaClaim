package ke.afyaclaim.pos.data.repository

import ke.afyaclaim.pos.data.local.AfyaDatabase
import ke.afyaclaim.pos.data.prefs.SessionStore
import ke.afyaclaim.pos.data.remote.AfyaApi
import ke.afyaclaim.pos.data.remote.ApiErrors
import ke.afyaclaim.pos.data.remote.dto.LoginRequest
import ke.afyaclaim.pos.data.sync.SyncScheduler
import ke.afyaclaim.pos.domain.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: AfyaApi,
    private val db: AfyaDatabase,
    private val sessionStore: SessionStore,
    private val scheduler: SyncScheduler,
) {
    val session: Flow<Session?> = sessionStore.sessionFlow

    suspend fun login(email: String, password: String): Result<Session> = withContext(Dispatchers.IO) {
        runCatching { api.login(LoginRequest(email.trim(), password)) }
            .mapCatching { response ->
                val user = response.user
                val pharmacy = user.pharmacy
                    ?: error("This account is not linked to a pharmacy. Use the AfyaClaim dashboard instead.")
                val previous = sessionStore.sessionFlow.first()
                val lastUserId = previous?.userId
                val session = Session(
                    token = response.token,
                    userId = user.id,
                    name = user.name,
                    email = user.email,
                    pharmacyName = pharmacy.name,
                    pharmacyTown = pharmacy.town.orEmpty(),
                )
                if (lastUserId != null && lastUserId != user.id) {
                    db.clearAllTables()
                    sessionStore.resetCursor()
                }
                sessionStore.save(session)
                scheduler.schedulePeriodic()
                scheduler.syncNow()
                session
            }
            .recoverCatching { throw IllegalStateException(ApiErrors.message(it), it) }
    }

    suspend fun signOut() {
        scheduler.cancelAll()
        sessionStore.signOut()
    }
}
