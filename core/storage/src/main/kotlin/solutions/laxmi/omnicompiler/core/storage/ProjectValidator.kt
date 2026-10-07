package solutions.laxmi.omnicompiler.core.storage

import solutions.laxmi.omnicompiler.core.model.ProjectIssue

/** One project folder as found on disk, before any checks. */
data class ScannedFolder(
    val folder: DocEntry,
    /** Raw manifest text; null when `.omni/project.json` doesn't exist (or wasn't read, see [ProjectFolderStore.scan]). */
    val manifestText: String?,
    /** Top-level entries of the folder, excluding the hidden `.omni` metadata folder. */
    val entries: List<DocEntry>,
    /** The manifest document as found; null when there is none. */
    val manifest: DocEntry? = null,
)

/** The outcome of checking one folder: a manifest that is valid to use, the files to index, and what was wrong. */
data class ValidatedProject(
    val folder: DocEntry,
    val manifest: ProjectManifest,
    val sourceFiles: List<DocEntry>,
    val issues: List<ProjectIssue>,
    /** The manifest on disk must be (re)written: it was missing, corrupt, or had to change (new id, entry). */
    val rewriteManifest: Boolean,
    /** Keep the unreadable manifest as a backup before rewriting it. */
    val backupCorrupt: Boolean,
)

/**
 * The "proper checking" for projects loaded from disk. Pure: everything it needs is passed in, so it is
 * unit-tested without a device. It never deletes or edits source files; it only decides what to index
 * and how to repair the manifest.
 */
class ProjectValidator(
    private val limits: Limits,
    private val knownRuntime: (String) -> Boolean,
    /** Runtime for a folder without a manifest, from its source files (null when nothing matches). */
    private val inferRuntime: (List<String>) -> InferredRuntime?,
    private val newId: () -> String,
    private val now: () -> Long,
) {
    data class Limits(val maxFileBytes: Long, val maxFiles: Int, val maxFileName: Int, val defaultTimeMs: Int, val defaultMemMb: Int)

    /** A runtime chosen for an imported folder, and its entry file name (e.g. `solution.py`). */
    data class InferredRuntime(val runtimeId: String, val entryName: String)

    /**
     * Checks [folder]. [takenIds] are ids already claimed by folders checked earlier in the same scan; the
     * caller adds the result's id so later duplicates get a new one. [known] is the index's last manifest for this
     * folder: when the folder's own manifest is missing or unreadable it is restored from it, so the project keeps
     * its id, tests and limits instead of coming back as a new import.
     */
    fun validate(folder: ScannedFolder, takenIds: Set<String>, known: ProjectManifest? = null): ValidatedProject? {
        val issues = mutableListOf<ProjectIssue>()
        val files = acceptedFiles(folder.entries, issues)
        val parsed = folder.manifestText?.let(ManifestCodec::decode)
        val corrupt = folder.manifestText != null && parsed == null

        var manifest = when {
            parsed != null -> parsed
            known != null -> {
                if (corrupt) issues += ProjectIssue.ManifestRebuilt
                known
            }
            else -> {
                // No readable manifest: a folder with no source files isn't a project, just an unrelated folder.
                if (files.isEmpty()) return null
                issues += if (corrupt) ProjectIssue.ManifestRebuilt else ProjectIssue.Imported
                val inferred = inferRuntime(files.map { it.name })
                ProjectManifest(
                    id = newId(),
                    name = folder.folder.name,
                    runtimeId = inferred?.runtimeId.orEmpty(),
                    entry = inferred?.entryName?.takeIf { name -> files.any { it.name == name } } ?: files.first().name,
                    limits = ManifestLimits(limits.defaultTimeMs, limits.defaultMemMb),
                    createdAt = folder.folder.lastModified.takeIf { it > 0 } ?: now(),
                )
            }
        }
        var rewrite = parsed == null

        if (manifest.id.isBlank() || manifest.id in takenIds) {
            if (parsed != null) issues += ProjectIssue.DuplicateId
            manifest = manifest.copy(id = newId())
            rewrite = true
        }
        if (manifest.name != folder.folder.name) {
            // The folder name is what the user sees and renames in a file manager; the manifest follows it.
            manifest = manifest.copy(name = folder.folder.name)
            rewrite = true
        }
        if (manifest.runtimeId.isBlank() || !knownRuntime(manifest.runtimeId)) {
            issues += ProjectIssue.UnknownRuntime(manifest.runtimeId)
        }
        if (files.none { it.name == manifest.entry }) {
            val standIn = files.firstOrNull()
            if (standIn == null) {
                issues += ProjectIssue.EntryMissing
            } else {
                manifest = manifest.copy(entry = standIn.name)
                rewrite = true
            }
        }
        val dedupedTests = manifest.tests.distinctBy { it.id }.map { if (it.id.isBlank()) it.copy(id = newId()) else it }
        if (dedupedTests != manifest.tests) {
            manifest = manifest.copy(tests = dedupedTests)
            rewrite = true
        }
        return ValidatedProject(folder.folder, manifest, files, issues, rewrite, backupCorrupt = corrupt)
    }

    private fun acceptedFiles(entries: List<DocEntry>, issues: MutableList<ProjectIssue>): List<DocEntry> {
        val accepted = mutableListOf<DocEntry>()
        entries.filter { !it.isHidden }.sortedBy { it.name.lowercase() }.forEach { entry ->
            val reason = when {
                entry.isDirectory -> ProjectIssue.SkipReason.FOLDER
                !isValidName(entry.name) -> ProjectIssue.SkipReason.BAD_NAME
                entry.size > limits.maxFileBytes -> ProjectIssue.SkipReason.TOO_LARGE
                isBinaryMime(entry.mimeType) -> ProjectIssue.SkipReason.BINARY
                else -> null
            }
            if (reason != null) {
                issues += ProjectIssue.FileSkipped(entry.name, reason)
            } else if (accepted.size < limits.maxFiles) {
                accepted += entry
            } else if (issues.none { it is ProjectIssue.TooManyFiles }) {
                issues += ProjectIssue.TooManyFiles(limits.maxFiles)
            }
        }
        return accepted
    }

    private fun isValidName(name: String) =
        name.isNotBlank() && name.length <= limits.maxFileName && '/' !in name && '\\' !in name && '\u0000' !in name

    private fun isBinaryMime(mime: String) =
        BINARY_MIME_PREFIXES.any { mime.startsWith(it) } || mime in BINARY_MIME_TYPES

    private companion object {
        val BINARY_MIME_PREFIXES = listOf("image/", "audio/", "video/", "font/")
        val BINARY_MIME_TYPES = setOf("application/zip", "application/pdf", "application/x-executable", "application/vnd.android.package-archive")
    }
}
