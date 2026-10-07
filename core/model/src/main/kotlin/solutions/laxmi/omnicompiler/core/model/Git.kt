package solutions.laxmi.omnicompiler.core.model

/** Code hosts reached through their web APIs with a personal access token. */
enum class GitHost { GITHUB, GITLAB }

/** A connected account: the host and the login its token belongs to. */
data class GitAccount(val host: GitHost, val login: String)

data class RemoteRepo(
    val host: GitHost,
    /** GitHub `owner/name`; GitLab numeric project id (as text). */
    val id: String,
    val fullName: String,
    val defaultBranch: String,
    val isPrivate: Boolean,
)

/** One entry of a repository folder listing; [sha] is the git blob or tree id. */
data class RemoteEntry(val name: String, val path: String, val isFolder: Boolean, val sha: String, val size: Long?)

/**
 * Where a project came from and what it looked like there at the last pull or push. A project maps to one
 * repository folder; its files, subfolders included, are tracked by their path relative to that folder.
 */
data class ProjectRemote(
    val host: GitHost,
    val repoId: String,
    val repoName: String,
    val branch: String,
    /** Folder inside the repository ("" for the root). */
    val path: String,
    val baseCommit: String,
    /** File path (relative to [path]) → git blob id at [baseCommit]. */
    val baseBlobs: Map<String, String>,
    /** Files changed both here and on the remote since [baseCommit]; they keep the local version until resolved. */
    val conflicts: Set<String> = emptySet(),
)

data class FileChange(val name: String, val kind: Kind) {
    enum class Kind { Added, Modified, Deleted }
}

/** Local changes against the base, plus unresolved conflicts from the last pull. */
data class SourceStatus(val remote: ProjectRemote, val changes: List<FileChange>)

data class PullResult(val updated: Int, val conflicts: Set<String>)
