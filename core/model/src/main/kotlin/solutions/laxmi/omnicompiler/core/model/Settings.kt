package solutions.laxmi.omnicompiler.core.model

/** Display names for these enums live in `core/ui` (localized). */
/** App chrome colours; SYSTEM follows the device's dark-theme setting. */
enum class AppTheme { SYSTEM, LIGHT, DARK }

/** Code colours. AUTO follows the app theme (Paper in light, Signal in dark); the rest are fixed choices. */
enum class EditorTheme {
    AUTO, SIGNAL, GRAPHITE, PAPER, CONTRAST;

    fun resolved(darkApp: Boolean): EditorTheme = if (this == AUTO) (if (darkApp) SIGNAL else PAPER) else this
}

enum class CodeFont { JETBRAINS_MONO, FIRA_CODE, IBM_PLEX_MONO }

enum class LineSpacing(val multiplier: Float) {
    TIGHT(1.3f),
    NORMAL(1.5f),
    LOOSE(1.75f),
}

data class EditorSettings(
    val theme: EditorTheme = EditorTheme.AUTO,
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
    /** Draw spaces and tabs (leading and trailing) as faint marks. */
    val showInvisibles: Boolean = false,
    /** Keep the enclosing block's first lines pinned while scrolling (stands in for code folding). */
    val stickyScroll: Boolean = true,
    /** Hide the on-screen keyboard while a hardware keyboard is connected. */
    val hardwareKeyboardOnly: Boolean = true,
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
