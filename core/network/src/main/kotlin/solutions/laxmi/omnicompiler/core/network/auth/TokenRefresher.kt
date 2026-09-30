package solutions.laxmi.omnicompiler.core.network.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import solutions.laxmi.omnicompiler.core.common.TimeSource
import solutions.laxmi.omnicompiler.core.datastore.SessionStore
import solutions.laxmi.omnicompiler.core.network.BuildConfig
import solutions.laxmi.omnicompiler.core.network.api.AuthPublicApi
import solutions.laxmi.omnicompiler.core.network.dto.RefreshRequestDto
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rotates tokens through `POST /auth/refresh`, one caller at a time.
 *
 * The auth service treats a second use of a refresh token as theft and revokes every session of
 * the user, so concurrent 401s must collapse into a single refresh. Callers pass the access token
 * they used; if it is already stale in the store, another caller has refreshed and we reuse that.
 */
@Singleton
class TokenRefresher @Inject internal constructor(
    private val api: AuthPublicApi,
    private val store: SessionStore,
    private val time: TimeSource,
) {
    private val mutex = Mutex()

    /** Returns a usable access token, or null when the session is gone (caller should give up). */
    suspend fun refresh(staleAccessToken: String?): String? = mutex.withLock {
        val current = store.current() ?: return null
        if (staleAccessToken != null && current.accessToken != staleAccessToken) return current.accessToken
        try {
            val tokens = api.refresh(BuildConfig.JWT_AUDIENCE, RefreshRequestDto(current.refreshToken)).data ?: return null
            val updated = tokens.toStoredSession(time.now().toEpochMilliseconds(), current.user)
            // Persist before anyone uses the new token so a crash can't strand us on a revoked refresh token.
            store.save(updated)
            updated.accessToken
        } catch (e: HttpException) {
            // 401/403/404 mean the refresh token is dead (expired, reused, locked, deleted): sign out.
            if (e.code() in DEAD_SESSION_CODES) store.clear()
            null
        } catch (e: IOException) {
            null
        }
    }

    /** Access token that is valid for at least [MIN_VALIDITY_MS], refreshing proactively if needed. */
    suspend fun validAccessToken(): String? {
        val current = store.current() ?: return null
        val remaining = current.accessExpiresAtEpochMs - time.now().toEpochMilliseconds()
        return if (remaining > MIN_VALIDITY_MS) current.accessToken else refresh(current.accessToken) ?: current.accessToken
    }

    private companion object {
        const val MIN_VALIDITY_MS = 30_000L
        val DEAD_SESSION_CODES = setOf(401, 403, 404)
    }
}
