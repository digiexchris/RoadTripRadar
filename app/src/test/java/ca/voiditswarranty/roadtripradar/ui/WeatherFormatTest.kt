package ca.voiditswarranty.roadtripradar.ui

import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherFormatTest {

    @Test
    fun formatTempRoundsAndAppliesUnit() {
        assertEquals("21°C", formatTemp(20.6, TemperatureUnit.CELSIUS))
        assertEquals("69°F", formatTemp(20.555, TemperatureUnit.FAHRENHEIT)) // 20.555*9/5+32 = 69.0
        assertEquals("294 K", formatTemp(21.0, TemperatureUnit.KELVIN))      // 21 + 273.15 -> 294
    }

    @Test
    fun formatTrendSignsAndScalesPerUnit() {
        // Celsius: 1.0°C/h -> "+1.0°/h"
        assertEquals("+1.0°/h", formatTrend(1.0, TemperatureUnit.CELSIUS))
        // Negative -> "-"
        assertEquals("-0.5°/h", formatTrend(-0.5, TemperatureUnit.CELSIUS))
        // Fahrenheit: a 1°C delta scales to 1.8°F -> "+1.8°/h"
        assertEquals("+1.8°/h", formatTrend(1.0, TemperatureUnit.FAHRENHEIT))
        // Kelvin uses no degree symbol on the magnitude delta.
        assertEquals("+1.0/h", formatTrend(1.0, TemperatureUnit.KELVIN))
    }

    @Test
    fun windValueConvertsKmhToUnit() {
        assertEquals(10, windValue(10.0, WindSpeedUnit.KMH))
        assertEquals(6, windValue(10.0, WindSpeedUnit.MPH))    // 10*0.621371 = 6.21 -> 6
        assertEquals(5, windValue(10.0, WindSpeedUnit.KNOTS))  // 10*0.539957 = 5.39 -> 5
    }

    @Test
    fun tempUnitSymbol_celsius_returnsDegreeC() {
        assertEquals("°C", tempUnitSymbol(TemperatureUnit.CELSIUS))
    }

    @Test
    fun tempUnitSymbol_fahrenheit_returnsDegreeF() {
        assertEquals("°F", tempUnitSymbol(TemperatureUnit.FAHRENHEIT))
    }

    @Test
    fun tempUnitSymbol_kelvin_returnsK() {
        assertEquals("K", tempUnitSymbol(TemperatureUnit.KELVIN))
    }

    @Test
    fun windArrowRotation_knownWindKnownBearing_returnsDeltaPlus180() {
        // North wind (0°) + 180° = 180° (pointing south), then minus camera bearing.
        // bearing 0 (north-up): rotation = 180°
        assertEquals(180.0f, windArrowRotationDeg(0.0, 0.0), 0.0001f)
        // East wind (90°) + 180° = 270°, minus bearing 90° = 180°
        assertEquals(180.0f, windArrowRotationDeg(90.0, 90.0), 0.0001f)
    }

    @Test
    fun windArrowRotation_unrotatedMap_pointingSouth() {
        // A north wind on a north-up map should produce an arrow that points
        // down (180°), regardless of cardinal direction.
        assertEquals(180.0f, windArrowRotationDeg(0.0, 0.0), 0.0001f)
    }

    @Test
    fun windArrowRotation_bearingCompensates() {
        // Wind from west (270°) becomes "going east" (90°), minus bearing 90°
        // (map rotated 90° clockwise) → 0° (or 360° before mod). The function
        // does NOT wrap; the result is exactly 360°.
        assertEquals(360.0f, windArrowRotationDeg(270.0, 90.0), 0.0001f)
    }

    @Test
    fun windArrowRotation_acceptsIntInputs() {
        // The function signature takes Number, so Int inputs are also valid.
        assertEquals(180.0f, windArrowRotationDeg(0, 0), 0.0001f)
    }
}