package ca.voiditswarranty.roadtripradar.ui

import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.data.PreferencesRepository
import ca.voiditswarranty.roadtripradar.data.isDarkForAppTheme
import ca.voiditswarranty.roadtripradar.data.resolvedStyleUri
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Robolectric tests for the map-style + theme-switching state machine.
 *
 * The map-rendering side of style switching is fundamentally visual and MapLibre-coupled
 * (a `MaplibreMap` keyed on `(mapStyleUri, customThemeVersion)`), but the **state machine**
 * driving it is fully testable on the JVM:
 *
 *  - `MapViewModel.setMapStyleForTest` flips the `mapStyle` Compose state.
 *  - `MapStyle.isDarkForAppTheme(context)` collapses `AUTO` against the system night mode
 *    (read from the `Configuration`) and reports whether the resolved style is dark.
 *  - `MapStyle.resolvedStyleUri(context)` returns the URI the map loads.
 *  - `MapStyle.intrinsicallyDark` pins which enum variants are inherently dark.
 *
 * Tests 1-3 exercise the `setMapStyleForTest` seam and the dark/light resolution together;
 * tests 4-5 drive the `AUTO` collapse via Robolectric's `RuntimeEnvironment.setQualifiers`
 * to flip the system night-mode flag; tests 6-7 pin the resolved style URI; test 8 pins the
 * `intrinsicallyDark` contract for every enum value.
 *
 * The VM is constructed directly (not via `MapViewModelFactory`) to avoid the
 * process-wide `CarViewModelHolder` singleton leaking state between tests — same pattern
 * as `BaseCarScreenTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapStyleSwitchingTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Fresh VM per test — bypasses the `CarViewModelHolder` singleton. */
    private fun freshVm(): MapViewModel = MapViewModel(
        appContext = context,
        prefsRepo = PreferencesRepository(context),
        externalScope = CoroutineScope(Dispatchers.Unconfined),
    )

    // -------- style switching flips isDarkForAppTheme --------

    @Test
    fun style_libertyToDark_isDarkForAppThemeBecomesTrue() {
        // The persisted default is AUTO (a meta-style), so set LIBERTY explicitly
        // before asserting the LIBERTY -> DARK switch flips isDarkForAppTheme.
        val vm = freshVm()
        vm.setMapStyleForTest(MapStyle.LIBERTY)
        assertEquals(MapStyle.LIBERTY, vm.mapStyle)
        assertFalse(MapStyle.LIBERTY.isDarkForAppTheme(context))

        vm.setMapStyleForTest(MapStyle.DARK)
        assertEquals(MapStyle.DARK, vm.mapStyle)
        assertTrue(MapStyle.DARK.isDarkForAppTheme(context))
    }

    @Test
    fun style_darkToLiberty_isDarkForAppThemeBecomesFalse() {
        val vm = freshVm()
        vm.setMapStyleForTest(MapStyle.DARK)
        assertTrue(MapStyle.DARK.isDarkForAppTheme(context))

        vm.setMapStyleForTest(MapStyle.LIBERTY)
        assertEquals(MapStyle.LIBERTY, vm.mapStyle)
        assertFalse(MapStyle.LIBERTY.isDarkForAppTheme(context))
    }

    @Test
    fun style_colorDarkToLiberty_isDarkForAppThemeBecomesFalse() {
        val vm = freshVm()
        vm.setMapStyleForTest(MapStyle.COLOR_DARK)
        assertTrue(MapStyle.COLOR_DARK.isDarkForAppTheme(context))

        vm.setMapStyleForTest(MapStyle.LIBERTY)
        assertEquals(MapStyle.LIBERTY, vm.mapStyle)
        assertFalse(MapStyle.LIBERTY.isDarkForAppTheme(context))
    }

    // -------- AUTO collapses against the system night mode --------

    @Test
    fun style_autoWithSystemLight_resolvesToLiberty() {
        // Day mode: AUTO collapses to LIBERTY (light), so isDarkForAppTheme is false.
        // Robolectric uses Android resource qualifiers — `notnight` for day.
        RuntimeEnvironment.setQualifiers("+notnight")
        assertFalse(MapStyle.AUTO.isDarkForAppTheme(context))
    }

    @Test
    fun style_autoWithSystemDark_resolvesToColorDark() {
        // Night mode: AUTO collapses to COLOR_DARK (dark), so isDarkForAppTheme is true.
        // Robolectric uses Android resource qualifiers — `night` for night.
        RuntimeEnvironment.setQualifiers("+night")
        assertTrue(MapStyle.AUTO.isDarkForAppTheme(context))
    }

    // -------- resolvedStyleUri --------

    @Test
    fun style_resolvedStyleUri_liberty_returnsHttpsUrl() {
        assertEquals(
            "https://tiles.openfreemap.org/styles/liberty",
            MapStyle.LIBERTY.resolvedStyleUri(context),
        )
    }

    @Test
    fun style_resolvedStyleUri_dark_returnsAssetUri() {
        assertEquals(
            "asset://dark.json",
            MapStyle.DARK.resolvedStyleUri(context),
        )
    }

    // -------- intrinsicallyDark contract --------

    @Test
    fun style_intrinsicallyDark_pinsAllVariants() {
        // Pins which enum variants are inherently dark (ignoring AUTO, which is a
        // meta-style whose darkness depends on the system theme at resolve time).
        assertFalse(MapStyle.LIBERTY.intrinsicallyDark)
        assertTrue(MapStyle.DARK.intrinsicallyDark)
        assertTrue(MapStyle.COLOR_DARK.intrinsicallyDark)
        assertFalse(MapStyle.CUSTOM_LIGHT.intrinsicallyDark)
        assertTrue(MapStyle.CUSTOM_DARK.intrinsicallyDark)
        assertFalse(MapStyle.AUTO.intrinsicallyDark)
    }
}