package solutions.laxmi.omnicompiler.core.integrity

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import solutions.laxmi.omnicompiler.core.network.humancheck.HumanCheckTokens
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Play Integrity settings. [cloudProjectNumber] is the Google Cloud project linked to the app in Play Console (not a
 * secret); 0 turns attestation off, which is right for debug builds (Play never recognises them).
 */
data class IntegrityConfig(val cloudProjectNumber: Long, val timeout: Duration = 10.seconds)

/** Starts attestation set-up when the app launches, so the first sign-in doesn't wait for it. */
interface AppIntegrity {
    fun onLaunch()
}

/** A prepared Play Integrity token provider; see [IntegrityTokens]. */
internal fun interface TokenProvider {
    /** @throws ProviderInvalidException when Play has discarded this provider and a new one must be prepared */
    suspend fun request(requestHash: String): String
}

/** Prepares token providers; the seam between [IntegrityTokens] and Google's SDK. */
internal fun interface TokenProviderSource {
    suspend fun prepare(cloudProjectNumber: Long): TokenProvider
}

internal class ProviderInvalidException(cause: Throwable) : Exception(cause)

/**
 * Standard-request attestation, following Google's guidance: prepare one token provider ahead of need (at launch),
 * reuse it for every request, and prepare a new one only when Play reports it invalid. Any other failure yields no
 * token rather than an error: the request goes out without one and the server's policy decides.
 */
internal class IntegrityTokens(
    private val source: TokenProviderSource,
    private val config: IntegrityConfig,
    private val scope: CoroutineScope,
) : HumanCheckTokens, AppIntegrity {

    private val mutex = Mutex()
    private var provider: TokenProvider? = null

    private val enabled get() = config.cloudProjectNumber > 0

    override fun onLaunch() {
        if (enabled) scope.launch { attempt("prepare") { provider() } }
    }

    override suspend fun token(requestHash: String): String? {
        if (!enabled) return null
        return withTimeoutOrNull(config.timeout) {
            attempt("request") {
                val current = provider()
                try {
                    current.request(requestHash)
                } catch (e: ProviderInvalidException) {
                    discard(current)
                    provider().request(requestHash)
                }
            }
        }.also { if (it == null) Timber.w("No integrity token; the request goes out without one") }
    }

    private suspend fun provider(): TokenProvider = mutex.withLock {
        provider ?: source.prepare(config.cloudProjectNumber).also { provider = it }
    }

    /** Forgets [stale] unless another caller has already replaced it. */
    private suspend fun discard(stale: TokenProvider) = mutex.withLock {
        if (provider === stale) provider = null
    }

    private suspend fun <T> attempt(step: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Play services missing or outdated, offline, quota: logged, never surfaced (the server decides).
        Timber.w(e, "Play Integrity %s failed", step)
        null
    }
}
