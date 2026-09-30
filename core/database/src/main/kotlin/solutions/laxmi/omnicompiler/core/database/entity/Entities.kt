package solutions.laxmi.omnicompiler.core.database.entity

import androidx.room.Relation
import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects", indices = [Index("updated_at")])
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "runtime_id") val runtimeId: String,
    @ColumnInfo(name = "time_limit_ms") val timeLimitMs: Int,
    @ColumnInfo(name = "mem_limit_mb") val memLimitMb: Int,
    @ColumnInfo(name = "last_verdict") val lastVerdict: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** Document id of the project's folder on device storage; null until the project is written there. */
    @ColumnInfo(name = "folder_doc_id") val folderDocId: String? = null,
    /** Problems found when the folder was last checked, one `code:detail` per line (see ProjectIssue). */
    @ColumnInfo(name = "issues", defaultValue = "") val issues: String = "",
    /** Document a single-file import came from (see ProjectManifest.origin). */
    @ColumnInfo(name = "origin_uri") val originUri: String? = null,
)

@Entity(
    tableName = "files",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["project_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["project_id", "name"], unique = true)],
)
data class FileEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    val name: String,
    val content: String,
    @ColumnInfo(name = "is_entry") val isEntry: Boolean,
    val position: Int,
    /**
     * Bumped only when the content is replaced by something other than the editor (reset to starter, a language
     * switch, an edit made outside the app). The editor reloads its buffer when this changes, never on its own saves.
     */
    @ColumnInfo(name = "content_version", defaultValue = "0") val contentVersion: Int = 0,
    /** The file's document id and last known on-disk state; a mismatch on rescan means it changed outside the app. */
    @ColumnInfo(name = "doc_id") val docId: String? = null,
    @ColumnInfo(name = "last_modified", defaultValue = "0") val lastModified: Long = 0,
    @ColumnInfo(name = "size", defaultValue = "0") val size: Long = 0,
)

/** A project with its files and tests read in one transaction, so observers never see a half-updated workspace. */
data class ProjectWithChildren(
    @Embedded val project: ProjectEntity,
    @Relation(parentColumn = "id", entityColumn = "project_id") val files: List<FileEntity>,
    @Relation(parentColumn = "id", entityColumn = "project_id") val tests: List<TestCaseEntity>,
)

@Entity(
    tableName = "test_cases",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["project_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("project_id")],
)
data class TestCaseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    val name: String,
    val stdin: String,
    val expected: String,
    val position: Int,
)

/** One console entry: a finished (or failed-to-submit) run of a project. */
@Entity(
    tableName = "runs",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["project_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["project_id", "started_at"])],
)
data class RunEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    @ColumnInfo(name = "job_id") val jobId: String?,
    @ColumnInfo(name = "runtime_id") val runtimeId: String,
    val mode: String,
    val status: String,
    val verdict: String?,
    @ColumnInfo(name = "total_time_ms") val totalTimeMs: Int?,
    @ColumnInfo(name = "test_count") val testCount: Int,
    /** Test labels at run time, newline-separated (tests may be renamed or deleted later). */
    @ColumnInfo(name = "test_names", defaultValue = "") val testNames: String,
    /** Ids of the project tests this run used, newline-separated, in judge order; empty for stdin runs. */
    @ColumnInfo(name = "test_ids", defaultValue = "") val testIds: String,
    @ColumnInfo(name = "compile_output") val compileOutput: String?,
    @ColumnInfo(name = "diag_line") val diagnosticLine: Int?,
    @ColumnInfo(name = "diag_column") val diagnosticColumn: Int?,
    @ColumnInfo(name = "diag_message") val diagnosticMessage: String?,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
    @ColumnInfo(name = "from_cache") val fromCache: Boolean,
    @ColumnInfo(name = "started_at") val startedAt: Long,
)

@Entity(
    tableName = "run_results",
    primaryKeys = ["run_id", "test_index"],
    foreignKeys = [ForeignKey(RunEntity::class, ["id"], ["run_id"], onDelete = ForeignKey.CASCADE)],
)
data class RunResultEntity(
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "test_index") val index: Int,
    val verdict: String,
    @ColumnInfo(name = "time_ms") val timeMs: Int?,
    val stdin: String?,
    val expected: String?,
    val stdout: String?,
    val stderr: String?,
)

@Entity(tableName = "runtimes")
data class RuntimeEntity(
    @PrimaryKey val id: String,
    val language: String,
    val version: String,
    val status: String,
    val filename: String,
    val available: Boolean,
    val lane: String,
)

/** Cached page of `GET /me/submissions`; [position] preserves server (newest-first) order. */
@Entity(tableName = "submissions", indices = [Index("position"), Index("created_at")])
data class SubmissionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "runtime_id") val runtimeId: String,
    val status: String,
    val verdict: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "total_time_ms") val totalTimeMs: Int?,
    val position: Long,
)

/** Single-row cursor state for the submissions RemoteMediator. */
@Entity(tableName = "submission_cursor")
data class SubmissionCursorEntity(
    @PrimaryKey val key: Int = 0,
    @ColumnInfo(name = "next_cursor") val nextCursor: String?,
    @ColumnInfo(name = "end_reached") val endReached: Boolean,
)

/** A run requested while offline. [payload] is an opaque, versioned JSON blob owned by the data layer. */
@Entity(
    tableName = "pending_runs",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["project_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("project_id")],
)
data class PendingRunEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    val payload: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

data class ProjectSummaryRow(
    val id: String,
    val name: String,
    @ColumnInfo(name = "runtime_id") val runtimeId: String,
    @ColumnInfo(name = "time_limit_ms") val timeLimitMs: Int,
    @ColumnInfo(name = "mem_limit_mb") val memLimitMb: Int,
    @ColumnInfo(name = "last_verdict") val lastVerdict: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "folder_doc_id") val folderDocId: String?,
    @ColumnInfo(name = "issues") val issues: String,
    @ColumnInfo(name = "origin_uri") val originUri: String?,
    @ColumnInfo(name = "file_names") val fileNames: String?,
    @ColumnInfo(name = "test_count") val testCount: Int,
)

data class VerdictCount(val verdict: String?, val count: Int)
