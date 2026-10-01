package solutions.laxmi.omnicompiler.baselineprofile

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File

/**
 * A large multi-file project (one ~6,000-line file plus helpers) placed in the projects folder like files copied in
 * from another app; the app's own sync picks it up when it comes to the foreground.
 */
internal object LargeProject {
    const val NAME = "bench-large"
    const val ENTRY = "solution.js"
    const val BIG_FILE = "big.js"

    /** The folder chosen in [ensureReady]: the picker's Documents, where the app keeps an OmniCompiler folder. */
    private const val PROJECTS_ROOT = "/sdcard/Documents/OmniCompiler"

    fun install(device: UiDevice) {
        // Written to the test APK's own external storage (no permission needed), then copied by the shell,
        // which can write to Documents; shell commands here take no redirection.
        val context = InstrumentationRegistry.getInstrumentation().context
        val staging = File(context.getExternalFilesDir(null), NAME).apply {
            deleteRecursively()
            mkdirs()
        }
        File(staging, ENTRY).writeText(ENTRY_SOURCE)
        File(staging, BIG_FILE).writeText(bigSource())
        (1..HELPER_FILES).forEach { n -> File(staging, "util$n.js").writeText(helperSource(n)) }
        device.executeShellCommand("mkdir -p $PROJECTS_ROOT")
        device.executeShellCommand("rm -r $PROJECTS_ROOT/$NAME")
        device.executeShellCommand("cp -r ${staging.absolutePath} $PROJECTS_ROOT/")
    }

    private const val HELPER_FILES = 5
    private const val BIG_BLOCKS = 1_200

    private val ENTRY_SOURCE = """
        const lines = [];
        process.stdin.on('data', d => lines.push(d.toString()));
        process.stdin.on('end', () => {
          const n = parseInt(lines.join('').trim());
          console.log(n * 2);
        });
    """.trimIndent() + "\n"

    private fun bigSource(): String = buildString {
        repeat(BIG_BLOCKS) { i ->
            appendLine("// block $i: compute something non-trivial")
            appendLine("function work$i(items, factor = ${i % 7 + 1}) {")
            appendLine("  const out = items.filter(x => x % ${i % 5 + 2} === 0).map((x, idx) => ({ id: idx, value: x * factor, label: `item-${'$'}{idx}` }));")
            appendLine("  return out.reduce((acc, it) => acc + it.value, 0) / Math.max(out.length, 1);")
            appendLine("}")
        }
    }

    private fun helperSource(n: Int): String = buildString {
        repeat(300) { k -> appendLine("export const v${n}_$k = (a, b) => a * $k + b; // helper $k") }
    }
}
