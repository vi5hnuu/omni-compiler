package solutions.laxmi.omnicompiler.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.network.error.ApiCallRunner
import java.net.UnknownHostException

class ApiCallRunnerTest {

    private val runner = ApiCallRunner(Json { ignoreUnknownKeys = true })

    private fun httpError(code: Int, body: String, vararg headers: Pair<String, String>): HttpException {
        val raw = okhttp3.Response.Builder()
            .code(code)
            .message("error")
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .request(okhttp3.Request.Builder().url("https://example.test/").build())
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        return HttpException(Response.error<Unit>(body.toResponseBody(), raw))
    }

    @Test
    fun `judge rate limit carries Retry-After`() = runTest {
        val result = runner.judge<Unit> {
            throw httpError(429, """{"error":{"code":"rate_limited","message":"slow down"}}""", "Retry-After" to "12")
        }
        val error = (result as Outcome.Failure).error
        assertThat(error).isInstanceOf(AppError.RateLimited::class.java)
        assertThat((error as AppError.RateLimited).retryAfterSeconds).isEqualTo(12)
    }

    @Test
    fun `judge validation keeps the offending field`() = runTest {
        val result = runner.judge<Unit> {
            throw httpError(400, """{"error":{"code":"invalid_request","message":"bad cursor","details":{"field":"before"}}}""")
        }
        assertThat((result as Outcome.Failure).error).isEqualTo(AppError.Validation("bad cursor", "before"))
    }

    @Test
    fun `auth unverified e-mail is recognised`() = runTest {
        val result = runner.auth<Unit> {
            throw httpError(403, """{"success":false,"message":"Please verify your e-mail before signing in."}""")
        }
        assertThat((result as Outcome.Failure).error).isInstanceOf(AppError.EmailNotVerified::class.java)
    }

    @Test
    fun `unreachable host maps to offline`() = runTest {
        val result = runner.judge<Unit> { throw UnknownHostException() }
        assertThat((result as Outcome.Failure).error).isInstanceOf(AppError.Offline::class.java)
    }
}
