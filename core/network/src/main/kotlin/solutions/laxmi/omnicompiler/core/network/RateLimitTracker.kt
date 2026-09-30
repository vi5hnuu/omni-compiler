package solutions.laxmi.omnicompiler.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response
import solutions.laxmi.omnicompiler.core.model.RateLimitSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/** Latest execute-bucket rate limit, read from response headers so the UI can show remaining runs. */
@Singleton
class RateLimitTracker @Inject constructor() {
    private val state = MutableStateFlow<RateLimitSnapshot?>(null)
    val snapshot: StateFlow<RateLimitSnapshot?> = state.asStateFlow()

    internal fun record(response: Response) {
        val limit = response.header("X-RateLimit-Limit")?.toIntOrNull() ?: return
        val remaining = response.header("X-RateLimit-Remaining")?.toIntOrNull() ?: return
        val reset = response.header("X-RateLimit-Reset")?.toLongOrNull() ?: return
        state.value = RateLimitSnapshot(limit, remaining, reset)
    }

    fun clear() {
        state.value = null
    }
}

/** Only execute calls share the tier bucket; reads use separate, larger buckets. */
internal class RateLimitInterceptor @Inject constructor(private val tracker: RateLimitTracker) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val path = chain.request().url.encodedPath
        if (path.endsWith("/execute") && !path.endsWith("/batch/execute")) tracker.record(response)
        return response
    }
}
