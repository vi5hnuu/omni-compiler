package solutions.laxmi.omnicompiler.core.catalog

import kotlinx.serialization.Serializable

/** Mirrors `catalog.json` produced by `scripts/gen-catalog.mjs`. */
@Serializable
internal data class CatalogJson(
    val version: Int,
    val defaultLimits: LimitsJson,
    val runtimeLimits: Map<String, LimitsJson>,
    val languages: List<LanguageJson>,
    val runtimes: List<RuntimeJson> = emptyList(),
    val examples: List<ExampleJson>,
    val problems: List<ProblemJson>,
)

@Serializable
internal data class LimitsJson(val timeMs: Int, val memMb: Int)

/** Build-time snapshot of ls-judge runtime definitions (deploy/firecracker/runtimes). */
@Serializable
internal data class RuntimeJson(
    val id: String,
    val language: String,
    val version: String,
    val status: String,
    val filename: String,
    val lane: String,
)

@Serializable
internal data class TestJson(val stdin: String, val expected: String)

@Serializable
internal data class StarterJson(val code: String, val tests: List<TestJson>)

@Serializable
internal data class LanguageJson(
    val base: String,
    val name: String,
    val shortCode: String,
    val category: String,
    val tagline: String? = null,
    val importHint: String,
    val multiFileSupported: Boolean,
    val lineComment: String? = null,
    val blockComment: List<String>? = null,
    val starter: StarterJson? = null,
)

@Serializable
internal data class ExampleJson(
    val id: String,
    val title: String,
    val difficulty: String,
    val statement: String,
    val tests: List<TestJson>,
)

@Serializable
internal data class ProblemExampleJson(val input: String, val output: String, val explanation: String? = null)

@Serializable
internal data class ProblemJson(
    val slug: String,
    val title: String,
    val difficulty: String,
    val tags: List<String>,
    val tagline: String,
    val statement: List<String>,
    val examples: List<ProblemExampleJson>,
    val constraints: List<String>,
    val solutions: Map<String, String>,
    val tests: List<TestJson>,
)
