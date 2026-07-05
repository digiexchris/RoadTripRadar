package ca.voiditswarranty.roadtripradar.car

import ca.voiditswarranty.roadtripradar.model.WeatherMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Position

/**
 * Tests for the pure helpers extracted from [CarMapContainer] into [CarMapContainerLogic].
 *
 * Each helper in [CarMapContainerLogic] is a pure function: it takes inputs, returns an
 * output, and never touches the [org.maplibre.android.maps.Style] / [androidx.car.app.CarContext]
 * state that the rest of [CarMapContainer] depends on. Splitting these out keeps the
 * MapLibre-coupled state machine in the hub itself (where it needs to be — the hub owns
 * the MapView and its overlays) while making the math and decisions that drive that state
 * machine testable in isolation.
 *
 * Test style mirrors [CarRangeRingsTest]: plain JUnit 4, no Robolectric, no mocks.
 */
class CarMapContainerLogicTest {

    // Anchor position reused across the shouldRebuildRings tests so equality on Position
    // is straightforward. The values are arbitrary; the test is about the decision, not
    // the coordinates.
    private val anchor = Position(longitude = -79.3832, latitude = 43.6532)
    private val moved = Position(longitude = -79.3000, latitude = 43.7000)

    // -------- shouldRebuildRings --------

    @Test
    fun shouldRebuildRings_firstCall_returnsTrue() {
        // No cached center (first call after style load) → always rebuild.
        assertTrue(shouldRebuildRings(anchor, lastCenter = null, currentZoom = 14.0, lastZoom = null))
    }

    @Test
    fun shouldRebuildRings_sameCenterSameZoomBracket_returnsFalse() {
        // Both center and zoom are unchanged AND in the same bracket — no need to rebuild.
        assertFalse(
            shouldRebuildRings(anchor, lastCenter = anchor, currentZoom = 14.5, lastZoom = 14.0),
        )
    }

    @Test
    fun shouldRebuildRings_sameCenterZoomCrossesBracketBoundary_returnsTrue() {
        // Centers equal, but zoom crossed the 14/12 boundary (14.0 → 11.9) → new ring
        // distances, must rebuild.
        assertTrue(
            shouldRebuildRings(anchor, lastCenter = anchor, currentZoom = 11.9, lastZoom = 14.0),
        )
    }

    @Test
    fun shouldRebuildRings_centerChanged_returnsTrue() {
        // Even if the zoom bracket is the same, a new center means a new ring geometry.
        assertTrue(
            shouldRebuildRings(moved, lastCenter = anchor, currentZoom = 14.0, lastZoom = 14.0),
        )
    }

    @Test
    fun shouldRebuildRings_lastZoomMissingButCenterSame_returnsTrue() {
        // Edge case: lastCenter is present (so the center didn't change) but lastZoom is
        // null (e.g. after a style reload that nulled the cache but kept a stale center
        // reference). The function must still rebuild to populate the zoom cache.
        assertTrue(shouldRebuildRings(anchor, lastCenter = anchor, currentZoom = 14.0, lastZoom = null))
    }

    // -------- carOffsetPadding --------

    @Test
    fun carOffsetPadding_hostNotLaidOut_returnsNull() {
        assertEquals(null, carOffsetPadding(mapHostHeight = 0, fraction = 0.5f))
    }

    @Test
    fun carOffsetPadding_fractionExactlyHalf_returnsNull() {
        // The natural-center fraction — no padding, no offset.
        assertEquals(null, carOffsetPadding(mapHostHeight = 400, fraction = 0.5f))
    }

    @Test
    fun carOffsetPadding_quarterFromBottom_pushesPuckDown() {
        // height=400, fraction=0.25 → desired bottom offset = 100px → top=200, bottom=0.
        // (Puck appears at 25% from the bottom of the visible area.)
        assertEquals(Pair(200.0, 0.0), carOffsetPadding(mapHostHeight = 400, fraction = 0.25f))
    }

    @Test
    fun carOffsetPadding_threeQuartersFromBottom_pushesPuckUp() {
        // height=400, fraction=0.75 → desired bottom offset = 300px → top=0, bottom=200.
        // (Puck appears at 75% from the bottom.)
        assertEquals(Pair(0.0, 200.0), carOffsetPadding(mapHostHeight = 400, fraction = 0.75f))
    }

