package solutions.laxmi.omnicompiler.core.network.humancheck

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import retrofit2.Invocation

/** Header the auth service reads the attestation from. */
internal const val HUMAN_CHECK_HEADER = "X-Captcha-Token"

/** Header that names this app to the auth service: tokens are issued for it, and its bot-protection policy applies. */
internal const val AUDIENCE_HEADER = "X-Audience"

/** Names the app on every auth call, so each one is judged by this app's policy rather than the service default. */
internal class AudienceInterceptor(private val audience: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().newBuilder().header(AUDIENCE_HEADER, audience).build())
}

/**
 * Attaches a [HumanCheckTokens] token to calls annotated with [HumanCheck], bound to that exact request:
 *
 * `requestHash = base64url-without-padding(SHA-256(UTF-8(action) || '\n' || body))`
 *
 * The body is serialised once here and sent as those same bytes, so client and server hash identical input. A token
 * is single use: a retried request (e.g. after a token refresh) reuses it and the server may refuse it.
 */
internal class HumanCheckInterceptor(private val tokens: HumanCheckTokens) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val action = request.tag(Invocation::class.java)?.method()?.getAnnotation(HumanCheck::class.java)?.action
            ?: return chain.proceed(request)
        val original = request.body
        val bytes = original?.let { body -> Buffer().also(body::writeTo).readByteArray() } ?: ByteArray(0)
        // OkHttp interceptors are synchronous and already run on a background thread.
        val token = runBlocking { tokens.token(requestHash(action, bytes)) }
        val builder = request.newBuilder()
        if (original != null) builder.method(request.method, bytes.toRequestBody(original.contentType()))
        if (token != null) builder.header(HUMAN_CHECK_HEADER, token)
        return chain.proceed(builder.build())
    }
}

/** The request hash the auth service recomputes; see [HumanCheckInterceptor]. */
internal fun requestHash(action: String, body: ByteArray): String =
    Buffer().writeUtf8(action).writeByte('\n'.code).write(body).sha256().base64Url().trimEnd('=')
