package solutions.laxmi.omnicompiler.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import solutions.laxmi.omnicompiler.core.data.execution.CompilerOutputParser
import solutions.laxmi.omnicompiler.core.data.runtime.compareVersions
import solutions.laxmi.omnicompiler.core.model.CompileDiagnostic
import solutions.laxmi.omnicompiler.core.model.CompileProblem

class CompilerOutputParserTest {

    @Test
    fun `parses gcc errors and warnings`() {
        val output = """
            /workspace/main.cpp: In function 'int main()':
            /workspace/main.cpp:12:31: error: expected ';' before 'int'
            solver.h:8:9: warning: unused variable 'k' [-Wunused-variable]
            main.cpp:12:31: note: some note
        """.trimIndent()
        val problems = CompilerOutputParser.parse(output, structured = null, entryFileName = "main.cpp")
        assertThat(problems).containsExactly(
            CompileProblem("main.cpp", 12, 31, "expected ';' before 'int'", isError = true),
            CompileProblem("solver.h", 8, 9, "unused variable 'k' [-Wunused-variable]", isError = false),
        ).inOrder()
    }

    @Test
    fun `adds the judge's structured error when stderr has no location`() {
        val problems = CompilerOutputParser.parse("Syntax error", CompileDiagnostic(3, 5, "Syntax error"), entryFileName = "solution.py")
        assertThat(problems).containsExactly(CompileProblem("solution.py", 3, 5, "Syntax error", isError = true))
    }

    @Test
    fun `ignores timestamps and urls`() {
        val problems = CompilerOutputParser.parse("at 12:30:45 see https://x.dev:443/a", structured = null, entryFileName = null)
        assertThat(problems).isEmpty()
    }

    @Test
    fun `versions compare numerically`() {
        assertThat(compareVersions("3.10", "3.9")).isGreaterThan(0)
        assertThat(compareVersions("1.22", "1.22")).isEqualTo(0)
        assertThat(compareVersions("17", "21")).isLessThan(0)
    }
}
