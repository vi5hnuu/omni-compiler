package solutions.laxmi.omnicompiler.core.network.error

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import retrofit2.Response
import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.network.dto.AuthEnvelope
import solutions.laxmi.omnicompiler.core.network.dto.JudgeErrorEnvelope
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a network call and converts every failure into an [AppError].
 * The two backends use different error envelopes, hence the two entry points.
 */
@Singleton
internal class ApiCallRunner @Inject constructor(private val json: Json) {

    suspend fun <T> judge(block: suspend () -> T): Outcome<T> = run(block, ::judgeError)

    suspend fun <T> auth(block: suspend () -> T): Outcome<T> = run(block, ::authError)

    private suspend fun <T> run(block: suspend () -> T, mapHttp: (HttpException) -> AppError): Outcome<T> =
        try {
            Outcome.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            Outcome.Failure(mapHttp(e))
        } catch (e: InterruptedIOException) {
            Outcome.Failure(AppError.Timeout())
        } catch (e: UnknownHostException) {
            Outcome.Failure(AppError.Offline())
        } catch (e: ConnectException) {
            Outcome.Failure(AppError.Offline())
        } catch (e: IOException) {
            Outcome.Failure(AppError.Offline("Network error. Check your connection."))
        } catch (e: SerializationException) {
            Outcome.Failure(AppError.Unknown("Unexpected response from the server."))
        }

    private fun judgeError(e: HttpException): AppError {
        val body = e.response()?.errorBody()?.string()
        val error = body?.let { runCatching { json.decodeFromString(JudgeErrorEnvelope.serializer(), it).error }.getOrNull() }
        val message = error?.message?.takeIf { it.isNotBlank() } ?: defaultMessage(e.code())
        val requestId = error?.requestId ?: e.response()?.headers()?.get("X-Request-ID")
        return when (error?.code) {
            "unauthorized" -> AppError.Unauthorized(message)
            "forbidden" -> AppError.Forbidden(message)
            "not_found" -> AppError.NotFound(message)
            "conflict", "idempotency_key_in_use" -> AppError.Conflict(message)
            "invalid_request", "unprocessable_entity", "payload_too_large", "unsupported_media_type" ->
                AppError.Validation(message, (error.details as? JsonObject)?.get("field")?.jsonPrimitive?.contentOrNull)
            "rate_limited" -> AppError.RateLimited(message, e.retryAfterSeconds())
            "payment_required", "not_implemented", "too_early", "service_unavailable" -> AppError.NotAvailable(message)
            else -> byStatus(e, message, requestId)
        }
    }

    private fun authError(e: HttpException): AppError {
        val body = e.response()?.errorBody()?.string()
        val message = body
            ?.let { runCatching { json.decodeFromString(AuthEnvelope.serializer(JsonObject.serializer()), it).message }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
            ?: defaultMessage(e.code())
        // The auth service has no machine error codes; this 403 is identified by its stable copy.
        if (e.code() == 403 && message.contains("verify", ignoreCase = true)) return AppError.EmailNotVerified(message)
        return byStatus(e, message, requestId = null)
    }

    private fun byStatus(e: HttpException, message: String, requestId: String?): AppError = when (e.code()) {
        400, 422 -> AppError.Validation(message)
        401 -> AppError.Unauthorized(message)
        403 -> AppError.Forbidden(message)
        404 -> AppError.NotFound(message)
        409 -> AppError.Conflict(message)
        429 -> AppError.RateLimited(message, e.retryAfterSeconds())
        501, 503, 425, 402 -> AppError.NotAvailable(message)
        else -> AppError.Server(message, e.code(), requestId)
    }

    private fun HttpException.retryAfterSeconds(): Long? = response()?.headers()?.get("Retry-After")?.toLongOrNull()

    private fun defaultMessage(status: Int) = when (status) {
        429 -> "Too many requests. Please slow down."
        in 500..599 -> "The server had a problem. Please try again."
        else -> "Request failed ($status)."
    }
}

/** Retrofit only throws for non-2xx on typed bodies; `Response<T>` calls use this to get the same behaviour. */
internal fun <T> Response<T>.requireSuccess(): Response<T> = if (isSuccessful) this else throw HttpException(this)
