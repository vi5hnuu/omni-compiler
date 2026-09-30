package solutions.laxmi.omnicompiler.core.editor

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import solutions.laxmi.omnicompiler.core.model.EditorTheme

/** Colours of one editor theme (design screen U5). Syntax roles mirror the design's token classes. */
@Immutable
data class EditorPalette(
    val isDark: Boolean,
    val background: Color,
    val text: Color,
    val keyword: Color,
    val type: Color,
    val function: Color,
    val string: Color,
    val number: Color,
    val preprocessor: Color,
    val comment: Color,
    val operator: Color,
    val lineNumber: Color,
    val lineNumberActive: Color,
    val activeLine: Color,
    val cursor: Color,
    val selection: Color,
    val indentGuide: Color,
    val indentGuideActive: Color,
    val popup: Color,
    val popupSelected: Color,
    val popupBorder: Color,
    val matched: Color,
    val error: Color,
    val minimapLine: Color,
    val minimapViewport: Color,
)

private val Accent = Color(0xFFEC3013)
private val AccentText = Color(0xFFFF5A3F)

fun EditorTheme.palette(): EditorPalette = when (this) {
    EditorTheme.SIGNAL -> EditorPalette(
        isDark = true,
        background = Color(0xFF000000), text = Color(0xFFF3F2F2),
        keyword = Color(0xFFFF6B52), type = Color(0xFF7CC4E8), function = Color(0xFFF0D27A),
        string = Color(0xFF9FD8A8), number = Color(0xFFFF9783), preprocessor = Color(0xFFFF6B52),
        comment = Color(0xFF5F5C5C), operator = Color(0xFF9B9797),
        lineNumber = Color(0xFF4F4C4C), lineNumberActive = Color(0xFFF5F4F4), activeLine = Color(0xFF161515),
        cursor = Accent, selection = Color(0x59EC3013),
        indentGuide = Color(0x14FFFFFF), indentGuideActive = Color(0x38FFFFFF),
        popup = Color(0xFF161515), popupSelected = Color(0xFF242222), popupBorder = Color(0x29FFFFFF),
        matched = AccentText, error = Accent,
        minimapLine = Color(0xFF4F4C4C), minimapViewport = Color(0xFF161515),
    )
    EditorTheme.GRAPHITE -> EditorPalette(
        isDark = true,
        background = Color(0xFF1A1918), text = Color(0xFFE4E1E1),
        keyword = Color(0xFFFF7A63), type = Color(0xFFA9C2D0), function = Color(0xFFF3F2F2),
        string = Color(0xFFC9B39C), number = Color(0xFFE8A48F), preprocessor = Color(0xFFFF7A63),
        comment = Color(0xFF6B6767), operator = Color(0xFF9B9797),
        lineNumber = Color(0xFF5A5655), lineNumberActive = Color(0xFFE4E1E1), activeLine = Color(0xFF242220),
        cursor = Accent, selection = Color(0x59EC3013),
        indentGuide = Color(0x14FFFFFF), indentGuideActive = Color(0x33FFFFFF),
        popup = Color(0xFF242220), popupSelected = Color(0xFF2D2B2B), popupBorder = Color(0x29FFFFFF),
        matched = AccentText, error = Accent,
        minimapLine = Color(0xFF5A5655), minimapViewport = Color(0xFF242220),
    )
    EditorTheme.PAPER -> EditorPalette(
        isDark = false,
        background = Color(0xFFF3F2F2), text = Color(0xFF201E1D),
        keyword = Color(0xFFAE1800), type = Color(0xFF2D5B73), function = Color(0xFF201E1D),
        string = Color(0xFF605D5D), number = Color(0xFFAE1800), preprocessor = Color(0xFFAE1800),
        comment = Color(0xFF9B9797), operator = Color(0xFF605D5D),
        lineNumber = Color(0xFF9B9797), lineNumberActive = Color(0xFF201E1D), activeLine = Color(0xFFE6E4E4),
        cursor = Accent, selection = Color(0x66FF9783),
        indentGuide = Color(0x1A201E1D), indentGuideActive = Color(0x40201E1D),
        popup = Color(0xFFEAE9E9), popupSelected = Color(0xFFD7D3D3), popupBorder = Color(0x33201E1D),
        matched = Color(0xFFAE1800), error = Accent,
        minimapLine = Color(0xFFBAB6B6), minimapViewport = Color(0xFFE0DEDE),
    )
    EditorTheme.CONTRAST -> EditorPalette(
        isDark = true,
        background = Color(0xFF000000), text = Color(0xFFFFFFFF),
        keyword = Color(0xFFFFFFFF), type = Color(0xFF7FD4FF), function = Color(0xFFFFD24A),
        string = Color(0xFF7CF0A0), number = Color(0xFFFFB3A3), preprocessor = Color(0xFFFFFFFF),
        comment = Color(0xFFBAB6B6), operator = Color(0xFFFFFFFF),
        lineNumber = Color(0xFFBAB6B6), lineNumberActive = Color(0xFFFFFFFF), activeLine = Color(0xFF1C1C1C),
        cursor = Color(0xFFFF5A3F), selection = Color(0x80EC3013),
        indentGuide = Color(0x33FFFFFF), indentGuideActive = Color(0x80FFFFFF),
        popup = Color(0xFF111111), popupSelected = Color(0xFF333333), popupBorder = Color(0x66FFFFFF),
        matched = Color(0xFFFFD24A), error = Color(0xFFFF5A3F),
        minimapLine = Color(0xFF8A8686), minimapViewport = Color(0xFF222222),
    )
}

