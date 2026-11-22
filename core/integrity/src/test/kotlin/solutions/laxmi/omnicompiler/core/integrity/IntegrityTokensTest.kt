package solutions.laxmi.omnicompiler.core.integrity

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class IntegrityTokensTest {

    /** Counts prepares; each provider answers with its generation, or fails as told. */
    private class FakeSource : TokenProviderSource {
        var prepared = 0
        var prepareFailure: Exception? = null
        var invalidateGeneration = -1
        var hang = false
        override suspend fun prepare(cloudProjectNumber: Long): TokenProvider {
            prepareFailure?.let { throw it }
            val generation = ++prepared
            return TokenProvider { hash ->
                if (hang) awaitCancellation()
                if (generation == invalidateGeneration) throw ProviderInvalidException(IllegalStateException("-19"))
                "token-$generation-$hash"
            }
        }
    }

    private val source = FakeSource()

    private fun TestScope.tokens(project: Long = 123) = IntegrityTokens(source, IntegrityConfig(project, timeout = 5.seconds), backgroundScope)

    @Test
    fun `prepares once and reuses the provider`() = runTest {
        val tokens = tokens()
        assertThat(tokens.token("h1")).isEqualTo("token-1-h1")
        assertThat(tokens.token("h2")).isEqualTo("token-1-h2")
        assertThat(source.prepared).isEqualTo(1)
    }

    @Test
    fun `an invalid provider is replaced and the request retried once`() = runTest {
        val tokens = tokens()
        tokens.token("h")
        source.invalidateGeneration = 1
        assertThat(tokens.token("h")).isEqualTo("token-2-h")
        assertThat(source.prepared).isEqualTo(2)
    }

    @Test
    fun `failures give no token instead of an error`() = runTest {
        source.prepareFailure = IllegalStateException("Play services missing")
        assertThat(tokens().token("h")).isNull()
    }

    @Test
    fun `a slow Play gives up after the timeout`() = runTest {
        source.hang = true
        assertThat(tokens().token("h")).isNull()
    }

    @Test
    fun `no cloud project means attestation is off`() = runTest {
        val tokens = tokens(project = 0)
        tokens.onLaunch()
        assertThat(tokens.token("h")).isNull()
        assertThat(source.prepared).isEqualTo(0)
    }

    @Test
    fun `launch prepares ahead of the first request`() = runTest {
        val tokens = tokens()
        tokens.onLaunch()
        testScheduler.runCurrent()
        assertThat(source.prepared).isEqualTo(1)
        tokens.token("h")
        assertThat(source.prepared).isEqualTo(1)
    }
}
