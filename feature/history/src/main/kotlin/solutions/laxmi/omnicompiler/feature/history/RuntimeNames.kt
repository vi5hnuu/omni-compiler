package solutions.laxmi.omnicompiler.feature.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import solutions.laxmi.omnicompiler.core.data.runtime.RuntimeRepository

/** A runtime as people read it ("Python", "3.11") rather than its judge id ("python-3.11"). */
data class RuntimeName(val language: String, val version: String)

/** Display names by runtime id; ids missing from the catalog have no entry and are shown raw. */
internal fun RuntimeRepository.runtimeNames(): Flow<Map<String, RuntimeName>> = languages.map { languages ->
    languages.flatMap { language -> language.runtimes.map { it.id to RuntimeName(language.info.name, it.version) } }.toMap()
}
