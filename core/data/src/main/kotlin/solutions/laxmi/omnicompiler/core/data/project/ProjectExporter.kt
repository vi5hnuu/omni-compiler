package solutions.laxmi.omnicompiler.core.data.project

import android.content.Context
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import solutions.laxmi.omnicompiler.core.common.Dispatcher
import solutions.laxmi.omnicompiler.core.common.OmniDispatcher
import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.ErrorReason
import solutions.laxmi.omnicompiler.core.model.Outcome
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/** A file ready to hand to the system share sheet. */
data class SharedFile(val uri: String, val mimeType: String, val displayName: String)

/** Packages project sources for sharing (web playground's "download file / project .zip"). */
interface ProjectExporter {
    suspend fun exportZip(projectId: String): Outcome<SharedFile>
    suspend fun exportFile(projectId: String, fileId: String): Outcome<SharedFile>
}

internal class CacheProjectExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projects: ProjectRepository,
    @Dispatcher(OmniDispatcher.IO) private val io: CoroutineDispatcher,
) : ProjectExporter {

    private val authority get() = "${context.packageName}$AUTHORITY_SUFFIX"

    override suspend fun exportZip(projectId: String): Outcome<SharedFile> {
        val workspace = projects.observeWorkspace(projectId).first() ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.ProjectNotFound))
        return write("${workspace.project.name}.zip", MIME_ZIP) { file ->
            ZipOutputStream(file.outputStream().buffered()).use { zip ->
                workspace.files.forEach { source ->
                    zip.putNextEntry(ZipEntry(source.name))
                    zip.write(source.content.encodeToByteArray())
                    zip.closeEntry()
                }
                if (workspace.tests.isNotEmpty()) {
                    workspace.tests.forEachIndexed { i, test ->
                        zip.putNextEntry(ZipEntry("tests/${i + 1}.in"))
                        zip.write(test.stdin.encodeToByteArray())
                        zip.closeEntry()
                        zip.putNextEntry(ZipEntry("tests/${i + 1}.out"))
                        zip.write(test.expected.encodeToByteArray())
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    override suspend fun exportFile(projectId: String, fileId: String): Outcome<SharedFile> {
        val source = projects.observeWorkspace(projectId).first()?.files?.firstOrNull { it.id == fileId }
            ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.FileNotFound))
        return write(source.name, MIME_TEXT) { it.writeText(source.content) }
    }

    private suspend fun write(name: String, mime: String, block: (File) -> Unit): Outcome<SharedFile> = withContext(io) {
        try {
            val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
            // Old exports are disposable; keep the cache from growing.
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            block(file)
            Outcome.Success(SharedFile(FileProvider.getUriForFile(context, authority, file).toString(), mime, file.name))
        } catch (e: IOException) {
            Outcome.Failure(AppError.Unknown(reason = ErrorReason.ExportFailed))
        }
    }

    private companion object {
        /** Must match the FileProvider declared in the app manifest and res/xml/file_paths.xml. */
        const val AUTHORITY_SUFFIX = ".files"
        const val EXPORT_DIR = "exports"
        const val MIME_ZIP = "application/zip"
        const val MIME_TEXT = "text/plain"
    }
}
