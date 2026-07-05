package ca.voiditswarranty.roadtripradar.car

import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the small car-surface UI helpers in [CarUi.kt] — the
 * [radarModeLabel] lookup and the three `nextCycle()` extension functions on
 * [WindSpeedUnit], [TemperatureUnit], and [MapStyle]. These are the building
 * blocks the car screens (Home / Weather / Settings) reuse for unit / style
 * picker rows.
 *
 * Uses Robolectric because [radarModeLabel] calls `Context.getString(...)` —
 * the enums are pure-Kotlin, but a real `Context` is needed to resolve the
 * resource. The same `ApplicationProvider` Context is fine for the cycles
 * (no resource access), so we can assert all three in the same class without
 * a separate plain-JUnit variant.
 *
 * Pinned to SDK 33 because Robolectric 4.16.1's `ConnectivityManager` shadow
 * doesn't implement `registerDefaultNetworkCallback` on the project's
 * `compileSdk` (36). SDK 33 is well-supported and the test doesn't touch
 * `MapViewModel` so the shadow issue is moot here, but pinning the SDK
 * keeps the test class consistent with the rest of the car test suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CarUiTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    // -------- radarModeLabel --------

    @Test
    fun radarModeLabel_off_returnsRadarOffString() {
        // The car screens use this label for the "Radar: <off|on|playing>"
        // header / toggle. Pinned to the strings.xml resource so the test
        // references the source of truth — adding a new locale won't drift
        // the contract.
        val expected = context.getString(R.string.car_radar_off)
        assertEquals(expected, radarModeLabel(context, WeatherMode.OFF))
    }

    @Test
    fun radarModeLabel_on_returnsRadarOnString() {
        val expected = context.getString(R.string.car_radar_on)
        assertEquals(expected, radarModeLabel(context, WeatherMode.ON))
    }

    @Test
    fun radarModeLabel_playing_returnsRadarPlayingString() {
        val expected = context.getString(R.string.car_radar_playing)
        assertEquals(expected, radarModeLabel(context, WeatherMode.PLAYING))
    }

    // -------- WindSpeedUnit.nextCycle --------

    @Test
    fun nextCycle_windSpeedUnit_kmh_advancesToMph() {
        assertEquals(WindSpeedUnit.MPH, WindSpeedUnit.KMH.nextCycle())
    }

    @Test
    fun nextCycle_windSpeedUnit_mph_advancesToKnots() {
        assertEquals(WindSpeedUnit.KNOTS, WindSpeedUnit.MPH.nextCycle())
    }

    @Test
    fun nextCycle_windSpeedUnit_knots_wrapsToKmh() {
        // The last entry wraps to the first — the cycle is for a "tap to change"
        // picker row in the car Settings screen.
        assertEquals(WindSpeedUnit.KMH, WindSpeedUnit.KNOTS.nextCycle())
    }

    // -------- TemperatureUnit.nextCycle --------

    @Test
    fun nextCycle_temperatureUnit_celsius_advancesToFahrenheit() {
        assertEquals(TemperatureUnit.FAHRENHEIT, TemperatureUnit.CELSIUS.nextCycle())
    }

    @Test
    fun nextCycle_temperatureUnit_fahrenheit_advancesToKelvin() {
        assertEquals(TemperatureUnit.KELVIN, TemperatureUnit.FAHRENHEIT.nextCycle())
    }

    @Test
    fun nextCycle_temperatureUnit_kelvin_wrapsToCelsius() {
        assertEquals(TemperatureUnit.CELSIUS, TemperatureUnit.KELVIN.nextCycle())
    }

    // -------- MapStyle.nextCycle --------

    @Test
    fun nextCycle_mapStyle_liberty_advancesToDark() {
        assertEquals(MapStyle.DARK, MapStyle.LIBERTY.nextCycle())
    }

    @Test
    fun nextCycle_mapStyle_dark_advancesToColorDark() {
        assertEquals(MapStyle.COLOR_DARK, MapStyle.DARK.nextCycle())
    }

    @Test
    fun nextCycle_mapStyle_colorDark_advancesToCustomLight() {
        assertEquals(MapStyle.CUSTOM_LIGHT, MapStyle.COLOR_DARK.nextCycle())
    }

    @Test
    fun nextCycle_mapStyle_customLight_advancesToCustomDark() {
        assertEquals(MapStyle.CUSTOM_DARK, MapStyle.CUSTOM_LIGHT.nextCycle())
    }

    @Test
    fun nextCycle_mapStyle_customDark_advancesToAuto() {
        assertEquals(MapStyle.AUTO, MapStyle.CUSTOM_DARK.nextCycle())
    }

    @Test
    fun nextCycle_mapStyle_auto_wrapsToLiberty() {
        // AUTO is the last enum entry; the cycle wraps back to LIBERTY so the
        // picker can keep tapping without dead-ending.
        assertEquals(MapStyle.LIBERTY, MapStyle.AUTO.nextCycle())
    }

    // -------- contract pin: all enum values are covered --------

    @Test
    fun nextCycle_windSpeedUnit_coversAllEntriesExactlyOnce() {
        // Belt-and-suspenders: a future addition to the enum shouldn't silently
        // add a value that the cycle skips. Apply nextCycle to every entry and
        // assert the result set is the same size as the input set.
        val cycled = WindSpeedUnit.entries.map { it.nextCycle() }.toSet()
        assertEquals(
            "nextCycle mapped every entry to a distinct target",
            WindSpeedUnit.entries.toSet(),
            cycled,
        )
    }

    @Test
    fun nextCycle_temperatureUnit_coversAllEntriesExactlyOnce() {
        val cycled = TemperatureUnit.entries.map { it.nextCycle() }.toSet()
        assertEquals(TemperatureUnit.entries.toSet(), cycled)
    }

    @Test
    fun nextCycle_mapStyle_coversAllEntriesExactlyOnce() {
        val cycled = MapStyle.entries.map { it.nextCycle() }.toSet()
        assertEquals(MapStyle.entries.toSet(), cycled)
        // MapStyle.entries.size is 6; assert the set is non-empty so the test
        // doesn't pass on a vacuous enum.
        assertTrue("MapStyle has at least one entry", MapStyle.entries.isNotEmpty())
    }
}
