package ca.voiditswarranty.roadtripradar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.maplibre.spatialk.geojson.Position

/**
 * Tests for [Waypoint] — the data class that backs the phone + car route editor. The
 * `position` getter is a derived `Position(latitude, longitude)` (read-only); the
 * `create(...)` factory is the standard builder used by the search-result and drop-pin
 * flows.
 */
class WaypointTest {

    private val pos = Position(latitude = 45.4, longitude = -75.7)

    @Test
    fun position_returnsLatLonTuple() {
        val w = Waypoint(id = "x", lat = 45.4, lon = -75.7)
        assertEquals(45.4, w.position.latitude, 0.0)
        assertEquals(-75.7, w.position.longitude, 0.0)
    }

    @Test
    fun position_worksForNegativeCoords() {
        // South / West hemisphere.
        val w = Waypoint(id = "x", lat = -33.86, lon = 151.21)
        assertEquals(-33.86, w.position.latitude, 0.0)
        assertEquals(151.21, w.position.longitude, 0.0)
    }

    @Test
    fun create_withName_setsAllFields() {
        val w = Waypoint.create(
            position = pos,
            name = "Coffee Shop",
            subtitle = "123 Main St",
        )
        assertEquals("Coffee Shop", w.name)
        assertEquals("123 Main St", w.subtitle)
        assertEquals(45.4, w.lat, 0.0)
        assertEquals(-75.7, w.lon, 0.0)
        assertNotNull("factory should always assign a UUID-style id", w.id)
    }

    @Test
    fun create_withOptionalParams_omitsNulls() {
        val w = Waypoint.create(position = pos)
        assertEquals(null, w.name)
        assertEquals(null, w.subtitle)
        assertEquals(null, w.iconName)
        // source defaults to DROPPED_PIN.
        assertEquals(WaypointSource.DROPPED_PIN, w.source)
    }

    @Test
    fun create_preservesProvidedId() {
        // Test passes a known id; the factory should not overwrite it.
        val w = Waypoint.create(position = pos, id = "pinned-id")
        assertEquals("pinned-id", w.id)
    }

    @Test
    fun equality_basedOnAllFields() {
        val a = Waypoint(id = "1", lat = 45.4, lon = -75.7, name = "X")
        val b = Waypoint(id = "1", lat = 45.4, lon = -75.7, name = "X")
        val c = Waypoint(id = "1", lat = 45.4, lon = -75.7, name = "Y")
        val d = Waypoint(id = "2", lat = 45.4, lon = -75.7, name = "X")
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertNotEquals(a, d)
    }
}
