package solutions.laxmi.omnicompiler.core.data.project

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import solutions.laxmi.omnicompiler.core.common.IdGenerator
import solutions.laxmi.omnicompiler.core.common.TimeSource
import solutions.laxmi.omnicompiler.core.data.mapper.toStored
import solutions.laxmi.omnicompiler.core.data.runtime.RuntimeRepository
import solutions.laxmi.omnicompiler.core.database.OmniDatabase
import solutions.laxmi.omnicompiler.core.database.dao.FileDao
import solutions.laxmi.omnicompiler.core.database.dao.ProjectDao
import solutions.laxmi.omnicompiler.core.database.dao.TestCaseDao
import solutions.laxmi.omnicompiler.core.database.entity.FileEntity
import solutions.laxmi.omnicompiler.core.database.entity.ProjectEntity
import solutions.laxmi.omnicompiler.core.database.entity.TestCaseEntity
import solutions.laxmi.omnicompiler.core.datastore.PreferencesStore
import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.ErrorReason
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.ProjectIssue
import solutions.laxmi.omnicompiler.core.storage.DocEntry
import solutions.laxmi.omnicompiler.core.storage.ManifestCodec
import solutions.laxmi.omnicompiler.core.storage.ManifestLimits
import solutions.laxmi.omnicompiler.core.storage.ManifestRemote
import solutions.laxmi.omnicompiler.core.storage.ManifestTest
import solutions.laxmi.omnicompiler.core.storage.ProjectFolderStore
import solutions.laxmi.omnicompiler.core.storage.ProjectManifest
import solutions.laxmi.omnicompiler.core.storage.ProjectRoot
import solutions.laxmi.omnicompiler.core.storage.ProjectValidator
import solutions.laxmi.omnicompiler.core.storage.ScannedFolder
import solutions.laxmi.omnicompiler.core.storage.ValidatedProject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serialises every disk operation on projects (writes and scans). Without it a scan could observe the app's
 * own half-finished save and mistake it for an edit made in another app.
 */
@Singleton
internal class ProjectDiskLock @Inject constructor() {
    private val mutex = Mutex()
    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}

/**
 * Keeps the Room index in step with the project folders on disk (files are the source of truth).
 * Only files whose size or last-modified time changed are read, so a rescan of unchanged projects is cheap.
 */
