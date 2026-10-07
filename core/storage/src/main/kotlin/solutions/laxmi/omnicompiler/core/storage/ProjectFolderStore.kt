package solutions.laxmi.omnicompiler.core.storage

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import solutions.laxmi.omnicompiler.core.common.Dispatcher
import solutions.laxmi.omnicompiler.core.common.OmniDispatcher
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The folder projects live in: the granted tree and the document id of the projects folder inside it. */
data class ProjectRoot(val treeUri: String, val docId: String)

/**
 * Project folders on shared storage, reached through the Storage Access Framework so no broad storage
 * permission is needed on any Android version. Every call may throw [IOException] ([AccessLostException]
 * when the grant is gone); callers map that to a user-facing error.
 */
interface ProjectFolderStore {
    /** Takes a persistent grant on the user's pick and returns the projects folder inside it (created if needed). */
    suspend fun adopt(treeUri: String): ProjectRoot

    /** The grant is still held and the folder still exists. */
    suspend fun isAccessible(root: ProjectRoot): Boolean

    /**
     * Every candidate project folder under [root], with its manifest and top-level entries. The manifest text is not
     * read when [skipManifest] says the caller already knows it (unchanged since it was last read); it is null then.
     */
    suspend fun scan(root: ProjectRoot, skipManifest: (folder: DocEntry, manifest: DocEntry) -> Boolean = { _, _ -> false }): List<ScannedFolder>

    suspend fun scanFolder(root: ProjectRoot, folder: DocEntry): ScannedFolder

    /** Current metadata of a document, or null when it no longer exists. */
    suspend fun entry(root: ProjectRoot, docId: String): DocEntry?

    suspend fun readBytes(root: ProjectRoot, docId: String, maxBytes: Long): ByteArray

    /** Creates a uniquely named project folder (suffixing `-2`, `-3`… on clashes). */
    suspend fun createFolder(root: ProjectRoot, name: String): DocEntry

    /** Writes [name] inside [folderDocId], creating it when missing; returns its fresh metadata. */
    suspend fun writeFile(root: ProjectRoot, folderDocId: String, name: String, content: String): DocEntry

    /** Overwrites an existing file; returns its fresh metadata (the new last-modified time). */
    suspend fun updateFile(root: ProjectRoot, docId: String, content: String): DocEntry

    /** Replaces the manifest atomically (temp file renamed over it); returns the written manifest's metadata. */
    suspend fun writeManifest(root: ProjectRoot, folderDocId: String, manifest: ProjectManifest, corruptBackup: String? = null): DocEntry

    suspend fun rename(root: ProjectRoot, docId: String, name: String): DocEntry

    suspend fun delete(root: ProjectRoot, docId: String)

    /** Human-readable location, e.g. `Documents/OmniCompiler`. */
    fun displayPath(root: ProjectRoot): String

    /** A folder the user picked to import (any provider, e.g. a cloud drive): its root and top-level entries. */
    suspend fun scanPicked(treeUri: String): Pair<ProjectRoot, ScannedFolder>

    /** Reads one picked document; with [keepAccess] the grant is persisted so it can be written back later. */
    suspend fun readDocument(uri: String, maxBytes: Long, keepAccess: Boolean): PickedDocument

    suspend fun writeDocument(uri: String, content: String)

    /** Gives back a grant taken with `keepAccess` (e.g. the project linked to it was deleted); grants are capped per app. */
    fun releaseDocument(uri: String)

    /**
     * Reads a file another app handed over (ACTION_VIEW / ACTION_EDIT). Such URIs often come from a FileProvider,
     * which offers only OpenableColumns, so the name comes from there and the type from the resolver.
     */
    suspend fun readSharedDocument(uri: String, maxBytes: Long): PickedDocument

    /** Whether this app holds a write grant for [uri] (file managers may hand files over read-only). */
    fun canWrite(uri: String): Boolean
}

/** A single document opened through the system picker. */
class PickedDocument(val name: String, val mimeType: String, val bytes: ByteArray)

