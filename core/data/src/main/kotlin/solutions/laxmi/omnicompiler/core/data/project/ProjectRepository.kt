package solutions.laxmi.omnicompiler.core.data.project

import solutions.laxmi.omnicompiler.core.storage.DocEntry
import solutions.laxmi.omnicompiler.core.storage.ManifestCodec
import solutions.laxmi.omnicompiler.core.data.mapper.toManifest
import solutions.laxmi.omnicompiler.core.model.ProjectRemote
import solutions.laxmi.omnicompiler.core.storage.ProjectRoot
import solutions.laxmi.omnicompiler.core.storage.ProjectFolderStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import solutions.laxmi.omnicompiler.core.common.ApplicationScope
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import solutions.laxmi.omnicompiler.core.common.IdGenerator
import solutions.laxmi.omnicompiler.core.common.TimeSource
import solutions.laxmi.omnicompiler.core.data.mapper.toModel
import solutions.laxmi.omnicompiler.core.data.runtime.RuntimeRepository
import solutions.laxmi.omnicompiler.core.database.OmniDatabase
import solutions.laxmi.omnicompiler.core.database.dao.FileDao
import solutions.laxmi.omnicompiler.core.database.dao.ProjectDao
import solutions.laxmi.omnicompiler.core.database.dao.TestCaseDao
import solutions.laxmi.omnicompiler.core.database.entity.FileEntity
import solutions.laxmi.omnicompiler.core.database.entity.FileHeaderRow
import solutions.laxmi.omnicompiler.core.database.entity.ProjectEntity
import solutions.laxmi.omnicompiler.core.database.entity.TestCaseEntity
import solutions.laxmi.omnicompiler.core.datastore.PreferencesStore
import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.ErrorReason
import solutions.laxmi.omnicompiler.core.model.Limits
import solutions.laxmi.omnicompiler.core.model.OpenFile
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.ProjectFilter
import solutions.laxmi.omnicompiler.core.model.ProjectLimits
import solutions.laxmi.omnicompiler.core.model.ProjectPaths
import solutions.laxmi.omnicompiler.core.model.ProjectSummary
import solutions.laxmi.omnicompiler.core.model.ProjectWorkspace
import solutions.laxmi.omnicompiler.core.model.Runtime
import solutions.laxmi.omnicompiler.core.model.SourceFile
import solutions.laxmi.omnicompiler.core.model.TestCase
import solutions.laxmi.omnicompiler.core.model.TestCaseDraft
import solutions.laxmi.omnicompiler.core.model.Verdict
import solutions.laxmi.omnicompiler.core.model.WorkspaceOutline
import javax.inject.Inject
import javax.inject.Singleton

/** Starting content for a new project (blank language starter, example, or problem). */
data class ProjectTemplate(
    val name: String,
    val code: String?,
    val tests: List<TestCaseDraft>?,
)

/**
 * Projects, files and test cases. ls-judge has no storage API, so projects live on this device as folders
 * the user can see (see [ProjectFolderRepository]); Room indexes them for fast lists and observation.
 */
interface ProjectRepository {
    fun observeSummaries(query: String, filter: ProjectFilter): Flow<List<ProjectSummary>>
    /** The project with file headers and tests; cheap to re-query on every save, whatever the file sizes. */
    fun observeOutline(projectId: String): Flow<WorkspaceOutline?>

    /** An open file's text, re-read only when it is replaced outside the editor (never on the editor's own saves). */
    fun observeFile(fileId: String): Flow<OpenFile?>

    /** Everything including contents, read once: for runs, pushes and exports. */
    suspend fun snapshot(projectId: String): ProjectWorkspace?
    val lastProjectId: Flow<String?>

    /** The project to open at launch: the last one used, else the most recent, else a new starter. */
    suspend fun resolveStartupProject(): Outcome<String>

