package ca.voiditswarranty.roadtripradar.ui

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.delay
import org.junit.Rule
import org.junit.runner.RunWith
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Base class for phone-surface Robolectric Compose tests.
 *
 * Provides:
 *  - [composeTestRule] for Compose UI assertions.
 *  - [context] for [ApplicationProvider.getApplicationContext].
 *  - [runOnMain] for safe UI-thread work.
 *  - [awaitMapIdle] for waiting on MapLibre's native map to settle.
 *  - [awaitThemeLoaded] for waiting on the VM's `customThemeVersion` to
 *    increment after a theme change.
 *
 * Subclasses set up the host content (typically `setContent { ... }` with
 * the composable under test) and call these helpers. GPS location
 * injection is handled by the VM's `setUserPositionForTest()` seam and
 * the `userPosition` Compose state rather than a LocationManager test
 * provider.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
abstract class BasePhoneScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    protected val context: Context
        get() = ApplicationProvider.getApplicationContext()

    /**
     * Run [action] on the main thread. Useful for VM mutations that must
     * happen on the UI thread.
     */
    protected fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            composeTestRule.runOnUiThread { action() }
        }
    }

    /**
     * Wait for MapLibre's native map to settle. Polls
     * [CameraState.moveReason] until it is [CameraMoveReason.NONE] or the
     * timeout elapses. Returns true if the map settled; false on timeout.
     */
    protected suspend fun awaitMapIdle(
        cameraState: CameraState,
        timeoutMs: Long = 5000L,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val reason = cameraState.moveReason
            if (reason == CameraMoveReason.NONE) return true
            delay(50)
        }
        return false
    }

    /**
     * Wait for the VM's `customThemeVersion` to change from [before]. Returns
     * the new version (or [before] on timeout). The caller reads the
     * VM's version via its existing accessor (e.g. a `setCustomThemeVersionForTest`
     * seam or direct field read).
     */
    protected suspend fun awaitThemeLoaded(
        getCurrentVersion: () -> Int,
        before: Int,
        timeoutMs: Long = 5000L,
    ): Int {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val current = getCurrentVersion()
            if (current != before) return current
            delay(50)
        }
        return getCurrentVersion()
    }
}