package solutions.laxmi.omnicompiler.core.model

import kotlin.time.Instant

data class Project(
    val id: String,
    val name: String,
    val runtimeId: String,
    val limits: Limits,
    val lastVerdict: Verdict?,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** What the last check of the project's folder found (skipped files, rebuilt manifest, …). */
    val issues: List<ProjectIssue> = emptyList(),
    /** Imported from a single file that can be written back ("Save to original"). */
    val hasOrigin: Boolean = false,
    /** Set when the project was imported from GitHub or GitLab. */
    val remote: ProjectRemote? = null,
)

/**
 * A source or data file of a project; [name] is its path in `/workspace` (`src/util/helper.py`, or `main.py` at the
 * root). Exactly one root file per project is the entry; it is sent as `code` and written under the runtime's own
 * filename, the rest go in `files` with their paths.
 */
data class SourceFile(
    val id: String,
    val projectId: String,
    val name: String,
    val content: String,
    val isEntry: Boolean,
    val position: Int,
    /** Changes when the content was replaced outside the editor; the editor reloads only then. */
    val contentVersion: Int = 0,
)

data class TestCase(
    val id: String,
    val projectId: String,
    val name: String,
    val stdin: String,
    val expected: String,
    val position: Int,
)

data class TestCaseDraft(val stdin: String, val expected: String, val name: String = "")

data class ProjectSummary(
    val project: Project,
    val fileNames: List<String>,
    val testCount: Int,
)

/** A complete snapshot, contents included: what a run, a push or an export works from. */
data class ProjectWorkspace(
    val project: Project,
    val files: List<SourceFile>,
    val tests: List<TestCase>,
) {
    val entry: SourceFile? get() = files.firstOrNull { it.isEntry }
}

/** A file of a project without its text: enough for tabs, lists and menus. */
data class FileHeader(
    val id: String,
    val projectId: String,
    val name: String,
    val isEntry: Boolean,
    val position: Int,
    /** Changes when the content was replaced outside the editor; the editor reloads only then. */
    val contentVersion: Int = 0,
)

/** The text of one open file, read again only when [contentVersion] changes. */
data class OpenFile(val id: String, val content: String, val contentVersion: Int)

/** What screens observe: the project, its files without contents, and its tests. */
data class WorkspaceOutline(
    val project: Project,
    val files: List<FileHeader>,
    val tests: List<TestCase>,
) {
    val entry: FileHeader? get() = files.firstOrNull { it.isEntry }
}

/** Filters on the projects screen. */
enum class ProjectFilter {
    ALL, MULTI_FILE, SCRATCH;

    fun matches(summary: ProjectSummary): Boolean = when (this) {
        ALL -> true
        MULTI_FILE -> summary.fileNames.size > 1
        SCRATCH -> summary.fileNames.size <= 1
    }
}
