package solutions.laxmi.omnicompiler.feature.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PasswordStrengthTest {
    @Test
    fun `length rules mirror the auth service`() {
        assertThat(PasswordStrength.of("")).isEqualTo(PasswordStrength.Empty)
        assertThat(PasswordStrength.of("short1!")).isEqualTo(PasswordStrength.TooShort)
    }

    @Test
    fun `mixed long passwords rate strong`() {
        assertThat(PasswordStrength.of("Correct-Horse-9")).isEqualTo(PasswordStrength.Strong)
        assertThat(PasswordStrength.of("aaaaaaaa")).isEqualTo(PasswordStrength.Weak)
    }
}
