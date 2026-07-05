package ca.voiditswarranty.roadtripradar.car

import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.data.OpenMeteoSnapshot
import ca.voiditswarranty.roadtripradar.data.PreferencesRepository
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behaviour tests for [CarWeatherWidget] — the right-side weather card drawn on top of
 * the car map. The widget reads [MapViewModel.windEnabled], [MapViewModel.openMeteoSnapshot],
 * and the unit preferences on every [CarWeatherWidget.update] call, formats the
 * temperature + wind, and applies a theme-aware palette.
 *
 * The widget uses a real [MapViewModel] (constructed with a real
 * [PreferencesRepository] backed by Robolectric's `SharedPreferences`) so the test
 * exercises the same code path as production. The `isDark` flag the widget needs is
 * passed in directly (it comes from `CarContext.isDarkMode()` in production) so the
 * test doesn't need a real `CarContext` — Robolectric's `ApplicationProvider` provides
 * the layout-inflater Context.
 *
 * Pinned to SDK 33 because Robolectric 4.16.1's `ConnectivityManager` shadow doesn't
 * implement `registerDefaultNetworkCallback` on the compileSdk (36), which the real
 * `MapViewModel.init` calls. SDK 33 is well-supported and exercises the same code paths
 * the widget touches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CarWeatherWidgetTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Build a real MapViewModel; same construction as the production holder. */
    private fun freshVm(): MapViewModel {
        val prefs = PreferencesRepository(context)
        return MapViewModel(
            appContext = context,
            prefsRepo = prefs,
            externalScope = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )
    }

    private fun widget(vm: MapViewModel, isDark: Boolean = false) =
        CarWeatherWidget(context, vm, initialIsDark = isDark)

    private fun rootOf(w: CarWeatherWidget): View = w.view.findViewById(R.id.car_weather_root)
    private fun contentGroupOf(w: CarWeatherWidget): LinearLayout =
        w.view.findViewById(R.id.car_weather_content)
    private fun unavailableGroupOf(w: CarWeatherWidget): LinearLayout =
        w.view.findViewById(R.id.car_weather_unavailable)
    private fun tempTextOf(w: CarWeatherWidget): TextView =
        w.view.findViewById(R.id.car_weather_temp)
    private fun trendTextOf(w: CarWeatherWidget): TextView =
        w.view.findViewById(R.id.car_weather_trend)
    private fun arrowOf(w: CarWeatherWidget): ImageView =
        w.view.findViewById(R.id.car_weather_arrow)
    private fun windSpeedTextOf(w: CarWeatherWidget): TextView =
        w.view.findViewById(R.id.car_weather_wind_speed)
    private fun windUnitTextOf(w: CarWeatherWidget): TextView =
        w.view.findViewById(R.id.car_weather_wind_unit)
    private fun badgeOf(w: CarWeatherWidget): FrameLayout =
        w.view.findViewById(R.id.car_weather_badge)

    // -------- visibility --------

    @Test
    fun update_windDisabled_hidesRoot() {
        val vm = freshVm()
        vm.updateWindEnabled(false)
        val w = widget(vm)
        w.update(cameraBearingDegrees = 0.0)
        assertEquals(View.GONE, rootOf(w).visibility)
    }

    @Test
    fun update_windEnabled_showsRoot() {
        val vm = freshVm()
        vm.updateWindEnabled(true)
        val w = widget(vm)
        w.update(cameraBearingDegrees = 0.0)
        assertEquals(View.VISIBLE, rootOf(w).visibility)
    }

    // -------- content vs unavailable groups --------

    @Test
    fun update_noSnapshot_showsUnavailableGroup() {
        val vm = freshVm()
        vm.updateWindEnabled(true)
        // openMeteoSnapshot stays null (default).
        val w = widget(vm)
        w.update(cameraBearingDegrees = 0.0)
        assertEquals(View.VISIBLE, unavailableGroupOf(w).visibility)
        assertEquals(View.GONE, contentGroupOf(w).visibility)
    }

    @Test
    fun update_withSnapshot_showsContentGroup() {
        val vm = freshVm()
        vm.updateWindEnabled(true)
        vm.setOpenMeteoSnapshotForTest(
            OpenMeteoSnapshot(
                temperatureCelsius = 20.0,
                weatherCode = 0,
                windSpeedKmh = 10.0,
                windDirectionDeg = 0,
                windGustsKmh = 15.0,
                tempTrendCelsius = 1.0,
            )
        )
        val w = widget(vm)
        w.update(cameraBearingDegrees = 0.0)
        assertEquals(View.VISIBLE, contentGroupOf(w).visibility)
        assertEquals(View.GONE, unavailableGroupOf(w).visibility)
    }

    // -------- text content --------

    @Test
    fun update_withSnapshot_setsTemperatureText() {
        val vm = freshVm()
        vm.updateWindEnabled(true)
        vm.setOpenMeteoSnapshotForTest(
            OpenMeteoSnapshot(
                temperatureCelsius = 20.0,
                weatherCode = 0,
                windSpeedKmh = 10.0,
                windDirectionDeg = 0,
                windGustsKmh = 15.0,
                tempTrendCelsius = 1.0,
            )
        )
        val w = widget(vm)
        w.update(cameraBearingDegrees = 0.0)
        // Exact value depends on formatTemp + temperatureUnit, but the text must be
        // populated and contain something numeric.
        val tempText = tempTextOf(w).text.toString()
        assertNotEquals("", tempText)
        assertEquals(true, tempText.contains("20"))
    }

    @Test
    fun update_withSnapshot_setsWindSpeedAndUnit() {
        val vm = freshVm()
        vm.updateWindEnabled(true)
        vm.setOpenMeteoSnapshotForTest(
            OpenMeteoSnapshot(
                temperatureCelsius = 20.0,
                weatherCode = 0,
                windSpeedKmh = 10.0,
                windDirectionDeg = 0,
                windGustsKmh = 15.0,
                tempTrendCelsius = 1.0,
            )
        )
        val w = widget(vm)
        w.update(cameraBearingDegrees = 0.0)
        // windSpeedText reads e.g. "10↑15" (windValue(10)↑windValue(15)) — assert
        // both numbers are in the formatted string.
        val speedText = windSpeedTextOf(w).text.toString()
        assertEquals(true, speedText.contains("10"))
        assertEquals(true, speedText.contains("15"))
        assertEquals(true, speedText.contains("↑"))
        // windUnitText is the unit label (km/h, mph, or kn depending on prefs default).
        val unitText = windUnitTextOf(w).text.toString()
        assertNotEquals("", unitText)
    }

    @Test
    fun update_trendText_usesDashWhenTrendNull() {
        val vm = freshVm()
        vm.updateWindEnabled(true)
        vm.setOpenMeteoSnapshotForTest(
            OpenMeteoSnapshot(
                temperatureCelsius = 20.0,
                weatherCode = 0,
                windSpeedKmh = 10.0,
                windDirectionDeg = 0,
                windGustsKmh = 15.0,
                tempTrendCelsius = null,  // no trend available
            )
        )
        val w = widget(vm)
        w.update(cameraBearingDegrees = 0.0)
        // The widget uses a literal "—/h" placeholder when trend is null (no resource
        // lookup). The em-dash is U+2014.
        assertEquals("—/h", trendTextOf(w).text.toString())
    }

    @Test
    fun update_trendText_formatsValueWhenTrendSet() {
        val vm = freshVm()
        vm.updateWindEnabled(true)
        vm.setOpenMeteoSnapshotForTest(
            OpenMeteoSnapshot(
                temperatureCelsius = 20.0,
                weatherCode = 0,
                windSpeedKmh = 10.0,
                windDirectionDeg = 0,
                windGustsKmh = 15.0,
                tempTrendCelsius = 1.5,
            )
        )
        val w = widget(vm)
        w.update(cameraBearingDegrees = 0.0)
        // Trend is set, so trendText should NOT be the em-dash placeholder.
        val trendText = trendTextOf(w).text.toString()
        assertNotEquals("—/h", trendText)
        assertNotEquals("", trendText)
    }

    // -------- arrow rotation --------

    @Test
    fun update_arrowRotation_isWindDirectionRelativeToCameraBearing() {
        // Wind direction is where the wind comes FROM. The arrow rotation tracks the
        // map bearing so the wind direction stays oriented as the map rotates — same
        // math as the phone WeatherWidget.
        val vm = freshVm()
        vm.updateWindEnabled(true)
        // Wind from the north (windDirectionDeg = 0) → arrow rotation should match
        // the camera-bearing offset only (the phone computes this via
        // windArrowRotationDeg(windDirection, cameraBearing)).
        vm.setOpenMeteoSnapshotForTest(
            OpenMeteoSnapshot(
                temperatureCelsius = 20.0,
                weatherCode = 0,
                windSpeedKmh = 10.0,
                windDirectionDeg = 0,
                windGustsKmh = 15.0,
                tempTrendCelsius = null,
            )
        )
        val w = widget(vm)
        w.update(cameraBearingDegrees = 0.0)
        // With wind from north and camera at 0°, the rotation depends on the exact
        // windArrowRotationDeg formula. We just assert the rotation is the same
        // numeric value the helper computes for these inputs.
        val expected = ca.voiditswarranty.roadtripradar.ui.windArrowRotationDeg(0, 0.0)
        assertEquals(expected, arrowOf(w).rotation, 0.5f)
    }

    // -------- palette / theme --------

    @Test
    fun update_dark_appliesDarkPalette() {
        val vm = freshVm()
        vm.updateWindEnabled(true)
        vm.setOpenMeteoSnapshotForTest(
            OpenMeteoSnapshot(
                temperatureCelsius = 20.0,
                weatherCode = 0,
                windSpeedKmh = 10.0,
                windDirectionDeg = 0,
                windGustsKmh = 15.0,
                tempTrendCelsius = 1.0,
            )
        )
        val w = widget(vm, isDark = true)
        w.update(cameraBearingDegrees = 0.0)
        val root = rootOf(w) as LinearLayout
        assertEquals(
            "dark background tint",
            CarWeatherWidget.darkBackground,
            root.backgroundTintList?.defaultColor,
        )
        assertEquals(
            "dark arrow tint",
            CarWeatherWidget.darkArrowTint,
            arrowOf(w).imageTintList?.defaultColor,
        )
        assertEquals(
            "dark badge bg",
            CarWeatherWidget.darkBadgeBg,
            badgeOf(w).backgroundTintList?.defaultColor,
        )
    }

    @Test
    fun update_light_appliesLightPalette() {
        val vm = freshVm()
        vm.updateWindEnabled(true)
        vm.setOpenMeteoSnapshotForTest(
            OpenMeteoSnapshot(
                temperatureCelsius = 20.0,
                weatherCode = 0,
                windSpeedKmh = 10.0,
                windDirectionDeg = 0,
                windGustsKmh = 15.0,
                tempTrendCelsius = 1.0,
            )
        )
        val w = widget(vm, isDark = false)
        w.update(cameraBearingDegrees = 0.0)
        val root = rootOf(w) as LinearLayout
        assertEquals(
            "light background tint",
            CarWeatherWidget.lightBackground,
            root.backgroundTintList?.defaultColor,
        )
        assertEquals(
            "light arrow tint",
            CarWeatherWidget.lightArrowTint,
            arrowOf(w).imageTintList?.defaultColor,
        )
        assertEquals(
            "light badge bg",
            CarWeatherWidget.lightBadgeBg,
            badgeOf(w).backgroundTintList?.defaultColor,
        )
    }

    // -------- setDark transitions --------

    @Test
    fun setDark_switchesPaletteWhenCalledAfterConstruction() {
        // Construct light, then switch to dark — the next update should paint with
        // the dark palette. This mirrors the production code path: CarMapContainer
        // calls setDark on style reload, and the next refresh paints with the new
        // palette.
        val vm = freshVm()
        vm.updateWindEnabled(true)
        vm.setOpenMeteoSnapshotForTest(
            OpenMeteoSnapshot(
                temperatureCelsius = 20.0,
                weatherCode = 0,
                windSpeedKmh = 10.0,
                windDirectionDeg = 0,
                windGustsKmh = 15.0,
                tempTrendCelsius = null,
            )
        )
        val w = widget(vm, isDark = false)
        w.update(cameraBearingDegrees = 0.0)
        // Light first.
        assertEquals(
            CarWeatherWidget.lightBackground,
            (rootOf(w) as LinearLayout).backgroundTintList?.defaultColor,
        )
        // Flip to dark and re-render.
        w.setDark(true)
        w.update(cameraBearingDegrees = 0.0)
        assertEquals(
            CarWeatherWidget.darkBackground,
            (rootOf(w) as LinearLayout).backgroundTintList?.defaultColor,
        )
    }

    @Test
    fun setDark_sameValue_isNoOp() {
        // setDark is called on every style reload by CarMapContainer. The widget
        // should be safe to call setDark with the same value repeatedly without
        // the next update painting incorrectly.
        val vm = freshVm()
        vm.updateWindEnabled(true)
        val w = widget(vm, isDark = false)
        w.update(cameraBearingDegrees = 0.0)
        val rootBefore = (rootOf(w) as LinearLayout).backgroundTintList?.defaultColor
        w.setDark(false)  // same value
        w.update(cameraBearingDegrees = 0.0)
        assertEquals(
            rootBefore,
            (rootOf(w) as LinearLayout).backgroundTintList?.defaultColor,
        )
    }
}
