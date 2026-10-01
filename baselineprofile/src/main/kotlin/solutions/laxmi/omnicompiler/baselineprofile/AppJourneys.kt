package solutions.laxmi.omnicompiler.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until

/** The release-like build under test (benchmarkRelease / nonMinifiedRelease keep the release application id). */
internal const val PACKAGE = "solutions.laxmi.omnicompiler"

private const val TIMEOUT_MS = 15_000L
/** How long to look for an optional first-run step before assuming it was already done. */
private const val STEP_MS = 3_000L

/**
 * Gets past first-run screens the way a user would: continue as a guest, then pick the projects folder in the
 * system picker (which opens in Documents). Does nothing once the editor is showing.
 */
internal fun MacrobenchmarkScope.ensureReady() {
    device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), TIMEOUT_MS)
    // These screens recompose while they load (welcome counts, picker roots), so steps use UiObject, which finds its
    // node again on every action, instead of a UiObject2 snapshot that goes stale between lookup and click.
    val guest = device.findObject(UiSelector().text("Continue as guest"))
    if (guest.waitForExists(STEP_MS)) {
        guest.click()
        guest.waitUntilGone(TIMEOUT_MS)
    }
    val choose = device.findObject(UiSelector().text("Choose folder"))
    if (choose.waitForExists(STEP_MS)) {
        choose.click()
        device.findObject(UiSelector().textMatches("(?i)use this folder")).takeIf { it.waitForExists(TIMEOUT_MS) }?.click()
        device.findObject(UiSelector().textMatches("(?i)allow")).takeIf { it.waitForExists(TIMEOUT_MS) }?.click()
    }
    check(device.wait(Until.hasObject(By.desc("Open drawer")), TIMEOUT_MS)) { "The editor did not open" }
}

/** Opens [LargeProject] from the drawer and waits for its files. */
internal fun MacrobenchmarkScope.openLargeProject() {
    tap(UiSelector().description("Open drawer"))
    tap(UiSelector().text(LargeProject.NAME))
    device.wait(Until.hasObject(By.text(LargeProject.BIG_FILE)), TIMEOUT_MS)
    device.waitForIdle()
}

/** Switches to the large file and back to the entry file through the tabs. */
internal fun MacrobenchmarkScope.switchFiles() {
    listOf(LargeProject.BIG_FILE, LargeProject.ENTRY, LargeProject.BIG_FILE).forEach { name ->
        tap(UiSelector().text(name))
        device.waitForIdle()
    }
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
    tap(UiSelector().description("Open drawer"))
    // Long file lists push Settings below the fold; scrolling the drawer exercises its list too.
    if (!device.findObject(UiSelector().text("Settings")).waitForExists(STEP_MS)) {
        UiScrollable(UiSelector().scrollable(true)).takeIf { it.exists() }?.scrollToEnd(1)
    }
    device.pressBack()
    device.waitForIdle()
}

/** Waits for [selector] and taps it; fails the journey with the selector when it never appears. */
private fun MacrobenchmarkScope.tap(selector: UiSelector) {
    val target = device.findObject(selector)
    check(target.waitForExists(TIMEOUT_MS)) { "Not found: $selector" }
    target.click()
}

/** Few steps make a fast swipe, i.e. a fling. */
private const val FLING_STEPS = 6
