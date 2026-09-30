package solutions.laxmi.omnicompiler.core.ui

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalResources

/**
 * User-visible text produced outside the UI (ViewModels, error mapping). It is resolved against the
 * current configuration only when shown, so ViewModels never hold English strings.
 */
@Immutable
sealed interface UiText {
    /** Text that must not be translated: server messages, file names, ids. */
    data class Raw(val value: String) : UiText

    class Res(@StringRes val id: Int, vararg val args: Any) : UiText {
        override fun equals(other: Any?) = other is Res && id == other.id && args.contentEquals(other.args)
        override fun hashCode() = 31 * id + args.contentHashCode()
    }

    class Plural(@PluralsRes val id: Int, val count: Int, vararg val args: Any) : UiText {
        override fun equals(other: Any?) =
            other is Plural && id == other.id && count == other.count && args.contentEquals(other.args)
        override fun hashCode() = 31 * (31 * id + count) + args.contentHashCode()
    }

    fun asString(resources: Resources): String = when (this) {
        is Raw -> value
        is Res -> resources.getString(id, *resolveArgs(resources))
        is Plural -> resources.getQuantityString(id, count, *resolveArgs(resources))
    }

    /** Arguments may themselves be [UiText] (e.g. a message plus a localized suffix). */
    private fun resolveArgs(resources: Resources): Array<Any> {
        val raw = when (this) {
            is Res -> args
            is Plural -> args
            is Raw -> emptyArray()
        }
        return Array(raw.size) { i -> (raw[i] as? UiText)?.asString(resources) ?: raw[i] }
    }
}

/** Resolves in composition; recomposes when the configuration (language) changes. */
@Composable
fun UiText.asString(): String = asString(LocalResources.current)
