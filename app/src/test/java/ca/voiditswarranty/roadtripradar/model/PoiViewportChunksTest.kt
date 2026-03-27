package ca.voiditswarranty.roadtripradar.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

class PoiViewportChunksTest {

    private val kmPerDegLat = 111.0
    private val gridStepDeg = 25.0 / kmPerDegLat

    @Test
    fun boundingBoxesIntersect_trueWhenOverlapping() {
        val a = BoundingBox(
            southwest = Position(latitude = 0.0, longitude = 0.0),
            northeast = Position(latitude = 1.0, longitude = 1.0),
        )
        val b = BoundingBox(
            southwest = Position(latitude = 0.5, longitude = 0.5),
            northeast = Position(latitude = 2.0, longitude = 2.0),
        )
        assertTrue(PoiViewportChunks.boundingBoxesIntersect(a, b))
    }

    @Test
    fun boundingBoxesIntersect_falseWhenSeparated() {
        val a = BoundingBox(
            southwest = Position(latitude = 0.0, longitude = 0.0),
            northeast = Position(latitude = 1.0, longitude = 1.0),
        )
        val b = BoundingBox(
            southwest = Position(latitude = 5.0, longitude = 5.0),
            northeast = Position(latitude = 6.0, longitude = 6.0),
        )
        assertFalse(PoiViewportChunks.boundingBoxesIntersect(a, b))
    }

    @Test
    fun poiLoadPlateForVisibleBounds_matchesGridCellsForManualLoadPlate() {
        val sw = Position(latitude = 45.0, longitude = -75.0)
        val ne = Position(latitude = 45.1, longitude = -74.9)
        val visible = BoundingBox(southwest = sw, northeast = ne)
        val plate = PoiViewportChunks.poiLoadPlateForVisibleBounds(visible)
        val (loadBounds, _) = PoiViewportChunks.gridCellsForManualLoad(visible)
        assertEquals(loadBounds.southwest.latitude, plate.southwest.latitude, 1e-9)
        assertEquals(loadBounds.northeast.latitude, plate.northeast.latitude, 1e-9)
        assertEquals(loadBounds.southwest.longitude, plate.southwest.longitude, 1e-9)
        assertEquals(loadBounds.northeast.longitude, plate.northeast.longitude, 1e-9)
    }

    @Test
    fun padBounds_scalesSpanAboutCenter() {
        val sw = Position(latitude = 1.0, longitude = 2.0)
        val ne = Position(latitude = 3.0, longitude = 6.0)
        val input = BoundingBox(southwest = sw, northeast = ne)
        val out = PoiViewportChunks.padBounds(input, 1.5)
        // lat span 2 → pad 0.5 each side; lon span 4 → pad 1 each side
        assertEquals(0.5, out.southwest.latitude, 1e-9)
        assertEquals(1.0, out.southwest.longitude, 1e-9)
        assertEquals(3.5, out.northeast.latitude, 1e-9)
        assertEquals(7.0, out.northeast.longitude, 1e-9)
        val centerLat = (out.southwest.latitude + out.northeast.latitude) / 2.0
        val centerLon = (out.southwest.longitude + out.northeast.longitude) / 2.0
        assertEquals(2.0, centerLat, 1e-9)
        assertEquals(4.0, centerLon, 1e-9)
    }

    @Test
    fun clampBoundsToMaxCenterExtentKm_makesSquareUsingLargerKmSpan() {
        val sw = Position(latitude = 0.0, longitude = 0.0)
        val ne = Position(latitude = 1.0, longitude = 0.5)
        val input = BoundingBox(southwest = sw, northeast = ne)
        val out = PoiViewportChunks.clampBoundsToMaxCenterExtentKm(input, maxExtentKm = 500.0)
        val centerLat = (sw.latitude + ne.latitude) / 2.0
        val centerLon = (sw.longitude + ne.longitude) / 2.0
        val latSpanKm = abs(out.northeast.latitude - out.southwest.latitude) * kmPerDegLat
        val lonSpanKm =
            abs(out.northeast.longitude - out.southwest.longitude) * kmPerDegLat * cos(Math.toRadians(centerLat))
        assertEquals(latSpanKm, lonSpanKm, 0.05)
        val paddedLatKm = 1.0 * kmPerDegLat
        val paddedLonKm = 0.5 * kmPerDegLat * cos(0.0)
        val expectedSideKm = minOf(max(paddedLatKm, paddedLonKm), 500.0)
        assertEquals(expectedSideKm, latSpanKm, 0.05)
    }