@Singleton
internal class ProjectSync @Inject constructor(
    private val store: ProjectFolderStore,
    private val db: OmniDatabase,
    private val projects: ProjectDao,
    private val files: FileDao,
    private val tests: TestCaseDao,
    private val runtimes: RuntimeRepository,
    private val preferences: PreferencesStore,
    private val ids: IdGenerator,
    private val time: TimeSource,
    private val lock: ProjectDiskLock,
) {

    /** Writes projects that only exist in the index (created before folders existed), then rescans everything. */
    suspend fun syncAll(root: ProjectRoot): Outcome<Unit> = lock.withLock {
        guard {
            exportIndexOnly(root)
            val validator = validator()
            val taken = mutableSetOf<String>()
            val seen = mutableSetOf<String>()
            val indexed = projects.all().filter { it.folderDocId != null }.associateBy { it.folderDocId }
            store.scan(root).forEach { folder ->
                val checked = validator.validate(folder, taken, knownManifest(folder, indexed[folder.folder.docId])) ?: return@forEach
                taken += checked.manifest.id
                seen += checked.manifest.id
                apply(root, folder, checked)
            }
            removeMissing(seen)
        }
    }

    /** Rescans one project's folder, e.g. when it's opened or after its folder was renamed. */
    suspend fun syncProject(root: ProjectRoot, projectId: String): Outcome<Unit> = lock.withLock { syncProjectLocked(root, projectId) }

    /** For callers already holding [ProjectDiskLock]. */
    suspend fun syncProjectLocked(root: ProjectRoot, projectId: String): Outcome<Unit> = guard {
        val folderDocId = projects.get(projectId)?.folderDocId ?: return@guard
        val folder = store.entry(root, folderDocId)
        if (folder == null) {
            removeMissing(projects.all().map { it.id }.toSet() - projectId)
            return@guard
        }
        val others = projects.all().filter { it.id != projectId }.map { it.id }.toSet()
        val scanned = store.scanFolder(root, folder)
        validator().validate(scanned, others, knownManifest(scanned, projects.get(projectId)))?.let { apply(root, scanned, it) }
    }

    /** The index's manifest for [project], needed only when the folder's own manifest can't be read. */
    private suspend fun knownManifest(folder: ScannedFolder, project: ProjectEntity?): ProjectManifest? {
        if (project == null || folder.manifestText?.let(ManifestCodec::decode) != null) return null
        return manifestOf(project)
    }

    /** Writes the project's manifest from the index. Caller holds [ProjectDiskLock]. */
    suspend fun writeManifestLocked(root: ProjectRoot, projectId: String) {
        val project = projects.get(projectId) ?: return
        val folder = project.folderDocId ?: return
        store.writeManifest(root, folder, manifestOf(project))
    }

    private suspend fun manifestOf(project: ProjectEntity): ProjectManifest {
        val entry = files.list(project.id).firstOrNull { it.isEntry }
        return ProjectManifest(
            id = project.id,
            name = project.name,
            runtimeId = project.runtimeId,
            entry = entry?.name.orEmpty(),
            limits = ManifestLimits(project.timeLimitMs, project.memLimitMb),
            tests = tests.list(project.id).map { ManifestTest(it.id, it.name, it.stdin, it.expected) },
            createdAt = project.createdAt,
            lastVerdict = project.lastVerdict,
            origin = project.originUri,
            remote = project.remoteJson?.let(ManifestCodec::decodeRemote),
        )
    }

    /**
     * Copies a picked folder (any provider) into a new project folder and indexes it. The same checks as
     * for projects on disk apply; returns the new project's id, or null when there is nothing to import.
     */
    suspend fun importFolderLocked(root: ProjectRoot, treeUri: String): String? {
        val (source, scanned) = store.scanPicked(treeUri)
        val taken = projects.all().map { it.id }.toSet()
        // A picked folder is always a new project here, even if it carries a manifest from elsewhere.
        val checked = validator().validate(scanned, taken + (ManifestCodec.decode(scanned.manifestText.orEmpty())?.id ?: "")) ?: return null
        val target = store.createFolder(root, checked.folder.name)
        var copied = 0
        checked.sourceFiles.forEach { entry ->
            val bytes = store.readBytes(source, entry.docId, MAX_FILE_BYTES)
            if (bytes.none { it == 0.toByte() }) {
                store.writeFile(root, target.docId, entry.name, bytes.decodeToString())
                copied++
            }
        }
        if (copied == 0) {
            store.delete(root, target.docId)
            return null
        }
        store.writeManifest(root, target.docId, checked.manifest.copy(name = target.name, createdAt = time.now().toEpochMilliseconds()))
        return indexFolderLocked(root, target)
    }

    /**
     * Copies one picked file into a new project (runtime chosen from its extension) and remembers where it
     * came from so it can be saved back. Returns the project id; throws [UnknownLanguageException] when no
     * runtime matches and returns null when the file isn't text.
     */
    suspend fun importFileLocked(root: ProjectRoot, documentUri: String): String? {
        val picked = store.readDocument(documentUri, MAX_FILE_BYTES, keepAccess = true)
        if (picked.bytes.any { it == 0.toByte() } || picked.name.isBlank()) return null
        val runtime = runtimeForFile(picked.name) ?: throw UnknownLanguageException(picked.name)
        val folder = store.createFolder(root, picked.name.substringBeforeLast('.').ifBlank { picked.name })
        store.writeFile(root, folder.docId, picked.name, picked.bytes.decodeToString())
        val defaults = preferences.runSettings.first().defaultLimits.raisedTo(runtimes.defaultLimits(runtime.id))
        store.writeManifest(
            root,
            folder.docId,
            ProjectManifest(
                id = ids.newId(),
                name = folder.name,
                runtimeId = runtime.id,
                entry = picked.name,
                limits = ManifestLimits(defaults.timeMs, defaults.memMb),
                createdAt = time.now().toEpochMilliseconds(),
                origin = documentUri,
            ),
        )
        return indexFolderLocked(root, folder)
    }

    /**
     * Creates a project from files fetched elsewhere (a GitHub/GitLab folder): writes them, lets the validator
     * pick the runtime and entry file, then records the remote in the manifest.
     */
    suspend fun createFromFilesLocked(root: ProjectRoot, name: String, files: Map<String, String>, remote: ManifestRemote): String? {
        if (files.isEmpty()) return null
        val folder = store.createFolder(root, name)
        files.forEach { (fileName, content) -> store.writeFile(root, folder.docId, fileName, content) }
        val scanned = store.scanFolder(root, folder)
        val checked = validator().validate(scanned, projects.all().map { it.id }.toSet()) ?: return null
        store.writeManifest(root, folder.docId, checked.manifest.copy(remote = remote, createdAt = time.now().toEpochMilliseconds()))
        return indexFolderLocked(root, folder)
    }

    private suspend fun indexFolderLocked(root: ProjectRoot, folder: DocEntry): String? {
        val scanned = store.scanFolder(root, folder)
        val checked = validator().validate(scanned, projects.all().map { it.id }.toSet()) ?: return null
        apply(root, scanned, checked)
        return checked.manifest.id
    }

    private suspend fun runtimeForFile(name: String): solutions.laxmi.omnicompiler.core.model.Runtime? {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        return runtimes.languages.first().firstNotNullOfOrNull { language -> language.defaultRuntime?.takeIf { it.extension == ext } }
    }

    /**
     * The manifest is written before the files, so an export cut short (process death) is resumed into the same
     * folder on the next sync instead of leaving a manifest-less copy that would be adopted as a second project.
     */
    private suspend fun exportIndexOnly(root: ProjectRoot) {
        val pending = projects.all().filter { it.folderDocId == null }
        if (pending.isEmpty()) return
        val started = store.scan(root)
            .mapNotNull { scanned -> scanned.manifestText?.let(ManifestCodec::decode)?.let { it.id to scanned.folder } }
            .toMap()
        pending.forEach { exported ->
            val folder = started[exported.id] ?: store.createFolder(root, exported.name)
            // The folder name wins on clashes (it may have been suffixed); keep the index in step.
            val project = exported.copy(name = folder.name)
            if (project.name != exported.name) projects.upsert(project)
            store.writeManifest(root, folder.docId, manifestOf(project))
            files.list(project.id).forEach { file ->
                val written = store.writeFile(root, folder.docId, file.name, file.content)
                files.setDiskState(file.id, written.docId, written.lastModified, written.size)
            }
            projects.setFolder(project.id, folder.docId)
        }
    }

    private suspend fun apply(root: ProjectRoot, scanned: ScannedFolder, checked: ValidatedProject) {
        if (checked.rewriteManifest) {
            store.writeManifest(root, checked.folder.docId, checked.manifest, corruptBackup = scanned.manifestText.takeIf { checked.backupCorrupt })
        }
        val manifest = checked.manifest
        val issues = checked.issues.toMutableList()
        val existing = files.list(manifest.id).associateBy { it.name }

        // Read changed files outside the transaction (disk I/O), dropping binaries that slipped past the MIME check.
        val fresh = mutableMapOf<String, String>()
        val kept = checked.sourceFiles.filter { entry ->
            val row = existing[entry.name]
            if (row != null && row.lastModified == entry.lastModified && row.size == entry.size) return@filter true
            val bytes = store.readBytes(root, entry.docId, MAX_FILE_BYTES)
            if (bytes.any { it == 0.toByte() }) {
                issues += ProjectIssue.FileSkipped(entry.name, ProjectIssue.SkipReason.BINARY)
                false
            } else {
                fresh[entry.name] = bytes.decodeToString()
                true
            }
        }
        val ordered = kept.sortedWith(compareByDescending<DocEntry> { it.name == manifest.entry }.thenBy { it.name.lowercase() })
        val rows = ordered.mapIndexed { position, entry ->
            val row = existing[entry.name]
            val content = fresh[entry.name]
            FileEntity(
                id = row?.id ?: ids.newId(),
                projectId = manifest.id,
                name = entry.name,
                content = content ?: row?.content.orEmpty(),
                isEntry = entry.name == manifest.entry,
                position = position,
                // A changed file already open in the editor must reload; unchanged ones keep their version.
                contentVersion = (row?.contentVersion ?: 0) + if (row != null && content != null && content != row.content) 1 else 0,
                docId = entry.docId,
                lastModified = entry.lastModified,
                size = entry.size,
            )
        }
        val previous = projects.get(manifest.id)
        val touched = maxOf(previous?.updatedAt ?: 0, ordered.maxOfOrNull { it.lastModified } ?: 0, manifest.createdAt)
        val testRows = manifest.tests.mapIndexed { i, t -> TestCaseEntity(t.id, manifest.id, t.name, t.stdin, t.expected, i) }
        db.withTransaction {
            projects.upsert(
                ProjectEntity(
                    id = manifest.id,
                    name = manifest.name,
                    runtimeId = manifest.runtimeId,
                    timeLimitMs = manifest.limits.timeMs,
                    memLimitMb = manifest.limits.memMb,
                    lastVerdict = manifest.lastVerdict,
                    createdAt = manifest.createdAt,
                    updatedAt = touched,
                    folderDocId = checked.folder.docId,
                    issues = issues.toStored(),
                    originUri = manifest.origin,
                    remoteJson = manifest.remote?.let(ManifestCodec::encodeRemote),
                ),
            )
            val gone = existing.values.filter { row -> rows.none { it.id == row.id } }.map { it.id }
            if (gone.isNotEmpty()) files.deleteAll(gone)
            files.upsertAll(rows)
            if (tests.list(manifest.id) != testRows) tests.replaceAll(manifest.id, testRows)
        }
    }

    private suspend fun removeMissing(seen: Set<String>) {
        val missing = projects.all().filter { it.folderDocId != null && it.id !in seen }
        missing.forEach { projects.delete(it.id) }
        val last = preferences.lastProjectId.first()
        if (last != null && missing.any { it.id == last }) preferences.setLastProjectId(null)
    }

    private suspend fun validator(): ProjectValidator {
        val languages = runtimes.languages.first()
        val known = languages.flatMap { it.runtimes }.map { it.id }.toSet()
        // Extension → the language's default runtime, for folders that arrive without a manifest.
        val byExtension = languages.mapNotNull { language ->
            language.defaultRuntime?.let { runtime -> runtime.extension.takeIf { it.isNotEmpty() }?.let { it to runtime } }
        }.toMap()
        val defaults = preferences.runSettings.first().defaultLimits
        return ProjectValidator(
            limits = ProjectValidator.Limits(MAX_FILE_BYTES, MAX_FILES, MAX_FILE_NAME, defaults.timeMs, defaults.memMb),
            knownRuntime = { it in known },
            inferRuntime = { names ->
                names.firstNotNullOfOrNull { name -> byExtension[name.substringAfterLast('.', "").lowercase()] }
                    ?.let { ProjectValidator.InferredRuntime(it.id, it.filename) }
            },
            newId = ids::newId,
            now = { time.now().toEpochMilliseconds() },
        )
    }

    private inline fun guard(block: () -> Unit): Outcome<Unit> = try {
        block()
        Outcome.Success(Unit)
    } catch (e: IOException) {
        Outcome.Failure(AppError.Unknown(reason = ErrorReason.ProjectsFolderUnavailable))
    }

    companion object {
        /** Largest file indexed from disk; runs still enforce the judge's own source limit. */
        const val MAX_FILE_BYTES = 512L * 1024
        const val MAX_FILES = 21
        const val MAX_FILE_NAME = 128
    }
}

/** No runtime runs files with this name's extension. */
internal class UnknownLanguageException(val fileName: String) : Exception(fileName)
