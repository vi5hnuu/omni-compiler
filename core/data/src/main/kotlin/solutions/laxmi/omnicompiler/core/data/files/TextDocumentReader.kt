package solutions.laxmi.omnicompiler.core.data.files

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import solutions.laxmi.omnicompiler.core.common.Dispatcher
import solutions.laxmi.omnicompiler.core.common.OmniDispatcher
import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.Outcome
import java.io.IOException
import javax.inject.Inject

/** Reads a user-picked document (Storage Access Framework URI) as UTF-8 text, bounded by the judge's limits. */
interface TextDocumentReader {
    suspend fun read(uri: String, maxBytes: Int = MAX_STDIN_BYTES): Outcome<String>

    companion object {
        /** ls-judge rejects stdin/expected values over 1 MiB. */
        const val MAX_STDIN_BYTES = 1024 * 1024
    }
}

internal class ContentResolverTextReader @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(OmniDispatcher.IO) private val io: CoroutineDispatcher,
) : TextDocumentReader {
    override suspend fun read(uri: String, maxBytes: Int): Outcome<String> = withContext(io) {
        try {
            val stream = context.contentResolver.openInputStream(uri.toUri())
                ?: return@withContext Outcome.Failure(AppError.NotFound("Couldn't open that file."))
            stream.use { input ->
                val bytes = input.readBounded(maxBytes + 1)
                if (bytes.size > maxBytes) Outcome.Failure(AppError.Validation("File is larger than ${maxBytes / 1024} KB."))
                else Outcome.Success(bytes.decodeToString())
            }
        } catch (e: IOException) {
            Outcome.Failure(AppError.Unknown("Couldn't read that file."))
        } catch (e: SecurityException) {
            Outcome.Failure(AppError.Forbidden("No permission to read that file."))
        }
    }
}

/** Reads at most [limit] bytes (InputStream.readNBytes needs API 33). */
private fun java.io.InputStream.readBounded(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
