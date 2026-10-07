package solutions.laxmi.omnicompiler.core.model

/** Something the checks found and either fixed or reported. Stored with the project and shown on its row. */
sealed interface ProjectIssue {
    val code: String
    val detail: String get() = ""

    /** No manifest: the folder was imported and its runtime inferred. */
    data object Imported : ProjectIssue { override val code = "imported" }

    /** The manifest couldn't be read; it was rebuilt from the files and the old one kept as `.bak`. */
    data object ManifestRebuilt : ProjectIssue { override val code = "manifest_rebuilt" }

    /** Another folder had the same id (a copied folder); this one got a new id. */
    data object DuplicateId : ProjectIssue { override val code = "duplicate_id" }

    data class UnknownRuntime(val runtimeId: String) : ProjectIssue {
        override val code = "unknown_runtime"
        override val detail = runtimeId
    }

    /** No file to run: the named entry is gone and nothing could stand in for it. */
    data object EntryMissing : ProjectIssue { override val code = "entry_missing" }

    data class FileSkipped(val name: String, val reason: SkipReason) : ProjectIssue {
        override val code = "file_skipped_${reason.name.lowercase()}"
        override val detail = name
    }

    data class TooManyFiles(val limit: Int) : ProjectIssue {
        override val code = "too_many_files"
        override val detail = limit.toString()
    }

    /** TOO_MANY: past the project's file limit (the file stays in the folder, it just isn't opened). */
    enum class SkipReason { TOO_LARGE, BINARY, BAD_NAME, FOLDER, TOO_MANY }

    companion object {
        /** Round-trip for storage in the index (one `code:detail` per line). */
        fun parse(line: String): ProjectIssue? {
            val code = line.substringBefore(':')
            val detail = line.substringAfter(':', "")
            return when (code) {
                Imported.code -> Imported
                ManifestRebuilt.code -> ManifestRebuilt
                DuplicateId.code -> DuplicateId
                EntryMissing.code -> EntryMissing
                "unknown_runtime" -> UnknownRuntime(detail)
                "too_many_files" -> detail.toIntOrNull()?.let(::TooManyFiles)
                else -> SkipReason.entries.firstOrNull { code == "file_skipped_${it.name.lowercase()}" }?.let { FileSkipped(detail, it) }
            }
        }

        fun format(issue: ProjectIssue) = if (issue.detail.isEmpty()) issue.code else "${issue.code}:${issue.detail}"
    }
}
