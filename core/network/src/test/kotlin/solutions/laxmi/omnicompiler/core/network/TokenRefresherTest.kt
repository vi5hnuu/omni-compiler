package solutions.laxmi.omnicompiler.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import solutions.laxmi.omnicompiler.core.common.TimeSource
import solutions.laxmi.omnicompiler.core.datastore.SessionStore
import solutions.laxmi.omnicompiler.core.datastore.StoredSession
import solutions.laxmi.omnicompiler.core.network.api.AuthPublicApi
import solutions.laxmi.omnicompiler.core.network.auth.TokenRefresher
import kotlin.time.Instant

class TokenRefresherTest {

    private lateinit var server: MockWebServer
    private lateinit var store: FakeSessionStore
    private lateinit var refresher: TokenRefresher

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthPublicApi::class.java)
        store = FakeSessionStore(StoredSession("old-access", "old-refresh", 0, user = null))
        refresher = TokenRefresher(api, store, FixedTime)
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `concurrent refreshes with the same stale token hit the server once`() = runTest {
        server.enqueue(tokens("new-access", "new-refresh"))

        val results = (1..5).map { async { refresher.refresh("old-access") } }.awaitAll()

        assertThat(results).containsExactly("new-access", "new-access", "new-access", "new-access", "new-access")
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(store.current()?.refreshToken).isEqualTo("new-refresh")
    }

    @Test
    fun `rejected refresh token signs the user out`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).body("""{"success":false,"message":"Session expired. Please sign in again."}""").build())

        assertThat(refresher.refresh("old-access")).isNull()
        assertThat(store.current()).isNull()
    }

    @Test
    fun `network failure keeps the session for a later retry`() = runTest {
        server.close()

        assertThat(refresher.refresh("old-access")).isNull()
        assertThat(store.current()).isNotNull()
    }

    private fun tokens(access: String, refresh: String) = MockResponse.Builder()
        .code(200)
        .body("""{"success":true,"data":{"accessToken":"$access","refreshToken":"$refresh","tokenType":"Bearer","expiresInSeconds":900}}""")
        .build()

    private object FixedTime : TimeSource {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_000_000)
    }

    private class FakeSessionStore(initial: StoredSession?) : SessionStore {
        private val state = MutableStateFlow(initial)
        override val session = state
        override suspend fun current() = state.value
        override suspend fun save(session: StoredSession) { state.value = session }
        override suspend fun update(transform: (StoredSession) -> StoredSession) { state.value = state.value?.let(transform) }
        override suspend fun clear() { state.value = null }
    }
}