@Singleton
internal class SafProjectFolderStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(OmniDispatcher.IO) private val io: CoroutineDispatcher,
) : ProjectFolderStore {

    private val resolver get() = context.contentResolver

    private fun tree(root: ProjectRoot) = DocumentTree(resolver, Uri.parse(root.treeUri))

    override suspend fun adopt(treeUri: String): ProjectRoot = withContext(io) {
        val uri = Uri.parse(treeUri)
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: SecurityException) {
            throw AccessLostException(e)
        }
        val tree = DocumentTree(resolver, uri)
        val pickedDocId = DocumentsContract.getTreeDocumentId(uri)
        val picked = tree.entry(pickedDocId) ?: throw IOException("Picked folder is gone")
        // Use the pick itself when it is our folder or already holds projects; otherwise keep projects together
        // in an OmniCompiler subfolder instead of scattering them into e.g. Documents.
        val holdsProjects = picked.name == ProjectLayout.ROOT_DIR ||
            tree.children(pickedDocId).any { it.isDirectory && tree.child(it.docId, ProjectLayout.META_DIR) != null }
        val docId = if (holdsProjects) pickedDocId else {
            tree.child(pickedDocId, ProjectLayout.ROOT_DIR)?.takeIf { it.isDirectory }?.docId
                ?: tree.createDirectory(pickedDocId, ProjectLayout.ROOT_DIR)
        }
        ProjectRoot(treeUri, docId)
    }

    override suspend fun isAccessible(root: ProjectRoot): Boolean = withContext(io) {
        val granted = resolver.persistedUriPermissions.any { it.uri.toString() == root.treeUri && it.isReadPermission && it.isWritePermission }
        granted && try {
            tree(root).entry(root.docId)?.isDirectory == true
        } catch (e: IOException) {
            false
        }
    }

    override suspend fun scan(root: ProjectRoot, skipManifest: (folder: DocEntry, manifest: DocEntry) -> Boolean): List<ScannedFolder> = withContext(io) {
        val tree = tree(root)
        tree.children(root.docId).filter { it.isDirectory && !it.isHidden }.map { folder -> scanFolder(tree, folder, skipManifest) }
    }

    override suspend fun scanFolder(root: ProjectRoot, folder: DocEntry): ScannedFolder = withContext(io) { scanFolder(tree(root), folder) }

    private fun scanFolder(
        tree: DocumentTree,
        folder: DocEntry,
        skipManifest: (folder: DocEntry, manifest: DocEntry) -> Boolean = { _, _ -> false },
    ): ScannedFolder {
        val entries = tree.children(folder.docId)
        val meta = entries.firstOrNull { it.isDirectory && it.name == ProjectLayout.META_DIR }
        val manifest = meta?.let { tree.child(it.docId, ProjectLayout.MANIFEST) }
        val text = manifest?.takeUnless { skipManifest(folder, it) }?.let { tree.readText(it.docId, MAX_MANIFEST_BYTES).decodeToString() }
        return ScannedFolder(folder, text, entries.filter { it !== meta }, manifest)
    }

    override suspend fun entry(root: ProjectRoot, docId: String): DocEntry? = withContext(io) { tree(root).entry(docId) }

    override suspend fun readBytes(root: ProjectRoot, docId: String, maxBytes: Long): ByteArray = withContext(io) {
        tree(root).readText(docId, maxBytes)
    }

    override suspend fun createFolder(root: ProjectRoot, name: String): DocEntry = withContext(io) {
        val tree = tree(root)
        val taken = tree.children(root.docId).map { it.name.lowercase() }.toSet()
        val unique = generateSequence(1) { it + 1 }
            .map { if (it == 1) name else "$name-$it" }
            .first { it.lowercase() !in taken }
        val docId = tree.createDirectory(root.docId, unique)
        tree.entry(docId) ?: throw IOException("Created folder vanished")
    }

    override suspend fun writeFile(root: ProjectRoot, folderDocId: String, name: String, content: String): DocEntry = withContext(io) {
        val tree = tree(root)
        val docId = tree.child(folderDocId, name)?.docId ?: tree.createFile(folderDocId, name)
        tree.writeText(docId, content)
        tree.entry(docId) ?: throw IOException("Written file vanished")
    }

    override suspend fun updateFile(root: ProjectRoot, docId: String, content: String): DocEntry = withContext(io) {
        val tree = tree(root)
        tree.writeText(docId, content)
        tree.entry(docId) ?: throw IOException("Written file vanished")
    }

    override suspend fun writeManifest(root: ProjectRoot, folderDocId: String, manifest: ProjectManifest, corruptBackup: String?): DocEntry = withContext(io) {
        val tree = tree(root)
        val meta = tree.child(folderDocId, ProjectLayout.META_DIR)?.docId ?: tree.createDirectory(folderDocId, ProjectLayout.META_DIR)
        val existing = tree.children(meta)
        if (corruptBackup != null) {
            val backup = existing.firstOrNull { it.name == ProjectLayout.MANIFEST_BACKUP }?.docId ?: tree.createFile(meta, ProjectLayout.MANIFEST_BACKUP)
            tree.writeText(backup, corruptBackup)
        }
        // Write a temp file, then swap it in: if the app dies mid-write the old manifest (or, between delete and
        // rename, none) is left, never a truncated one; a missing manifest is restored from the index.
        val temp = existing.firstOrNull { it.name == ProjectLayout.MANIFEST_TEMP }?.docId ?: tree.createFile(meta, ProjectLayout.MANIFEST_TEMP)
        tree.writeText(temp, ManifestCodec.encode(manifest))
        existing.firstOrNull { it.name == ProjectLayout.MANIFEST }?.let { tree.delete(it.docId) }
        val written = tree.rename(temp, ProjectLayout.MANIFEST)
        tree.entry(written) ?: throw IOException("Written manifest vanished")
    }

    override suspend fun rename(root: ProjectRoot, docId: String, name: String): DocEntry = withContext(io) {
        val tree = tree(root)
        val renamed = tree.rename(docId, name)
        tree.entry(renamed) ?: throw IOException("Renamed document vanished")
    }

    override suspend fun delete(root: ProjectRoot, docId: String) = withContext(io) { tree(root).delete(docId) }

    override fun displayPath(root: ProjectRoot): String {
        // External storage ids read "primary:Documents/OmniCompiler"; other providers get the folder name only.
        val id = root.docId
        return if (':' in id) id.substringAfter(':').ifEmpty { id } else id.substringAfterLast('/')
    }

    override suspend fun scanPicked(treeUri: String): Pair<ProjectRoot, ScannedFolder> = withContext(io) {
        val uri = Uri.parse(treeUri)
        val root = ProjectRoot(treeUri, DocumentsContract.getTreeDocumentId(uri))
        val tree = DocumentTree(resolver, uri)
        val folder = tree.entry(root.docId) ?: throw IOException("Picked folder is gone")
        root to scanFolder(tree, folder)
    }

    override suspend fun readDocument(uri: String, maxBytes: Long, keepAccess: Boolean): PickedDocument = withContext(io) {
        val parsed = Uri.parse(uri)
        try {
            if (keepAccess) keepAccess(parsed)
            val (name, mime) = resolver.query(parsed, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0).orEmpty() to c.getString(1).orEmpty() else null }
                ?: throw IOException("Can't read $uri")
            PickedDocument(name, mime, readLimited(parsed, maxBytes))
        } catch (e: SecurityException) {
            throw AccessLostException(e)
        }
    }

    override suspend fun readSharedDocument(uri: String, maxBytes: Long): PickedDocument = withContext(io) {
        val parsed = Uri.parse(uri)
        try {
            val name = resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?: parsed.lastPathSegment.orEmpty()
            PickedDocument(name, resolver.getType(parsed).orEmpty(), readLimited(parsed, maxBytes))
        } catch (e: SecurityException) {
            throw AccessLostException(e)
        }
    }

    /** Read-write when the provider allows it; read-only providers (some cloud drives) still grant reading. */
    private fun keepAccess(uri: Uri) {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: SecurityException) {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun releaseDocument(uri: String) {
        val parsed = Uri.parse(uri)
        val held = resolver.persistedUriPermissions.firstOrNull { it.uri == parsed } ?: return
        val flags = (if (held.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (held.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        try {
            resolver.releasePersistableUriPermission(parsed, flags)
        } catch (e: SecurityException) {
            // Already gone (revoked by the user or the provider): nothing left to release.
        }
    }

    override fun canWrite(uri: String): Boolean =
        context.checkUriPermission(Uri.parse(uri), Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    /** Reads at most [maxBytes], failing fast instead of loading an oversized file into memory. */
    private fun readLimited(uri: Uri, maxBytes: Long): ByteArray =
        resolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > maxBytes) throw FileTooLargeException(uri.toString())
            }
            out.toByteArray()
        } ?: throw IOException("Can't open $uri")

    override suspend fun writeDocument(uri: String, content: String) = withContext(io) {
        try {
            resolver.openOutputStream(Uri.parse(uri), "wt")?.use { it.write(content.encodeToByteArray()) } ?: throw IOException("Can't write $uri")
        } catch (e: SecurityException) {
            throw AccessLostException(e)
        }
    }

    private companion object {
        const val MAX_MANIFEST_BYTES = 2L * 1024 * 1024
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface StorageModule {
    @Binds
    fun bindsProjectFolderStore(impl: SafProjectFolderStore): ProjectFolderStore
}
