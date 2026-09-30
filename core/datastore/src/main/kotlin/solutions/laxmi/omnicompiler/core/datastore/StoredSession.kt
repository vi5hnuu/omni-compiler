package solutions.laxmi.omnicompiler.core.datastore

import kotlinx.serialization.Serializable

/** Persisted credentials. Only ever written encrypted (see [SessionSerializer]). */
@Serializable
data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAtEpochMs: Long,
    val user: StoredUser?,
)

@Serializable
data class StoredUser(
    val id: String,
    val accountType: String,
    val provider: String,
    val email: String? = null,
    val username: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val profileUrl: String? = null,
    val verified: Boolean = false,
    val createdAt: String? = null,
)
