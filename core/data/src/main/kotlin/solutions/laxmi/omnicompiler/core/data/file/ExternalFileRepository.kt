package solutions.laxmi.omnicompiler.core.data.file

import solutions.laxmi.omnicompiler.core.data.project.ProjectSync
import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.ErrorReason
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.storage.AccessLostException
import solutions.laxmi.omnicompiler.core.storage.FileTooLargeException
import solutions.laxmi.omnicompiler.core.storage.ProjectFolderStore
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import javax.inject.Inject

/** A file opened from another app, edited where it lives. [writable] is false when it was shared read-only. */
data class ExternalFile(val uri: String, val name: String, val text: String, val writable: Boolean)

/** Reads and writes files other apps open with us (a file manager's "Open with"), without copying them. */
interface ExternalFileRepository {
    suspend fun open(uri: String): Outcome<ExternalFile>

    suspend fun save(uri: String, text: String): Outcome<Unit>
}

internal class DefaultExternalFileRepository @Inject constructor(
    private val store: ProjectFolderStore,
) : ExternalFileRepository {

    override suspend fun open(uri: String): Outcome<ExternalFile> = try {
        val document = store.readSharedDocument(uri, ProjectSync.MAX_FILE_BYTES)
        val text = decodeText(document.bytes)
        if (text == null) {
            Outcome.Failure(AppError.Validation(reason = ErrorReason.DocumentNotText))
        } else {
            Outcome.Success(ExternalFile(uri, document.name, text, store.canWrite(uri)))
        }
    } catch (e: FileTooLargeException) {
        Outcome.Failure(AppError.Validation(reason = ErrorReason.DocumentTooLarge((ProjectSync.MAX_FILE_BYTES / 1024).toInt())))
    } catch (e: AccessLostException) {
        Outcome.Failure(AppError.Forbidden(reason = ErrorReason.DocumentNoPermission))
    } catch (e: IOException) {
        Outcome.Failure(AppError.Unknown(reason = ErrorReason.DocumentReadFailed))
    }

    override suspend fun save(uri: String, text: String): Outcome<Unit> = try {
        store.writeDocument(uri, text)
        Outcome.Success(Unit)
    } catch (e: AccessLostException) {
        Outcome.Failure(AppError.Forbidden(reason = ErrorReason.DocumentNoPermission))
    } catch (e: IOException) {
        Outcome.Failure(AppError.Unknown(reason = ErrorReason.DocumentOpenFailed))
    }

    /** Strict UTF-8 decoding: binary files (images, archives) and other encodings are refused rather than garbled. */
    private fun decodeText(bytes: ByteArray): String? {
        if (bytes.any { it == 0.toByte() }) return null
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        } catch (e: CharacterCodingException) {
            null
        }
    }
}
