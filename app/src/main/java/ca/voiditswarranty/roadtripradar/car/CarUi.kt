package ca.voiditswarranty.roadtripradar.car

import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit

/**
 * Small car-surface UI helpers shared across the car screens, so the unit
 * "cycle to next" behaviour isn't re-implemented per screen. Used by the car
 * Settings picker rows (wind speed, temperature).
 */

/** Cycle to the next enum entry (wrapping). Used by the car unit picker rows. */
internal fun WindSpeedUnit.nextCycle(): WindSpeedUnit {
    val all = WindSpeedUnit.entries
    return all[(all.indexOf(this) + 1) % all.size]
}

internal fun TemperatureUnit.nextCycle(): TemperatureUnit {
    val all = TemperatureUnit.entries
    return all[(all.indexOf(this) + 1) % all.size]
}