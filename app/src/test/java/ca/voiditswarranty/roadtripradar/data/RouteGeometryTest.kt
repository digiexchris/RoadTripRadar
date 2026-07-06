package ca.voiditswarranty.roadtripradar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.spatialk.geojson.Position

/**
 * Tests for [RouteGeometry] — pure helpers that decide which [org.maplibre.spatialk.geojson.LineString]s
 * to draw for a waypoint list. The phone (`ui.MapLayers.WaypointRouteLineLayer`) and the car
 * (`car.CarMapContainer.updateRoute`) both consume these — the geometry is decided once, the
 * rendering differs.
 *
 * The current production code:
 * - [inactiveRouteLegs] returns one [org.maplibre.spatialk.geojson.LineString] per consecutive
 *   pair of waypoints (`[A, B, C] → [A→B, B→C]`). It does NOT special-case the active index;
 *   the comment in the source notes the active leg is also drawn dashed so it remains visible
 *   under the live solid user→target approach leg.
 * - [activeRouteLeg] returns a single [org.maplibre.spatialk.geojson.LineString] from the
 *   user's current position to the active waypoint, or `null` when there is no active
 *   waypoint / the index is out of range / the waypoint list is empty.
 */
class RouteGeometryTest {

    private fun pos(lat: Double, lon: Double) = Position(latitude = lat, longitude = lon)

    private fun wp(lat: Double, lon: Double, id: String = "$lat,$lon"): Waypoint =
        Waypoint(id = id, lat = lat, lon = lon)

    // -------- inactiveRouteLegs --------

    @Test
    fun inactiveRouteLegs_emptyWaypoints_returnsEmptyList() {
        assertEquals(emptyList<org.maplibre.spatialk.geojson.LineString>(), inactiveRouteLegs(emptyList()))
    }

    @Test
    fun inactiveRouteLegs_singleWaypoint_returnsEmptyList() {
        // No pairs possible from a list of size 1.
        assertEquals(emptyList<org.maplibre.spatialk.geojson.LineString>(), inactiveRouteLegs(listOf(wp(0.0, 0.0))))
    }

    @Test
    fun inactiveRouteLegs_twoWaypoints_returnsOneLeg() {
        val a = wp(0.0, 0.0, "a")
        val b = wp(1.0, 1.0, "b")
        val legs = inactiveRouteLegs(listOf(a, b))
        assertEquals("two waypoints → exactly one leg", 1, legs.size)
        assertEquals(listOf(a.position, b.position), legs[0].coordinates)
    }

    @Test
    fun inactiveRouteLegs_threeWaypoints_returnsTwoConsecutiveLegs() {
        val a = wp(0.0, 0.0, "a")
        val b = wp(1.0, 1.0, "b")
        val c = wp(2.0, 2.0, "c")
        val legs = inactiveRouteLegs(listOf(a, b, c))
        assertEquals("three waypoints → exactly two legs", 2, legs.size)
        assertEquals(listOf(a.position, b.position), legs[0].coordinates)
        assertEquals(listOf(b.position, c.position), legs[1].coordinates)
    }

    @Test
    fun inactiveRouteLegs_doesNotSkipActiveIndex() {
        // The current implementation draws all consecutive legs, including the one ending at
        // the active waypoint. This is intentional per the source comment — the active leg is
        // also drawn dashed underneath the live solid user→target leg. If a future refactor
        // decides to skip the active leg, this test will fail and surface the contract change.
        val a = wp(0.0, 0.0, "a")
        val b = wp(1.0, 1.0, "b")
        val c = wp(2.0, 2.0, "c")
        val d = wp(3.0, 3.0, "d")
        val legs = inactiveRouteLegs(listOf(a, b, c, d))
        assertEquals("four waypoints → three legs, all consecutive", 3, legs.size)
        assertEquals(listOf(a.position, b.position), legs[0].coordinates)
        assertEquals(listOf(b.position, c.position), legs[1].coordinates)
        assertEquals(listOf(c.position, d.position), legs[2].coordinates)
    }

    // -------- activeRouteLeg --------

    @Test
    fun activeRouteLeg_emptyWaypoints_returnsNull() {
        assertNull(activeRouteLeg(emptyList(), activeIndex = 0, userPosition = pos(0.0, 0.0)))
    }

    @Test
    fun activeRouteLeg_activeIndexNull_returnsNull() {
        // No active waypoint → no live approach leg.
        val w = wp(0.0, 0.0)
        assertNull(activeRouteLeg(listOf(w), activeIndex = null, userPosition = pos(1.0, 1.0)))
    }

    @Test
    fun activeRouteLeg_activeIndexOutOfRange_returnsNull() {
        // activeIndex beyond waypoints.indices → null. Pin this so a future off-by-one
        // surfaces as a test diff.
        val a = wp(0.0, 0.0, "a")
        val b = wp(1.0, 1.0, "b")
        assertNull(activeRouteLeg(listOf(a, b), activeIndex = 5, userPosition = pos(2.0, 2.0)))
        assertNull(activeRouteLeg(listOf(a, b), activeIndex = -1, userPosition = pos(2.0, 2.0)))
    }

    @Test
    fun activeRouteLeg_validInputs_returnsLegFromUserToActiveWaypoint() {
        val a = wp(0.0, 0.0, "a")
        val b = wp(1.0, 1.0, "b")
        val c = wp(2.0, 2.0, "c")
        val user = pos(0.5, 0.5)
        val leg = activeRouteLeg(listOf(a, b, c), activeIndex = 1, userPosition = user)
        assertNotNull("active waypoint with valid index → non-null leg", leg)
        assertEquals(listOf(user, b.position), leg!!.coordinates)
    }

    @Test
    fun activeRouteLeg_activeIndexAtLastWaypoint_returnsLeg() {
        // activeIndex = last index is in range. Live leg is still drawn (live → last waypoint).
        val a = wp(0.0, 0.0, "a")
        val b = wp(1.0, 1.0, "b")
        val user = pos(0.5, 0.5)
        val leg = activeRouteLeg(listOf(a, b), activeIndex = 1, userPosition = user)
        assertNotNull(leg)
        assertEquals(listOf(user, b.position), leg!!.coordinates)
    }

    @Test
    fun activeRouteLeg_returnsSameLineStringShapeRegardlessOfInactiveLegs() {
        // The active leg's geometry is independent of the inactive legs — pin the contract
        // that adding/removing inactive waypoints doesn't change the active leg's coordinates.
        val a = wp(0.0, 0.0, "a")
        val b = wp(1.0, 1.0, "b")
        val c = wp(2.0, 2.0, "c")
        val user = pos(0.5, 0.5)
        val leg1 = activeRouteLeg(listOf(a, b), activeIndex = 1, userPosition = user)
        val leg2 = activeRouteLeg(listOf(a, b, c), activeIndex = 1, userPosition = user)
        assertNotNull(leg1)
        assertNotNull(leg2)
        assertEquals(leg1!!.coordinates, leg2!!.coordinates)
    }
}