/**
 * Builds a VS Code–format TextMate theme from the palette, so one Kotlin definition drives both the
 * TextMate token colours and the editor chrome.
 */
internal fun EditorPalette.toTextMateThemeJson(name: String): String {
    fun hex(c: Color) = "#%08X".format(c.toArgb()).let { argb -> "#" + argb.substring(3) + argb.substring(1, 3) }
    fun rule(scopes: List<String>, color: Color, style: String = "") =
        """{"scope":[${scopes.joinToString(",") { "\"$it\"" }}],"settings":{"foreground":"${hex(color)}"${if (style.isNotEmpty()) ",\"fontStyle\":\"$style\"" else ""}}}"""
    val rules = listOf(
        rule(listOf("comment", "punctuation.definition.comment"), comment, "italic"),
        rule(listOf("string", "string.quoted", "string.template", "constant.character"), string),
        rule(listOf("constant.numeric", "constant.language", "constant.other"), number),
        rule(listOf("keyword", "keyword.control", "storage", "storage.type", "storage.modifier", "variable.language"), keyword, "bold"),
        rule(listOf("keyword.operator", "punctuation", "meta.brace"), operator),
        rule(listOf("meta.preprocessor", "keyword.control.directive", "punctuation.definition.directive", "entity.name.function.preprocessor"), preprocessor, "bold"),
        rule(listOf("entity.name.type", "entity.name.class", "entity.other.inherited-class", "support.type", "support.class", "storage.type.primitive", "storage.type.built-in"), type),
        rule(listOf("entity.name.function", "support.function", "meta.function-call.generic", "variable.function"), function, "bold"),
        rule(listOf("variable", "variable.other", "entity.name.variable"), text),
    )
    return """
        {"name":"$name","type":"${if (isDark) "dark" else "light"}",
         "colors":{
           "editor.background":"${hex(background)}","editor.foreground":"${hex(text)}",
           "editorLineNumber.foreground":"${hex(lineNumber)}","editorLineNumber.activeForeground":"${hex(lineNumberActive)}",
           "editor.lineHighlightBackground":"${hex(activeLine)}","editor.selectionBackground":"${hex(selection)}",
           "editorCursor.foreground":"${hex(cursor)}",
           "editorIndentGuide.background":"${hex(indentGuide)}","editorIndentGuide.activeBackground":"${hex(indentGuideActive)}"
         },
         "tokenColors":[${rules.joinToString(",")}]}
    """.trimIndent()
}
