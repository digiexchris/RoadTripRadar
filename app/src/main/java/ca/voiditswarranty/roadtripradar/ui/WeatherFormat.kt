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

/** The symbol for a temperature unit (°C / °F / K), used by the car settings/weather unit pickers. */
internal fun tempUnitSymbol(unit: TemperatureUnit): String = when (unit) {
    TemperatureUnit.CELSIUS -> "°C"
    TemperatureUnit.FAHRENHEIT -> "°F"
    TemperatureUnit.KELVIN -> "K"
}

/**
 * Rotation in degrees for a wind-direction arrow that should point where the wind is going *to*
 * (the bearing it comes *from* + 180°), corrected for the map's current [cameraBearing] so the
 * arrow stays oriented relative to the map as it rotates. Shared by the phone `WeatherWidget`
 * (the car surface now renders wind direction as compass text via [compassDirectionLabel]
 * instead of an arrow).
 */
internal fun windArrowRotationDeg(windDirectionDeg: Number, cameraBearing: Number): Float =
    (windDirectionDeg.toDouble() + 180.0 - cameraBearing.toDouble()).toFloat()

/**
 * The 16-point compass label for a bearing in degrees (N, NNE, NE, … NNW). Used for the wind
 * direction on the car weather content pane, where the wind arrow is rendered as text instead of
 * a rotated glyph. [bearingDeg] is the meteorological "from" direction (e.g. 0° → N, a wind from
 * the north), matching `OpenMeteoSnapshot.windDirectionDeg` — so the label reads as the standard
 * "wind from X" direction.
 */
private val COMPASS_RES = intArrayOf(
    R.string.compass_n, R.string.compass_nne, R.string.compass_ne, R.string.compass_ene,
    R.string.compass_e, R.string.compass_ese, R.string.compass_se, R.string.compass_sse,
    R.string.compass_s, R.string.compass_ssw, R.string.compass_sw, R.string.compass_wsw,
    R.string.compass_w, R.string.compass_wnw, R.string.compass_nw, R.string.compass_nnw,
)

internal fun compassDirectionLabel(context: Context, bearingDeg: Number): String {
    val deg = ((bearingDeg.toDouble() % 360.0) + 360.0) % 360.0
    val sector = ((deg / 22.5) + 0.5).toInt() % 16
    return context.getString(COMPASS_RES[sector])
}