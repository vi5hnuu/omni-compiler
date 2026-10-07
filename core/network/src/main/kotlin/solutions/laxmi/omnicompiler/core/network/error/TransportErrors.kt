package solutions.laxmi.omnicompiler.core.network.error

import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.ErrorReason
import timber.log.Timber
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Maps a failed request's transport exception to an [AppError]. "Can't reach the server" stays a connectivity error
 * (runs queue and retry), but it is never reported as the device being offline: whether the device has a network
 * at all is the ConnectivityObserver's call, not something an exception can tell.
 */
internal fun transportError(e: IOException): AppError = when (e) {
    is InterruptedIOException -> AppError.Timeout().also { Timber.w(e, "Request timed out") }
    is UnknownHostException, is ConnectException, is NoRouteToHostException ->
        AppError.Offline(reason = ErrorReason.ServerUnreachable).also { Timber.w(e, "Server unreachable") }
    is SSLException -> AppError.Unknown(reason = ErrorReason.SecureConnectionFailed).also { Timber.w(e, "TLS handshake failed") }
    else -> AppError.Offline(reason = ErrorReason.NetworkError).also { Timber.w(e, "Network I/O failed") }
}
