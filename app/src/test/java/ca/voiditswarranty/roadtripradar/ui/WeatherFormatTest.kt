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
}