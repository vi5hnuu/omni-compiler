package solutions.laxmi.omnicompiler.core.ui

import solutions.laxmi.omnicompiler.core.model.AppError

/** User-facing copy for an error; server messages are already human-readable and are preferred. */
fun AppError.userMessage(): String = when (this) {
    is AppError.RateLimited -> retryAfterSeconds?.let { "$message Try again in ${it}s." } ?: message
    is AppError.Server -> requestId?.let { "$message (ref ${it.take(8)})" } ?: message
    else -> message
}
