package solutions.laxmi.omnicompiler.core.network.source

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import solutions.laxmi.omnicompiler.core.model.AppError
import solutions.laxmi.omnicompiler.core.model.ErrorReason
import solutions.laxmi.omnicompiler.core.model.GitHost
import solutions.laxmi.omnicompiler.core.model.Outcome
import solutions.laxmi.omnicompiler.core.model.RemoteEntry
import solutions.laxmi.omnicompiler.core.model.RemoteRepo
import solutions.laxmi.omnicompiler.core.network.api.GitHubApi
import solutions.laxmi.omnicompiler.core.network.api.GitLabApi
import solutions.laxmi.omnicompiler.core.network.dto.GhCreateBlobDto
import solutions.laxmi.omnicompiler.core.network.dto.GhCreateCommitDto
import solutions.laxmi.omnicompiler.core.network.dto.GhCreateTreeDto
import solutions.laxmi.omnicompiler.core.network.dto.GhTreeEntryDto
import solutions.laxmi.omnicompiler.core.network.dto.GhUpdateRefDto
import solutions.laxmi.omnicompiler.core.network.dto.GlCommitActionDto
import solutions.laxmi.omnicompiler.core.network.dto.GlCreateCommitDto
import java.io.IOException
import javax.inject.Inject

/** One file change in a commit: new [content], or null to delete the file. */
data class CommitChange(val path: String, val content: String?, val existed: Boolean)

/** GitHub and GitLab behind one interface; every call takes the account's token. */
interface GitNetworkDataSource {
    suspend fun login(host: GitHost, token: String): Outcome<String>
    suspend fun repos(host: GitHost, token: String, page: Int): Outcome<List<RemoteRepo>>
    suspend fun branches(host: GitHost, token: String, repoId: String): Outcome<List<String>>
    suspend fun head(host: GitHost, token: String, repoId: String, branch: String): Outcome<String>
    suspend fun list(host: GitHost, token: String, repoId: String, ref: String, path: String): Outcome<List<RemoteEntry>>
    suspend fun blobText(host: GitHost, token: String, repoId: String, sha: String): Outcome<String>

    /** Commits [changes] on top of [baseCommit] and moves [branch] to it only if it still points at the base. */
    suspend fun commit(host: GitHost, token: String, repoId: String, branch: String, baseCommit: String, message: String, changes: List<CommitChange>): Outcome<String>
}

internal class RetrofitGitNetworkDataSource @Inject constructor(
    private val github: GitHubApi,
    private val gitlab: GitLabApi,
) : GitNetworkDataSource {

    override suspend fun login(host: GitHost, token: String) = call {
        when (host) {
            GitHost.GITHUB -> github.user(bearer(token)).login
            GitHost.GITLAB -> gitlab.user(token).username
        }
    }

    override suspend fun repos(host: GitHost, token: String, page: Int) = call {
        when (host) {
            GitHost.GITHUB -> github.repos(bearer(token), page).map { RemoteRepo(host, it.fullName, it.fullName, it.defaultBranch ?: "main", it.private) }
            GitHost.GITLAB -> gitlab.projects(token, page).map {
                RemoteRepo(host, it.id.toString(), it.pathWithNamespace, it.defaultBranch ?: "main", it.visibility != "public")
            }
        }
    }

    override suspend fun branches(host: GitHost, token: String, repoId: String) = call {
        when (host) {
            GitHost.GITHUB -> github.branches(bearer(token), repoId).map { it.name }
            GitHost.GITLAB -> gitlab.branches(token, repoId).map { it.name }
        }
    }

    override suspend fun head(host: GitHost, token: String, repoId: String, branch: String) = call {
        when (host) {
            GitHost.GITHUB -> github.branch(bearer(token), repoId, segments(branch)).commit.sha
            GitHost.GITLAB -> gitlab.branch(token, repoId, branch).commit.id
        }
    }

    override suspend fun list(host: GitHost, token: String, repoId: String, ref: String, path: String) = call {
        when (host) {
            GitHost.GITHUB -> github.contents(bearer(token), repoId, segments(path), ref).map {
                RemoteEntry(it.name, it.path, it.type == "dir", it.sha, it.size)
            }
            GitHost.GITLAB -> gitlab.tree(token, repoId, path, ref).map { RemoteEntry(it.name, it.path, it.type == "tree", it.id, null) }
        }.sortedWith(compareByDescending<RemoteEntry> { it.isFolder }.thenBy { it.name.lowercase() })
    }

    override suspend fun blobText(host: GitHost, token: String, repoId: String, sha: String) = call {
        when (host) {
            GitHost.GITHUB -> github.blob(bearer(token), repoId, sha).let { blob ->
                if (blob.encoding == "base64") Base64.decode(blob.content, Base64.DEFAULT).decodeToString() else blob.content
            }
            GitHost.GITLAB -> gitlab.rawBlob(token, repoId, sha).use { it.string() }
        }
    }

    override suspend fun commit(
        host: GitHost,
        token: String,
        repoId: String,
        branch: String,
        baseCommit: String,
        message: String,
        changes: List<CommitChange>,
    ) = call {
        when (host) {
            GitHost.GITHUB -> {
                val auth = bearer(token)
                val baseTree = github.commit(auth, repoId, baseCommit).tree.sha
                val entries = coroutineScope {
                    changes.map { change ->
                        async {
                            GhTreeEntryDto(change.path, sha = change.content?.let { github.createBlob(auth, repoId, GhCreateBlobDto(it)).sha })
                        }
                    }.awaitAll()
                }
                val tree = github.createTree(auth, repoId, GhCreateTreeDto(baseTree, entries)).sha
                val commit = github.createCommit(auth, repoId, GhCreateCommitDto(message, tree, listOf(baseCommit))).sha
                github.updateBranch(auth, repoId, segments(branch), GhUpdateRefDto(commit)).close()
                commit
            }
            GitHost.GITLAB -> gitlab.createCommit(
                token,
                repoId,
                GlCreateCommitDto(
                    branch = branch,
                    commitMessage = message,
                    actions = changes.map { change ->
                        val action = when {
                            change.content == null -> "delete"
                            change.existed -> "update"
                            else -> "create"
                        }
                        GlCommitActionDto(action, change.path, change.content)
                    },
                ),
            ).id
        }
    }

    private fun bearer(token: String) = "Bearer $token"

    /** Encodes each path segment but keeps the slashes GitHub routes on. */
    private fun segments(path: String) = path.split('/').filter { it.isNotEmpty() }.joinToString("/") { Uri.encode(it) }

    private suspend fun <T> call(block: suspend () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (e: HttpException) {
        Outcome.Failure(
            when (e.code()) {
                401 -> AppError.Unauthorized(reason = ErrorReason.GitTokenRejected)
                // 409/422 on a fast-forward-only ref update: the branch moved underneath us.
                409, 422 -> AppError.Conflict(reason = ErrorReason.GitPullFirst)
                else -> AppError.Unknown(reason = ErrorReason.GitRequestFailed)
            },
        )
    } catch (e: IOException) {
        Outcome.Failure(AppError.Offline(reason = ErrorReason.NetworkError))
    } catch (e: SerializationException) {
        Outcome.Failure(AppError.Unknown(reason = ErrorReason.BadResponse))
    }
}
