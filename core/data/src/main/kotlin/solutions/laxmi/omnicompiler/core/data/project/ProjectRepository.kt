package solutions.laxmi.omnicompiler.core.data.project

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
import solutions.laxmi.omnicompiler.core.database.entity.ProjectEntity
import solutions.laxmi.omnicompiler.core.database.entity.TestCaseEntity
import solutions.laxmi.omnicompiler.core.datastore.PreferencesStore
import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.ErrorReason
import solutions.laxmi.omnicompiler.core.model.Limits
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.ProjectFilter
import solutions.laxmi.omnicompiler.core.model.ProjectSummary
import solutions.laxmi.omnicompiler.core.model.ProjectWorkspace
import solutions.laxmi.omnicompiler.core.model.Runtime
import solutions.laxmi.omnicompiler.core.model.SourceFile
import solutions.laxmi.omnicompiler.core.model.TestCase
import solutions.laxmi.omnicompiler.core.model.TestCaseDraft
import solutions.laxmi.omnicompiler.core.model.Verdict
import javax.inject.Inject
import javax.inject.Singleton

/** Starting content for a new project (blank language starter, example, or problem). */
data class ProjectTemplate(
    val name: String,
    val code: String?,
    val tests: List<TestCaseDraft>?,
)

/**
 * Projects, files and test cases. Stored only on this device: ls-judge has no storage API, so the
 * interface is shaped to allow a remote-synced implementation later without touching callers.
 */
interface ProjectRepository {
    fun observeSummaries(query: String, filter: ProjectFilter): Flow<List<ProjectSummary>>
    fun observeWorkspace(projectId: String): Flow<ProjectWorkspace?>
    val lastProjectId: Flow<String?>

    /** The project to open at launch: the last one used, else the most recent, else a new starter. */
    suspend fun resolveStartupProject(): Outcome<String>

    suspend fun create(runtime: Runtime, template: ProjectTemplate? = null): Outcome<String>
    suspend fun duplicate(projectId: String): Outcome<String>
    suspend fun rename(projectId: String, name: String): Outcome<Unit>
    suspend fun delete(projectId: String)
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
}

