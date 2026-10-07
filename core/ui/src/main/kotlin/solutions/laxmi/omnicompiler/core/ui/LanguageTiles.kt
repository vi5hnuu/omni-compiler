package solutions.laxmi.omnicompiler.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import solutions.laxmi.omnicompiler.core.designsystem.theme.OmniTheme

/** 2-letter mono language tile (C+, Py, Jv…); [selected] turns it red like the active language. */
@Composable
fun LanguageTile(code: String, modifier: Modifier = Modifier, selected: Boolean = false, size: Dp = 28.dp) {
    val colors = OmniTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .background(if (selected) colors.accent else colors.surfaceRaised)
            .then(if (selected) Modifier else Modifier.border(1.dp, colors.divider)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = code,
            style = OmniTheme.typography.badge.copy(fontSize = (size.value * 0.38f).sp),
            color = if (selected) colors.onAccent else colors.textPrimary,
        )
    }
}

/** Short tag shown before a file name in tabs and lists: the language code, `H`, or `in` for data. */
fun fileBadgeFor(fileName: String, entryShortCode: String?, isEntry: Boolean): String {
    if (isEntry && entryShortCode != null) return entryShortCode
    val name = fileName.substringAfterLast('/')
    return when (name.substringAfterLast('.', "").lowercase()) {
        "h", "hh", "hpp", "hxx" -> "H"
        "txt", "in", "dat", "csv", "tsv" -> "in"
        "out" -> "out"
        "" -> "·"
        else -> name.substringAfterLast('.').take(2).replaceFirstChar { it.uppercase() }
    }
}
