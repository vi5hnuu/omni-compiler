package solutions.laxmi.omnicompiler.core.datastore

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/** A personal access token and the login it belongs to. */
@Serializable
data class StoredGitToken(val token: String, val login: String)

/** Tokens per host name (`GITHUB`, `GITLAB`), encrypted at rest like the session. */
@Serializable
data class StoredGitCredentials(val tokens: Map<String, StoredGitToken> = emptyMap())

interface GitCredentialStore {
    val credentials: Flow<Map<String, StoredGitToken>>
    suspend fun token(host: String): StoredGitToken?
    suspend fun save(host: String, token: StoredGitToken)
    suspend fun remove(host: String)
    suspend fun clear()
}

@Singleton
internal class EncryptedGitCredentialStore @Inject constructor(
    private val dataStore: DataStore<StoredGitCredentials?>,
) : GitCredentialStore {
    override val credentials: Flow<Map<String, StoredGitToken>> = dataStore.data.map { it?.tokens.orEmpty() }

    override suspend fun token(host: String): StoredGitToken? = credentials.first()[host]

    override suspend fun save(host: String, token: StoredGitToken) {
        dataStore.updateData { current -> StoredGitCredentials(current?.tokens.orEmpty() + (host to token)) }
    }

    override suspend fun remove(host: String) {
        dataStore.updateData { current -> StoredGitCredentials(current?.tokens.orEmpty() - host) }
    }

    override suspend fun clear() {
        dataStore.updateData { null }
    }
}
