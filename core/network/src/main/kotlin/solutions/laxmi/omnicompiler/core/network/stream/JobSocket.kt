package solutions.laxmi.omnicompiler.core.network.stream

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import solutions.laxmi.omnicompiler.core.network.BuildConfig
import solutions.laxmi.omnicompiler.core.network.di.Authenticated
import solutions.laxmi.omnicompiler.core.network.dto.AgentFrameDto
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Raised when the socket can't deliver the job to completion; callers fall back to polling. */
class JobStreamException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Live results for one job over `GET /jobs/{id}` upgraded to a WebSocket.
 * Frames sent before we subscribed are not replayed, so callers must fetch the full job once done.
 */
@Singleton
internal class JobSocket @Inject constructor(
    @Authenticated private val client: OkHttpClient,
    private val json: Json,
) {
    fun frames(jobId: String): Flow<AgentFrameDto> = callbackFlow {
        val request = Request.Builder()
            .url(socketUrl(jobId))
            .header("Sec-WebSocket-Protocol", SUBPROTOCOL)
            .build()
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = runCatching { json.decodeFromString(AgentFrameDto.serializer(), text) }.getOrNull() ?: return
                trySend(frame)
                if (frame.done) {
                    webSocket.close(NORMAL_CLOSURE, null)
                    channel.close()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                channel.close(JobStreamException("Stream closed before completion ($code)"))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                channel.close(JobStreamException("Stream failed (${response?.code ?: "no response"})", t))
            }
        })
        awaitClose { socket.cancel() }
        // trySend on the default 64-slot buffer would silently drop frames of a large run; frames are
        // bounded by the job's test count, so the buffer below is unbounded.
    }.buffer(Channel.UNLIMITED)

    private fun socketUrl(jobId: String): String {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        return "$base/jobs/$jobId"
    }

    private companion object {
        const val SUBPROTOCOL = "ls-judge-v1"
        const val NORMAL_CLOSURE = 1000
    }
}
