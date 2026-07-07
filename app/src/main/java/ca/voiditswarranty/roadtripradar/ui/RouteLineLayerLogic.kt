package ca.voiditswarranty.roadtripradar.ui

import ca.voiditswarranty.roadtripradar.data.Waypoint
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Position

/**
 * Pure route-line layer logic, extracted from [WaypointRouteLineLayer] so the
 * past-leg and active-leg builders can be unit-tested in isolation.
 *
 * The two halves of the route line:
 *  - **Past legs** ([pastLegs]): the planned segments *strictly before* the
 *    active index. Drawn dashed. The user→first-waypoint approach is *not* a
 *    past leg — it's drawn by the active leg, so the line never duplicates.
 *  - **Active leg** ([buildActiveLegLineString]): the solid user→target
 *    approach that advances as the user moves. The bug fixed in commit
 *    `04634bf` was that this was being drawn as the static planned leg
 *    `waypoints[activeIndex] → waypoints[activeIndex + 1]`, so the line never
 *    advanced. The fix is that the active leg always starts at the user's
 *    live [userPosition].
 */

/**
 * The planned legs that are *strictly before* the active index. Excludes the
 * user→first-waypoint approach (that leg is owned by the active leg) and the
 * activeIndex→next planned leg (also owned by the active leg).
 *
 * Returns an empty list if [activeIndex] is null or 0, or if there are fewer
 * than 2 waypoints.
 */
internal fun pastLegs(
    waypoints: List<Waypoint>,
    activeIndex: Int?,
): List<LineString> {
    if (waypoints.size < 2 || activeIndex == null || activeIndex <= 0) return emptyList()
    val lastPastIndex = (activeIndex - 1).coerceAtMost(waypoints.size - 1)
    return (1..lastPastIndex).map { i ->
        LineString(listOf(waypoints[i].position, waypoints[i + 1].position))
    }
}

/**
 * The active "approach" leg from the user's live [userPosition] to the active
 * target waypoint. Returns null if there's no active waypoint, the active
 * index is out of range, or the waypoints list is empty.
 */
internal fun buildActiveLegLineString(
    waypoints: List<Waypoint>,
    activeIndex: Int?,
    userPosition: Position,
): LineString? {
    if (waypoints.isEmpty() || activeIndex == null || activeIndex !in waypoints.indices) return null
    return LineString(listOf(userPosition, waypoints[activeIndex].position))
}