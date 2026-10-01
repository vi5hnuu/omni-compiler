package solutions.laxmi.omnicompiler.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code paths of a typical session (start, open a large multi-file project, switch files, scroll, use
 * the drawer) into the app's baseline profile, so they run precompiled from the first launch.
 *
 * Run with `./gradlew :app:generateBaselineProfile` on a connected device; the result is written to
 * `app/src/main/generated/baselineProfiles/` and should be committed.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        LargeProject.install(device)
        pressHome()
        startActivityAndWait()
        ensureReady()
        openLargeProject()
        switchFiles()
        flingCode()
        openAndCloseDrawer()
    }
}
