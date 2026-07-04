package ca.voiditswarranty.roadtripradar.ui

import android.content.Context
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Shared weather formatting helpers used by both the phone [WeatherWidget] and the
 * Android Auto car screens. Kept i18n-clean for the wind unit label (via string
 * resources); temperature formatting uses the unit symbols directly.
 */
internal fun formatTemp(celsius: Double, unit: TemperatureUnit): String {
    val value = when (unit) {
        TemperatureUnit.CELSIUS -> celsius
        TemperatureUnit.FAHRENHEIT -> celsius * 9.0 / 5.0 + 32
        TemperatureUnit.KELVIN -> celsius + 273.15
    }
    val rounded = round(value).toInt()
    return when (unit) {
        TemperatureUnit.CELSIUS -> "$rounded°C"
        TemperatureUnit.FAHRENHEIT -> "$rounded°F"
        TemperatureUnit.KELVIN -> "$rounded K"
    }
}

internal fun formatTrend(deltaCelsius: Double, unit: TemperatureUnit): String {
    val converted = when (unit) {
        TemperatureUnit.CELSIUS -> deltaCelsius
        TemperatureUnit.FAHRENHEIT -> deltaCelsius * 9.0 / 5.0
        TemperatureUnit.KELVIN -> deltaCelsius
    }
    val rounded = round(converted * 10.0) / 10.0
    val sign = when {
        rounded > 0 -> "+"
        rounded < 0 -> "-"
        else -> ""
    }
    val mag = abs(rounded)
    val num = String.format(Locale.US, "%.1f", mag)
    val suffix = when (unit) {
        TemperatureUnit.CELSIUS -> "°"
        TemperatureUnit.FAHRENHEIT -> "°"
        TemperatureUnit.KELVIN -> ""
    }
    return "$sign$num$suffix/h"
}

internal fun windValue(kmh: Double, unit: WindSpeedUnit): Int = when (unit) {
    WindSpeedUnit.KMH -> kmh.toInt()
    WindSpeedUnit.MPH -> (kmh * 0.621371).toInt()
    WindSpeedUnit.KNOTS -> (kmh * 0.539957).toInt()
}

internal fun windUnitLabel(context: Context, unit: WindSpeedUnit): String = when (unit) {
    WindSpeedUnit.KMH -> context.getString(R.string.wind_unit_kmh)
    WindSpeedUnit.MPH -> context.getString(R.string.wind_unit_mph)
    WindSpeedUnit.KNOTS -> context.getString(R.string.wind_unit_kn)
}