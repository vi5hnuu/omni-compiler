package solutions.laxmi.omnicompiler.core.model

/** Display names for these enums live in `core/ui` (localized). */
enum class EditorTheme { SIGNAL, GRAPHITE, PAPER, CONTRAST }

enum class CodeFont { JETBRAINS_MONO, FIRA_CODE, IBM_PLEX_MONO }

enum class LineSpacing(val multiplier: Float) {
    TIGHT(1.3f),
    NORMAL(1.5f),
    LOOSE(1.75f),
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
