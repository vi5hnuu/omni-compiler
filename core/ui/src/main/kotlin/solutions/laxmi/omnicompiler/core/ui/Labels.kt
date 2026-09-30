package solutions.laxmi.omnicompiler.core.ui

import solutions.laxmi.omnicompiler.core.model.AppTheme
import androidx.annotation.StringRes
import solutions.laxmi.omnicompiler.core.model.CodeFont
import solutions.laxmi.omnicompiler.core.model.Difficulty
import solutions.laxmi.omnicompiler.core.model.EditorTheme
import solutions.laxmi.omnicompiler.core.model.LanguageCategory
import solutions.laxmi.omnicompiler.core.model.LineSpacing
import solutions.laxmi.omnicompiler.core.model.Verdict

/** Localized display names for domain enums (the model itself stays language-free). */
@get:StringRes
val Verdict.labelRes: Int
    get() = when (this) {
        Verdict.AC -> R.string.verdict_ac
        Verdict.WA -> R.string.verdict_wa
        Verdict.TLE -> R.string.verdict_tle
        Verdict.MLE -> R.string.verdict_mle
        Verdict.RE -> R.string.verdict_re
        Verdict.CE -> R.string.verdict_ce
        Verdict.IE -> R.string.verdict_ie
        Verdict.SK -> R.string.verdict_sk
    }

@get:StringRes
val EditorTheme.labelRes: Int
    get() = when (this) {
        EditorTheme.AUTO -> R.string.theme_auto
        EditorTheme.SIGNAL -> R.string.theme_signal
        EditorTheme.GRAPHITE -> R.string.theme_graphite
        EditorTheme.PAPER -> R.string.theme_paper
        EditorTheme.CONTRAST -> R.string.theme_contrast
    }

@get:StringRes
val EditorTheme.descriptionRes: Int
    get() = when (this) {
        EditorTheme.AUTO -> R.string.theme_auto_description
        EditorTheme.SIGNAL -> R.string.theme_signal_description
        EditorTheme.GRAPHITE -> R.string.theme_graphite_description
        EditorTheme.PAPER -> R.string.theme_paper_description
        EditorTheme.CONTRAST -> R.string.theme_contrast_description
    }

/** Font family names are proper nouns and are not translated. */
val CodeFont.displayName: String
    get() = when (this) {
        CodeFont.JETBRAINS_MONO -> "JetBrains Mono"
        CodeFont.FIRA_CODE -> "Fira Code"
        CodeFont.IBM_PLEX_MONO -> "IBM Plex"
    }

@get:StringRes
val LineSpacing.labelRes: Int
    get() = when (this) {
        LineSpacing.TIGHT -> R.string.line_spacing_tight
        LineSpacing.NORMAL -> R.string.line_spacing_normal
        LineSpacing.LOOSE -> R.string.line_spacing_loose
    }

@get:StringRes
val LanguageCategory.labelRes: Int
    get() = when (this) {
        LanguageCategory.COMPILED -> R.string.category_compiled
        LanguageCategory.SCRIPTING -> R.string.category_scripting
        LanguageCategory.JVM -> R.string.category_jvm
        LanguageCategory.FUNCTIONAL -> R.string.category_functional
        LanguageCategory.ESOTERIC -> R.string.category_esoteric
    }

@get:StringRes
val Difficulty.labelRes: Int
    get() = when (this) {
        Difficulty.EASY -> R.string.difficulty_easy
        Difficulty.MEDIUM -> R.string.difficulty_medium
        Difficulty.HARD -> R.string.difficulty_hard
    }

@get:StringRes
val AppTheme.labelRes: Int
    get() = when (this) {
        AppTheme.SYSTEM -> R.string.app_theme_system
        AppTheme.LIGHT -> R.string.app_theme_light
        AppTheme.DARK -> R.string.app_theme_dark
    }
