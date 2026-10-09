package solutions.laxmi.omnicompiler.core.storage

import solutions.laxmi.omnicompiler.core.model.ProjectIssue
import solutions.laxmi.omnicompiler.core.model.ProjectPaths

/** A file somewhere inside a project folder: its path relative to that folder (`src/util/helper.py`) and its document. */
data class FolderFile(val path: String, val doc: DocEntry)

/** One project folder as found on disk, before any checks. */
data class ScannedFolder(
    val folder: DocEntry,
    /** Raw manifest text; null when `.omni/project.json` doesn't exist (or wasn't read, see [ProjectFolderStore.scan]). */
    val manifestText: String?,
    /** Every file in the folder and its subfolders, except the app's `.omni` and version control's `.git` folders. */
    val files: List<FolderFile>,
    /** The manifest document as found; null when there is none. */
    val manifest: DocEntry? = null,
    /** Folders nested so deep that files in them would pass the judge's depth limit; not descended into. */
    val tooDeep: List<String> = emptyList(),
)

/** The outcome of checking one folder: a manifest that is valid to use, the files to index, and what was wrong. */
data class ValidatedProject(
    val folder: DocEntry,
    val manifest: ProjectManifest,
    val sourceFiles: List<FolderFile>,
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
    /** [maxFiles] counts every file, the entry included; [maxTotalBytes] is what one run may send altogether. */
    data class Limits(val maxFileBytes: Long, val maxFiles: Int, val maxTotalBytes: Long, val defaultTimeMs: Int, val defaultMemMb: Int)

    /** A runtime chosen for an imported folder, and its entry file name (e.g. `solution.py`, always at the root). */
    data class InferredRuntime(val runtimeId: String, val entryName: String)

    /**
     * Checks [folder]. [takenIds] are ids already claimed by folders checked earlier in the same scan; the
     * caller adds the result's id so later duplicates get a new one. [known] is the index's last manifest for this
     * folder: when the folder's own manifest is missing or unreadable it is restored from it, so the project keeps
     * its id, tests and limits instead of coming back as a new import.
     */
    fun validate(folder: ScannedFolder, takenIds: Set<String>, known: ProjectManifest? = null): ValidatedProject? {
        val issues = mutableListOf<ProjectIssue>()
        val files = acceptedFiles(folder, issues)
        // The judge writes the entry at the workspace root, so only a root file can be it. Build scripts go last so a
        // Java folder with a compile.sh is still guessed as Java; a folder of nothing but scripts can still run one.
        val rootFiles = files.filter { ProjectPaths.isRoot(it.path) }.sortedBy { it.path in ProjectPaths.BUILD_SCRIPTS }
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
                val inferred = inferRuntime(rootFiles.map { it.path })
                ProjectManifest(
                    id = newId(),
                    name = folder.folder.name,
                    runtimeId = inferred?.runtimeId.orEmpty(),
                    entry = inferred?.entryName?.takeIf { name -> rootFiles.any { it.path == name } } ?: rootFiles.firstOrNull()?.path.orEmpty(),
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
        if (rootFiles.none { it.path == manifest.entry }) {
            val standIn = rootFiles.firstOrNull()
            if (standIn == null) {
                issues += ProjectIssue.EntryMissing
            } else {
                manifest = manifest.copy(entry = standIn.path)
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

    /**
     * The files the project opens and runs, shallowest first so a cap keeps the root files. Each skipped file is
     * named with its reason; it stays in the folder untouched.
     */
    private fun acceptedFiles(folder: ScannedFolder, issues: MutableList<ProjectIssue>): List<FolderFile> {
        folder.tooDeep.forEach { issues += ProjectIssue.FileSkipped(it, ProjectIssue.SkipReason.FOLDER) }
        val accepted = mutableListOf<FolderFile>()
        var totalBytes = 0L
        folder.files.sortedWith(compareBy<FolderFile> { it.path.count { c -> c == '/' } }.thenBy { it.path.lowercase() }).forEach { file ->
            val reason = when {
                ProjectPaths.problem(file.path) != null -> ProjectIssue.SkipReason.BAD_NAME
                file.doc.size > limits.maxFileBytes -> ProjectIssue.SkipReason.TOO_LARGE
                isBinaryMime(file.doc.mimeType) -> ProjectIssue.SkipReason.BINARY
                accepted.size >= limits.maxFiles -> ProjectIssue.SkipReason.TOO_MANY
                totalBytes + file.doc.size > limits.maxTotalBytes -> ProjectIssue.SkipReason.TOO_MUCH_DATA
                else -> null
            }
            when (reason) {
                null -> {
                    accepted += file
                    totalBytes += file.doc.size
                }
                ProjectIssue.SkipReason.TOO_MANY -> {
                    if (issues.none { it is ProjectIssue.TooManyFiles }) issues += ProjectIssue.TooManyFiles(limits.maxFiles)
                    // Named too, so source control can tell such a file is still in the folder rather than deleted.
                    issues += ProjectIssue.FileSkipped(file.path, reason)
                }
                else -> issues += ProjectIssue.FileSkipped(file.path, reason)
            }
        }
        return accepted
    }

    private fun isBinaryMime(mime: String) =
        BINARY_MIME_PREFIXES.any { mime.startsWith(it) } || mime in BINARY_MIME_TYPES

    private companion object {
        val BINARY_MIME_PREFIXES = listOf("image/", "audio/", "video/", "font/")
        val BINARY_MIME_TYPES = setOf("application/zip", "application/pdf", "application/x-executable", "application/vnd.android.package-archive")
    }
}
