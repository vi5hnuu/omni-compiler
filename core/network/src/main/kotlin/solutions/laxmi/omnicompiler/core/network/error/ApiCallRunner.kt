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
import solutions.laxmi.omnicompiler.core.model.ErrorReason
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
            Outcome.Failure(AppError.Offline(reason = ErrorReason.NetworkError))
        } catch (e: SerializationException) {
            Outcome.Failure(AppError.Unknown(reason = ErrorReason.BadResponse))
        }

    private fun judgeError(e: HttpException): AppError {
        val body = e.response()?.errorBody()?.string()
        val error = body?.let { runCatching { json.decodeFromString(JudgeErrorEnvelope.serializer(), it).error }.getOrNull() }
        val message = error?.message.orEmpty()
        val requestId = error?.requestId ?: e.response()?.headers()?.get("X-Request-ID")
        return when (error?.code) {
            "unauthorized" -> AppError.Unauthorized(message, fallbackReason(e.code(), message))
            "forbidden" -> AppError.Forbidden(message, fallbackReason(e.code(), message))
            "not_found" -> AppError.NotFound(message, fallbackReason(e.code(), message))
            "conflict", "idempotency_key_in_use" -> AppError.Conflict(message, fallbackReason(e.code(), message))
            "invalid_request", "unprocessable_entity", "payload_too_large", "unsupported_media_type" ->
                AppError.Validation(
                    message,
                    (error.details as? JsonObject)?.get("field")?.jsonPrimitive?.contentOrNull,
                    fallbackReason(e.code(), message),
                )
            "rate_limited" -> AppError.RateLimited(message, e.retryAfterSeconds(), fallbackReason(e.code(), message))
            "payment_required", "not_implemented", "too_early", "service_unavailable" ->
                AppError.NotAvailable(message, fallbackReason(e.code(), message))
            else -> byStatus(e, message, requestId)
        }
    }

    private fun authError(e: HttpException): AppError {
        val body = e.response()?.errorBody()?.string()
        val message = body
            ?.let { runCatching { json.decodeFromString(AuthEnvelope.serializer(JsonObject.serializer()), it).message }.getOrNull() }
            .orEmpty()
        // The auth service has no machine error codes; this 403 is identified by its stable copy.
        if (e.code() == 403 && message.contains("verify", ignoreCase = true)) return AppError.EmailNotVerified(message)
        return byStatus(e, message, requestId = null)
    }

    private fun byStatus(e: HttpException, message: String, requestId: String?): AppError {
        val reason = fallbackReason(e.code(), message)
        return when (e.code()) {
            400, 422 -> AppError.Validation(message, reason = reason)
            401 -> AppError.Unauthorized(message, reason)
            403 -> AppError.Forbidden(message, reason)
            404 -> AppError.NotFound(message, reason)
            409 -> AppError.Conflict(message, reason)
            429 -> AppError.RateLimited(message, e.retryAfterSeconds(), reason)
            501, 503, 425, 402 -> AppError.NotAvailable(message, reason)
            else -> AppError.Server(message, e.code(), requestId, reason)
        }
    }

    private fun HttpException.retryAfterSeconds(): Long? = response()?.headers()?.get("Retry-After")?.toLongOrNull()

    /** Servers normally explain errors; when a body has no message, the UI localizes one from the status. */
    private fun fallbackReason(status: Int, message: String): ErrorReason? = when {
        message.isNotBlank() -> null
        status == 429 -> ErrorReason.TooManyRequests
        status in 500..599 -> ErrorReason.ServerTrouble
        else -> ErrorReason.RequestFailed(status)
    }
}

/** Retrofit only throws for non-2xx on typed bodies; `Response<T>` calls use this to get the same behaviour. */
internal fun <T> Response<T>.requireSuccess(): Response<T> = if (isSuccessful) this else throw HttpException(this)