    suspend fun create(runtime: Runtime, template: ProjectTemplate? = null): Outcome<String>
    suspend fun duplicate(projectId: String): Outcome<String>
    suspend fun rename(projectId: String, name: String): Outcome<Unit>
    /** Deletes the project's folder and its index entry; nothing is removed when the folder can't be deleted. */
    suspend fun delete(projectId: String): Outcome<Unit>
    suspend fun markOpened(projectId: String)
    suspend fun changeRuntime(projectId: String, runtime: Runtime): Outcome<Unit>
    suspend fun setLimits(projectId: String, limits: Limits)
    suspend fun recordVerdict(projectId: String, verdict: Verdict?)

    suspend fun updateFileContent(fileId: String, content: String)
    suspend fun addFile(projectId: String, name: String, content: String): Outcome<SourceFile>
    suspend fun renameFile(fileId: String, projectId: String, name: String): Outcome<Unit>
    suspend fun deleteFile(fileId: String)

    suspend fun addTest(projectId: String, draft: TestCaseDraft): TestCase
    suspend fun updateTest(test: TestCase)
    suspend fun deleteTest(testId: String)
    suspend fun duplicateTest(test: TestCase): TestCase
    suspend fun replaceTests(projectId: String, drafts: List<TestCaseDraft>)

    /** Restores the language's reference starter code and tests. */
    suspend fun resetToStarter(projectId: String): Outcome<Unit>

    /** Re-reads every project folder (edits made in other apps, folders added or removed). */
    suspend fun refreshFromDisk(): Outcome<Unit>

    /** Copies a folder picked with the system picker (device or cloud provider) into a new project. */
    suspend fun importFolder(treeUri: String): Outcome<String>

    /** Copies one picked file into a new project; the original stays linked so it can be saved back. */
    suspend fun importFile(documentUri: String): Outcome<String>

    /** New project holding a copy of [text] as [fileName] (e.g. a file opened from another app). */
    suspend fun importText(fileName: String, text: String): Outcome<String>

    /** Writes a project's entry file back to the document it was imported from. */
    suspend fun saveToOrigin(projectId: String): Outcome<Unit>

    /** New project from files fetched from GitHub/GitLab, tracking [remote]. */
    suspend fun importRemote(name: String, files: Map<String, String>, remote: ProjectRemote): Outcome<String>

    /**
     * Applies files from the remote (content, or null to delete) as outside edits (an open editor reloads), then
     * records the new [remote] state. [expectedLocal] is each touched file's text when the caller compared it (null:
     * absent); if any differs now, nothing is written and the result is a conflict, so a pull can't overwrite an edit
     * made after it looked. The entry file is never deleted this way, and files the project can't hold (too large,
     * past the file limit) end up in the folder but not in the project, exactly as with any rescan.
     */
    suspend fun applyRemote(projectId: String, changes: Map<String, String?>, remote: ProjectRemote, expectedLocal: Map<String, String?>): Outcome<Unit>

    /** Names of the files in the project's folder (whether or not the project opens them); null when it can't be read. */
    suspend fun folderFileNames(projectId: String): Set<String>?

    suspend fun setRemote(projectId: String, remote: ProjectRemote): Outcome<Unit>
}

