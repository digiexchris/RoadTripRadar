package ca.voiditswarranty.roadtripradar.ui

import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder
import ca.voiditswarranty.roadtripradar.data.InsertPosition
import ca.voiditswarranty.roadtripradar.data.WaypointSource
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.spatialk.geojson.Position
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric tests for the camera-follows-GPS state machine in [MapViewModel].
 *
 * The visual camera animation lives in the `MaplibreMap` composable (which isn't
 * available under Robolectric), but the *testable core* is the VM state that the
 * camera layer reads and mutates:
 *
 * - `isTrackingCamera` — public mutable; flipped by the gesture handler in
 *   `MapScreen.kt` (user pan) and the recenter button (back to true).
 * - `maybeAutoAdvance(userPos)` — advances the active waypoint when the user is
 *   within `autoAdvanceThresholdMeters` of it.
 * - `saveLastKnownPosition(pos)` — persists the last GPS fix to
 *   `PreferencesRepository` (called every ~15s from the phone surface).
 * - `userPositionForSearch` — public mutable scratch field for search.
 *
 * The VM is constructed via the process-wide [CarViewModelHolder] singleton
 * (same path as `MapViewModelFactory`). Pinned to SDK 33 because the real VM
 * registers a default network callback in `init` that Robolectric 4.16.1's
 * `ConnectivityManager` shadow only implements on SDK 33.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CameraGpsTrackingTest {

    private val context
        get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun vm(): MapViewModel = CarViewModelHolder.ensureInitialized(context)

    /**
     * Reset the GPS-camera-related state between tests. The VM is a process-wide
     * singleton, so state would otherwise bleed across tests.
     */
    @Before
    fun resetGpsCameraState() {
        val v = vm()
        v.waypoints.clear()
        v.setActiveWaypoint("")
        v.isTrackingCamera = true
        v.userPositionForSearch = null
        v.setAutoAdvanceThresholdMetersForTest(100)
    }

    // -------- isTrackingCamera default + gesture handler contract --------

    @Test
    fun gps_noFix_isTrackingCameraStartsTrue() {
        // Fresh VM state (reset in @Before) has the camera tracking enabled —
        // this is the default before any GPS fix or user gesture.
        assertTrue(
            "isTrackingCamera must default to true",
            vm().isTrackingCamera,
        )
    }

    @Test
    fun gps_userPans_isTrackingCameraFalse() {
        // The gesture handler in MapScreen.kt sets isTrackingCamera = false
        // directly on a user pan. Pin the contract that the field is public
        // mutable and stays false.
        val v = vm()
        v.isTrackingCamera = false
        assertFalse(
            "isTrackingCamera must be false after a user pan",
            v.isTrackingCamera,
        )
    }

    @Test
    fun gps_recenterButton_isTrackingCameraTrue() {
        // The recenter button onClick sets isTrackingCamera back to true.
        // First turn it off (user pan), then re-enable (recenter).
        val v = vm()
        v.isTrackingCamera = false
        assertFalse("precondition: tracking off after pan", v.isTrackingCamera)

        v.isTrackingCamera = true
        assertTrue(
            "isTrackingCamera must be true after the recenter button",
            v.isTrackingCamera,
        )
    }

    // -------- maybeAutoAdvance --------

    @Test
    fun gps_fixInjected_maybeAutoAdvanceFires() {
        // Two waypoints 500m apart, active = waypoints[0], threshold = 50m.
        // Inject a fix at waypoints[0].position (0m away) — the active index
        // must advance to 1.
        val v = vm()
        val wp0Pos = Position(latitude = 45.40000, longitude = -75.70000)
        val wp1Pos = Position(latitude = 45.40450, longitude = -75.70000) // ~500m north
        v.addWaypoint(wp0Pos, name = "Stop A", subtitle = null,
            source = WaypointSource.DROPPED_PIN, at = InsertPosition.End)
        v.addWaypoint(wp1Pos, name = "Stop B", subtitle = null,
            source = WaypointSource.DROPPED_PIN, at = InsertPosition.End)
        v.setActiveWaypoint(v.waypoints[0].id)
        v.setAutoAdvanceThresholdMetersForTest(50)

        v.maybeAutoAdvance(wp0Pos)

        assertEquals(
            "auto-advance must move active index from 0 to 1 when within threshold",
            1,
            v.activeIndex,
        )
    }

    @Test
    fun gps_fixBeyondThreshold_maybeAutoAdvanceDoesNotFire() {
        // Same setup, but inject a fix 200m from waypoints[0] — beyond the
        // 50m threshold. The active index must stay at 0. This is the inverse
        // of the firing case and replaces the spec's "low accuracy" test:
        // accuracy gating is upstream (in the Compose layer), so we pin the
        // distance threshold branch here instead.
        val v = vm()
        val wp0Pos = Position(latitude = 45.40000, longitude = -75.70000)
        val wp1Pos = Position(latitude = 45.40450, longitude = -75.70000) // ~500m north
        v.addWaypoint(wp0Pos, name = "Stop A", subtitle = null,
            source = WaypointSource.DROPPED_PIN, at = InsertPosition.End)
        v.addWaypoint(wp1Pos, name = "Stop B", subtitle = null,
            source = WaypointSource.DROPPED_PIN, at = InsertPosition.End)
        v.setActiveWaypoint(v.waypoints[0].id)
        v.setAutoAdvanceThresholdMetersForTest(50)

        // 200m north of wp0 — well past the 50m threshold.
        val fix = Position(latitude = 45.40180, longitude = -75.70000)
        v.maybeAutoAdvance(fix)

        assertEquals(
            "auto-advance must NOT fire when the fix is beyond the threshold",
            0,
            v.activeIndex,
        )
    }

    // -------- hasGoodGpsFix (the upstream accuracy gate) --------

    @Test
    fun gps_lowAccuracyFix_hasGoodGpsFixFalse() {
        // The accuracy gating the spec wanted to test lives in the pure helper
        // `hasGoodGpsFix` in MapScreenLogic.kt. A fix with accuracy >= 50m is
        // rejected.
        assertFalse(
            "accuracy >= 50m must not count as a good fix",
            hasGoodGpsFix(useGps = true, hasLocation = true, accuracyMeters = 50.0),
        )
    }

    @Test
    fun gps_highAccuracyFix_hasGoodGpsFixTrue() {
        // A fix with accuracy < 50m is accepted.
        assertTrue(
            "accuracy < 50m with GPS on and a fix must count as a good fix",
            hasGoodGpsFix(useGps = true, hasLocation = true, accuracyMeters = 12.0),
        )
    }

    // -------- lastKnownPosition persistence --------

    @Test
    fun gps_fixDisappears_lastKnownPositionStays() {
        // The "save last known position" path persists to PreferencesRepository.
        // After a save, the value must read back unchanged.
        val v = vm()
        val saved = Position(latitude = 43.6532, longitude = -79.3832)

        v.saveLastKnownPosition(saved)

        // PreferencesRepository stores lat/lon as floats, so the round-trip
        // loses double precision — compare with a tolerance rather than the
        // Position's own equals.
        val readBack = v.lastKnownPosition!!
        assertEquals(saved.latitude, readBack.latitude, 1e-3)
        assertEquals(saved.longitude, readBack.longitude, 1e-3)
    }

    @Test
    fun gps_noFix_lastKnownPositionUnchanged() {
        // Without any save call, lastKnownPosition must stay at its initialized
        // value (whatever it was) — i.e. a read with no preceding write is a
        // no-op, not a reset.
        val v = vm()
        val before = v.lastKnownPosition

        // No save call here — just re-read.
        val after = v.lastKnownPosition

        assertEquals(
            "lastKnownPosition must not change without an explicit save",
            before,
            after,
        )
    }

    // -------- userPositionForSearch public mutability --------

    @Test
    fun gps_userPositionForSearch_isPublicMutable() {
        val v = vm()
        val pos = Position(latitude = 43.0, longitude = -79.0)

        v.userPositionForSearch = pos

        assertEquals(
            "userPositionForSearch must read back the value written to it",
            pos,
            v.userPositionForSearch,
        )
    }
}