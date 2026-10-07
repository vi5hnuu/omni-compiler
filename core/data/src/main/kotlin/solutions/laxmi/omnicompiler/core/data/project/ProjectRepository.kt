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
     * Applies files from the remote (content, or null to delete) as outside edits (an open editor reloads),
     * then records the new [remote] state. The entry file is never deleted this way.
     */
    suspend fun applyRemote(projectId: String, changes: Map<String, String?>, remote: ProjectRemote): Outcome<Unit>

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
     * Orders editor saves and snapshots. Saves queue on it in call order, and a snapshot waits behind every save
     * requested before it, so a run (or search, export, push) never reads text older than what was typed.
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
                file.copy(id = ids.newId(), projectId = copyId, contentVersion = 0, docId = written.docId, lastModified = written.lastModified, size = written.size)
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
        project.originUri?.let(store::releaseDocument)
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
     * Runs in the application scope, so a screen closing mid-save (Back, switching projects) can't cancel the write.
     * Started undispatched: the save takes its place on [saveLock] before this call returns control to the caller,
     * ahead of any snapshot requested afterwards.
     */
    override suspend fun updateFileContent(fileId: String, content: String) {
        appScope.async(start = CoroutineStart.UNDISPATCHED) { saveLock.withLock { writeFileContent(fileId, content) } }.await()
    }

    private suspend fun writeFileContent(fileId: String, content: String) {
        val file = files.get(fileId) ?: return
        val docId = file.docId ?: return indexContent(fileId, content, written = null)
        // Index first, then disk, both under the disk lock: a rescan in between would see the older file, take it
        // for an edit made in another app and reload the editor mid-typing.
        val onDiskToo = onDisk { root ->
            indexContent(fileId, content, written = null)
            files.setDiskStateOf(fileId, store.updateFile(root, docId, content))
        }
        // The folder can't be reached: the index still keeps the text, and the next save retries the disk.
        if (onDiskToo is Outcome.Failure) indexContent(fileId, content, written = null)
    }

    private suspend fun FileDao.setDiskStateOf(fileId: String, written: DocEntry) =
        setDiskState(fileId, written.docId, written.lastModified, written.size)

    /** Edits count as activity: Projects ordering and "open most recent" follow them. */
    private suspend fun indexContent(fileId: String, content: String, written: DocEntry?) = db.withTransaction {
        files.updateContent(fileId, content)
        written?.let { files.setDiskState(fileId, it.docId, it.lastModified, it.size) }
        projects.touchForFile(fileId, time.now().toEpochMilliseconds())
    }

    override suspend fun addFile(projectId: String, name: String, content: String): Outcome<SourceFile> {
        val existing = files.list(projectId)
        validateFileName(name, existing.map { it.name })?.let { return Outcome.Failure(it) }
        if (existing.size > MAX_EXTRA_FILES) return Outcome.Failure(AppError.Validation(reason = ErrorReason.TooManyFiles(MAX_EXTRA_FILES)))
        val folder = projects.get(projectId)?.folderDocId ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.ProjectNotFound))
        val trimmed = name.trim()
        return onDisk { root ->
            // A file the index skipped (binary, too large) may already sit there under this name; never overwrite it.
            val folderEntry = store.entry(root, folder) ?: throw java.io.IOException("Project folder is gone")
            if (store.scanFolder(root, folderEntry).entries.any { it.name == trimmed }) {
                return@onDisk null
            }
            val written = store.writeFile(root, folder, trimmed, content)
            val entity = FileEntity(ids.newId(), projectId, trimmed, content, isEntry = false, position = files.nextPosition(projectId), docId = written.docId, lastModified = written.lastModified, size = written.size)
            files.insert(entity)
            projects.touch(projectId, time.now().toEpochMilliseconds())
            entity.toModel()
        }.let { result ->
            when {
                result is Outcome.Success && result.value == null -> Outcome.Failure(AppError.Validation(reason = ErrorReason.FileExists(trimmed)))
                result is Outcome.Success -> Outcome.Success(result.value!!)
                else -> result as Outcome.Failure
            }
        }
    }

    override suspend fun renameFile(fileId: String, projectId: String, name: String): Outcome<Unit> {
        val existing = files.list(projectId)
        val file = existing.firstOrNull { it.id == fileId } ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.FileNotFound))
        if (file.isEntry) return Outcome.Failure(AppError.Validation(reason = ErrorReason.EntryNamedByRuntime))
        validateFileName(name, existing.filter { it.id != fileId }.map { it.name })?.let { return Outcome.Failure(it) }
        val docId = file.docId ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.FileNotFound))
        return onDisk { root ->
            val renamed = store.rename(root, docId, name.trim())
            db.withTransaction {
                files.rename(fileId, renamed.name)
                files.setDiskState(fileId, renamed.docId, renamed.lastModified, renamed.size)
            }
        }
    }

    override suspend fun deleteFile(fileId: String) {
        val file = files.get(fileId)?.takeIf { !it.isEntry } ?: return
        val docId = file.docId
        if (docId != null && onDisk { root -> store.delete(root, docId) } is Outcome.Failure) return
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

    override suspend fun applyRemote(projectId: String, changes: Map<String, String?>, remote: ProjectRemote): Outcome<Unit> {
        val project = projects.get(projectId) ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.ProjectNotFound))
        val folder = project.folderDocId ?: return Outcome.Failure(AppError.Unknown(reason = ErrorReason.ProjectsFolderUnavailable))
        return onDisk { root ->
            val existing = files.list(projectId).associateBy { it.name }
            changes.forEach { (name, content) ->
                val row = existing[name]
                when {
                    content == null && row != null && !row.isEntry -> {
                        row.docId?.let { store.delete(root, it) }
                        files.deleteNonEntry(row.id)
                    }
                    content != null && row != null -> {
                        val written = row.docId?.let { store.updateFile(root, it, content) } ?: store.writeFile(root, folder, name, content)
                        db.withTransaction {
                            files.replaceContent(row.id, content)
                            files.setDiskState(row.id, written.docId, written.lastModified, written.size)
                        }
                    }
                    content != null -> {
                        val written = store.writeFile(root, folder, name, content)
                        files.insert(
                            FileEntity(ids.newId(), projectId, name, content, isEntry = false, position = files.nextPosition(projectId),
                                docId = written.docId, lastModified = written.lastModified, size = written.size),
                        )
                    }
                }
            }
            projects.upsert(project.copy(remoteJson = ManifestCodec.encodeRemote(remote.toManifest()), updatedAt = time.now().toEpochMilliseconds()))
            sync.writeManifestLocked(root, projectId)
        }
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

    /** Mirrors ls-judge's server-side checks so errors surface before a run is attempted. */
    private fun validateFileName(name: String, taken: List<String>): AppError.Validation? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> AppError.Validation(reason = ErrorReason.FileNameRequired)
            '/' in trimmed || '\\' in trimmed || trimmed.contains("..") || '\u0000' in trimmed ->
                AppError.Validation(reason = ErrorReason.FlatWorkspace)
            trimmed.startsWith('.') -> AppError.Validation(reason = ErrorReason.FlatWorkspace)
            trimmed.length > MAX_FILE_NAME -> AppError.Validation(reason = ErrorReason.FileNameTooLong(MAX_FILE_NAME))
            trimmed in taken -> AppError.Validation(reason = ErrorReason.FileExists(trimmed))
            else -> null
        }
    }

    /** The folder name for a project: what the user typed, minus characters storage can't hold. */
    private fun projectName(value: String) = safeFileName(value, MAX_PROJECT_NAME)

    private companion object {
        const val MAX_EXTRA_FILES = 20
        const val MAX_FILE_NAME = 128
        const val MAX_PROJECT_NAME = 64
    }
}
