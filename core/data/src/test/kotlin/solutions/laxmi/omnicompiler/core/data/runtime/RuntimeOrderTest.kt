package solutions.laxmi.omnicompiler.core.data.runtime

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import solutions.laxmi.omnicompiler.core.model.Lane
import solutions.laxmi.omnicompiler.core.model.Runtime
import solutions.laxmi.omnicompiler.core.model.RuntimeStatus

class RuntimeOrderTest {

    private fun rt(version: String, status: RuntimeStatus = RuntimeStatus.READY) =
        Runtime("cpp-$version", "cpp", version, status, "solution.cpp", available = true, lane = Lane.HOT)

    @Test
    fun `catalog order wins over numeric order for standard years`() {
        val sorted = listOf(rt("98"), rt("11"), rt("23"), rt("17")).sortedWith(runtimeOrder(listOf("23", "17", "11", "98")))
        assertThat(sorted.map { it.version }).containsExactly("23", "17", "11", "98").inOrder()
    }

    @Test
    fun `ready comes first and unlisted versions follow newest first`() {
        val sorted = listOf(rt("23", RuntimeStatus.BUILDING), rt("17"), rt("2.1"), rt("2.10")).sortedWith(runtimeOrder(listOf("23", "17")))
        assertThat(sorted.map { it.version }).containsExactly("17", "2.10", "2.1", "23").inOrder()
    }
}
