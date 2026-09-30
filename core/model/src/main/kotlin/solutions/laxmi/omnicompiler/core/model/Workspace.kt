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
)

/**
 * A source or data file in a project's flat `/workspace`. Exactly one file per project is the entry;
 * it is sent as `code` and written under the runtime's own filename, the rest go in `files`.
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

data class ProjectWorkspace(
    val project: Project,
    val files: List<SourceFile>,
    val tests: List<TestCase>,
) {
    val entry: SourceFile? get() = files.firstOrNull { it.isEntry }
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
