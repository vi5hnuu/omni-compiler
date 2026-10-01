package solutions.laxmi.omnicompiler.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Release-build numbers for startup and the large-file editor journey, without and with the baseline profile.
 * Run with `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest` on a connected device.
 */
@RunWith(AndroidJUnit4::class)
class PerformanceBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithProfile() = startup(CompilationMode.Partial())

    @Test
    fun largeFileWithoutProfile() = largeFile(CompilationMode.None())

    @Test
    fun largeFileWithProfile() = largeFile(CompilationMode.Partial())

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        ensureReady()
    }

    private fun largeFile(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = {
            LargeProject.install(device)
            pressHome()
            startActivityAndWait()
            ensureReady()
            openLargeProject()
        },
    ) {
        switchFiles()
        flingCode()
    }

    private companion object {
        const val ITERATIONS = 5
    }
}
