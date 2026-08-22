package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.turf.measurement.bearingTo
import org.maplibre.spatialk.turf.measurement.distance
import org.maplibre.spatialk.units.Bearing
import org.maplibre.spatialk.units.Length
import org.maplibre.spatialk.units.extensions.inDegrees

/**
 * Compute the camera padding for an off-center map. The user's
 * "center offset" slider is a fraction of the usable map area; the
 * computed top + bottom padding pins the map's rendered center to
 * a point `fraction * height` above the bottom of the usable area.
 *
 * @param usableHeight total usable map area in dp.
 * @param centerOffsetFraction slider value in `[0, 0.5]`; 0 = center, 0.5 = bottom.
 * @return the top and bottom padding to apply to the camera.
 */
internal fun computeCameraOffsetPadding(
    usableHeight: Dp,
    centerOffsetFraction: Float,
): Pair<Dp, Dp> {
    val desiredBottomOffset = usableHeight * centerOffsetFraction
    val computedTopPadding = (usableHeight - (desiredBottomOffset * 2f)).coerceAtLeast(0.dp)
    val computedBottomPadding = ((desiredBottomOffset * 2f) - usableHeight).coerceAtLeast(0.dp)
    return computedTopPadding to computedBottomPadding
}

/**
 * Compute the POI info tuple (distance + bearing) for the nav widget,
 * or null if the user position or POI position is unknown.
 */
internal fun computePoiInfo(
    userPosition: Position?,
    poiPosition: Position?,
): Pair<Length, Double>? {
    if (userPosition == null || poiPosition == null) return null
    val dist = distance(userPosition, poiPosition)
    val poiBearing = userPosition.bearingTo(poiPosition)
    val poiBearingDeg = (poiBearing - Bearing.North).inDegrees
    return dist to poiBearingDeg
}

/**
 * Whether the GPS fix is "good enough" to display the position
 * indicator. Currently: a fix exists and accuracy is under 50m.
 */
internal fun hasGoodGpsFix(
    useGps: Boolean,
    hasLocation: Boolean,
    accuracyMeters: Double?,
): Boolean {
    if (!useGps || !hasLocation) return false
    if (accuracyMeters == null) return false
    return accuracyMeters < 50.0
}