    @Test
    fun carOffsetPadding_zeroFraction_puckAtTop() {
        // height=400, fraction=0 → desired bottom offset = 0 → top=400, bottom=0.
        assertEquals(Pair(400.0, 0.0), carOffsetPadding(mapHostHeight = 400, fraction = 0f))
    }

    @Test
    fun carOffsetPadding_fullFraction_puckAtBottom() {
        // height=400, fraction=1 → desired bottom offset = 400 → top=0, bottom=400.
        assertEquals(Pair(0.0, 400.0), carOffsetPadding(mapHostHeight = 400, fraction = 1f))
    }

    @Test
    fun carOffsetPadding_fractionOutOfRange_clampsToValidRange() {
        // Anything outside [0, 1] is clamped via coerceIn before the math runs. Negative
        // → 0, > 1 → 1. Production code uses vm.prefsRepo...fraction.coerceIn(0f, 1f).
        assertEquals(
            Pair(400.0, 0.0),
            carOffsetPadding(mapHostHeight = 400, fraction = -0.5f),
        )
        assertEquals(
            Pair(0.0, 400.0),
            carOffsetPadding(mapHostHeight = 400, fraction = 2f),
        )
    }

    // -------- isDriving --------

    @Test
    fun isDriving_zeroMovement_isFalse() {
        assertFalse(isDriving(movedDeg = 0.0))
    }

    @Test
    fun isDriving_belowThreshold_isFalse() {
        // MOVEMENT_DEG_THRESHOLD = 0.0001. 0.00005 is well under it.
        assertFalse(isDriving(movedDeg = 0.00005))
    }

    @Test
    fun isDriving_exactlyAtThreshold_isFalse() {
        // Boundary: the inline check in pollPosition is `moved > threshold`, so the
        // boundary value itself is NOT driving. The helper must mirror that (strict
        // greater-than, not >=).
        assertFalse(isDriving(movedDeg = 0.0001))
    }

    @Test
    fun isDriving_justAboveThreshold_isTrue() {
        assertTrue(isDriving(movedDeg = 0.00011))
    }

    @Test
    fun isDriving_largeMovement_isTrue() {
        assertTrue(isDriving(movedDeg = 1.0))
    }

    @Test
    fun isDriving_customThreshold_appliesIt() {
        // The threshold parameter lets the test pin the boundary exactly. With a 0.5
        // threshold, 0.4 is parked and 0.6 is driving.
        assertFalse(isDriving(movedDeg = 0.4, threshold = 0.5))
        assertTrue(isDriving(movedDeg = 0.6, threshold = 0.5))
    }

    // -------- carRadarDisplayIndex --------

    @Test
    fun carRadarDisplayIndex_noFrames_returnsZero() {
        // Empty frame set → nothing to display; the caller passes 0 down to the layer loop,
        // which is a no-op because there's nothing in paths. The `pathCount == 0` short-
        // circuit fires before mode is considered.
        assertEquals(0, carRadarDisplayIndex(pathCount = 0, driving = false, mode = WeatherMode.PLAYING, carFrameIndex = 3))
        assertEquals(0, carRadarDisplayIndex(pathCount = 0, driving = true, mode = WeatherMode.PLAYING, carFrameIndex = 0))
    }

    @Test
    fun carRadarDisplayIndex_driving_returnsLastIndex() {
        // SA-1: animated content is forbidden while driving. Pin to the last (most-recent)
        // frame regardless of mode or playhead.
        assertEquals(4, carRadarDisplayIndex(pathCount = 5, driving = true, mode = WeatherMode.PLAYING, carFrameIndex = 2))
        assertEquals(2, carRadarDisplayIndex(pathCount = 3, driving = true, mode = WeatherMode.OFF, carFrameIndex = 0))
    }

    @Test
    fun carRadarDisplayIndex_paused_returnsLastIndex() {
        // "Paused" on the phone (WeatherMode.ON — the user has the radar on but not
        // playing) pins to the latest frame so the user sees a static most-recent radar
        // image, not the playhead's position.
        assertEquals(4, carRadarDisplayIndex(pathCount = 5, driving = false, mode = WeatherMode.ON, carFrameIndex = 2))
    }

