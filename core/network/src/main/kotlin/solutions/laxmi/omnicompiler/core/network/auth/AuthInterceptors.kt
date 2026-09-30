package solutions.laxmi.omnicompiler.core.network.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

private const val AUTHORIZATION = "Authorization"
private const val BEARER = "Bearer "

/** Attaches the current (proactively refreshed) access token. Anonymous calls go out without one. */
internal class BearerInterceptor @Inject constructor(private val refresher: TokenRefresher) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(AUTHORIZATION) != null) return chain.proceed(request)
        // OkHttp interceptors are synchronous and already run on a background thread.
        val token = runBlocking { refresher.validAccessToken() }
        return chain.proceed(if (token == null) request else request.withBearer(token))
    }
}

/** Handles a 401 on an authenticated request by refreshing once and retrying. */
internal class TokenAuthenticator @Inject constructor(private val refresher: TokenRefresher) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val sent = response.request.header(AUTHORIZATION)?.removePrefix(BEARER) ?: return null
        if (response.priorResponseCount() >= 1) return null
        val fresh = runBlocking { refresher.refresh(sent) } ?: return null
        return response.request.withBearer(fresh)
    }

    private fun Response.priorResponseCount(): Int = generateSequence(priorResponse) { it.priorResponse }.count()
}

internal fun Request.withBearer(token: String): Request = newBuilder().header(AUTHORIZATION, BEARER + token).build()
