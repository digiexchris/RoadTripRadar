package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.OnDoneCallback
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder
import ca.voiditswarranty.roadtripradar.data.OpenMeteoSnapshot
import ca.voiditswarranty.roadtripradar.model.PrefsDefaults
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [WeatherScreen] — the car surface that surfaces weather / radar
 * settings: radar mode (off / on / playing), wind-arrow / legend / timeline
 * toggles, opacity preset cycle, unit pickers, metric toggle, and a parked-only
 * "Turn radar off" row. The screen builds a [ListTemplate] whose row structure
 * is fixed (a current-conditions header row + 8 settings rows); the test pins
 * the toggle states, the cycle-row behavior, and the click-wiring.
 *
 * Uses Robolectric + `TestCarContext` so [ListTemplate] / [Row] get built with a
 * real `Context`. Pinned to SDK 33 because the real [MapViewModel] registers a
 * default network callback in `init` that Robolectric 4.16.1's
 * `ConnectivityManager` shadow only implements on SDK 33.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WeatherScreenTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun vm() = CarViewModelHolder.ensureInitialized(context)

    private fun buildScreen(): ListTemplate {
        val carContext = TestCarContext.createCarContext(context)
        val screen = WeatherScreen(carContext)
        return screen.onGetTemplate() as ListTemplate
    }

    private fun rowsOf(template: ListTemplate): List<Row> =
        template.singleList!!.items.filterIsInstance<Row>()

    private fun rowTitles(template: ListTemplate): List<String> =
        rowsOf(template).map { it.title!!.toCharSequence().toString() }

    private fun rowByTitle(template: ListTemplate, title: String): Row =
        rowsOf(template).first { it.title!!.toCharSequence().toString() == title }

    private fun clickRow(row: Row) {
        row.onClickDelegate!!.sendClick(object : OnDoneCallback {})
    }

    private fun toggleRow(row: Row, checked: Boolean) {
        row.toggle!!.onCheckedChangeDelegate.sendCheckedChange(
            checked,
            object : OnDoneCallback {},
        )
    }

    /** Reset VM state to defaults (the VM is a process-wide singleton). */
    @Before
    fun resetWeather() {
        val v = vm()
        v.setOpenMeteoSnapshotForTest(null)
        v.updateWindEnabled(PrefsDefaults.WIND_ENABLED)
        v.updateShowLegend(PrefsDefaults.SHOW_LEGEND)
        v.updateShowTimeline(PrefsDefaults.SHOW_TIMELINE)
        v.updateRadarOpacity(PrefsDefaults.RADAR_OPACITY)
        v.saveRadarOpacity()
        v.updateUseMetric(PrefsDefaults.USE_METRIC)
    }

    // -------- structural contract --------

    @Test
    fun weatherScreen_titleIsWeatherTitle() {
        val template = buildScreen()
        val expected = context.getString(R.string.car_weather_title)
        assertEquals(expected, template.header!!.title!!.toCharSequence().toString())
    }

    @Test
    fun weatherScreen_hasBackHeaderAction() {
        val template = buildScreen()
        assertNotNull(
            "WeatherScreen is a pushed screen; must have a BACK header action",
            template.header!!.startHeaderAction,
        )
    }

    @Test
    fun weatherScreen_noSnapshot_showsNoDataRow() {
        // openMeteoSnapshot is null in @Before. The first row's title is the
        // "Weather unavailable" string.
        val template = buildScreen()
        val first = rowsOf(template).first()
        val title = first.title!!.toCharSequence().toString()
        assertEquals(
            "no-snapshot state must show the no-data row title",
            context.getString(R.string.car_weather_no_data),
            title,
        )
    }

    @Test
    fun weatherScreen_withSnapshot_showsTempAndCondition() {
        val v = vm()
        v.setOpenMeteoSnapshotForTest(
            OpenMeteoSnapshot(
                temperatureCelsius = 20.0,
                weatherCode = 0,  // clear sky
                windSpeedKmh = 10.0,
                windDirectionDeg = 90,
                windGustsKmh = 15.0,
                tempTrendCelsius = null,
            )
        )
        val template = buildScreen()
        val first = rowsOf(template).first()
        val title = first.title!!.toCharSequence().toString()
        // Title format: "<temp> · <condition>". The clear-sky label is "Clear".
        assertTrue(
            "snapshot row title should include a numeric temperature; got '$title'",
            title.any { it.isDigit() },
        )
    }

    // -------- toggles --------

    @Test
    fun weatherScreen_windArrowToggle_reflectsVmWindEnabled() {
        val v = vm()
        v.updateWindEnabled(true)
        val template = buildScreen()
        val row = rowByTitle(template, context.getString(R.string.car_weather_wind_arrow))
        assertTrue("wind arrow toggle should be checked when enabled", row.toggle!!.isChecked)
        v.updateWindEnabled(false)
        val template2 = buildScreen()
        val row2 = rowByTitle(template2, context.getString(R.string.car_weather_wind_arrow))
        assertFalse("wind arrow toggle should be unchecked when disabled", row2.toggle!!.isChecked)
    }

    @Test
    fun weatherScreen_legendToggle_reflectsVmShowLegend() {
        val v = vm()
        v.updateShowLegend(true)
        val template = buildScreen()
        val row = rowByTitle(template, context.getString(R.string.car_weather_legend))
        assertTrue(row.toggle!!.isChecked)
    }

    @Test
    fun weatherScreen_timelineToggle_reflectsVmShowTimeline() {
        val v = vm()
        v.updateShowTimeline(true)
        val template = buildScreen()
        val row = rowByTitle(template, context.getString(R.string.car_weather_timeline))
        assertTrue(row.toggle!!.isChecked)
    }

    // -------- cycle rows --------

    @Test
    fun weatherScreen_radarModeRow_clickCyclesWeatherMode() {
        val v = vm()
        // Default weather mode is OFF (per the "WEATHER_MODE" constant value
        // "ON" — actually WEATHER_MODE is the radar mode, which is OFF by
        // default; pin the cycle in a known starting state instead).
        v.updateWeatherMode(WeatherMode.OFF)
        val template = buildScreen()
        val row = rowByTitle(template, context.getString(R.string.car_weather_radar_mode))
        clickRow(row)
        // cycleWeatherMode: OFF -> PLAYING
        assertEquals(WeatherMode.PLAYING, v.weatherMode)
    }

    @Test
    fun weatherScreen_opacityRow_clickCyclesToNextPreset() {
        val v = vm()
        v.updateRadarOpacity(0f)
        v.saveRadarOpacity()
        val template = buildScreen()
        val row = rowByTitle(template, context.getString(R.string.car_weather_opacity))
        clickRow(row)
        // nextOpacity: 0.0 -> 0.25
        assertEquals(0.25f, v.radarOpacity, 0.001f)
    }

    @Test
    fun weatherScreen_windUnitRow_clickCyclesToNext() {
        val v = vm()
        v.updateWindSpeedUnit(ca.voiditswarranty.roadtripradar.model.WindSpeedUnit.KMH)
        val template = buildScreen()
        val row = rowByTitle(template, context.getString(R.string.car_weather_wind_unit))
        clickRow(row)
        assertEquals(
            ca.voiditswarranty.roadtripradar.model.WindSpeedUnit.MPH,
            v.windSpeedUnit,
        )
    }

    @Test
    fun weatherScreen_tempUnitRow_clickCyclesToNext() {
        val v = vm()
        v.updateTemperatureUnit(ca.voiditswarranty.roadtripradar.model.TemperatureUnit.CELSIUS)
        val template = buildScreen()
        val row = rowByTitle(template, context.getString(R.string.car_weather_temp_unit))
        clickRow(row)
        assertEquals(
            ca.voiditswarranty.roadtripradar.model.TemperatureUnit.FAHRENHEIT,
            v.temperatureUnit,
        )
    }

    // -------- parked-only "Turn radar off" row --------

    @Test
    fun weatherScreen_radarOffRow_isPresent() {
        val template = buildScreen()
        val titles = rowTitles(template)
        assertTrue(
            "WeatherScreen must always show the Turn radar off row; got $titles",
            titles.contains(context.getString(R.string.car_action_radar_off)),
        )
    }

    @Test
    fun weatherScreen_radarOffRow_clickInvokesTurnOff() {
        val v = vm()
        v.updateWeatherMode(WeatherMode.PLAYING)
        val template = buildScreen()
        val row = rowByTitle(template, context.getString(R.string.car_action_radar_off))
        clickRow(row)
        assertEquals(
            "Turn radar off click must set weatherMode to OFF",
            WeatherMode.OFF,
            v.weatherMode,
        )
    }
}
