package solutions.laxmi.omnicompiler.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import solutions.laxmi.omnicompiler.core.network.api.AuthPublicApi
import solutions.laxmi.omnicompiler.core.network.dto.RefreshRequestDto

class LogoutTest {
    private val server = MockWebServer().apply { start() }

    @After
    fun tearDown() = server.close()

    @Test
    fun `logout carries the captured access token and the refresh token`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"success":true,"message":"Signed out."}""").build())
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthPublicApi::class.java)

        api.logout("Bearer access-1", RefreshRequestDto("refresh-1"))

        val request = server.takeRequest()
        assertThat(request.target).isEqualTo("/auth/logout")
        assertThat(request.headers["Authorization"]).isEqualTo("Bearer access-1")
        assertThat(request.body?.utf8()).contains("refresh-1")
    }
}
