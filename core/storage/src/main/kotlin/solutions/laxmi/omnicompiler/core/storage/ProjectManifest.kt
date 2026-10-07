package solutions.laxmi.omnicompiler.core.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * `<project>/.omni/project.json`: everything about a project that isn't a source file. Source files sit in the
 * project folder and its subfolders, laid out exactly as the judge's `/workspace` receives them.
 */
@Serializable
data class ProjectManifest(
    val schema: Int = SCHEMA,
    val id: String,
    val name: String,
    val runtimeId: String,
    /** Entry file name (always a root file); empty when unknown (the runtime's default file name is used). */
    val entry: String = "",
    val limits: ManifestLimits,
    val tests: List<ManifestTest> = emptyList(),
    val createdAt: Long,
    val lastVerdict: String? = null,
    /** Document the project was imported from as a single file; "Save to original" writes the entry back there. */
    val origin: String? = null,
    /** GitHub/GitLab folder the project tracks (see ProjectRemote). */
    val remote: ManifestRemote? = null,
) {
    companion object {
        const val SCHEMA = 1
    }
}

@Serializable
data class ManifestLimits(val timeMs: Int, val memMb: Int)

@Serializable
data class ManifestRemote(
    val host: String,
    val repoId: String,
    val repoName: String,
    val branch: String,
    val path: String = "",
    val baseCommit: String,
    val baseBlobs: Map<String, String> = emptyMap(),
    val conflicts: List<String> = emptyList(),
)

@Serializable
data class ManifestTest(
    val id: String,
    val name: String = "",
    val stdin: String = "",
    val expected: String = "",
)

/** Reads and writes manifests; unknown keys are kept compatible so newer app versions can add fields. */
object ManifestCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(manifest: ProjectManifest): String = json.encodeToString(ProjectManifest.serializer(), manifest)

    /** Compact form of a remote, as the index stores it. */
    fun encodeRemote(remote: ManifestRemote): String = compact.encodeToString(ManifestRemote.serializer(), remote)

    fun decodeRemote(text: String): ManifestRemote? = try {
        compact.decodeFromString(ManifestRemote.serializer(), text)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private val compact = Json { ignoreUnknownKeys = true }

    /** Null when the text isn't a manifest this app can read (corrupt JSON, missing fields, wrong types). */
    fun decode(text: String): ProjectManifest? = try {
        json.decodeFromString(ProjectManifest.serializer(), text)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}

/** Layout constants shared by the store, the validator and sync. */
object ProjectLayout {
    const val META_DIR = ".omni"
    const val MANIFEST = "project.json"
    const val MANIFEST_BACKUP = "project.json.bak"
    /** Written first and renamed over the manifest, so an interrupted write never leaves a half-written manifest. */
    const val MANIFEST_TEMP = "project.json.tmp"
    /** Subfolder created inside the user's pick unless the pick already holds projects. */
    const val ROOT_DIR = "OmniCompiler"
}
