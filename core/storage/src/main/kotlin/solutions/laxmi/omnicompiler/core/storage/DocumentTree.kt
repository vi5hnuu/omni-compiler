package solutions.laxmi.omnicompiler.core.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException

/** A document (file or folder) inside the user's picked tree, as the provider reports it. */
data class DocEntry(
    val docId: String,
    val name: String,
    val mimeType: String,
    val lastModified: Long,
    val size: Long,
    /** The provider can rename it in place (not every storage provider supports renaming). */
    val supportsRename: Boolean = false,
) {
    val isDirectory: Boolean get() = mimeType == Document.MIME_TYPE_DIR
    val isHidden: Boolean get() = name.startsWith('.')
}

/**
 * Thin Storage Access Framework layer over one granted tree. Uses [DocumentsContract] directly: `DocumentFile`
 * issues one provider query per property, which is far too slow for scanning many projects.
 * All calls block and must run off the main thread; provider failures surface as [IOException].
 */
internal class DocumentTree(private val resolver: ContentResolver, private val treeUri: Uri) {

    fun children(parentDocId: String): List<DocEntry> = io {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        resolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toEntry()) }
        } ?: throw IOException("Can't list $parentDocId")
    }

    fun entry(docId: String): DocEntry? = io {
        resolver.query(documentUri(docId), PROJECTION, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.toEntry() else null
        }
    }

    fun child(parentDocId: String, name: String): DocEntry? = children(parentDocId).firstOrNull { it.name == name }

    fun createDirectory(parentDocId: String, name: String): String = create(parentDocId, Document.MIME_TYPE_DIR, name)

    fun createFile(parentDocId: String, name: String): String = create(parentDocId, MIME_OCTET, name)

    fun readText(docId: String, maxBytes: Long): ByteArray = io {
        resolver.openInputStream(documentUri(docId))?.use { input ->
            val bytes = input.readNBytesCompat(maxBytes + 1)
            if (bytes.size > maxBytes) throw FileTooLargeException(docId)
            bytes
        } ?: throw IOException("Can't open $docId")
    }

    fun writeText(docId: String, text: String) = io {
        // "wt" truncates, so a shorter save never leaves the tail of the previous content behind.
        resolver.openOutputStream(documentUri(docId), "wt")?.use { it.write(text.encodeToByteArray()) }
            ?: throw IOException("Can't write $docId")
    }

    /** Returns the renamed document's id; providers that key documents by path change it. */
    fun rename(docId: String, name: String): String = io {
        val renamed = DocumentsContract.renameDocument(resolver, documentUri(docId), name) ?: throw IOException("Can't rename $docId")
        DocumentsContract.getDocumentId(renamed)
    }

    /** Deletes a file, or a folder with everything in it. */
    fun delete(docId: String) = io {
        if (!DocumentsContract.deleteDocument(resolver, documentUri(docId))) throw IOException("Can't delete $docId")
    }

    private fun create(parentDocId: String, mime: String, name: String): String = io {
        val created = DocumentsContract.createDocument(resolver, documentUri(parentDocId), mime, name)
            ?: throw IOException("Can't create $name")
        DocumentsContract.getDocumentId(created)
    }

    private fun documentUri(docId: String) = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

    private fun android.database.Cursor.toEntry() = DocEntry(
        docId = getString(0),
        name = getString(1).orEmpty(),
        mimeType = getString(2).orEmpty(),
        lastModified = if (isNull(3)) 0L else getLong(3),
        size = if (isNull(4)) 0L else getLong(4),
        supportsRename = !isNull(5) && getInt(5) and Document.FLAG_SUPPORTS_RENAME != 0,
    )

    /** Providers throw a mix of runtime exceptions (revoked grant, missing document); callers see one type. */
    private inline fun <T> io(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw e
    } catch (e: SecurityException) {
        throw AccessLostException(e)
    } catch (e: IllegalArgumentException) {
        throw IOException(e)
    } catch (e: IllegalStateException) {
        throw IOException(e)
    }

    private companion object {
        const val MIME_OCTET = "application/octet-stream"
        val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_SIZE,
            Document.COLUMN_FLAGS,
        )
    }
}

/** The picked folder's permission was revoked or the folder is gone; the user must pick again. */
class AccessLostException(cause: Throwable? = null) : IOException("Projects folder access lost", cause)

class FileTooLargeException(docId: String) : IOException("File too large: $docId")

/** `InputStream.readNBytes` is API 33+; reads at most [limit] bytes on every supported version. */
private fun java.io.InputStream.readNBytesCompat(limit: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var remaining = limit
    while (remaining > 0) {
        val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (read < 0) break
        out.write(buffer, 0, read)
        remaining -= read
    }
    return out.toByteArray()
}
