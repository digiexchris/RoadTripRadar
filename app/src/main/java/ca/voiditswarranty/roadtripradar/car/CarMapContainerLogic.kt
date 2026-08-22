package ca.voiditswarranty.roadtripradar.car

import ca.voiditswarranty.roadtripradar.data.Waypoint
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Position

/**
 * Pure helpers extracted from [CarMapContainer]. Each function in this file takes only
 * primitive / SpatialK inputs, returns a value, and never touches the
 * [org.maplibre.android.maps.Style] / [androidx.car.app.CarContext] / [android.view.View]
 * state that the rest of [CarMapContainer] depends on. That separation lets the decisions
 * driving the MapLibre state machine be unit-tested without a Robolectric runtime or a
 * MapLibre test double.
 *
 * The math here mirrors the inline expressions in [CarMapContainer] verbatim — the
 * extraction is purely for testability. Behavior is identical to the previous inlined
 * version; [CarMapContainer] now calls these helpers instead of inlining the same logic.
 */

/**
 * Whether the range-ring geometry needs to be rebuilt for a new camera frame. Mirrors
 * the decision inlined in [CarMapContainer.updateRangeRings]: rebuild when there's no
 * cached center yet (first call), when the user has moved to a new center, OR when the
 * zoom has crossed a [rangeRingsZoomBracket] boundary (the ring-distance set changes
 * per bracket).
 *
 * Returns `true` for any of:
 * - [lastCenter] is `null` (first call after style load, or after a style reload that
 *   nulled the cache).
 * - [lastCenter] != [currentCenter] (user moved).
 * - The two zooms are in different brackets, even if [lastCenter] == [currentCenter].
 * - [lastZoom] is `null` (e.g. cached center present but zoom was reset by a style
 *   reload — be safe, rebuild).
 *
 * Returns `false` only when both center and zoom bracket are unchanged.
 */
internal fun shouldRebuildRings(
    currentCenter: Position?,
    lastCenter: Position?,
    currentZoom: Double?,
    lastZoom: Double?,
): Boolean {
    if (lastCenter == null) return true
    if (lastZoom == null) return true
    if (currentCenter != lastCenter) return true
    return rangeRingsZoomBracket(lastZoom!!) != rangeRingsZoomBracket(currentZoom!!)
}

/**
 * The (top, bottom) padding in map-view pixels implied by the user's center-offset
 * fraction. Mirrors the inlined formula in [CarMapContainer.carOffsetPadding]: with
 * [mapHostHeight] == 0 the host hasn't been laid out yet, so return `null` (no padding
 * to apply); with `fraction == 0.5f` the user wants the natural center, so return
 * `null`. Otherwise, derive the bottom offset from the fraction and split it into
 * top + bottom padding so the puck appears at that fraction from the bottom of the
 * visible area.
 *
 * [fraction] is clamped to [0, 1] before the math runs (matches the `coerceIn` in the
 * production code that read it from preferences).
 */
internal fun carOffsetPadding(
    mapHostHeight: Int,
    fraction: Float,
): Pair<Double, Double>? {
    if (mapHostHeight <= 0) return null
    val clamped = fraction.coerceIn(0f, 1f)
    if (clamped == 0.5f) return null
    val height = mapHostHeight.toDouble()
    val desiredBottomOffset = height * clamped.toDouble()
    val top = (height - 2 * desiredBottomOffset).coerceAtLeast(0.0)
    val bottom = (2 * desiredBottomOffset - height).coerceAtLeast(0.0)
    return top to bottom
}

/**
 * Whether a position-move over one poll interval qualifies as "driving" for the SA-1
 * radar freeze. Mirrors the inlined `moved > MOVEMENT_DEG_THRESHOLD` check in
 * [CarMapContainer.pollPosition]: strict greater-than, so a move exactly equal to
 * [threshold] is NOT driving.
 *
 * The threshold parameter defaults to the production value (~0.0001° per poll, ~11 m)
 * but is overridable for tests.
 */
internal fun isDriving(
    movedDeg: Double,
    threshold: Double = MOVEMENT_DEG_THRESHOLD,
): Boolean = movedDeg > threshold

/**
 * Threshold above which one poll's position delta is considered "driving-paced" for
 * the SA-1 radar freeze. ~11 m per poll at 2-second cadence. Mirrors the constant
 * previously in [CarMapContainer] companion object.
 */
internal const val MOVEMENT_DEG_THRESHOLD: Double = 0.0001

