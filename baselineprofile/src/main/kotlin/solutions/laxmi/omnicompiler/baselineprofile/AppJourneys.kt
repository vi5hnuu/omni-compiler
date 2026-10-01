package solutions.laxmi.omnicompiler.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** The release-like build under test (benchmarkRelease / nonMinifiedRelease keep the release application id). */
internal const val PACKAGE = "solutions.laxmi.omnicompiler"

private const val TIMEOUT_MS = 15_000L
private val CASE_INSENSITIVE = Pattern.CASE_INSENSITIVE

/**
 * Gets past first-run screens the way a user would: continue as a guest, then pick the projects folder in the
 * system picker (which opens in Documents). Does nothing once the editor is showing.
 */
internal fun MacrobenchmarkScope.ensureReady() {
    device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), TIMEOUT_MS)
    device.findObject(By.text("Continue as guest"))?.let { guest ->
        guest.click()
        device.wait(Until.gone(By.text("Continue as guest")), TIMEOUT_MS)
    }
    device.wait(Until.findObject(By.text("Choose folder")), 2_000)?.let { choose ->
        choose.click()
        device.wait(Until.findObject(By.text(Pattern.compile("use this folder", CASE_INSENSITIVE))), TIMEOUT_MS)?.click()
        device.wait(Until.findObject(By.text(Pattern.compile("allow", CASE_INSENSITIVE))), TIMEOUT_MS)?.click()
    }
    check(device.wait(Until.hasObject(By.desc("Open drawer")), TIMEOUT_MS)) { "The editor did not open" }
}

/** Opens [LargeProject] from the drawer and waits for its files. */
internal fun MacrobenchmarkScope.openLargeProject() {
    device.findObject(By.desc("Open drawer")).click()
    val row = device.wait(Until.findObject(By.text(LargeProject.NAME)), TIMEOUT_MS) ?: error("${LargeProject.NAME} is not listed")
    row.click()
    device.wait(Until.hasObject(By.text(LargeProject.BIG_FILE)), TIMEOUT_MS)
    device.waitForIdle()
}

/** Switches to the large file and back to the entry file through the tabs. */
internal fun MacrobenchmarkScope.switchFiles() {
    device.findObject(By.text(LargeProject.BIG_FILE)).click()
    device.waitForIdle()
    device.findObject(By.text(LargeProject.ENTRY)).click()
    device.waitForIdle()
    device.findObject(By.text(LargeProject.BIG_FILE)).click()
    device.waitForIdle()
}

/** Flings through the open file, down then up, in the middle of the screen where the code is. */
internal fun MacrobenchmarkScope.flingCode() {
    val x = device.displayWidth / 2
    val top = device.displayHeight / 4
    val bottom = device.displayHeight * 3 / 5
    repeat(3) {
        device.swipe(x, bottom, x, top, FLING_STEPS)
        device.waitForIdle()
    }
    repeat(3) {
        device.swipe(x, top, x, bottom, FLING_STEPS)
        device.waitForIdle()
    }
}

/** Opens the drawer, scrolls it and closes it with Back. */
internal fun MacrobenchmarkScope.openAndCloseDrawer() {
    device.findObject(By.desc("Open drawer")).click()
    device.wait(Until.findObject(By.text("Settings")), TIMEOUT_MS)
        ?: device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 1f)
    device.pressBack()
    device.waitForIdle()
}

/** Few steps make a fast swipe, i.e. a fling. */
private const val FLING_STEPS = 6
