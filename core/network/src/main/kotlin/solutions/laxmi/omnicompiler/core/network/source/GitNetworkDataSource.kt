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
import solutions.laxmi.omnicompiler.core.network.error.transportError
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
            GitHost.GITHUB -> allPages { page -> github.branches(bearer(token), repoId, page) }.map { it.name }
            GitHost.GITLAB -> allPages { page -> gitlab.branches(token, repoId, page) }.map { it.name }
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
            // GitHub's contents API silently stops at 1,000 entries; a listing that long may be cut short.
            GitHost.GITHUB -> github.contents(bearer(token), repoId, segments(path), ref)
                .also { if (it.size >= GITHUB_CONTENTS_LIMIT) throw TruncatedListingException() }
                .map { RemoteEntry(it.name, it.path, it.type == "dir", it.sha, it.size) }
            // A truncated listing would make pull treat the missing files as deleted upstream, so every page is read.
            GitHost.GITLAB -> allPages { page -> gitlab.tree(token, repoId, path, ref, page) }.map { RemoteEntry(it.name, it.path, it.type == "tree", it.id, null) }
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
                // A few at a time: GitHub's secondary rate limits punish bursts of content-creating requests.
                val uploads = Semaphore(MAX_PARALLEL_REQUESTS)
                val entries = coroutineScope {
                    changes.map { change ->
                        async {
                            val sha = change.content?.let { uploads.withPermit { github.createBlob(auth, repoId, GhCreateBlobDto(it)).sha } }
                            GhTreeEntryDto(change.path, sha = sha)
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

    /**
     * Reads a 100-per-page listing to the end. Bounded so a huge repository can't loop forever; reaching the bound
     * fails rather than returning part of the listing, since sync reads a missing file as deleted.
     */
    private suspend fun <T> allPages(fetch: suspend (page: Int) -> List<T>): List<T> {
        val all = mutableListOf<T>()
        for (page in 1..MAX_PAGES) {
            val items = fetch(page)
            all += items
            if (items.size < PAGE_SIZE) return all
        }
        throw TruncatedListingException()
    }

    /** GitHub sends `X-RateLimit-Remaining: 0` (or `Retry-After` for secondary limits); GitLab sends `RateLimit-Remaining`. */
    private fun HttpException.isRateLimited(): Boolean {
        val headers = response()?.headers() ?: return code() == 429
        return code() == 429 || headers["Retry-After"] != null ||
            headers["X-RateLimit-Remaining"] == "0" || headers["RateLimit-Remaining"] == "0"
    }

    /** Encodes each path segment but keeps the slashes GitHub routes on. */
    private fun segments(path: String) = path.split('/').filter { it.isNotEmpty() }.joinToString("/") { Uri.encode(it) }

    private suspend fun <T> call(block: suspend () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (e: HttpException) {
        Outcome.Failure(
            when (e.code()) {
                401 -> AppError.Unauthorized(reason = ErrorReason.GitTokenRejected)
                // Both hosts answer an exhausted quota with 403/429 plus their rate-limit headers.
                403, 429 -> if (e.isRateLimited()) {
                    AppError.RateLimited(reason = ErrorReason.GitRateLimited)
                } else {
                    AppError.Forbidden(reason = ErrorReason.GitAccessDenied)
                }
                404 -> AppError.NotFound(reason = ErrorReason.GitNotFound)
                // 409/422 on a fast-forward-only ref update: the branch moved underneath us.
                409, 422 -> AppError.Conflict(reason = ErrorReason.GitPullFirst)
                else -> AppError.Unknown(reason = ErrorReason.GitRequestFailed)
            },
        )
    } catch (e: TruncatedListingException) {
        Outcome.Failure(AppError.Unknown(reason = ErrorReason.GitListingTooLarge))
    } catch (e: IOException) {
        Outcome.Failure(transportError(e))
    } catch (e: SerializationException) {
        Outcome.Failure(AppError.Unknown(reason = ErrorReason.BadResponse))
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 20
        const val MAX_PARALLEL_REQUESTS = 4
        const val GITHUB_CONTENTS_LIMIT = 1_000
    }
}

/** A listing couldn't be read completely; callers must not act on part of it. */
private class TruncatedListingException : Exception("Listing too long to read completely")
