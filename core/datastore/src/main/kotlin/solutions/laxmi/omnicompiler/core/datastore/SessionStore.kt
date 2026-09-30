package solutions.laxmi.omnicompiler.core.datastore

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
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
}

@Singleton
internal class EncryptedSessionStore @Inject constructor(
    private val dataStore: DataStore<StoredSession?>,
) : SessionStore {
    override val session: Flow<StoredSession?> = dataStore.data

    override suspend fun current(): StoredSession? = dataStore.data.first()

    override suspend fun save(session: StoredSession) {
        dataStore.updateData { session }
    }

    override suspend fun update(transform: (StoredSession) -> StoredSession) {
        dataStore.updateData { existing -> existing?.let(transform) }
    }

    override suspend fun clear() {
        dataStore.updateData { null }
    }
}
