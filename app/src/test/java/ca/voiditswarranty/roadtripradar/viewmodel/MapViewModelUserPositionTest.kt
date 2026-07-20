package ca.voiditswarranty.roadtripradar.viewmodel

import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.spatialk.geojson.Position
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric tests for [MapViewModel.updateUserPosition] — the only app-code
 * change introduced to support the dev-only test harness server. The harness
 * exposes `userPosition` (and its accuracy/bearing/speed companions) over a
 * WebSocket; previously this state lived only as a local `val` in `MapScreen`
 * and was invisible to anything outside the composable.
 *
 * The VM is constructed directly (not via `MapViewModelFactory`) to avoid the
 * process-wide `CarViewModelHolder` singleton leaking state between tests —
 * same pattern as `MapStyleSwitchingTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapViewModelUserPositionTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun freshVm(): MapViewModel = MapViewModel(
        appContext = context,
        prefsRepo = PreferencesRepository(context),
        externalScope = CoroutineScope(Dispatchers.Unconfined),
    )

    @Test
    fun defaults_nullBeforeAnyCall() {
        val vm = freshVm()
        assertNull(vm.userPosition)
        assertNull(vm.userPositionAccuracy)
        assertNull(vm.userPositionBearing)
        assertNull(vm.userPositionSpeed)
    }

    @Test
    fun newPosition_setsAllFourFields() {
        val vm = freshVm()
        val pos = Position(latitude = 43.6534, longitude = -79.3847)

        vm.updateUserPosition(
            pos = pos,
            accuracy = 5.0,
            bearing = 180.0,
            speed = 12.5,
        )

        assertEquals(pos, vm.userPosition)
        assertEquals(5.0, vm.userPositionAccuracy!!, 1e-9)
        assertEquals(180.0, vm.userPositionBearing!!, 1e-9)
        assertEquals(12.5, vm.userPositionSpeed!!, 1e-9)
    }

    @Test
    fun nullPosition_clearsAllFourFields() {
        val vm = freshVm()
        vm.updateUserPosition(
            pos = Position(latitude = 43.6534, longitude = -79.3847),
            accuracy = 5.0,
            bearing = 180.0,
            speed = 12.5,
        )

        vm.updateUserPosition(pos = null, accuracy = null, bearing = null, speed = null)

        assertNull(vm.userPosition)
        assertNull(vm.userPositionAccuracy)
        assertNull(vm.userPositionBearing)
        assertNull(vm.userPositionSpeed)
    }

    @Test
    fun repeatedCalls_overwriteNotAccumulate() {
        val vm = freshVm()
        val pos1 = Position(latitude = 43.6534, longitude = -79.3847)
        val pos2 = Position(latitude = 40.7128, longitude = -74.0060)

        vm.updateUserPosition(pos1, accuracy = 5.0, bearing = 10.0, speed = 1.0)
        vm.updateUserPosition(pos2, accuracy = 25.0, bearing = 270.0, speed = 30.0)

        assertEquals(pos2, vm.userPosition)
        assertEquals(25.0, vm.userPositionAccuracy!!, 1e-9)
        assertEquals(270.0, vm.userPositionBearing!!, 1e-9)
        assertEquals(30.0, vm.userPositionSpeed!!, 1e-9)
    }
}