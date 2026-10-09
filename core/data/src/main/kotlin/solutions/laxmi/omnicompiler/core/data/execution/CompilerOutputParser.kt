package solutions.laxmi.omnicompiler.core.data.execution

import solutions.laxmi.omnicompiler.core.model.CompileDiagnostic
import solutions.laxmi.omnicompiler.core.model.CompileProblem

/**
 * Extracts `file:line[:col]: error|warning: message` lines (gcc, clang, rustc short form, javac-like)
 * from compiler stderr, and merges the judge's structured error when it adds information.
 */
internal object CompilerOutputParser {

    private val pattern = Regex("""^\s*(?:-->\s*)?([^\s:][^:\n]*?):(\d+)(?::(\d+))?:\s*(fatal error|error|warning|note)?:?\s*(.+)$""")
    private const val MAX_PROBLEMS = 50

    fun parse(output: String?, structured: CompileDiagnostic?, entryFileName: String?): List<CompileProblem> {
        val parsed = output.orEmpty().lineSequence()
            .mapNotNull { line -> pattern.find(line) }
            .filter { it.groupValues[4] != "note" && looksLikeSource(it.groupValues[1]) }
            .map { match ->
                CompileProblem(
                    fileName = projectPath(match.groupValues[1]),
                    line = match.groupValues[2].toIntOrNull(),
                    column = match.groupValues[3].toIntOrNull(),
                    message = match.groupValues[5].trim(),
                    isError = match.groupValues[4] != "warning",
                )
            }
            .distinct()
            .take(MAX_PROBLEMS)
            .toList()
        if (structured == null) return parsed
        val alreadyReported = parsed.any { it.line == structured.line && it.isError }
        return if (alreadyReported) parsed else listOf(
            CompileProblem(entryFileName, structured.line, structured.column, structured.message, isError = true),
        ) + parsed
    }

    /**
     * The path as the project names it: compilers print `/workspace/src/a.cpp` or `./src/a.cpp` for a file the project
     * calls `src/a.cpp`. Paths outside the workspace (system headers) are kept as printed and match no project file.
     */
    private fun projectPath(printed: String): String = printed.removePrefix(WORKSPACE).removePrefix("./")

    private const val WORKSPACE = "/workspace/"

    /** Guards against matching timestamps or URLs: the "file" part must look like a file name. */
    private fun looksLikeSource(candidate: String) = '.' in candidate && ' ' !in candidate.trim() && !candidate.startsWith("http")
}
