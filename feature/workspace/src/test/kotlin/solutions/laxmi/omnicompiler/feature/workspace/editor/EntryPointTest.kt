package solutions.laxmi.omnicompiler.feature.workspace.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EntryPointTest {

    @Test
    fun `finds main in compiled languages`() {
        assertThat(EntryPoint.line("cpp", "#include <iostream>\n\nint main() {\n  return 0;\n}")).isEqualTo(3)
        assertThat(EntryPoint.line("java", "class Main {\n    public static void main(String[] args) {}\n}")).isEqualTo(2)
        assertThat(EntryPoint.line("go", "package main\n\nfunc main() {\n}")).isEqualTo(3)
        assertThat(EntryPoint.line("rust", "use std::io;\nfn main() {\n}")).isEqualTo(2)
        assertThat(EntryPoint.line("csharp", "class P {\n  static void Main(string[] a) {}\n}")).isEqualTo(2)
        assertThat(EntryPoint.line("kotlin", "fun main() {\n}")).isEqualTo(1)
    }

    @Test
    fun `python uses the __main__ guard`() {
        assertThat(EntryPoint.line("python", "def solve():\n    pass\n\nif __name__ == \"__main__\":\n    solve()")).isEqualTo(4)
    }

    @Test
    fun `helpers named like main and script languages give no line`() {
        assertThat(EntryPoint.line("cpp", "int mainLoop() {}\nint domain(int x);")).isNull()
        assertThat(EntryPoint.line("go", "func mainHelper() {}")).isNull()
        assertThat(EntryPoint.line("ruby", "def main\nend")).isNull()
        assertThat(EntryPoint.line(null, "int main() {}")).isNull()
    }
}
