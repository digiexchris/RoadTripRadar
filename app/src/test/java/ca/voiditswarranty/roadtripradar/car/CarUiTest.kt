package ca.voiditswarranty.roadtripradar.car

import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the car-surface unit "cycle to next" helpers in [CarUi.kt] —
 * [WindSpeedUnit.nextCycle] and [TemperatureUnit.nextCycle], the building blocks
 * the car Settings picker rows reuse. Pure Kotlin (no Context / resource access),
 * so plain JUnit — no Robolectric needed.
 */
class CarUiTest {

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
}