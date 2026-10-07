package solutions.laxmi.omnicompiler.core.network.api

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import solutions.laxmi.omnicompiler.core.network.dto.GhBlobDto
import solutions.laxmi.omnicompiler.core.network.dto.GhBranchDto
import solutions.laxmi.omnicompiler.core.network.dto.GhCommitDto
import solutions.laxmi.omnicompiler.core.network.dto.GhContentDto
import solutions.laxmi.omnicompiler.core.network.dto.GhCreateBlobDto
import solutions.laxmi.omnicompiler.core.network.dto.GhCreateCommitDto
import solutions.laxmi.omnicompiler.core.network.dto.GhCreateTreeDto
import solutions.laxmi.omnicompiler.core.network.dto.GhRepoDto
import solutions.laxmi.omnicompiler.core.network.dto.GhShaDto
import solutions.laxmi.omnicompiler.core.network.dto.GhTreeListingDto
import solutions.laxmi.omnicompiler.core.network.dto.GhUpdateRefDto
import solutions.laxmi.omnicompiler.core.network.dto.GhUserDto
import solutions.laxmi.omnicompiler.core.network.dto.GlBranchDto
import solutions.laxmi.omnicompiler.core.network.dto.GlCommitIdDto
import solutions.laxmi.omnicompiler.core.network.dto.GlCreateCommitDto
import solutions.laxmi.omnicompiler.core.network.dto.GlProjectDto
import solutions.laxmi.omnicompiler.core.network.dto.GlTreeEntryDto
import solutions.laxmi.omnicompiler.core.network.dto.GlUserDto

/**
 * GitHub REST v3. `repo` is `owner/name` and `path`/`ref` arguments are pre-encoded per segment, because GitHub
 * expects real slashes between segments. The token is passed per call (`Bearer …`), never stored in the client.
 */
internal interface GitHubApi {
    @GET("user")
    suspend fun user(@Header("Authorization") auth: String): GhUserDto

    @GET("user/repos?sort=updated&per_page=100")
    suspend fun repos(@Header("Authorization") auth: String, @Query("page") page: Int): List<GhRepoDto>

    @GET("repos/{repo}/branches?per_page=100")
    suspend fun branches(@Header("Authorization") auth: String, @Path("repo", encoded = true) repo: String, @Query("page") page: Int): List<GhBranchDto>

    @GET("repos/{repo}/branches/{branch}")
    suspend fun branch(@Header("Authorization") auth: String, @Path("repo", encoded = true) repo: String, @Path("branch", encoded = true) branch: String): GhBranchDto

    @GET("repos/{repo}/contents/{path}")
    suspend fun contents(
        @Header("Authorization") auth: String,
        @Path("repo", encoded = true) repo: String,
        @Path("path", encoded = true) path: String,
        @Query("ref") ref: String,
    ): List<GhContentDto>

    @GET("repos/{repo}/git/blobs/{sha}")
    suspend fun blob(@Header("Authorization") auth: String, @Path("repo", encoded = true) repo: String, @Path("sha") sha: String): GhBlobDto

    @GET("repos/{repo}/git/trees/{sha}?recursive=1")
    suspend fun treeRecursive(@Header("Authorization") auth: String, @Path("repo", encoded = true) repo: String, @Path("sha") sha: String): GhTreeListingDto

    @GET("repos/{repo}/git/commits/{sha}")
    suspend fun commit(@Header("Authorization") auth: String, @Path("repo", encoded = true) repo: String, @Path("sha") sha: String): GhCommitDto

    @POST("repos/{repo}/git/blobs")
    suspend fun createBlob(@Header("Authorization") auth: String, @Path("repo", encoded = true) repo: String, @Body body: GhCreateBlobDto): GhShaDto

    @POST("repos/{repo}/git/trees")
    suspend fun createTree(@Header("Authorization") auth: String, @Path("repo", encoded = true) repo: String, @Body body: GhCreateTreeDto): GhShaDto

    @POST("repos/{repo}/git/commits")
    suspend fun createCommit(@Header("Authorization") auth: String, @Path("repo", encoded = true) repo: String, @Body body: GhCreateCommitDto): GhShaDto

    /** Fast-forward only (`force = false`): 422 means the branch moved since the base commit. */
    @PATCH("repos/{repo}/git/refs/heads/{branch}")
    suspend fun updateBranch(
        @Header("Authorization") auth: String,
        @Path("repo", encoded = true) repo: String,
        @Path("branch", encoded = true) branch: String,
        @Body body: GhUpdateRefDto,
    ): ResponseBody
}

/** GitLab v4 (gitlab.com). Project ids are numeric; branch and path parameters are URL-encoded whole. */
internal interface GitLabApi {
    @GET("user")
    suspend fun user(@Header("PRIVATE-TOKEN") token: String): GlUserDto

    @GET("projects?membership=true&simple=true&order_by=last_activity_at&per_page=100")
    suspend fun projects(@Header("PRIVATE-TOKEN") token: String, @Query("page") page: Int): List<GlProjectDto>

    @GET("projects/{id}/repository/branches?per_page=100")
    suspend fun branches(@Header("PRIVATE-TOKEN") token: String, @Path("id") id: String, @Query("page") page: Int): List<GlBranchDto>

    @GET("projects/{id}/repository/branches/{branch}")
    suspend fun branch(@Header("PRIVATE-TOKEN") token: String, @Path("id") id: String, @Path("branch") branch: String): GlBranchDto

    @GET("projects/{id}/repository/tree?per_page=100")
    suspend fun tree(
        @Header("PRIVATE-TOKEN") token: String,
        @Path("id") id: String,
        @Query("path") path: String,
        @Query("ref") ref: String,
        @Query("page") page: Int,
        @Query("recursive") recursive: Boolean = false,
    ): List<GlTreeEntryDto>

    @GET("projects/{id}/repository/blobs/{sha}/raw")
    suspend fun rawBlob(@Header("PRIVATE-TOKEN") token: String, @Path("id") id: String, @Path("sha") sha: String): ResponseBody

    @POST("projects/{id}/repository/commits")
    suspend fun createCommit(@Header("PRIVATE-TOKEN") token: String, @Path("id") id: String, @Body body: GlCreateCommitDto): GlCommitIdDto
}
