package solutions.laxmi.omnicompiler.core.data.git

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
}

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
        val candidates = entries.filter { !it.isFolder && (it.size ?: 0) <= ProjectSync.MAX_FILE_BYTES }.take(ProjectSync.MAX_FILES)
        val fetched = fetchTexts(repo, token, candidates).valueOr { return@withToken it }
        val name = path.substringAfterLast('/').ifEmpty { repo.fullName.substringAfterLast('/') }
        val remote = ProjectRemote(repo.host, repo.id, repo.fullName, branch, path, head, fetched.mapValues { (fileName, _) -> candidates.first { it.name == fileName }.sha })
        projects.importRemote(name, fetched, remote)
    }

    override fun observeStatus(projectId: String): Flow<SourceStatus?> = projects.observeWorkspace(projectId)
        .map { workspace -> workspace?.let(::statusOf) }
        .flowOn(cpu)

    override suspend fun commitAndPush(projectId: String, message: String): Outcome<Unit> {
        val status = currentStatus(projectId) ?: return notFound()
        val remote = status.remote
        if (remote.conflicts.isNotEmpty()) return Outcome.Failure(AppError.Conflict(reason = ErrorReason.GitResolveConflictsFirst))
        if (status.changes.isEmpty()) return Outcome.Failure(AppError.Validation(reason = ErrorReason.GitNothingToCommit))
        return withToken(remote.host) { token ->
            val head = network.head(remote.host, token, remote.repoId, remote.branch).valueOr { return@withToken it }
            if (head != remote.baseCommit) return@withToken Outcome.Failure(AppError.Conflict(reason = ErrorReason.GitPullFirst))
            val workspace = projects.observeWorkspace(projectId).first() ?: return@withToken notFound()
            val contents = workspace.files.associate { it.name to it.content }
            val changes = status.changes.map { change ->
                CommitChange(remotePath(remote, change.name), contents[change.name].takeIf { change.kind != FileChange.Kind.Deleted }, existed = change.name in remote.baseBlobs)
            }
            val commit = network.commit(remote.host, token, remote.repoId, remote.branch, remote.baseCommit, message.trim().ifEmpty { DEFAULT_MESSAGE }, changes)
                .valueOr { return@withToken it }
            projects.setRemote(projectId, remote.copy(baseCommit = commit, baseBlobs = contents.mapValues { (_, content) -> blobId(content) }))
        }
    }

    override suspend fun pull(projectId: String): Outcome<PullResult> {
        val workspace = projects.observeWorkspace(projectId).first() ?: return notFound()
        val remote = workspace.project.remote ?: return notFound()
        return withToken(remote.host) { token ->
            val head = network.head(remote.host, token, remote.repoId, remote.branch).valueOr { return@withToken it }
            if (head == remote.baseCommit) return@withToken Outcome.Success(PullResult(0, remote.conflicts))
            val theirs = network.list(remote.host, token, remote.repoId, head, remote.path).valueOr { return@withToken it }
                .filter { !it.isFolder }.associateBy { it.name }
            val mine = workspace.files.associate { it.name to blobId(it.content) }
            val base = remote.baseBlobs
            val apply = mutableMapOf<String, RemoteEntry?>()
            val conflicts = remote.conflicts.toMutableSet()
            val newBase = base.toMutableMap()
            (theirs.keys + base.keys).forEach { name ->
                val theirSha = theirs[name]?.sha
                val baseSha = base[name]
                val mySha = mine[name]
                when {
                    theirSha == baseSha -> Unit // unchanged upstream
                    mySha == baseSha -> { apply[name] = theirs[name]; newBase.setOrRemove(name, theirSha) }
                    mySha == theirSha -> newBase.setOrRemove(name, theirSha) // same edit on both sides
                    else -> conflicts += name // keep the local file; the old base keeps it marked as changed
                }
            }
            val fetched = fetchTexts(remote, token, apply.values.filterNotNull()).valueOr { return@withToken it }
            val changes = apply.mapValues { (name, entry) -> entry?.let { fetched[name] } }
            val updated = remote.copy(baseCommit = head, baseBlobs = newBase, conflicts = conflicts)
            when (val result = projects.applyRemote(projectId, changes, updated)) {
                is Outcome.Failure -> result
                is Outcome.Success -> Outcome.Success(PullResult(changes.size, conflicts))
            }
        }
    }

    override suspend fun resolve(projectId: String, fileName: String, keepMine: Boolean): Outcome<Unit> {
        val workspace = projects.observeWorkspace(projectId).first() ?: return notFound()
        val remote = workspace.project.remote ?: return notFound()
        return withToken(remote.host) { token ->
            val theirs = network.list(remote.host, token, remote.repoId, remote.baseCommit, remote.path).valueOr { return@withToken it }
                .firstOrNull { !it.isFolder && it.name == fileName }
            val newBase = remote.baseBlobs.toMutableMap().apply { setOrRemove(fileName, theirs?.sha) }
            val resolved = remote.copy(baseBlobs = newBase, conflicts = remote.conflicts - fileName)
            if (keepMine) {
                projects.setRemote(projectId, resolved)
            } else {
                val content = theirs?.let { entry -> fetchTexts(remote, token, listOf(entry)).valueOr { return@withToken it }[fileName] }
                projects.applyRemote(projectId, mapOf(fileName to content), resolved)
            }
        }
    }

    private suspend fun currentStatus(projectId: String): SourceStatus? = projects.observeWorkspace(projectId).first()?.let(::statusOf)

    private fun statusOf(workspace: ProjectWorkspace): SourceStatus? {
        val remote = workspace.project.remote ?: return null
        val local = workspace.files.associate { it.name to blobId(it.content) }
        val changes = buildList {
            local.forEach { (name, sha) ->
                when (remote.baseBlobs[name]) {
                    null -> add(FileChange(name, FileChange.Kind.Added))
                    sha -> Unit
                    else -> add(FileChange(name, FileChange.Kind.Modified))
                }
            }
            remote.baseBlobs.keys.filter { it !in local }.forEach { add(FileChange(it, FileChange.Kind.Deleted)) }
        }.sortedBy { it.name.lowercase() }
        return SourceStatus(remote, changes)
    }

    private suspend fun fetchTexts(repo: RemoteRepo, token: String, entries: List<RemoteEntry>) =
        fetchTexts(repo.host, repo.id, token, entries)

    private suspend fun fetchTexts(remote: ProjectRemote, token: String, entries: List<RemoteEntry>) =
        fetchTexts(remote.host, remote.repoId, token, entries)

    /** Downloads blobs in parallel; binary files (NUL bytes) are skipped rather than corrupted. */
    private suspend fun fetchTexts(host: GitHost, repoId: String, token: String, entries: List<RemoteEntry>): Outcome<Map<String, String>> = coroutineScope {
        val results = entries.map { entry -> async { entry.name to network.blobText(host, token, repoId, entry.sha) } }.awaitAll()
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
