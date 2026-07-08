package ca.voiditswarranty.roadtripradar.ui

import android.os.Looper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sanity tests for [BasePhoneScreenTest]'s helpers. Pins that the
 * `runOnMain` helper runs on the main thread, the `awaitMapIdle` helper
 * doesn't hang when the camera is already settled, and the
 * `awaitThemeLoaded` helper detects a version change and returns the
 * new value.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BasePhoneScreenTestSmokeTest : BasePhoneScreenTest() {

    @Test
    fun smoke_runOnMain_runsOnMainThread() {
        var capturedLooper: Looper? = null
        runOnMain { capturedLooper = Looper.myLooper() }
        assertEquals(Looper.getMainLooper(), capturedLooper)
    }

    @Test
    fun smoke_awaitMapIdle_returnsWithinTimeout() = runBlocking {
        // The real CameraState(CameraPosition()) defaults to moveReason = NONE,
        // so awaitMapIdle returns true immediately. No stub subclass needed
        // (CameraState is final).
        val settled = awaitMapIdle(
            cameraState = CameraState(CameraPosition()),
            timeoutMs = 1000L,
        )
        assertTrue("expected the camera to settle within 1s", settled)
    }

    @Test
    fun smoke_awaitThemeLoaded_returnsCurrentValue() = runBlocking {
        val current = 42
        val result = awaitThemeLoaded(
            getCurrentVersion = { current },
            before = 41,
            timeoutMs = 200L,
        )
        // The current version differs from `before`, so the helper
        // detects the change on the first poll and returns the new value.
        assertEquals(current, result)
    }
}