    @Test
    fun carRadarDisplayIndex_off_returnsLastIndex() {
        // "Off" (WeatherMode.OFF — the user has the radar disabled) should also pin to
        // the latest frame: the radar is not playing, and a stale playhead frame is the
        // wrong thing to show even if the layer is currently invisible. Previously the
        // inline code let OFF fall through to carFrameIndex, which is a dead-store
        // masked by the `show` flag. Pinned to lastIndex for clarity and so the
        // selection matches the user's mental model regardless of layer visibility.
        assertEquals(4, carRadarDisplayIndex(pathCount = 5, driving = false, mode = WeatherMode.OFF, carFrameIndex = 2))
    }

    @Test
    fun carRadarDisplayIndex_playingParked_returnsCarFrameIndex() {
        // Parked with the radar playing → follow the car-local animation playhead.
        assertEquals(2, carRadarDisplayIndex(pathCount = 5, driving = false, mode = WeatherMode.PLAYING, carFrameIndex = 2))
    }

    @Test
    fun carRadarDisplayIndex_outOfRangeCarFrameIndex_isNotClamped() {
        // Documented behavior: the inline code does NOT clamp carFrameIndex here. The
        // clamp (line 545 of CarMapContainer.kt) runs only at frame-set change time.
        // If the playhead has somehow drifted past lastIndex, the display loop will find
        // no matching layer and render the radar invisible for that tick. The next
        // animation tick (or pollPosition) brings it back into range.
        assertEquals(7, carRadarDisplayIndex(pathCount = 5, driving = false, mode = WeatherMode.PLAYING, carFrameIndex = 7))
    }

    // -------- pointGeoJson --------

    @Test
    fun pointGeoJson_emitsGeoJsonFeaturePoint() {
        // The output is a single GeoJSON Feature<Point> with no `properties`. GeoJSON
        // order is [longitude, latitude] — the OPPOSITE of native LatLng(lat, lon).
        // That's the bug that bit an earlier position-reconstruction attempt; the test
        // exists to pin the contract.
        val json = pointGeoJson(Position(longitude = -79.3832, latitude = 43.6532))
        assertEquals(
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[-79.3832,43.6532]}}""",
            json,
        )
    }

    // -------- lineStringsGeoJson --------

    @Test
    fun lineStringsGeoJson_emptyList_returnsEmptyFeatureCollection() {
        assertEquals(
            """{"type":"FeatureCollection","features":[]}""",
            lineStringsGeoJson(emptyList()),
        )
    }

    @Test
    fun lineStringsGeoJson_singleLeg_emitsOneFeature() {
        val leg = LineString(
            coordinates = listOf(
                Position(longitude = -79.0, latitude = 43.0),
                Position(longitude = -78.5, latitude = 43.5),
            ),
        )
        assertEquals(
            """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[[-79.0,43.0],[-78.5,43.5]]}}]}""",
            lineStringsGeoJson(listOf(leg)),
        )
    }

    @Test
    fun lineStringsGeoJson_multipleLegs_joinsWithComma() {
        val a = LineString(coordinates = listOf(Position(-79.0, 43.0), Position(-78.5, 43.5)))
        val b = LineString(coordinates = listOf(Position(-78.5, 43.5), Position(-78.0, 44.0)))
        val json = lineStringsGeoJson(listOf(a, b))
        // Two features, comma-separated, no trailing comma after the second feature.
        assertTrue("contains first feature", json.contains("""{"type":"Feature","geometry":{"type":"LineString","coordinates":[[-79.0,43.0],[-78.5,43.5]]}}"""))
        assertTrue("contains second feature", json.contains("""{"type":"Feature","geometry":{"type":"LineString","coordinates":[[-78.5,43.5],[-78.0,44.0]]}}"""))
        // No trailing comma between features — guard against a future joinToString that
        // accidentally introduces one (would produce invalid GeoJSON).
        assertFalse(
            "no trailing comma between features",
            json.contains("]]}}]},{"),
        )
    }

    // -------- lineStringGeoJson --------

    @Test
    fun lineStringGeoJson_nullLeg_returnsEmptyFeatureCollection() {
        assertEquals(
            """{"type":"FeatureCollection","features":[]}""",
            lineStringGeoJson(null),
        )
    }

    @Test
    fun lineStringGeoJson_nonNullLeg_emitsOneFeature() {
        val leg = LineString(
            coordinates = listOf(
                Position(longitude = -79.3832, latitude = 43.6532),
                Position(longitude = -79.3000, latitude = 43.7000),
            ),
        )
        assertEquals(
            """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[[-79.3832,43.6532],[-79.3,43.7]]}}]}""",
            lineStringGeoJson(leg),
        )
    }
}
