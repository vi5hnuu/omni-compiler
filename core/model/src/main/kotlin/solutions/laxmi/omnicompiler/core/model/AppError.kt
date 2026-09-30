package solutions.laxmi.omnicompiler.core.model

/**
 * Failure taxonomy shared by every layer; the UI maps each case to copy and actions.
 *
 * Errors explained by a server carry its [message] verbatim. Errors detected on the device carry a
 * [reason] instead of English text, so the UI can show them in the user's language.
 */
sealed interface AppError {
    /** Server-provided text, shown as sent; empty for client-side errors. */
    val message: String

    /** Client-side cause, localized by the UI; null when the server explained the error. */
    val reason: ErrorReason?

    data class Offline(override val message: String = "", override val reason: ErrorReason? = ErrorReason.Offline) : AppError
    data class Timeout(override val message: String = "", override val reason: ErrorReason? = ErrorReason.Timeout) : AppError
    data class Unauthorized(override val message: String = "", override val reason: ErrorReason? = null) : AppError
    data class Forbidden(override val message: String = "", override val reason: ErrorReason? = null) : AppError
    data class NotFound(override val message: String = "", override val reason: ErrorReason? = null) : AppError
    data class Conflict(override val message: String = "", override val reason: ErrorReason? = null) : AppError
    data class Validation(
        override val message: String = "",
        val field: String? = null,
        override val reason: ErrorReason? = null,
    ) : AppError
    data class RateLimited(
        override val message: String = "",
        val retryAfterSeconds: Long? = null,
        override val reason: ErrorReason? = null,
    ) : AppError
    data class NotAvailable(override val message: String = "", override val reason: ErrorReason? = null) : AppError
    data class Server(
        override val message: String = "",
        val status: Int,
        val requestId: String? = null,
        override val reason: ErrorReason? = null,
    ) : AppError
    data class Unknown(override val message: String = "", override val reason: ErrorReason? = ErrorReason.Unknown) : AppError

    /** Auth-specific: sign-in blocked until the e-mail address is verified. */
    data class EmailNotVerified(override val message: String = "", override val reason: ErrorReason? = null) : AppError
}

/** Result of a repository call. Avoids leaking transport exceptions to the UI. */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(block: (T) -> Unit): Outcome<T> = also { if (it is Outcome.Success) block(it.value) }

inline fun <T> Outcome<T>.onFailure(block: (AppError) -> Unit): Outcome<T> = also { if (it is Outcome.Failure) block(it.error) }

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value
