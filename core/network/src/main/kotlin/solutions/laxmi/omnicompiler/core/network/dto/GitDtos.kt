package solutions.laxmi.omnicompiler.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// GitHub REST v3

@Serializable
internal data class GhUserDto(val login: String)

@Serializable
internal data class GhRepoDto(
    @SerialName("full_name") val fullName: String,
    @SerialName("default_branch") val defaultBranch: String? = null,
    val private: Boolean = false,
)

@Serializable
internal data class GhBranchDto(val name: String, val commit: GhShaDto)

@Serializable
internal data class GhShaDto(val sha: String)

@Serializable
internal data class GhContentDto(val name: String, val path: String, val type: String, val sha: String, val size: Long? = null)

@Serializable
internal data class GhBlobDto(val content: String, val encoding: String)

@Serializable
internal data class GhCommitDto(val sha: String, val tree: GhShaDto)

@Serializable
internal data class GhCreateBlobDto(val content: String, val encoding: String = "utf-8")

@Serializable
internal data class GhTreeEntryDto(val path: String, val mode: String = "100644", val type: String = "blob", val sha: String?)

@Serializable
internal data class GhCreateTreeDto(@SerialName("base_tree") val baseTree: String, val tree: List<GhTreeEntryDto>)

@Serializable
internal data class GhCreateCommitDto(val message: String, val tree: String, val parents: List<String>)

@Serializable
internal data class GhUpdateRefDto(val sha: String, val force: Boolean = false)

// GitLab v4

@Serializable
internal data class GlUserDto(val username: String)

@Serializable
internal data class GlProjectDto(
    val id: Long,
    @SerialName("path_with_namespace") val pathWithNamespace: String,
    @SerialName("default_branch") val defaultBranch: String? = null,
    val visibility: String? = null,
)

@Serializable
internal data class GlBranchDto(val name: String, val commit: GlCommitIdDto)

@Serializable
internal data class GlCommitIdDto(val id: String)

@Serializable
internal data class GlTreeEntryDto(val id: String, val name: String, val type: String, val path: String)

@Serializable
internal data class GlCommitActionDto(val action: String, @SerialName("file_path") val filePath: String, val content: String? = null)

@Serializable
internal data class GlCreateCommitDto(
    val branch: String,
    @SerialName("commit_message") val commitMessage: String,
    val actions: List<GlCommitActionDto>,
)
