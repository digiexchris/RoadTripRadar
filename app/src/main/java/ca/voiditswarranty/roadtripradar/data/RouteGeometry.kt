package ca.voiditswarranty.roadtripradar.data

import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Position

/**
 * Shared route-geometry builders so the phone (`ui.MapLayers.WaypointRouteLineLayer`) and the car
 * (`car.CarMapContainer.updateRoute`) draw the *same* route. Each side wraps the returned
 * [LineString]s in its own layer/source model — Compose `FeatureCollection`s on the phone, raw
 * GeoJSON on the car — but the geometry (which legs, where the live approach leg goes) is decided
 * once here.
 */

/**
 * The planned legs between consecutive waypoints, drawn dashed on the phone (solid on the car).
 * Deliberately includes the leg from the previous waypoint to the active target so the planned
 * segment stays visible underneath the live solid user→target approach leg.
 */
fun inactiveRouteLegs(waypoints: List<Waypoint>): List<LineString> =
    (0 until waypoints.size - 1).map { i ->
        LineString(listOf(waypoints[i].position, waypoints[i + 1].position))
    }

/**
 * The live "approach" leg from the user's current [userPosition] to the active target, drawn solid
 * on both surfaces. `null` when there is no active waypoint or the active index is out of range.
 */
fun activeRouteLeg(waypoints: List<Waypoint>, activeIndex: Int?, userPosition: Position): LineString? {
    if (waypoints.isEmpty() || activeIndex == null || activeIndex !in waypoints.indices) return null
    return LineString(listOf(userPosition, waypoints[activeIndex].position))
}