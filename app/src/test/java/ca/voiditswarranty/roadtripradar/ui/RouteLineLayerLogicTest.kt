package ca.voiditswarranty.roadtripradar.ui

import ca.voiditswarranty.roadtripradar.data.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.spatialk.geojson.Position

/**
 * Unit tests for [pastLegs] and [buildActiveLegLineString].
 *
 * These helpers are extracted from [WaypointRouteLineLayer] so the active-leg
 * "draws from user position to the active target" rule is testable in isolation.
 * The bug fixed in commit 04634bf was that the active leg was being drawn as the
 * static planned leg between the current and next waypoint, not the live
 * user→target approach.
 */
class RouteLineLayerLogicTest {

    private fun wp(lat: Double, lon: Double) = Waypoint(
        id = "$lat-$lon",
        lat = lat,
        lon = lon,
    )

    // ---- pastLegs ----

    @Test
    fun pastLegs_emptyWaypoints_returnsEmpty() {
        val result = pastLegs(waypoints = emptyList(), activeIndex = 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun pastLegs_nullActiveIndex_returnsEmpty() {
        val result = pastLegs(
            waypoints = listOf(wp(0.0, 0.0), wp(1.0, 1.0)),
            activeIndex = null,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun pastLegs_activeIndexZero_returnsEmpty() {
        val result = pastLegs(
            waypoints = listOf(wp(0.0, 0.0), wp(1.0, 1.0), wp(2.0, 2.0)),
            activeIndex = 0,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun pastLegs_activeIndexTwo_returnsOneLeg() {
        val waypoints = listOf(
            wp(0.0, 0.0), wp(1.0, 1.0), wp(2.0, 2.0), wp(3.0, 3.0),
        )
        val result = pastLegs(waypoints = waypoints, activeIndex = 2)
        assertEquals(1, result.size)
        assertEquals(waypoints[1].position, result[0].coordinates[0])
        assertEquals(waypoints[2].position, result[0].coordinates[1])
    }

    @Test
    fun pastLegs_activeIndexLast_returnsAllPriorLegs() {
        val waypoints = listOf(
            wp(0.0, 0.0), wp(1.0, 1.0), wp(2.0, 2.0), wp(3.0, 3.0),
        )
        val result = pastLegs(waypoints = waypoints, activeIndex = 3)
        assertEquals(2, result.size)
    }

    // ---- buildActiveLegLineString ----

    @Test
    fun activeLeg_emptyWaypoints_returnsNull() {
        val result = buildActiveLegLineString(
            waypoints = emptyList(),
            activeIndex = 0,
            userPosition = Position(longitude = 0.0, latitude = 0.0),
        )
        assertNull(result)
    }

    @Test
    fun activeLeg_nullActiveIndex_returnsNull() {
        val result = buildActiveLegLineString(
            waypoints = listOf(wp(0.0, 0.0), wp(1.0, 1.0)),
            activeIndex = null,
            userPosition = Position(longitude = 0.0, latitude = 0.0),
        )
        assertNull(result)
    }

    @Test
    fun activeLeg_activeIndexOutOfRange_returnsNull() {
        val result = buildActiveLegLineString(
            waypoints = listOf(wp(0.0, 0.0), wp(1.0, 1.0)),
            activeIndex = 5,
            userPosition = Position(longitude = 0.0, latitude = 0.0),
        )
        assertNull(result)
    }

    @Test
    fun activeLeg_validInputs_legFromUserToActiveWaypoint() {
        val waypoints = listOf(
            wp(0.0, 0.0), wp(1.0, 1.0), wp(2.0, 2.0), wp(3.0, 3.0),
        )
        val userPos = Position(longitude = -1.0, latitude = -1.0)
        val result = buildActiveLegLineString(
            waypoints = waypoints,
            activeIndex = 2,
            userPosition = userPos,
        )
        assertEquals(2, result!!.coordinates.size)
        assertEquals(userPos, result.coordinates[0])
        assertEquals(waypoints[2].position, result.coordinates[1])
    }
}