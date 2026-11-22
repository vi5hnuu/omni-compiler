package solutions.laxmi.omnicompiler.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okio.ByteString.Companion.encodeUtf8
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import solutions.laxmi.omnicompiler.core.network.api.AuthPublicApi
import solutions.laxmi.omnicompiler.core.network.dto.LoginRequestDto
import solutions.laxmi.omnicompiler.core.network.dto.RefreshRequestDto
import solutions.laxmi.omnicompiler.core.network.humancheck.AudienceInterceptor
import solutions.laxmi.omnicompiler.core.network.humancheck.HumanCheckInterceptor
import solutions.laxmi.omnicompiler.core.network.humancheck.HumanCheckTokens
import solutions.laxmi.omnicompiler.core.network.humancheck.requestHash

/** The real Retrofit interface through the real interceptors, against a local server. */
class HumanCheckInterceptorTest {

    private lateinit var server: MockWebServer
    private val tokens = RecordingTokens()
    private lateinit var api: AuthPublicApi

    private class RecordingTokens : HumanCheckTokens {
        val hashes = mutableListOf<String>()
        var next: String? = "attestation"
        override suspend fun token(requestHash: String): String? = next.also { hashes += requestHash }
    }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder()
            .addInterceptor(AudienceInterceptor("test-app"))
            .addInterceptor(HumanCheckInterceptor(tokens))
            .build()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthPublicApi::class.java)
    }

    @After
    fun tearDown() = server.close()

    private fun ok() = server.enqueue(MockResponse.Builder().body("""{"success":true}""").build())

    @Test
    fun `a protected call carries a token bound to the exact bytes sent`() = runTest {
        ok()
        api.login(LoginRequestDto("a@example.com", "pw"))

        val sent = server.takeRequest()
        val body = sent.body!!.utf8()
        assertThat(sent.headers["X-Captcha-Token"]).isEqualTo("attestation")
        assertThat(sent.headers["X-Audience"]).isEqualTo("test-app")
        assertThat(tokens.hashes).containsExactly(requestHash("sign-in", body.toByteArray()))
    }

    @Test
    fun `a call without a body is bound to its action alone`() = runTest {
        ok()
        api.guest()

        assertThat(server.takeRequest().headers["X-Captcha-Token"]).isEqualTo("attestation")
        assertThat(tokens.hashes).containsExactly(requestHash("guest", ByteArray(0)))
    }

    @Test
    fun `unprotected calls get the audience but no token`() = runTest {
        ok()
        api.refresh(RefreshRequestDto("r"))

        val sent = server.takeRequest()
        assertThat(sent.headers["X-Audience"]).isEqualTo("test-app")
        assertThat(sent.headers["X-Captcha-Token"]).isNull()
        assertThat(tokens.hashes).isEmpty()
    }

    @Test
    fun `without a token the request still goes out and the server decides`() = runTest {
        tokens.next = null
        ok()
        api.login(LoginRequestDto("a@example.com", "pw"))

        val sent = server.takeRequest()
        assertThat(sent.headers["X-Captcha-Token"]).isNull()
        assertThat(sent.body!!.utf8()).contains("a@example.com")
    }

    @Test
    fun `request hash matches the auth service contract`() {
        // The same vectors are pinned in the auth service's PlayIntegrityVerifierTest; change both or neither.
        assertThat(requestHash("guest", ByteArray(0))).isEqualTo("CsKaWbmLQGONi8yvr6wLbItZfCo0un5PeTaIiGnW0h4")
        assertThat(requestHash("sign-in", """{"identifier":"a@example.com","password":"pw"}""".encodeUtf8().toByteArray()))
            .isEqualTo("mtM5WYoCTju295h7DQgOQ2dKPnVmwJvX2C23vHeChgA")
    }
}