    @Test
    fun clampBoundsToMaxCenterExtentKm_centersOnCameraTargetWhenProvided() {
        val sw = Position(latitude = 0.0, longitude = 0.0)
        val ne = Position(latitude = 2.0, longitude = 1.0)
        val input = BoundingBox(southwest = sw, northeast = ne)
        val targetLat = 1.7
        val targetLon = 0.9
        val out = PoiViewportChunks.clampBoundsToMaxCenterExtentKm(
            input,
            maxExtentKm = 500.0,
            centerLatitude = targetLat,
            centerLongitude = targetLon,
        )
        assertEquals(targetLat, (out.southwest.latitude + out.northeast.latitude) / 2.0, 1e-6)
        assertEquals(targetLon, (out.southwest.longitude + out.northeast.longitude) / 2.0, 1e-6)
        val midLat = 1.0
        val latSpanKm = abs(out.northeast.latitude - out.southwest.latitude) * kmPerDegLat
        val lonSpanKm =
            abs(out.northeast.longitude - out.southwest.longitude) * kmPerDegLat * cos(Math.toRadians(targetLat))
        assertEquals(latSpanKm, lonSpanKm, 0.08)
    }

    @Test
    fun clampBoundsToMaxCenterExtentKm_capsAtMaxExtent() {
        val huge = 10.0
        val sw = Position(latitude = 0.0, longitude = 0.0)
        val ne = Position(latitude = huge, longitude = huge)
        val input = BoundingBox(southwest = sw, northeast = ne)
        val out = PoiViewportChunks.clampBoundsToMaxCenterExtentKm(input, maxExtentKm = 50.0)
        val centerLat = huge / 2.0
        val latSpanKm = abs(out.northeast.latitude - out.southwest.latitude) * kmPerDegLat
        val lonSpanKm =
            abs(out.northeast.longitude - out.southwest.longitude) * kmPerDegLat * cos(Math.toRadians(centerLat))
        assertEquals(50.0, latSpanKm, 0.2)
        assertEquals(50.0, lonSpanKm, 2.0)
    }

    @Test
    fun worldGridCellsIntersecting_singleCell_whenFullyInside() {
        val pad = gridStepDeg * 0.1
        val sw = Position(latitude = pad, longitude = pad)
        val ne = Position(latitude = gridStepDeg - pad, longitude = gridStepDeg - pad)
        val cells = PoiViewportChunks.worldGridCellsIntersecting(BoundingBox(southwest = sw, northeast = ne))
        assertEquals(1, cells.size)
        assertEquals(0, cells[0].iLat)
        assertEquals(0, cells[0].iLon)
    }

    @Test
    fun worldGridCellsIntersecting_twoByTwo_whenSpanningOriginCorner() {
        val sw = Position(latitude = -0.01 * gridStepDeg, longitude = -0.01 * gridStepDeg)
        val ne = Position(latitude = 0.01 * gridStepDeg, longitude = 0.01 * gridStepDeg)
        val cells = PoiViewportChunks.worldGridCellsIntersecting(BoundingBox(southwest = sw, northeast = ne))
        val ids = cells.map { it.id }.toSet()
        assertEquals(setOf("g_0_0", "g_0_-1", "g_-1_0", "g_-1_-1"), ids)
    }

    @Test
    fun gridCellsForManualLoad_fromVisibleBounds_returnsSortedGridSubset() {
        val half = gridStepDeg * 0.4
        val sw = Position(latitude = 50.0 * gridStepDeg + half, longitude = -10.0 * gridStepDeg + half)
        val ne = Position(latitude = 50.0 * gridStepDeg + gridStepDeg - half, longitude = -10.0 * gridStepDeg + gridStepDeg - half)
        val visible = BoundingBox(southwest = sw, northeast = ne)
        val (loadBounds, cells) = PoiViewportChunks.gridCellsForManualLoad(visible)
        assertTrue(loadBounds.southwest.latitude <= visible.southwest.latitude)
        assertTrue(loadBounds.northeast.latitude >= visible.northeast.latitude)
        assertTrue(cells.isNotEmpty())
        assertTrue(cells.all { cell ->
            val b = cell.bounds
            loadBounds.southwest.latitude < b.northeast.latitude &&
                loadBounds.northeast.latitude > b.southwest.latitude &&
                loadBounds.southwest.longitude < b.northeast.longitude &&
                loadBounds.northeast.longitude > b.southwest.longitude
        })
    }
}
