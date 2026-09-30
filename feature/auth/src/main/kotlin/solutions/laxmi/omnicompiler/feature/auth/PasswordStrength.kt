package solutions.laxmi.omnicompiler.feature.auth

/** Four-segment meter from design A4. The auth service only enforces length (8–72). */
enum class PasswordStrength(val segments: Int, val label: String) {
    Empty(0, ""),
    TooShort(1, "Too short"),
    Weak(1, "Weak"),
    Fair(2, "Fair"),
    Good(3, "Good"),
    Strong(4, "Strong");

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
        fun hint(password: String): String? = when {
            password.length < MIN -> "Use at least $MIN characters"
            !password.any { !it.isLetterOrDigit() } -> "Add a symbol for max"
            !password.any(Char::isDigit) -> "Add a number"
            password.length < 12 -> "12+ characters is stronger"
            else -> null
        }
    }
}
