package solutions.laxmi.omnicompiler.core.data.git

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import solutions.laxmi.omnicompiler.core.common.Dispatcher
import solutions.laxmi.omnicompiler.core.common.OmniDispatcher
import solutions.laxmi.omnicompiler.core.data.project.ProjectRepository
import solutions.laxmi.omnicompiler.core.data.project.ProjectSync
import solutions.laxmi.omnicompiler.core.datastore.GitCredentialStore
import solutions.laxmi.omnicompiler.core.datastore.StoredGitToken
import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.ErrorReason
import solutions.laxmi.omnicompiler.core.model.FileChange
import solutions.laxmi.omnicompiler.core.model.GitAccount
import solutions.laxmi.omnicompiler.core.model.GitHost
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.ProjectIssue
import solutions.laxmi.omnicompiler.core.model.ProjectRemote
import solutions.laxmi.omnicompiler.core.model.ProjectWorkspace
import solutions.laxmi.omnicompiler.core.model.PullResult
import solutions.laxmi.omnicompiler.core.model.RemoteEntry
import solutions.laxmi.omnicompiler.core.model.RemoteRepo
import solutions.laxmi.omnicompiler.core.model.SourceStatus
import solutions.laxmi.omnicompiler.core.network.source.CommitChange
import solutions.laxmi.omnicompiler.core.network.source.GitNetworkDataSource
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GitHub/GitLab basics over their web APIs: connect with a token, import a repository folder as a project,
 * see what changed, commit & push, and pull with per-file conflict resolution. A project tracks one folder
 * (flat, like the judge's workspace); changes are found by comparing git blob ids, as git itself does.
 */
interface GitRepository {
    val accounts: Flow<List<GitAccount>>
    suspend fun connect(host: GitHost, token: String): Outcome<GitAccount>
    suspend fun disconnect(host: GitHost)

    suspend fun repos(host: GitHost, page: Int): Outcome<List<RemoteRepo>>
    suspend fun branches(repo: RemoteRepo): Outcome<List<String>>
    suspend fun list(repo: RemoteRepo, branch: String, path: String): Outcome<List<RemoteEntry>>
    suspend fun importFolder(repo: RemoteRepo, branch: String, path: String): Outcome<String>

    fun observeStatus(projectId: String): Flow<SourceStatus?>
    suspend fun commitAndPush(projectId: String, message: String): Outcome<Unit>
    suspend fun pull(projectId: String): Outcome<PullResult>

    /** Settles a conflict: keep the local version (it will overwrite the remote on push) or take the remote one. */
    suspend fun resolve(projectId: String, fileName: String, keepMine: Boolean): Outcome<Unit>

    /** A file's local text and its text at the tracked commit (null where it doesn't exist), to review before push or resolve. */
    suspend fun compare(projectId: String, fileName: String): Outcome<FileComparison>
}

data class FileComparison(val local: String?, val remote: String?)

@Singleton
internal class DefaultGitRepository @Inject constructor(
    private val network: GitNetworkDataSource,
    private val credentials: GitCredentialStore,
    private val projects: ProjectRepository,
    @Dispatcher(OmniDispatcher.Default) private val cpu: CoroutineDispatcher,
) : GitRepository {

    override val accounts: Flow<List<GitAccount>> = credentials.credentials.map { stored ->
        GitHost.entries.mapNotNull { host -> stored[host.name]?.let { GitAccount(host, it.login) } }
    }

    override suspend fun connect(host: GitHost, token: String): Outcome<GitAccount> {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return Outcome.Failure(AppError.Validation(reason = ErrorReason.GitTokenRejected))
        return when (val login = network.login(host, trimmed)) {
            is Outcome.Failure -> login
            is Outcome.Success -> {
                credentials.save(host.name, StoredGitToken(trimmed, login.value))
                Outcome.Success(GitAccount(host, login.value))
            }
        }
    }

    override suspend fun disconnect(host: GitHost) = credentials.remove(host.name)

    override suspend fun repos(host: GitHost, page: Int) = withToken(host) { network.repos(host, it, page) }

    override suspend fun branches(repo: RemoteRepo) = withToken(repo.host) { network.branches(repo.host, it, repo.id) }

    override suspend fun list(repo: RemoteRepo, branch: String, path: String) = withToken(repo.host) { network.list(repo.host, it, repo.id, branch, path) }

    override suspend fun importFolder(repo: RemoteRepo, branch: String, path: String): Outcome<String> = withToken(repo.host) { token ->
        val head = network.head(repo.host, token, repo.id, branch).valueOr { return@withToken it }
        val entries = network.list(repo.host, token, repo.id, head, path).valueOr { return@withToken it }
        // Only the folder's own files, as the judge runs a flat workspace; very large files are left out.
        val candidates = entries.filter { it.isTrackable() && (it.size ?: 0) <= ProjectSync.MAX_FILE_BYTES }.take(ProjectSync.MAX_FILES)
        val fetched = fetchTexts(repo, token, candidates).valueOr { return@withToken it }
        val name = path.substringAfterLast('/').ifEmpty { repo.fullName.substringAfterLast('/') }
        val remote = ProjectRemote(repo.host, repo.id, repo.fullName, branch, path, head, fetched.mapValues { (fileName, _) -> candidates.first { it.name == fileName }.sha })
        when (val created = projects.importRemote(name, fetched, remote)) {
            is Outcome.Failure -> created
            is Outcome.Success -> {
                untrackRefused(created.value, attempted = fetched.keys)
                created
            }
        }
    }

    // Status hashes every file, so it is recomputed from a fresh snapshot whenever the outline changes (each save).
    override fun observeStatus(projectId: String): Flow<SourceStatus?> = projects.observeOutline(projectId)
        .mapLatest { projects.snapshot(projectId)?.let(::statusOf) }
        .distinctUntilChanged()
        .flowOn(cpu)

    override suspend fun commitAndPush(projectId: String, message: String): Outcome<Unit> {
        // Status and pushed contents come from one snapshot, so a file removed meanwhile can't turn into a deletion.
        val workspace = projects.snapshot(projectId) ?: return notFound()
        val status = statusOf(workspace) ?: return notFound()
        val remote = status.remote
        if (remote.conflicts.isNotEmpty()) return Outcome.Failure(AppError.Conflict(reason = ErrorReason.GitResolveConflictsFirst))
        if (status.changes.isEmpty()) return Outcome.Failure(AppError.Validation(reason = ErrorReason.GitNothingToCommit))
        return withToken(remote.host) { token ->
            val head = network.head(remote.host, token, remote.repoId, remote.branch).valueOr { return@withToken it }
            if (head != remote.baseCommit) return@withToken Outcome.Failure(AppError.Conflict(reason = ErrorReason.GitPullFirst))
            // Last line of defence: a file still in the project folder is never deleted upstream, whatever the index
            // says (e.g. a file the app stopped opening). Without a readable folder, no deletion is pushed at all.
            val deletions = status.changes.filter { it.kind == FileChange.Kind.Deleted }.map { it.name }
            val stillInFolder = if (deletions.isEmpty()) emptySet() else {
                projects.folderFileNames(projectId) ?: return@withToken Outcome.Failure(AppError.Unknown(reason = ErrorReason.ProjectsFolderUnavailable))
            }
            val pushed = status.changes.filterNot { it.kind == FileChange.Kind.Deleted && it.name in stillInFolder }
            if (pushed.isEmpty()) return@withToken Outcome.Failure(AppError.Validation(reason = ErrorReason.GitNothingToCommit))
            val contents = workspace.files.associate { it.name to it.content }
            val changes = pushed.map { change ->
                val content = if (change.kind == FileChange.Kind.Deleted) null else contents.getValue(change.name)
                CommitChange(remotePath(remote, change.name), content, existed = change.name in remote.baseBlobs)
            }
            val commit = network.commit(remote.host, token, remote.repoId, remote.branch, remote.baseCommit, message.trim().ifEmpty { DEFAULT_MESSAGE }, changes)
                .valueOr { return@withToken it }
            // Only what was pushed moves the base; a file edited while the push was in flight stays "modified".
            val newBase = remote.trackedBase().toMutableMap()
            pushed.forEach { change -> newBase.setOrRemove(change.name, contents[change.name]?.takeIf { change.kind != FileChange.Kind.Deleted }?.let(::blobId)) }
            projects.setRemote(projectId, remote.copy(baseCommit = commit, baseBlobs = newBase))
        }
    }

    override suspend fun pull(projectId: String): Outcome<PullResult> {
        val workspace = projects.snapshot(projectId) ?: return notFound()
        val remote = workspace.project.remote ?: return notFound()
        return withToken(remote.host) { token ->
            val head = network.head(remote.host, token, remote.repoId, remote.branch).valueOr { return@withToken it }
            if (head == remote.baseCommit) return@withToken Outcome.Success(PullResult(0, remote.conflicts))
            val listing = network.list(remote.host, token, remote.repoId, head, remote.path).valueOr { return@withToken it }
            val theirs = listing.filter { it.isTrackable() }.associateBy { it.name }
            val base = remote.trackedBase()
            // An empty listing for a folder that had files means it was removed or moved upstream; deleting every
            // local file on that basis is never right, so the pull stops instead.
            if (listing.isEmpty() && base.isNotEmpty()) return@withToken Outcome.Failure(AppError.NotFound(reason = ErrorReason.GitFolderMissing))
            val localText = workspace.files.associate { it.name to it.content }
            val mine = localText.mapValues { (_, text) -> blobId(text) }
            val apply = mutableMapOf<String, RemoteEntry?>()
            val conflicts = remote.conflicts.toMutableSet()
            val newBase = base.toMutableMap()
            (theirs.keys + base.keys).forEach { name ->
                val theirSha = theirs[name]?.sha
                val baseSha = base[name]
                val mySha = mine[name]
                when {
                    theirSha == baseSha -> Unit // unchanged upstream
                    mySha == baseSha -> apply[name] = theirs[name] // unchanged here: take theirs (or their deletion)
                    mySha == theirSha -> newBase.setOrRemove(name, theirSha) // same edit on both sides
                    else -> conflicts += name // keep the local file; the old base keeps it marked as changed
                }
            }
            val fetched = fetchTexts(remote, token, apply.values.filterNotNull()).valueOr { return@withToken it }
            // A remote file whose text couldn't be fetched (binary content) is left alone: neither written nor tracked
            // as changed, so the local file and its base stay as they were.
            val changes = apply.filter { (name, entry) -> entry == null || name in fetched }.mapValues { (name, entry) -> entry?.let { fetched.getValue(name) } }
            changes.keys.forEach { name -> newBase.setOrRemove(name, theirs[name]?.sha) }
            val updated = remote.copy(baseCommit = head, baseBlobs = newBase, conflicts = conflicts)
            val expected = changes.keys.associateWith { localText[it] }
            projects.applyRemote(projectId, changes, updated, expected).valueOr { return@withToken it }
            untrackRefused(projectId, attempted = changes.filterValues { it != null }.keys)
            Outcome.Success(PullResult(changes.size, conflicts))
        }
    }

    override suspend fun resolve(projectId: String, fileName: String, keepMine: Boolean): Outcome<Unit> {
        val workspace = projects.snapshot(projectId) ?: return notFound()
        val remote = workspace.project.remote ?: return notFound()
        return withToken(remote.host) { token ->
            val theirs = network.list(remote.host, token, remote.repoId, remote.baseCommit, remote.path).valueOr { return@withToken it }
                .firstOrNull { it.isTrackable() && it.name == fileName }
            val newBase = remote.trackedBase().toMutableMap().apply { setOrRemove(fileName, theirs?.sha) }
            val resolved = remote.copy(baseBlobs = newBase, conflicts = remote.conflicts - fileName)
            if (keepMine) return@withToken projects.setRemote(projectId, resolved)
            // Taking theirs must never turn into deleting the local file because their text couldn't be fetched.
            val content = theirs?.let { entry ->
                fetchTexts(remote, token, listOf(entry)).valueOr { return@withToken it }[fileName]
                    ?: return@withToken Outcome.Failure(AppError.Validation(reason = ErrorReason.GitFileNotText))
            }
            val local = workspace.files.firstOrNull { it.name == fileName }?.content
            projects.applyRemote(projectId, mapOf(fileName to content), resolved, mapOf(fileName to local)).valueOr { return@withToken it }
            if (content != null) untrackRefused(projectId, attempted = setOf(fileName))
            Outcome.Success(Unit)
        }
    }

    override suspend fun compare(projectId: String, fileName: String): Outcome<FileComparison> {
        val workspace = projects.snapshot(projectId) ?: return notFound()
        val remote = workspace.project.remote ?: return notFound()
        val local = workspace.files.firstOrNull { it.name == fileName }?.content
        val sha = remote.baseBlobs[fileName] ?: return Outcome.Success(FileComparison(local, remote = null))
        return withToken(remote.host) { token ->
            when (val text = network.blobText(remote.host, token, remote.repoId, sha)) {
                is Outcome.Success -> Outcome.Success(FileComparison(local, text.value))
                is Outcome.Failure -> text
            }
        }
    }

    /**
     * Files written from the remote that the project then refused (too large, past the file limit, a name it can't
     * hold) stay in the folder but not in the project. They're dropped from the tracked set: tracked but absent, they
     * would read as deleted and a push would delete them upstream.
     */
    private suspend fun untrackRefused(projectId: String, attempted: Set<String>) {
        if (attempted.isEmpty()) return
        val workspace = projects.snapshot(projectId) ?: return
        val remote = workspace.project.remote ?: return
        val present = workspace.files.mapTo(HashSet()) { it.name }
        val refused = attempted.filter { it !in present && it in remote.baseBlobs }.toSet()
        if (refused.isNotEmpty()) projects.setRemote(projectId, remote.copy(baseBlobs = remote.baseBlobs - refused))
    }

    private fun statusOf(workspace: ProjectWorkspace): SourceStatus? {
        val remote = workspace.project.remote ?: return null
        val local = workspace.files.associate { it.name to blobId(it.content) }
        // Files still in the folder but not opened by the project (too large, binary, past the limit) aren't deleted.
        val skipped = workspace.project.issues.filterIsInstance<ProjectIssue.FileSkipped>().mapTo(HashSet()) { it.name }
        val base = remote.trackedBase()
        val changes = buildList {
            local.forEach { (name, sha) ->
                when (base[name]) {
                    null -> add(FileChange(name, FileChange.Kind.Added))
                    sha -> Unit
                    else -> add(FileChange(name, FileChange.Kind.Modified))
                }
            }
            base.keys.filter { it !in local && it !in skipped }.forEach { add(FileChange(it, FileChange.Kind.Deleted)) }
        }.sortedBy { it.name.lowercase() }
        return SourceStatus(remote, changes)
    }

    private suspend fun fetchTexts(repo: RemoteRepo, token: String, entries: List<RemoteEntry>) =
        fetchTexts(repo.host, repo.id, token, entries)

    private suspend fun fetchTexts(remote: ProjectRemote, token: String, entries: List<RemoteEntry>) =
        fetchTexts(remote.host, remote.repoId, token, entries)

    /**
     * Downloads blobs a few at a time; files known to be binary by name aren't fetched at all, and any other binary
     * file (NUL bytes) is skipped rather than corrupted.
     */
    private suspend fun fetchTexts(host: GitHost, repoId: String, token: String, entries: List<RemoteEntry>): Outcome<Map<String, String>> = coroutineScope {
        val downloads = Semaphore(MAX_PARALLEL_DOWNLOADS)
        val results = entries.filter { !isBinaryName(it.name) }
            .map { entry -> async { entry.name to downloads.withPermit { network.blobText(host, token, repoId, entry.sha) } } }
            .awaitAll()
        results.firstOrNull { it.second is Outcome.Failure }?.let { return@coroutineScope it.second as Outcome.Failure }
        Outcome.Success(results.mapNotNull { (name, result) -> (result as Outcome.Success).value.takeIf { '\u0000' !in it }?.let { name to it } }.toMap())
    }

    private suspend fun <T> withToken(host: GitHost, block: suspend (String) -> Outcome<T>): Outcome<T> {
        val token = credentials.token(host.name)?.token ?: return Outcome.Failure(AppError.Unauthorized(reason = ErrorReason.GitNotConnected))
        return block(token)
    }

    private fun <T> notFound(): Outcome<T> = Outcome.Failure(AppError.NotFound(reason = ErrorReason.ProjectNotFound))

    private companion object {
        const val DEFAULT_MESSAGE = "Update from Omni Compiler"
        const val MAX_PARALLEL_DOWNLOADS = 4

        /** Extensions that are never editable source; checked before downloading so large assets aren't fetched. */
        val BINARY_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "pdf", "zip", "gz", "tgz", "bz2", "xz", "7z", "rar", "jar",
            "war", "class", "so", "dll", "dylib", "exe", "bin", "o", "a", "lib", "apk", "aab", "mp3", "mp4", "mov", "wav",
            "ogg", "ttf", "otf", "woff", "woff2", "psd", "sqlite", "db",
        )

        fun isBinaryName(name: String) = name.substringAfterLast('.', "").lowercase() in BINARY_EXTENSIONS

        /**
         * Whether a remote file can be part of a project at all: not a folder, not hidden (dot files such as
         * `.gitignore` are ignored by projects), not binary by name, and a name the project folder accepts. Others
         * are never tracked, so they can never be pushed as deletions.
         */
        fun isTrackableName(name: String) =
            !name.startsWith('.') && !isBinaryName(name) && name.isNotBlank() && name.length <= ProjectSync.MAX_FILE_NAME

        fun RemoteEntry.isTrackable() = !isFolder && isTrackableName(name)

        /** The tracked files, without entries older versions recorded for files a project can't hold. */
        fun ProjectRemote.trackedBase(): Map<String, String> = baseBlobs.filterKeys(::isTrackableName)

        fun remotePath(remote: ProjectRemote, name: String) = if (remote.path.isEmpty()) name else "${remote.path.trimEnd('/')}/$name"

        /** The id git gives a file's content: SHA-1 of `blob <size>\0<bytes>`. */
        fun blobId(content: String): String {
            val bytes = content.encodeToByteArray()
            val digest = MessageDigest.getInstance("SHA-1")
            digest.update("blob ${bytes.size}\u0000".encodeToByteArray())
            digest.update(bytes)
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun MutableMap<String, String>.setOrRemove(key: String, value: String?) {
            if (value == null) remove(key) else put(key, value)
        }

        inline fun <T> Outcome<T>.valueOr(onFailure: (Outcome.Failure) -> Nothing): T = when (this) {
            is Outcome.Success -> value
            is Outcome.Failure -> onFailure(this)
        }
    }
}
