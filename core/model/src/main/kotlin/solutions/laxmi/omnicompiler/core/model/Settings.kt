package solutions.laxmi.omnicompiler.core.model

enum class EditorTheme(val label: String, val description: String) {
    SIGNAL("Signal", "Default"),
    GRAPHITE("Graphite", "Soft dark"),
    PAPER("Paper", "Light"),
    CONTRAST("Contrast", "AAA"),
}

enum class CodeFont(val label: String) {
    JETBRAINS_MONO("JetBrains Mono"),
    FIRA_CODE("Fira Code"),
    IBM_PLEX_MONO("IBM Plex"),
}

enum class LineSpacing(val label: String, val multiplier: Float) {
    TIGHT("Tight", 1.3f),
    NORMAL("1.5", 1.5f),
    LOOSE("Loose", 1.75f),
}

data class EditorSettings(
    val theme: EditorTheme = EditorTheme.SIGNAL,
    val font: CodeFont = CodeFont.JETBRAINS_MONO,
    val fontSizeSp: Int = 12,
    val ligatures: Boolean = true,
    val indentGuides: Boolean = true,
    val lineSpacing: LineSpacing = LineSpacing.NORMAL,
    val minimap: Boolean = true,
    val symbolRow: Boolean = true,
    val autocomplete: Boolean = true,
    val wordWrap: Boolean = false,
    val tabSize: Int = 4,
) {
    companion object {
        const val MIN_FONT_SP = 10
        const val MAX_FONT_SP = 16
        val TabSizes = listOf(2, 4, 8)
    }
}

data class RunSettings(
    val defaultRuntimeId: String? = null,
    val defaultLimits: Limits = Limits.Default,
    val runOnCtrlEnter: Boolean = true,
    val bypassCache: Boolean = false,
    val sendQueuedWhenOnline: Boolean = true,
    val benchmarkCopies: Int = 5,
)
