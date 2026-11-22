package solutions.laxmi.omnicompiler.core.appupdate

import com.google.common.truth.Truth.assertThat
import com.google.android.play.core.install.model.UpdateAvailability
import org.junit.Test

class MustUpdateNowTest {

    private val config = UpdateConfig(immediatePriority = 4)

    @Test
    fun `a high-priority release is required`() {
        assertThat(mustUpdateNow(UpdateAvailability.UPDATE_AVAILABLE, 4, immediateAllowed = true, config)).isTrue()
        assertThat(mustUpdateNow(UpdateAvailability.UPDATE_AVAILABLE, 5, immediateAllowed = true, config)).isTrue()
    }

    @Test
    fun `lower priorities are left to Play auto-update`() {
        assertThat(mustUpdateNow(UpdateAvailability.UPDATE_AVAILABLE, 3, immediateAllowed = true, config)).isFalse()
    }

    @Test
    fun `nothing runs when Play does not allow an immediate update`() {
        assertThat(mustUpdateNow(UpdateAvailability.UPDATE_AVAILABLE, 5, immediateAllowed = false, config)).isFalse()
    }

    @Test
    fun `an interrupted update is always resumed`() {
        assertThat(mustUpdateNow(UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS, 0, immediateAllowed = false, config)).isTrue()
    }

    @Test
    fun `no update, no prompt`() {
        assertThat(mustUpdateNow(UpdateAvailability.UPDATE_NOT_AVAILABLE, 5, immediateAllowed = true, config)).isFalse()
        assertThat(mustUpdateNow(UpdateAvailability.UNKNOWN, 5, immediateAllowed = true, config)).isFalse()
    }
}