/**
 * Which radar frame to show on the current paint pass, given the SA-1 freeze and the
 * user-controlled play/pause state. Mirrors the inlined `displayIndex` computation in
 * [CarMapContainer.updateRadar]:
 *
 * - [pathCount] == 0 → 0 (no frames; caller passes this into a for-loop that iterates
 *   the empty list, so it's a no-op).
 * - [driving] OR [mode] in `{ON, OFF}` (anything other than `PLAYING`) → `lastIndex`.
 *   The car freezes on the most-recent frame while the vehicle is moving (SA-1) or
 *   while the user has paused the radar (`ON`) or disabled it entirely (`OFF`). The
 *   `OFF` branch is masked by the `show` flag in [CarMapContainer.updateRadar] (the
 *   layer is invisible), but pinned to `lastIndex` here so the selection matches the
 *   user's mental model — "not playing → show the latest" — regardless of layer
 *   visibility.
 * - `PLAYING` and parked → [carFrameIndex] (the car-local animation playhead).
 *
 * Out-of-range [carFrameIndex] is NOT clamped here — the clamp happens at frame-set
 * change time in [CarMapContainer]. A drift past `lastIndex` produces an invisible
 * frame for one paint pass; the next animation tick or pollPosition will reset.
 */
internal fun carRadarDisplayIndex(
    pathCount: Int,
    driving: Boolean,
    mode: WeatherMode,
    carFrameIndex: Int,
): Int = when {
    pathCount == 0 -> 0
    driving || mode != WeatherMode.PLAYING -> pathCount - 1
    else -> carFrameIndex
}

/**
 * Serialize a single [Position] (the user's location puck) as a GeoJSON `Feature<Point>`.
 * GeoJSON coordinate order is [longitude, latitude] — the OPPOSITE of native
 * `LatLng(latitude, longitude)`. The earlier inline `centerOn` had a swap bug that
 * crashed on longitudes > 90°; this helper pins the contract.
 *
 * No `properties` field — the puck doesn't carry any.
 */
internal fun pointGeoJson(pos: Position): String =
    """{"type":"Feature","geometry":{"type":"Point","coordinates":[${pos.longitude},${pos.latitude}]}}"""

/**
 * Serialize a list of route legs (the dashed planned legs) to a GeoJSON
 * `FeatureCollection` of `LineString` features. Empty input → empty collection so the
 * caller can pass either an empty or non-empty list without a null check.
 */
internal fun lineStringsGeoJson(legs: List<LineString>): String {
    if (legs.isEmpty()) return """{"type":"FeatureCollection","features":[]}"""
    val features = legs.joinToString(",") { leg ->
        val coords = leg.coordinates.joinToString(",") { "[${it.longitude},${it.latitude}]" }
        """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

/**
 * Serialize a single route leg (the solid approach leg) to a GeoJSON `FeatureCollection`
 * with one `LineString` feature. `null` input → empty collection (matches the behavior
 * of [lineStringsGeoJson] on an empty list — the caller doesn't have to null-check).
 */
internal fun lineStringGeoJson(leg: LineString?): String {
    if (leg == null) return """{"type":"FeatureCollection","features":[]}"""
    val coords = leg.coordinates.joinToString(",") { "[${it.longitude},${it.latitude}]" }
    return """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]}}]}"""
}

/**
 * Serialize a list of point features to a GeoJSON `FeatureCollection`. Each feature is
 * a `Point` with the given `id` and `label` properties. Empty input → empty
 * collection so the caller doesn't have to null-check.
 *
 * Used by the car waypoint-marker layer to push inactive / active waypoint features
 * to their respective GeoJson sources.
 */
internal fun featureCollectionGeoJson(
    points: List<Position>,
    ids: List<String>,
    labels: List<String>,
): String {
    if (points.isEmpty()) return """{"type":"FeatureCollection","features":[]}"""
    val features = points.indices.joinToString(",") { i ->
        val p = points[i]
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${p.longitude},${p.latitude}]},"properties":{"id":"${ids[i]}","label":"${labels[i]}"}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

/**
 * Split waypoints into inactive and active marker features for the car map.
 * Each feature carries a 1-based number label (from the position in the full
 * list, NOT a filtered index) and the waypoint's stable id (used to look it up
 * on click to set the active waypoint).
 *
 * Returns `(inactiveJson, activeJson)` as two GeoJSON strings ready to push
 * to the corresponding GeoJsonSources. Empty waypoints → empty collections.
 *
 * Per the design spec, the car renders ALL waypoints as numbered circles —
 * unlike the phone, it does not branch on `iconName` to render maki icons.
 * (The phone has a separate maki-icon path for iconName waypoints; the car
 * does not port that path.)
 */
internal fun buildWaypointMarkerFeatures(
    waypoints: List<Waypoint>,
    activeWaypointId: String?,
): Pair<String, String> {
    val inactivePoints = mutableListOf<Position>()
    val inactiveIds = mutableListOf<String>()
    val inactiveLabels = mutableListOf<String>()
    val activePoints = mutableListOf<Position>()
    val activeIds = mutableListOf<String>()
    val activeLabels = mutableListOf<String>()

    waypoints.forEachIndexed { idx, wp ->
        val label = (idx + 1).toString()
        if (wp.id == activeWaypointId) {
            activePoints += wp.position
            activeIds += wp.id
            activeLabels += label
        } else {
            inactivePoints += wp.position
            inactiveIds += wp.id
            inactiveLabels += label
        }
    }

    return Pair(
        featureCollectionGeoJson(inactivePoints, inactiveIds, inactiveLabels),
        featureCollectionGeoJson(activePoints, activeIds, activeLabels),
    )
}