@Singleton
internal class LocalProjectRepository @Inject constructor(
    private val db: OmniDatabase,
    private val projects: ProjectDao,
    private val files: FileDao,
    private val tests: TestCaseDao,
    private val runtimes: RuntimeRepository,
    private val preferences: PreferencesStore,
    private val ids: IdGenerator,
    private val time: TimeSource,
) : ProjectRepository {

    override val lastProjectId: Flow<String?> = preferences.lastProjectId

    override fun observeSummaries(query: String, filter: ProjectFilter): Flow<List<ProjectSummary>> =
        projects.observeSummaries(query.trim()).map { rows ->
            rows.map { it.toModel() }.filter(filter::matches)
        }

    override fun observeWorkspace(projectId: String): Flow<ProjectWorkspace?> = combine(
        projects.observe(projectId),
        files.observe(projectId),
        tests.observe(projectId),
    ) { project, fileRows, testRows ->
        project?.let { ProjectWorkspace(it.toModel(), fileRows.map { f -> f.toModel() }, testRows.map { t -> t.toModel() }) }
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
        val name = uniqueName(template?.name?.let(::slugify) ?: "${info.base}-scratch")
        val code = template?.code ?: info.placeholderCode()
        val drafts = template?.tests ?: info.starter?.tests ?: listOf(TestCaseDraft("", ""))
        db.withTransaction {
            projects.upsert(ProjectEntity(projectId, name, runtime.id, limits.timeMs, limits.memMb, null, now, now))
            files.insert(FileEntity(ids.newId(), projectId, runtime.filename.ifBlank { "main" }, code, isEntry = true, position = 0))
            tests.upsertAll(drafts.mapIndexed { i, d -> d.toEntity(projectId, i) })
        }
        preferences.setLastProjectId(projectId)
        runtimes.markUsed(runtime.id)
        return Outcome.Success(projectId)
    }

    override suspend fun duplicate(projectId: String): Outcome<String> {
        val source = projects.get(projectId) ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.ProjectNotFound))
        val now = time.now().toEpochMilliseconds()
        val copyId = ids.newId()
        db.withTransaction {
            projects.upsert(source.copy(id = copyId, name = uniqueName("${source.name}-copy"), lastVerdict = null, createdAt = now, updatedAt = now))
            files.insertAll(files.list(projectId).map { it.copy(id = ids.newId(), projectId = copyId) })
            tests.upsertAll(tests.list(projectId).map { it.copy(id = ids.newId(), projectId = copyId) })
        }
        return Outcome.Success(copyId)
    }

    override suspend fun rename(projectId: String, name: String): Outcome<Unit> {
        val slug = slugify(name)
        if (slug.isEmpty()) return Outcome.Failure(AppError.Validation(reason = ErrorReason.NameRequired))
        val project = projects.get(projectId) ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.ProjectNotFound))
        projects.upsert(project.copy(name = slug, updatedAt = time.now().toEpochMilliseconds()))
        return Outcome.Success(Unit)
    }

    override suspend fun delete(projectId: String) {
        projects.delete(projectId)
        if (preferences.lastProjectId.first() == projectId) preferences.setLastProjectId(null)
    }

    override suspend fun markOpened(projectId: String) {
        preferences.setLastProjectId(projectId)
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
        db.withTransaction {
            projects.upsert(
                project.copy(
                    runtimeId = runtime.id,
                    timeLimitMs = limits.timeMs,
                    memLimitMb = limits.memMb,
                    updatedAt = time.now().toEpochMilliseconds(),
                ),
            )
            if (entry != null) {
                files.rename(entry.id, runtime.filename.ifBlank { entry.name })
                if (switchingLanguage && entryUntouched) files.updateContent(entry.id, newInfo.placeholderCode())
            }
            if (switchingLanguage && testsUntouched) {
                tests.replaceAll(projectId, newInfo.starter?.tests.orEmpty().mapIndexed { i, d -> d.toEntity(projectId, i) })
            }
        }
        runtimes.markUsed(runtime.id)
        return Outcome.Success(Unit)
    }

    override suspend fun setLimits(projectId: String, limits: Limits) {
        val project = projects.get(projectId) ?: return
        val clamped = limits.clamped()
        projects.upsert(project.copy(timeLimitMs = clamped.timeMs, memLimitMb = clamped.memMb))
    }

    override suspend fun recordVerdict(projectId: String, verdict: Verdict?) {
        projects.setLastVerdict(projectId, verdict?.code, time.now().toEpochMilliseconds())
    }

    override suspend fun updateFileContent(fileId: String, content: String) {
        files.updateContent(fileId, content)
    }

    override suspend fun addFile(projectId: String, name: String, content: String): Outcome<SourceFile> {
        val existing = files.list(projectId)
        validateFileName(name, existing.map { it.name })?.let { return Outcome.Failure(it) }
        if (existing.size > MAX_EXTRA_FILES) return Outcome.Failure(AppError.Validation(reason = ErrorReason.TooManyFiles(MAX_EXTRA_FILES)))
        val entity = FileEntity(ids.newId(), projectId, name.trim(), content, isEntry = false, position = files.nextPosition(projectId))
        files.insert(entity)
        projects.touch(projectId, time.now().toEpochMilliseconds())
        return Outcome.Success(entity.toModel())
    }

    override suspend fun renameFile(fileId: String, projectId: String, name: String): Outcome<Unit> {
        val existing = files.list(projectId)
        val file = existing.firstOrNull { it.id == fileId } ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.FileNotFound))
        if (file.isEntry) return Outcome.Failure(AppError.Validation(reason = ErrorReason.EntryNamedByRuntime))
        validateFileName(name, existing.filter { it.id != fileId }.map { it.name })?.let { return Outcome.Failure(it) }
        files.rename(fileId, name.trim())
        return Outcome.Success(Unit)
    }

    override suspend fun deleteFile(fileId: String) = files.deleteNonEntry(fileId)

    override suspend fun addTest(projectId: String, draft: TestCaseDraft): TestCase {
        val entity = draft.toEntity(projectId, tests.nextPosition(projectId))
        tests.upsert(entity)
        return entity.toModel()
    }

    override suspend fun updateTest(test: TestCase) {
        tests.upsert(TestCaseEntity(test.id, test.projectId, test.name, test.stdin, test.expected, test.position))
    }

    override suspend fun deleteTest(testId: String) = tests.delete(testId)

    override suspend fun duplicateTest(test: TestCase): TestCase =
        addTest(test.projectId, TestCaseDraft(test.stdin, test.expected, if (test.name.isBlank()) "" else "${test.name} copy"))

    override suspend fun replaceTests(projectId: String, drafts: List<TestCaseDraft>) {
        tests.replaceAll(projectId, drafts.mapIndexed { i, d -> d.toEntity(projectId, i) })
    }

    override suspend fun resetToStarter(projectId: String): Outcome<Unit> {
        val project = projects.get(projectId) ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.ProjectNotFound))
        val runtime = runtimes.runtime(project.runtimeId)
        val info = runtimes.languageInfo(runtime?.language ?: project.runtimeId.substringBefore('-'))
        val entry = files.list(projectId).firstOrNull { it.isEntry } ?: return Outcome.Failure(AppError.NotFound(reason = ErrorReason.EntryFileMissing))
        db.withTransaction {
            files.updateContent(entry.id, info.placeholderCode())
            tests.replaceAll(projectId, (info.starter?.tests ?: listOf(TestCaseDraft("", ""))).mapIndexed { i, d -> d.toEntity(projectId, i) })
        }
        return Outcome.Success(Unit)
    }

    private fun TestCaseDraft.toEntity(projectId: String, position: Int) =
        TestCaseEntity(ids.newId(), projectId, name, stdin, expected, position)

    private suspend fun uniqueName(base: String): String {
        val taken = projects.names().toSet()
        if (base !in taken) return base
        return generateSequence(2) { it + 1 }.map { "$base-$it" }.first { it !in taken }
    }

    /** Mirrors ls-judge's server-side checks so errors surface before a run is attempted. */
    private fun validateFileName(name: String, taken: List<String>): AppError.Validation? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> AppError.Validation(reason = ErrorReason.FileNameRequired)
            '/' in trimmed || '\\' in trimmed || trimmed.contains("..") || '\u0000' in trimmed ->
                AppError.Validation(reason = ErrorReason.FlatWorkspace)
            trimmed.length > MAX_FILE_NAME -> AppError.Validation(reason = ErrorReason.FileNameTooLong(MAX_FILE_NAME))
            trimmed in taken -> AppError.Validation(reason = ErrorReason.FileExists(trimmed))
            else -> null
        }
    }

    private fun slugify(value: String) = value.trim().lowercase()
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-')
        .take(MAX_PROJECT_NAME)

    private companion object {
        const val MAX_EXTRA_FILES = 20
        const val MAX_FILE_NAME = 128
        const val MAX_PROJECT_NAME = 48
    }
}
