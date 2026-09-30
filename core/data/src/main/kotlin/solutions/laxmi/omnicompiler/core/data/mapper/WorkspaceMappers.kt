package solutions.laxmi.omnicompiler.core.data.mapper

import solutions.laxmi.omnicompiler.core.model.ProjectRemote
import solutions.laxmi.omnicompiler.core.model.GitHost
import solutions.laxmi.omnicompiler.core.storage.ManifestRemote
import solutions.laxmi.omnicompiler.core.storage.ManifestCodec
import solutions.laxmi.omnicompiler.core.model.ProjectIssue
import solutions.laxmi.omnicompiler.core.database.entity.FileEntity
import solutions.laxmi.omnicompiler.core.database.entity.ProjectEntity
import solutions.laxmi.omnicompiler.core.database.entity.ProjectSummaryRow
import solutions.laxmi.omnicompiler.core.database.entity.RuntimeEntity
import solutions.laxmi.omnicompiler.core.database.entity.TestCaseEntity
import solutions.laxmi.omnicompiler.core.model.Lane
import solutions.laxmi.omnicompiler.core.model.Limits
import solutions.laxmi.omnicompiler.core.model.Project
import solutions.laxmi.omnicompiler.core.model.ProjectSummary
import solutions.laxmi.omnicompiler.core.model.Runtime
import solutions.laxmi.omnicompiler.core.model.RuntimeStatus
import solutions.laxmi.omnicompiler.core.model.SourceFile
import solutions.laxmi.omnicompiler.core.model.TestCase
import solutions.laxmi.omnicompiler.core.model.Verdict
import kotlin.time.Instant

internal fun ProjectEntity.toModel() = Project(
    id = id,
    name = name,
    runtimeId = runtimeId,
    limits = Limits(timeLimitMs, memLimitMb),
    lastVerdict = Verdict.fromCode(lastVerdict),
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
    issues = parseIssues(issues),
    hasOrigin = originUri != null,
    remote = remoteJson?.let(ManifestCodec::decodeRemote)?.toModel(),
)

internal fun ProjectSummaryRow.toModel() = ProjectSummary(
    project = Project(
        id = id,
        name = name,
        runtimeId = runtimeId,
        limits = Limits(timeLimitMs, memLimitMb),
        lastVerdict = Verdict.fromCode(lastVerdict),
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = Instant.fromEpochMilliseconds(updatedAt),
        issues = parseIssues(issues),
        hasOrigin = originUri != null,
        remote = remoteJson?.let(ManifestCodec::decodeRemote)?.toModel(),
    ),
    fileNames = fileNames?.split('\n')?.filter { it.isNotEmpty() }.orEmpty(),
    testCount = testCount,
)

internal fun FileEntity.toModel() = SourceFile(id, projectId, name, content, isEntry, position, contentVersion)

internal fun TestCaseEntity.toModel() = TestCase(id, projectId, name, stdin, expected, position)

internal fun RuntimeEntity.toModel() = Runtime(
    id = id,
    language = language,
    version = version,
    status = RuntimeStatus.fromWire(status),
    filename = filename,
    available = available,
    lane = Lane.fromWire(lane),
)

internal fun Runtime.toEntity() = RuntimeEntity(
    id = id,
    language = language,
    version = version,
    status = status.name.lowercase(),
    filename = filename,
    available = available,
    lane = lane.name.lowercase(),
)

internal fun parseIssues(stored: String): List<ProjectIssue> = stored.lines().filter { it.isNotBlank() }.mapNotNull(ProjectIssue::parse)

internal fun List<ProjectIssue>.toStored(): String = joinToString("\n", transform = ProjectIssue::format)

internal fun ManifestRemote.toModel(): ProjectRemote? = GitHost.entries.firstOrNull { it.name == host }?.let { gitHost ->
    ProjectRemote(gitHost, repoId, repoName, branch, path, baseCommit, baseBlobs, conflicts.toSet())
}

internal fun ProjectRemote.toManifest() = ManifestRemote(host.name, repoId, repoName, branch, path, baseCommit, baseBlobs, conflicts.sorted())
