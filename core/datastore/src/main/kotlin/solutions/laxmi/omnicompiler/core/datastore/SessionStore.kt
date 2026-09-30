package solutions.laxmi.omnicompiler.core.datastore

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for the signed-in credentials. */
interface SessionStore {
    val session: Flow<StoredSession?>
    suspend fun current(): StoredSession?
    suspend fun save(session: StoredSession)
    suspend fun update(transform: (StoredSession) -> StoredSession)
    suspend fun clear()

    /**
     * True after the server rejected the session (refresh token expired or revoked), until acknowledged
     * or a new session is saved. A user-initiated sign-out never sets it. Kept in memory: it only needs to
     * reach the sign-in screen the user is sent to right away.
     */
    val expired: StateFlow<Boolean>

    /** Clears the session because the server ended it. */
    suspend fun expire()

    fun acknowledgeExpiry()
}

@Singleton
internal class EncryptedSessionStore @Inject constructor(
    private val dataStore: DataStore<StoredSession?>,
) : SessionStore {
    override val session: Flow<StoredSession?> = dataStore.data

    private val expiredState = MutableStateFlow(false)
    override val expired: StateFlow<Boolean> = expiredState.asStateFlow()

    override suspend fun current(): StoredSession? = dataStore.data.first()

    override suspend fun save(session: StoredSession) {
        dataStore.updateData { session }
        expiredState.value = false
    }

    override suspend fun update(transform: (StoredSession) -> StoredSession) {
        dataStore.updateData { existing -> existing?.let(transform) }
    }

    override suspend fun clear() {
        dataStore.updateData { null }
    }

    override suspend fun expire() {
        clear()
        expiredState.value = true
    }

    override fun acknowledgeExpiry() {
        expiredState.value = false
    }
}
