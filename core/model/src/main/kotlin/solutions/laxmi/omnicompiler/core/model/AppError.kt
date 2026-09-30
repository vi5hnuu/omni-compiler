package solutions.laxmi.omnicompiler.core.model

/** Failure taxonomy shared by every layer; UI maps each case to copy and actions. */
sealed interface AppError {
    val message: String

    data class Offline(override val message: String = "You're offline.") : AppError
    data class Timeout(override val message: String = "The server took too long to respond.") : AppError
    data class Unauthorized(override val message: String = "Please sign in again.") : AppError
    data class Forbidden(override val message: String) : AppError
    data class NotFound(override val message: String) : AppError
    data class Conflict(override val message: String) : AppError
    data class Validation(override val message: String, val field: String? = null) : AppError
    data class RateLimited(override val message: String, val retryAfterSeconds: Long?) : AppError
    data class NotAvailable(override val message: String) : AppError
    data class Server(override val message: String, val status: Int, val requestId: String? = null) : AppError
    data class Unknown(override val message: String = "Something went wrong. Please try again.") : AppError

    /** Auth-specific: sign-in blocked until the e-mail address is verified. */
    data class EmailNotVerified(override val message: String) : AppError
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
