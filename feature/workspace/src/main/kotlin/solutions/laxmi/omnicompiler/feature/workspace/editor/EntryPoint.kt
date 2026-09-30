package solutions.laxmi.omnicompiler.feature.workspace.editor

/**
 * Finds the line that starts a program (its `main`), where the editor draws the "▶ Run" hint.
 * Only languages with an unambiguous entry declaration are listed; script languages that run
 * top to bottom have no such line, and a miss simply means no hint.
 */
internal object EntryPoint {

    private val C_LIKE = Regex("""^\s*(?:int|void|auto|signed\s+int)\s+main\s*\(""")

    private val PATTERNS: Map<String, Regex> = mapOf(
        "c" to C_LIKE,
        "cpp" to C_LIKE,
        "java" to Regex("""^\s*(?:public\s+)?static\s+(?:public\s+)?void\s+main\s*\("""),
        "groovy" to Regex("""^\s*(?:public\s+)?static\s+(?:public\s+)?void\s+main\s*\("""),
        "kotlin" to Regex("""^\s*fun\s+main\s*\("""),
        "scala" to Regex("""^\s*(?:def\s+main\s*\(|@main\s+def\s+\w+|object\s+\w+\s+extends\s+App\b)"""),
        "csharp" to Regex("""^\s*(?:public\s+|private\s+|internal\s+)?static\s+(?:async\s+)?(?:void|int|Task(?:<int>)?)\s+Main\s*\("""),
        "fsharp" to Regex("""^\s*\[<EntryPoint>]"""),
        "go" to Regex("""^\s*func\s+main\s*\(\s*\)"""),
        "rust" to Regex("""^\s*(?:pub\s+)?(?:async\s+)?fn\s+main\s*\("""),
        "zig" to Regex("""^\s*pub\s+fn\s+main\s*\("""),
        "d" to Regex("""^\s*(?:void|int)\s+main\s*\("""),
        "vlang" to Regex("""^\s*fn\s+main\s*\(\s*\)"""),
        "dart" to Regex("""^\s*(?:void\s+|Future<void>\s+)?main\s*\("""),
        "haskell" to Regex("""^main\s*(?:::|=)"""),
        "python" to Regex("""^if\s+__name__\s*==\s*['"]__main__['"]\s*:"""),
        "pypy" to Regex("""^if\s+__name__\s*==\s*['"]__main__['"]\s*:"""),
    )

    /** 1-based line of the entry declaration in [code], or null when the family has none or it isn't found. */
    fun line(languageBase: String?, code: String): Int? {
        val pattern = PATTERNS[languageBase] ?: return null
        code.lineSequence().forEachIndexed { index, text -> if (pattern.containsMatchIn(text)) return index + 1 }
        return null
    }
}
