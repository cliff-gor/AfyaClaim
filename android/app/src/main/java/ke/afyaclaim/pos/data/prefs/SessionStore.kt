package ke.afyaclaim.pos.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import ke.afyaclaim.pos.domain.model.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "afyaclaim_session")

/** Where the pull cursor (server updated_at high-water mark) lives. */
interface SyncCursorStore {
    suspend fun cursor(): String?
    suspend fun saveCursor(cursor: String, syncedAt: Long)
}

/** Persists the auth session, sync cursor and last sync outcome in DataStore. */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext context: Context,
) : SyncCursorStore {
    private val store = context.dataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private object Keys {
        val token = stringPreferencesKey("token")
        val userId = longPreferencesKey("user_id")
        val name = stringPreferencesKey("name")
        val email = stringPreferencesKey("email")
        val pharmacyName = stringPreferencesKey("pharmacy_name")
        val pharmacyTown = stringPreferencesKey("pharmacy_town")
        val cursor = stringPreferencesKey("sync_cursor")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastSyncError = stringPreferencesKey("last_sync_error")
    }

    /** Cold flow of the persisted session; the first emission arrives once DataStore is read. */
    val sessionFlow: Flow<Session?> = store.data.map(::readSession)

    val session: StateFlow<Session?> = sessionFlow.stateIn(scope, SharingStarted.Eagerly, null)

    val lastSyncAt: Flow<Long?> = store.data.map { it[Keys.lastSyncAt] }
    val lastSyncError: Flow<String?> = store.data.map { it[Keys.lastSyncError] }

    private fun readSession(prefs: Preferences): Session? {
        val token = prefs[Keys.token] ?: return null
        return Session(
            token = token,
            userId = prefs[Keys.userId] ?: 0,
            name = prefs[Keys.name].orEmpty(),
            email = prefs[Keys.email].orEmpty(),
            pharmacyName = prefs[Keys.pharmacyName].orEmpty(),
            pharmacyTown = prefs[Keys.pharmacyTown].orEmpty(),
        )
    }

    /** Called from OkHttp's background thread, so a blocking read is acceptable here. */
    fun cachedToken(): String? = session.value?.token
        ?: runBlocking { store.data.first()[Keys.token] }

    suspend fun save(session: Session) {
        store.edit {
            it[Keys.token] = session.token
            it[Keys.userId] = session.userId
            it[Keys.name] = session.name
            it[Keys.email] = session.email
            it[Keys.pharmacyName] = session.pharmacyName
            it[Keys.pharmacyTown] = session.pharmacyTown
        }
    }

    suspend fun signOut() {
        store.edit { it.remove(Keys.token) }
    }

    fun markExpired() {
        scope.launch { signOut() }
    }

    override suspend fun cursor(): String? = store.data.first()[Keys.cursor]

    override suspend fun saveCursor(cursor: String, syncedAt: Long) {
        store.edit {
            it[Keys.cursor] = cursor
            it[Keys.lastSyncAt] = syncedAt
            it.remove(Keys.lastSyncError)
        }
    }

    suspend fun recordSyncError(message: String) {
        store.edit { it[Keys.lastSyncError] = message }
    }

    /** A different pharmacist signing in must not inherit the previous cursor. */
    suspend fun resetCursor() {
        store.edit { it.remove(Keys.cursor) }
    }
}
