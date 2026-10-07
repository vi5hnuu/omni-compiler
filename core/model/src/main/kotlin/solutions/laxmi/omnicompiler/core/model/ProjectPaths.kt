package solutions.laxmi.omnicompiler.core.model

/**
 * A file's place in a project: a relative, `/`-separated path (`src/util/helper.py`, or `main.py` at the root),
 * exactly as the judge writes it under `/workspace`. The structural limits mirror ls-judge's path validator; which
 * characters a name may use is left to the server.
 */
object ProjectPaths {
    const val MAX_DEPTH = 8
    const val MAX_LENGTH = 255
    const val MAX_SEGMENT = 128

    /** Folders that belong to the app (`.omni`) or to version control (`.git`); never part of a project's files. */
    private val INTERNAL_FOLDERS = setOf(".omni", ".git")

    enum class Problem { EMPTY, BAD_SEGMENT, TOO_DEEP, TOO_LONG, SEGMENT_TOO_LONG }

    fun basename(path: String): String = path.substringAfterLast('/')

    /** The containing folder's path; empty for a file at the project root. */
    fun parent(path: String): String = path.substringBeforeLast('/', "")

    fun isRoot(path: String): Boolean = '/' !in path

    fun join(folder: String, name: String): String = if (folder.isEmpty()) name else "$folder/$name"

    /** Every folder on the way to [path], outermost first (`a/b/c.txt` → `a`, `a/b`). */
    fun ancestors(path: String): List<String> {
        val segments = path.split('/').dropLast(1)
        return segments.indices.map { segments.subList(0, it + 1).joinToString("/") }
    }

    /** What the user typed, tidied the way the judge tidies it: surrounding blanks, empty and `.` segments dropped. */
    fun normalize(input: String): String = input.trim().split('/').filter { it.isNotEmpty() && it != "." }.joinToString("/")

    fun isInternal(path: String): Boolean = path.substringBefore('/') in INTERNAL_FOLDERS

    /** Why [path] can't be a project file, or null when it can. */
    fun problem(path: String): Problem? {
        if (path.isEmpty()) return Problem.EMPTY
        if (path.length > MAX_LENGTH) return Problem.TOO_LONG
        val segments = path.split('/')
        if (segments.size > MAX_DEPTH) return Problem.TOO_DEEP
        return segments.firstNotNullOfOrNull { segment ->
            when {
                segment.isEmpty() || segment == "." || segment == ".." || '\\' in segment || '\u0000' in segment -> Problem.BAD_SEGMENT
                segment.length > MAX_SEGMENT -> Problem.SEGMENT_TOO_LONG
                else -> null
            }
        }
    }
}

/** What one run may send, as the judge enforces it. */
object ProjectLimits {
    /** Files besides the entry. */
    const val MAX_EXTRA_FILES = 500
    const val MAX_ENTRY_BYTES = 64 * 1024
    const val MAX_FILE_BYTES = 512 * 1024
    /** Entry plus every other file. */
    const val MAX_TOTAL_BYTES = 20 * 1024 * 1024
}
