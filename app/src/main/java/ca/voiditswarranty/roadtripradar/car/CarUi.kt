package ca.voiditswarranty.roadtripradar.car

import android.content.Context
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit

/**
 * Small car-surface UI helpers shared across the car screens (Home / Weather / Settings), so the
 * radar-mode label and the unit/style "cycle to next" behaviour aren't re-implemented per screen.
 */

/** Localized label for the radar [WeatherMode] (off / on / playing). */
internal fun radarModeLabel(context: Context, mode: WeatherMode): String = when (mode) {
    WeatherMode.OFF -> context.getString(R.string.car_radar_off)
    WeatherMode.ON -> context.getString(R.string.car_radar_on)
    WeatherMode.PLAYING -> context.getString(R.string.car_radar_playing)
}

/** Cycle to the next enum entry (wrapping). Used by the car unit/style picker rows. */
internal fun WindSpeedUnit.nextCycle(): WindSpeedUnit {
    val all = WindSpeedUnit.entries
    return all[(all.indexOf(this) + 1) % all.size]
}

internal fun TemperatureUnit.nextCycle(): TemperatureUnit {
    val all = TemperatureUnit.entries
    return all[(all.indexOf(this) + 1) % all.size]
}

internal fun MapStyle.nextCycle(): MapStyle {
    val all = MapStyle.entries
    return all[(all.indexOf(this) + 1) % all.size]
}