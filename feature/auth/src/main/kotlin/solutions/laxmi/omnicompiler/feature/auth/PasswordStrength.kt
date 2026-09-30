package solutions.laxmi.omnicompiler.feature.auth

import androidx.annotation.StringRes
import solutions.laxmi.omnicompiler.core.ui.UiText

/** Four-segment meter from design A4. The auth service only enforces length (8–72). */
enum class PasswordStrength(val segments: Int, @StringRes val labelRes: Int?) {
    Empty(0, null),
    TooShort(1, R.string.password_too_short),
    Weak(1, R.string.password_weak),
    Fair(2, R.string.password_fair),
    Good(3, R.string.password_good),
    Strong(4, R.string.password_strong);

    companion object {
        const val MIN = 8
        const val MAX = 72

        fun of(password: String): PasswordStrength {
            if (password.isEmpty()) return Empty
            if (password.length < MIN) return TooShort
            val classes = listOf(
                password.any(Char::isLowerCase),
                password.any(Char::isUpperCase),
                password.any(Char::isDigit),
                password.any { !it.isLetterOrDigit() },
            ).count { it }
            val score = classes + (if (password.length >= 12) 1 else 0) + (if (password.length >= 16) 1 else 0)
            return when {
                score >= 5 -> Strong
                score >= 4 -> Good
                score >= 3 -> Fair
                else -> Weak
            }
        }

        /** Next improvement to suggest, or null when there's nothing obvious left. */
        fun hint(password: String): UiText? = when {
            password.length < MIN -> UiText.Plural(R.plurals.password_hint_min, MIN, MIN)
            !password.any { !it.isLetterOrDigit() } -> UiText.Res(R.string.password_hint_symbol)
            !password.any(Char::isDigit) -> UiText.Res(R.string.password_hint_number)
            password.length < 12 -> UiText.Res(R.string.password_hint_length)
            else -> null
        }
    }
}
