package solutions.laxmi.omnicompiler.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LimitsTest {
    @Test
    fun `raising never lowers user limits`() {
        assertThat(Limits(8_000, 128).raisedTo(Limits(10_000, 64))).isEqualTo(Limits(10_000, 128))
    }

    @Test
    fun `clamps to judge ceilings`() {
        assertThat(Limits(60_000, 4_096).clamped()).isEqualTo(Limits(Limits.MAX_TIME_MS, Limits.MAX_MEM_MB))
    }
}