@Singleton
internal class LocalProjectRepository @Inject constructor(
    private val db: OmniDatabase,
    private val projects: ProjectDao,
    private val files: FileDao,
    private val tests: TestCaseDao,
    private val runtimes: RuntimeRepository,
    private val preferences: PreferencesStore,
    private val folders: ProjectFolderRepository,
    private val store: ProjectFolderStore,
    private val sync: ProjectSync,
    private val lock: ProjectDiskLock,
    private val ids: IdGenerator,
    private val time: TimeSource,
    @ApplicationScope private val appScope: CoroutineScope,
) : ProjectRepository {

    /**
     * Orders writes of file text to the index, and snapshots behind them: saves queue on it in call order, and a
     * snapshot waits for every save requested before it, so a run (or search, export, push) never reads text older
     * than what was typed. Only the quick index write is held under it; the slower disk write follows outside.
     */
    private val saveLock = Mutex()

    override val lastProjectId: Flow<String?> = preferences.lastProjectId

    override fun observeSummaries(query: String, filter: ProjectFilter): Flow<List<ProjectSummary>> =
        projects.observeSummaries(query.trim().escapeLike()).map { rows ->
            rows.map { it.toModel() }.filter(filter::matches)
        }

    override fun observeOutline(projectId: String): Flow<WorkspaceOutline?> =
        projects.observeOutline(projectId).map { row ->
            row?.let {
                WorkspaceOutline(
                    project = it.project.toModel(),
                    files = it.files.sortedWith(compareByDescending<FileHeaderRow> { f -> f.isEntry }.thenBy { f -> f.position }).map { f -> f.toModel() },
                    tests = it.tests.sortedBy { t -> t.position }.map { t -> t.toModel() },
                )
            }
        }.distinctUntilChanged()

    override fun observeFile(fileId: String): Flow<OpenFile?> =
        files.observeContentVersion(fileId).distinctUntilChanged().map { version ->
            version?.let { files.content(fileId)?.let { content -> OpenFile(fileId, content, version) } }
        }

    override suspend fun snapshot(projectId: String): ProjectWorkspace? =
        saveLock.withLock { projects.withChildren(projectId) }?.let {
            ProjectWorkspace(
                project = it.project.toModel(),
                files = it.files.sortedWith(compareByDescending<FileEntity> { f -> f.isEntry }.thenBy { f -> f.position }).map { f -> f.toModel() },
                tests = it.tests.sortedBy { t -> t.position }.map { t -> t.toModel() },
            )
        }

    override suspend fun resolveStartupProject(): Outcome<String> {
        preferences.lastProjectId.first()?.let { id -> if (projects.get(id) != null) return Outcome.Success(id) }
        projects.mostRecent()?.let { return Outcome.Success(it.id) }
        val runtime = runtimes.defaultRuntime()
            ?: return Outcome.Failure(AppError.Offline(reason = ErrorReason.LanguagesUnavailable))
        return create(runtime)
    }

    override suspend fun create(runtime: Runtime, template: ProjectTemplate?): Outcome<String> {
        val info = runtimes.languageInfo(runtime.language)
        val now = time.now().toEpochMilliseconds()
        val projectId = ids.newId()
        // User defaults, raised when the runtime needs more to run its own sample (JVM/CLR cold starts).
        val limits = preferences.runSettings.first().defaultLimits.raisedTo(runtimes.defaultLimits(runtime.id))
        val code = template?.code ?: info.placeholderCode()
        val drafts = template?.tests ?: info.starter?.tests ?: listOf(TestCaseDraft("", ""))
        val entryName = runtime.filename.ifBlank { "main" }
        val result = onDisk { root ->
            val folder = store.createFolder(root, projectName(template?.name ?: "${info.base}-scratch").ifEmpty { "project" })
            val entry = store.writeFile(root, folder.docId, entryName, code)
            db.withTransaction {
                projects.upsert(ProjectEntity(projectId, folder.name, runtime.id, limits.timeMs, limits.memMb, null, now, now, folderDocId = folder.docId))
                files.insert(FileEntity(ids.newId(), projectId, entryName, code, isEntry = true, position = 0, docId = entry.docId, lastModified = entry.lastModified, size = entry.size))
                tests.upsertAll(drafts.mapIndexed { i, d -> d.toEntity(projectId, i) })
            }
            sync.writeManifestLocked(root, projectId)
            projectId
        }
        if (result is Outcome.Success) {
            preferences.setLastProjectId(projectId)
            runtimes.markUsed(runtime.id)
        }
        return result
    }

    override suspend fun duplicate(projectId: String): Outcome<String> {
        val source = projects.get(projectId) ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.ProjectNotFound))
        val now = time.now().toEpochMilliseconds()
        val copyId = ids.newId()
        return onDisk { root ->
            val folder = store.createFolder(root, "${source.name}-copy")
            val copies = files.list(projectId).map { file ->
                val written = store.writeFile(root, folder.docId, file.name, file.content)
                file.copy(id = ids.newId(), projectId = copyId, contentVersion = 0, docId = written.docId, lastModified = written.lastModified, size = written.size, diskDirty = false)
            }
            db.withTransaction {
                projects.upsert(source.copy(id = copyId, name = folder.name, lastVerdict = null, createdAt = now, updatedAt = now, folderDocId = folder.docId, issues = ""))
                files.insertAll(copies)
                tests.upsertAll(tests.list(projectId).map { it.copy(id = ids.newId(), projectId = copyId) })
            }
            sync.writeManifestLocked(root, copyId)
            copyId
        }
    }

    override suspend fun rename(projectId: String, name: String): Outcome<Unit> {
        val wanted = projectName(name)
        if (wanted.isEmpty()) return Outcome.Failure(AppError.Validation(reason = ErrorReason.NameRequired))
        val project = projects.get(projectId) ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.ProjectNotFound))
        if (wanted == project.name) return Outcome.Success(Unit)
        val unique = uniqueName(wanted, except = projectId)
        return onDisk { root ->
            val folderDocId = project.folderDocId
            if (folderDocId == null) {
                projects.upsert(project.copy(name = unique, updatedAt = time.now().toEpochMilliseconds()))
            } else {
                val renamed = store.rename(root, folderDocId, unique)
                projects.upsert(project.copy(name = renamed.name, folderDocId = renamed.docId, updatedAt = time.now().toEpochMilliseconds()))
                // Path-based providers give the files new ids with the folder; the rescan also updates the manifest's name.
                sync.syncProjectLocked(root, projectId)
            }
        }
    }

    override suspend fun delete(projectId: String): Outcome<Unit> {
        val project = projects.get(projectId) ?: return Outcome.Success(Unit)
        val folder = project.folderDocId
        // Keep the index when the folder survives: dropping it would only make the project reappear on the next rescan.
        if (folder != null) onDisk { root -> store.delete(root, folder) }.let { if (it is Outcome.Failure) return it }
        projects.delete(projectId)
        // Duplicates share the original document; its grant goes only with the last project that links to it.
        project.originUri?.takeIf { projects.countWithOrigin(it) == 0 }?.let(store::releaseDocument)
        if (preferences.lastProjectId.first() == projectId) preferences.setLastProjectId(null)
        return Outcome.Success(Unit)
    }

    override suspend fun markOpened(projectId: String) {
        preferences.setLastProjectId(projectId)
        // Pick up edits made in other apps since the project was last indexed.
        folders.root()?.let { sync.syncProject(it, projectId) }
    }

    override suspend fun changeRuntime(projectId: String, runtime: Runtime): Outcome<Unit> {
        val project = projects.get(projectId) ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.ProjectNotFound))
        val previous = runtimes.runtime(project.runtimeId)
        val fileRows = files.list(projectId)
        val entry = fileRows.firstOrNull { it.isEntry }
        if (fileRows.any { !it.isEntry && it.name == runtime.filename }) {
            return Outcome.Failure(AppError.Validation(reason = ErrorReason.EntryNameTaken(runtime.filename, runtime.id)))
        }
        val switchingLanguage = previous?.language != runtime.language
        val newInfo = runtimes.languageInfo(runtime.language)
        // Only replace code/tests the user never touched: an untouched starter is safe to swap.
        val oldInfo = previous?.let { runtimes.languageInfo(it.language) }
        val entryUntouched = entry == null || entry.content.isBlank() || entry.content == oldInfo?.placeholderCode()
        val testsUntouched = oldInfo?.starter?.tests?.let { starter ->
            tests.list(projectId).map { TestCaseDraft(it.stdin, it.expected) } == starter.map { TestCaseDraft(it.stdin, it.expected) }
        } ?: false
        val limits = Limits(project.timeLimitMs, project.memLimitMb).raisedTo(runtimes.defaultLimits(runtime.id))
        val result = onDisk { root ->
            val newName = runtime.filename.ifBlank { entry?.name.orEmpty() }
            val newCode = newInfo.placeholderCode().takeIf { switchingLanguage && entryUntouched }
            var disk = entry?.docId?.let { docId -> if (entry.name != newName) store.rename(root, docId, newName) else null }
            if (entry != null && newCode != null) disk = store.updateFile(root, disk?.docId ?: entry.docId ?: return@onDisk, newCode)
            db.withTransaction {
                projects.upsert(project.copy(runtimeId = runtime.id, timeLimitMs = limits.timeMs, memLimitMb = limits.memMb, updatedAt = time.now().toEpochMilliseconds()))
                if (entry != null) {
                    files.rename(entry.id, newName)
                    if (newCode != null) files.replaceContent(entry.id, newCode)
                    disk?.let { files.setDiskState(entry.id, it.docId, it.lastModified, it.size) }
                }
                if (switchingLanguage && testsUntouched) {
                    tests.replaceAll(projectId, newInfo.starter?.tests.orEmpty().mapIndexed { i, d -> d.toEntity(projectId, i) })
                }
            }
            sync.writeManifestLocked(root, projectId)
        }
        if (result is Outcome.Success) runtimes.markUsed(runtime.id)
        return result
    }

    override suspend fun setLimits(projectId: String, limits: Limits) {
        val project = projects.get(projectId) ?: return
        val clamped = limits.clamped()
        projects.upsert(project.copy(timeLimitMs = clamped.timeMs, memLimitMb = clamped.memMb))
        saveManifest(projectId)
    }

    override suspend fun recordVerdict(projectId: String, verdict: Verdict?) {
        projects.setLastVerdict(projectId, verdict?.code, time.now().toEpochMilliseconds())
        saveManifest(projectId)
    }

    /**
     * Runs in the application scope, so a screen closing mid-save (Back, switching projects) can't cancel it.
     * Started undispatched: the save takes its place on [saveLock] before this call returns control to the caller,
     * ahead of any snapshot requested afterwards. The text goes to the index first (flagged as not yet on disk), then
     * to the project folder; if the app dies in between, the next rescan writes it out (see ProjectSync).
     */
    override suspend fun updateFileContent(fileId: String, content: String) {
        appScope.async(start = CoroutineStart.UNDISPATCHED) {
            val indexed = saveLock.withLock { files.get(fileId)?.also { indexContent(fileId, content) } != null }
            if (indexed) writeToDisk(fileId)
        }.await()
    }

    /**
     * Writes the file's current text (a newer save may have replaced the one that asked) and clears its "not on disk"
     * flag only if that text is still current. Under the disk lock with the disk-state update, so a rescan can't read
     * the file half-way. Unreachable folders keep the flag set: the next save or rescan writes it.
     */
    private suspend fun writeToDisk(fileId: String) {
        onDisk { root ->
            val row = files.get(fileId)?.takeIf { it.diskDirty } ?: return@onDisk
            // Not in the folder yet (index-only project): the folder export writes it with the rest.
            val docId = row.docId ?: return@onDisk
            val written = store.updateFile(root, docId, row.content)
            files.markWritten(fileId, row.content, written.docId, written.lastModified, written.size)
        }
    }

    /** Edits count as activity: Projects ordering and "open most recent" follow them. */
    private suspend fun indexContent(fileId: String, content: String) = db.withTransaction {
        files.updateContent(fileId, content)
        projects.touchForFile(fileId, time.now().toEpochMilliseconds())
    }

    /** [name] may be a path (`src/util/helper.py`); missing folders are created. */
    override suspend fun addFile(projectId: String, name: String, content: String): Outcome<SourceFile> {
        val existing = files.list(projectId)
        val path = ProjectPaths.normalize(name)
        checkPath(path, existing.mapTo(HashSet()) { it.name })?.let { return Outcome.Failure(it) }
        if (existing.size > MAX_EXTRA_FILES) return Outcome.Failure(AppError.Validation(reason = ErrorReason.TooManyFiles(MAX_EXTRA_FILES)))
        val folder = projects.get(projectId)?.folderDocId ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.ProjectNotFound))
        return onDisk { root ->
            // A file the index skipped (binary, too large) may already sit there under this path; never overwrite it.
            val folderEntry = store.entry(root, folder) ?: throw java.io.IOException("Project folder is gone")
            if (store.scanFolder(root, folderEntry).files.any { it.path == path || it.path.startsWith("$path/") }) {
                return@onDisk null
            }
            val written = store.writeFile(root, folder, path, content)
            val entity = FileEntity(ids.newId(), projectId, path, content, isEntry = false, position = files.nextPosition(projectId), docId = written.docId, lastModified = written.lastModified, size = written.size)
            files.insert(entity)
            projects.touch(projectId, time.now().toEpochMilliseconds())
            entity.toModel()
        }.let { result ->
            when {
                result is Outcome.Success && result.value == null -> Outcome.Failure(AppError.Validation(reason = ErrorReason.FileExists(path)))
                result is Outcome.Success -> Outcome.Success(result.value!!)
                else -> result as Outcome.Failure
            }
        }
    }

    /** A new name in the same folder renames the file; a path elsewhere moves it (empty folders left behind go). */
    override suspend fun renameFile(fileId: String, projectId: String, name: String): Outcome<Unit> {
        val existing = files.list(projectId)
        val file = existing.firstOrNull { it.id == fileId } ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.FileNotFound))
        if (file.isEntry) return Outcome.Failure(AppError.Validation(reason = ErrorReason.EntryNamedByRuntime))
        val path = ProjectPaths.normalize(name)
        if (path == file.name) return Outcome.Success(Unit)
        checkPath(path, existing.filter { it.id != fileId }.mapTo(HashSet()) { it.name })?.let { return Outcome.Failure(it) }
        val docId = file.docId ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.FileNotFound))
        val folder = projects.get(projectId)?.folderDocId ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.ProjectNotFound))
        return onDisk { root ->
            val sameFolder = ProjectPaths.parent(path) == ProjectPaths.parent(file.name)
            val written = if (sameFolder) store.rename(root, docId, ProjectPaths.basename(path)) else store.moveFile(root, folder, docId, file.name, path, file.content)
            db.withTransaction {
                files.rename(fileId, path)
                files.setDiskState(fileId, written.docId, written.lastModified, written.size)
                // A move rewrote the text from the index, so a pending disk write of it is done.
                if (!sameFolder) files.markWritten(fileId, file.content, written.docId, written.lastModified, written.size)
            }
        }
    }

    override suspend fun deleteFile(fileId: String) {
        val file = files.get(fileId)?.takeIf { !it.isEntry } ?: return
        val docId = file.docId
        val folder = projects.get(file.projectId)?.folderDocId
        if (docId != null && folder != null && onDisk { root -> store.deleteFile(root, folder, docId, file.name) } is Outcome.Failure) return
        files.deleteNonEntry(fileId)
    }

    override suspend fun addTest(projectId: String, draft: TestCaseDraft): TestCase {
        val entity = draft.toEntity(projectId, tests.nextPosition(projectId))
        tests.upsert(entity)
        saveManifest(projectId)
        return entity.toModel()
    }

    override suspend fun updateTest(test: TestCase) {
        tests.upsert(TestCaseEntity(test.id, test.projectId, test.name, test.stdin, test.expected, test.position))
        saveManifest(test.projectId)
    }

    override suspend fun deleteTest(testId: String) {
        val projectId = tests.get(testId)?.projectId
        tests.delete(testId)
        projectId?.let { saveManifest(it) }
    }

    override suspend fun duplicateTest(test: TestCase): TestCase =
        addTest(test.projectId, TestCaseDraft(test.stdin, test.expected, if (test.name.isBlank()) "" else "${test.name} copy"))

    override suspend fun replaceTests(projectId: String, drafts: List<TestCaseDraft>) {
        tests.replaceAll(projectId, drafts.mapIndexed { i, d -> d.toEntity(projectId, i) })
        saveManifest(projectId)
    }

    override suspend fun resetToStarter(projectId: String): Outcome<Unit> {
        val project = projects.get(projectId) ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.ProjectNotFound))
        val runtime = runtimes.runtime(project.runtimeId)
        val info = runtimes.languageInfo(runtime?.language ?: project.runtimeId.substringBefore('-'))
        val entry = files.list(projectId).firstOrNull { it.isEntry } ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.EntryFileMissing))
        val code = info.placeholderCode()
        return onDisk { root ->
            val written = entry.docId?.let { store.updateFile(root, it, code) }
            db.withTransaction {
                files.replaceContent(entry.id, code)
                written?.let { files.setDiskState(entry.id, it.docId, it.lastModified, it.size) }
                tests.replaceAll(projectId, (info.starter?.tests ?: listOf(TestCaseDraft("", ""))).mapIndexed { i, d -> d.toEntity(projectId, i) })
            }
            sync.writeManifestLocked(root, projectId)
        }
    }

    override suspend fun refreshFromDisk(): Outcome<Unit> {
        val root = folders.root() ?: return Outcome.Failure(AppError.Unknown(reason = ErrorReason.ProjectsFolderUnavailable))
        return sync.syncAll(root)
    }

    override suspend fun importFolder(treeUri: String): Outcome<String> =
        imported(onDisk { root -> sync.importFolderLocked(root, treeUri) })

    override suspend fun importFile(documentUri: String): Outcome<String> = try {
        imported(onDisk { root -> sync.importFileLocked(root, documentUri) })
    } catch (e: UnknownLanguageException) {
        Outcome.Failure(AppError.Validation(reason = ErrorReason.UnknownFileLanguage(e.fileName)))
    }

    override suspend fun importText(fileName: String, text: String): Outcome<String> = try {
        imported(onDisk { root -> sync.createSingleFileLocked(root, fileName, text, origin = null) })
    } catch (e: UnknownLanguageException) {
        Outcome.Failure(AppError.Validation(reason = ErrorReason.UnknownFileLanguage(e.fileName)))
    }

    override suspend fun saveToOrigin(projectId: String): Outcome<Unit> {
        val origin = projects.get(projectId)?.originUri ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.FileNotFound))
        val entry = files.list(projectId).firstOrNull { it.isEntry } ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.EntryFileMissing))
        return onDisk { store.writeDocument(origin, entry.content) }
    }

    override suspend fun importRemote(name: String, files: Map<String, String>, remote: ProjectRemote): Outcome<String> =
        imported(onDisk { root -> sync.createFromFilesLocked(root, projectName(name).ifEmpty { "repo" }, files, remote.toManifest()) })

    override suspend fun applyRemote(
        projectId: String,
        changes: Map<String, String?>,
        remote: ProjectRemote,
        expectedLocal: Map<String, String?>,
    ): Outcome<Unit> = appScope.async(start = CoroutineStart.UNDISPATCHED) {
        // Under the save lock (no editor save can land between the check and the writes) and in the application
        // scope (leaving the screen can't stop it half-way).
        saveLock.withLock { applyRemoteLocked(projectId, changes, remote, expectedLocal) }
    }.await()

    private suspend fun applyRemoteLocked(
        projectId: String,
        changes: Map<String, String?>,
        remote: ProjectRemote,
        expectedLocal: Map<String, String?>,
    ): Outcome<Unit> {
        val project = projects.get(projectId) ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.ProjectNotFound))
        val folder = project.folderDocId ?: return Outcome.Failure(AppError.Unknown(reason = ErrorReason.ProjectsFolderUnavailable))
        val applied = onDisk { root ->
            val existing = files.list(projectId).associateBy { it.name }
            if (changes.keys.any { existing[it]?.content != expectedLocal[it] }) return@onDisk false
            changes.forEach { (name, content) ->
                val row = existing[name]
                val docId = row?.docId
                when {
                    content == null -> if (row != null && !row.isEntry && docId != null) store.deleteFile(root, folder, docId, name)
                    docId != null -> {
                        store.updateFile(root, docId, content)
                        // The remote text replaces the file; a pending local write of the old text must not follow.
                        files.clearDiskDirty(row.id)
                    }
                    else -> store.writeFile(root, folder, name, content)
                }
            }
            projects.upsert(project.copy(remoteJson = ManifestCodec.encodeRemote(remote.toManifest()), updatedAt = time.now().toEpochMilliseconds()))
            sync.writeManifestLocked(root, projectId)
            // Index what the folder now holds through the same checks as any rescan (size, file limit, names).
            sync.syncProjectLocked(root, projectId) is Outcome.Success
        }
        return when {
            applied is Outcome.Failure -> applied
            (applied as Outcome.Success).value -> Outcome.Success(Unit)
            else -> Outcome.Failure(AppError.Conflict(reason = ErrorReason.GitLocalChanged))
        }
    }

    override suspend fun folderFileNames(projectId: String): Set<String>? {
        val folder = projects.get(projectId)?.folderDocId ?: return null
        val names = onDisk { root ->
            val entry = store.entry(root, folder) ?: throw java.io.IOException("Project folder is gone")
            store.scanFolder(root, entry).files.mapTo(HashSet()) { it.path }
        }
        return (names as? Outcome.Success)?.value
    }

    override suspend fun setRemote(projectId: String, remote: ProjectRemote): Outcome<Unit> {
        val project = projects.get(projectId) ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.ProjectNotFound))
        projects.upsert(project.copy(remoteJson = ManifestCodec.encodeRemote(remote.toManifest())))
        return onDisk { root -> sync.writeManifestLocked(root, projectId) }
    }

    private suspend fun imported(result: Outcome<String?>): Outcome<String> = when (result) {
        is Outcome.Failure -> result
        is Outcome.Success -> result.value?.let { id ->
            preferences.setLastProjectId(id)
            Outcome.Success(id)
        } ?: Outcome.Failure(AppError.Validation(reason = ErrorReason.NothingToImport))
    }

    /**
     * Runs [block] against the projects folder under the disk lock. A storage error re-checks access, so a
     * revoked grant or deleted folder sends the user back to the folder picker instead of failing silently.
     */
    private suspend fun <T> onDisk(block: suspend (ProjectRoot) -> T): Outcome<T> {
        val root = folders.root() ?: return Outcome.Failure(AppError.Unknown(reason = ErrorReason.ProjectsFolderUnavailable))
        return try {
            Outcome.Success(lock.withLock { block(root) })
        } catch (e: java.io.IOException) {
            folders.recheck()
            Outcome.Failure(AppError.Unknown(reason = ErrorReason.ProjectsFolderUnavailable))
        }
    }

    /** Tests, limits and verdicts live in the manifest; the index already holds the new values. */
    private suspend fun saveManifest(projectId: String) {
        onDisk { root -> sync.writeManifestLocked(root, projectId) }
    }

    /** `%` and `_` are LIKE wildcards; the summaries query declares `\` as its escape character. */
    private fun String.escapeLike() = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun TestCaseDraft.toEntity(projectId: String, position: Int) =
        TestCaseEntity(ids.newId(), projectId, name, stdin, expected, position)

    private suspend fun uniqueName(base: String, except: String): String {
        val taken = projects.all().filter { it.id != except }.map { it.name.lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        return generateSequence(2) { it + 1 }.map { "$base-$it" }.first { it.lowercase() !in taken }
    }

    /**
     * Mirrors ls-judge's structural path checks so a bad path is refused when typed, not when run. A path can't
     * clash with another file, nor with a folder of one (`src` as a file while `src/a.py` exists, or the reverse).
     */
    private fun checkPath(path: String, taken: Set<String>): AppError.Validation? {
        val reason = when (ProjectPaths.problem(path)) {
            ProjectPaths.Problem.EMPTY -> ErrorReason.FileNameRequired
            ProjectPaths.Problem.TOO_LONG -> ErrorReason.FileNameTooLong(ProjectPaths.MAX_LENGTH)
            ProjectPaths.Problem.SEGMENT_TOO_LONG -> ErrorReason.FileNameTooLong(ProjectPaths.MAX_SEGMENT)
            ProjectPaths.Problem.TOO_DEEP -> ErrorReason.PathTooDeep(ProjectPaths.MAX_DEPTH)
            ProjectPaths.Problem.BAD_SEGMENT -> ErrorReason.InvalidPath
            null -> when {
                ProjectPaths.isInternal(path) -> ErrorReason.InvalidPath
                path in taken || taken.any { it.startsWith("$path/") } || ProjectPaths.ancestors(path).any { it in taken } -> ErrorReason.FileExists(path)
                else -> null
            }
        }
        return reason?.let { AppError.Validation(reason = it) }
    }

    /** The folder name for a project: what the user typed, minus characters storage can't hold. */
    private fun projectName(value: String) = safeFileName(value, MAX_PROJECT_NAME)

    private companion object {
        const val MAX_EXTRA_FILES = ProjectLimits.MAX_EXTRA_FILES
        const val MAX_PROJECT_NAME = 64
    }
}
