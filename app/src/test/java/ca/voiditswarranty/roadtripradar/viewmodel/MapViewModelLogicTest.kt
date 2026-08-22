package ca.voiditswarranty.roadtripradar.viewmodel

import ca.voiditswarranty.roadtripradar.data.InsertPosition
import ca.voiditswarranty.roadtripradar.data.WaypointSource
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.model.PoiGridCell
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.buildJsonObject
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

/**
 * Tests for the pure helpers extracted from [MapViewModel] into [MapViewModelLogic].
 * Each test pins one behavior of one helper. The helpers are the "decision" or
 * "math" portions of the corresponding VM method — the VM still owns the state
 * mutation and coroutine launches.
 */
class MapViewModelLogicTest {

    // -------- Weather mode transitions --------

    @Test
    fun nextWeatherModeOnCycle_off_returnsPlaying() {
        assertEquals(WeatherMode.PLAYING, nextWeatherModeOnCycle(WeatherMode.OFF))
    }

    @Test
    fun nextWeatherModeOnCycle_playing_returnsOn() {
        assertEquals(WeatherMode.ON, nextWeatherModeOnCycle(WeatherMode.PLAYING))
    }

    @Test
    fun nextWeatherModeOnCycle_on_returnsOff() {
        assertEquals(WeatherMode.OFF, nextWeatherModeOnCycle(WeatherMode.ON))
    }

    @Test
    fun nextWeatherModeOnPlayPauseToggle_off_returnsPlaying() {
        assertEquals(WeatherMode.PLAYING, nextWeatherModeOnPlayPauseToggle(WeatherMode.OFF))
    }

    @Test
    fun nextWeatherModeOnPlayPauseToggle_playing_returnsOn() {
        // Pausing while playing lands on ON (paused but visible).
        assertEquals(WeatherMode.ON, nextWeatherModeOnPlayPauseToggle(WeatherMode.PLAYING))
    }

    @Test
    fun nextWeatherModeOnPlayPauseToggle_on_returnsPlaying() {
        // Resume from pause goes back to playing.
        assertEquals(WeatherMode.PLAYING, nextWeatherModeOnPlayPauseToggle(WeatherMode.ON))
    }

    @Test
    fun nextWeatherModeOnOnOffToggle_activeTrue_returnsOff() {
        assertEquals(WeatherMode.OFF, nextWeatherModeOnOnOffToggle(weatherActive = true))
    }

    @Test
    fun nextWeatherModeOnOnOffToggle_activeFalse_returnsPlaying() {
        // Turning on starts in PLAYING (not just ON).
        assertEquals(WeatherMode.PLAYING, nextWeatherModeOnOnOffToggle(weatherActive = false))
    }

    // -------- formatLatLng --------

    @Test
    fun formatLatLng_northAndEast_returnsFormattedString() {
        val s = formatLatLng(Position(latitude = 45.41234, longitude = -75.71234))
        assertEquals("45.41234, -75.71234", s)
    }

    @Test
    fun formatLatLng_negativeCoords_handlesSign() {
        val s = formatLatLng(Position(latitude = -33.8688, longitude = 151.2093))
        assertEquals("-33.86880, 151.20930", s)
    }

    @Test
    fun formatLatLng_zeroCoords_returnsZeroString() {
        val s = formatLatLng(Position(latitude = 0.0, longitude = 0.0))
        assertEquals("0.00000, 0.00000", s)
    }

    // -------- computeViewBox --------

    @Test
    fun computeViewBox_knownCenter_returnsExpectedBounds() {
        val box = computeViewBox(lat = 45.4, lon = -75.7, zoom = 10.0, screenWidthDp = 800.0, screenHeightDp = 600.0)
        // The box is centered on (lat, lon) with symmetric lat/lon deltas.
        assertEquals(-75.7, (box.west + box.east) / 2.0, 0.0001)
        assertEquals(45.4, (box.south + box.north) / 2.0, 0.0001)
        // West < east and south < north.
        assertTrue("west must be < east", box.west < box.east)
        assertTrue("south must be < north", box.south < box.north)
    }

    @Test
    fun computeViewBox_higherZoom_smallerBox() {
        val low = computeViewBox(45.4, -75.7, zoom = 6.0, screenWidthDp = 800.0, screenHeightDp = 600.0)
        val high = computeViewBox(45.4, -75.7, zoom = 14.0, screenWidthDp = 800.0, screenHeightDp = 600.0)
        // Higher zoom → smaller half-extents → narrower box.
        val lowWidth = low.east - low.west
        val highWidth = high.east - high.west
        assertTrue("higher zoom must produce a narrower box", highWidth < lowWidth)
    }

    @Test
    fun computeViewBox_higherEquator_latLonDeltaConverges() {
        // At the equator, cos(0)=1 so lat and lon deltas are equal.
        val box = computeViewBox(lat = 0.0, lon = 0.0, zoom = 10.0, screenWidthDp = 800.0, screenHeightDp = 600.0)
        val latSpan = box.north - box.south
        val lonSpan = box.east - box.west
        assertEquals("at equator, lat and lon spans must be equal", latSpan, lonSpan, 0.0001)
    }

    // -------- Tutorial index math --------

    @Test
    fun nextTutorialStep_midList_returnsNext() {
        assertEquals(2, nextTutorialStep(currentIndex = 1, stepCount = 5))
    }

    @Test
    fun nextTutorialStep_atLastStep_returnsEnd() {
        val result = nextTutorialStep(currentIndex = 4, stepCount = 5)
        assertTrue("expected TutorialEnd.End, got: $result", result is TutorialEnd.End)
    }

    @Test
    fun nextTutorialStep_atSecondToLast_returnsNext() {
        // 3 + 1 = 4 < 5 (stepCount), so returns the next index.
        assertEquals(4, nextTutorialStep(currentIndex = 3, stepCount = 5))
    }

    @Test
    fun previousTutorialStep_midList_returnsPrevious() {
        assertEquals(2, previousTutorialStep(currentIndex = 3))
    }

    @Test
    fun previousTutorialStep_atFirst_returnsFirst() {
        // Clamped to 0; no negative indices.
        assertEquals(0, previousTutorialStep(currentIndex = 0))
    }

    // -------- addWaypointIndex --------

    @Test
    fun addWaypointIndex_atStart_returnsZero() {
        val idx = addWaypointIndex(currentSize = 3, at = InsertPosition.Start, activeWaypointId = null, findById = { -1 })
        assertEquals(0, idx)
    }

    @Test
    fun addWaypointIndex_atEnd_returnsCurrentSize() {
        val idx = addWaypointIndex(currentSize = 3, at = InsertPosition.End, activeWaypointId = null, findById = { -1 })
        assertEquals(3, idx)
    }

    @Test
    fun addWaypointIndex_beforeLast_returnsSecondToLast() {
        // With 3 waypoints, BeforeLast is index 2 (right before the last at index 2).
        val idx = addWaypointIndex(currentSize = 3, at = InsertPosition.BeforeLast, activeWaypointId = null, findById = { -1 })
        assertEquals(2, idx)
    }

    @Test
    fun addWaypointIndex_beforeLast_emptyList_returnsZero() {
        // With 0 waypoints, "BeforeLast" must clamp to 0 (not -1).
        val idx = addWaypointIndex(currentSize = 0, at = InsertPosition.BeforeLast, activeWaypointId = null, findById = { -1 })
        assertEquals(0, idx)
    }

    @Test
    fun addWaypointIndex_atIndex_respectsValue() {
        val idx = addWaypointIndex(currentSize = 5, at = InsertPosition.Index(i = 2), activeWaypointId = null, findById = { -1 })
        assertEquals(2, idx)
    }

    @Test
    fun addWaypointIndex_atIndex_clampsToSize() {
        // An out-of-range index is clamped to the current size.
        val idx = addWaypointIndex(currentSize = 3, at = InsertPosition.Index(i = 99), activeWaypointId = null, findById = { -1 })
        assertEquals(3, idx)
    }

    @Test
    fun addWaypointIndex_replaceId_existing_returnsIndex() {
        val idx = addWaypointIndex(
            currentSize = 5,
            at = InsertPosition.ReplaceId(id = "wp-2"),
            activeWaypointId = null,
            findById = { id -> if (id == "wp-2") 2 else -1 },
        )
        assertEquals(2, idx)
    }

    @Test
    fun addWaypointIndex_replaceId_notFound_appends() {
        // If the id doesn't exist, the helper appends (returns current size).
        val idx = addWaypointIndex(
            currentSize = 5,
            at = InsertPosition.ReplaceId(id = "missing"),
            activeWaypointId = null,
            findById = { _ -> -1 },
        )
        assertEquals(5, idx)
    }

    // -------- maybeAutoAdvanceNextIndex --------

    @Test
    fun maybeAutoAdvance_disabled_returnsNull() {
        val result = maybeAutoAdvanceNextIndex(
            autoAdvanceEnabled = false,
            activeIndex = 0,
            totalWaypoints = 3,
            distanceMeters = 0.0,
            thresholdMeters = 100,
        )
        assertNull(result)
    }

    @Test
    fun maybeAutoAdvance_noActiveIndex_returnsNull() {
        val result = maybeAutoAdvanceNextIndex(
            autoAdvanceEnabled = true,
            activeIndex = null,
            totalWaypoints = 3,
            distanceMeters = 0.0,
            thresholdMeters = 100,
        )
        assertNull(result)
    }

    @Test
    fun maybeAutoAdvance_atLastWaypoint_returnsNull() {
        val result = maybeAutoAdvanceNextIndex(
            autoAdvanceEnabled = true,
            activeIndex = 2, // last of 3
            totalWaypoints = 3,
            distanceMeters = 0.0,
            thresholdMeters = 100,
        )
        assertNull(result)
    }

    @Test
    fun maybeAutoAdvance_distanceAtThreshold_returnsNull() {
        // distance >= threshold → no advance.
        val result = maybeAutoAdvanceNextIndex(
            autoAdvanceEnabled = true,
            activeIndex = 0,
            totalWaypoints = 3,
            distanceMeters = 100.0,
            thresholdMeters = 100,
        )
        assertNull(result)
    }

    @Test
    fun maybeAutoAdvance_distanceBelowThreshold_returnsNextIndex() {
        val result = maybeAutoAdvanceNextIndex(
            autoAdvanceEnabled = true,
            activeIndex = 0,
            totalWaypoints = 3,
            distanceMeters = 50.0,
            thresholdMeters = 100,
        )
        assertEquals(1, result)
    }

    @Test
    fun maybeAutoAdvance_singleWaypoint_totalIsOne_returnsNull() {
        // activeIndex 0 with totalWaypoints 1 → no next.
        val result = maybeAutoAdvanceNextIndex(
            autoAdvanceEnabled = true,
            activeIndex = 0,
            totalWaypoints = 1,
            distanceMeters = 0.0,
            thresholdMeters = 100,
        )
        assertNull(result)
    }

    // -------- customThemeFallback --------

    @Test
    fun customThemeFallback_customLight_returnsLiberty() {
        assertEquals(MapStyle.LIBERTY, customThemeFallback(MapStyle.CUSTOM_LIGHT))
    }

    @Test
    fun customThemeFallback_customDark_returnsColorDark() {
        assertEquals(MapStyle.COLOR_DARK, customThemeFallback(MapStyle.CUSTOM_DARK))
    }

    @Test
    fun customThemeFallback_builtIn_returnsNull() {
        // Built-in styles are not deletable; the helper returns null.
        assertNull(customThemeFallback(MapStyle.LIBERTY))
        assertNull(customThemeFallback(MapStyle.COLOR_DARK))
        assertNull(customThemeFallback(MapStyle.AUTO))
    }

    // -------- shouldShowWhatsNew --------

    @Test
    fun shouldShowWhatsNew_lastSeenNull_returnsFalse() {
        // First launch: silent — don't show the sheet, just stamp the version.
        assertFalse(shouldShowWhatsNew(lastSeenCode = null, currentVersionCode = 100, newReleaseCount = 5))
    }

    @Test
    fun shouldShowWhatsNew_currentEqualsLastSeen_returnsFalse() {
        assertFalse(shouldShowWhatsNew(lastSeenCode = 100, currentVersionCode = 100, newReleaseCount = 0))
    }

    @Test
    fun shouldShowWhatsNew_currentLessThanLastSeen_returnsFalse() {
        // Downgrade case — should not show.
        assertFalse(shouldShowWhatsNew(lastSeenCode = 100, currentVersionCode = 99, newReleaseCount = 0))
    }

    @Test
    fun shouldShowWhatsNew_currentGreaterNoReleases_returnsFalse() {
        // No showInApp releases in the gap → don't show.
        assertFalse(shouldShowWhatsNew(lastSeenCode = 100, currentVersionCode = 105, newReleaseCount = 0))
    }

    @Test
    fun shouldShowWhatsNew_currentGreaterWithReleases_returnsTrue() {
        assertTrue(shouldShowWhatsNew(lastSeenCode = 100, currentVersionCode = 105, newReleaseCount = 2))
    }

    // -------- retryDelayMs --------

    @Test
    fun retryDelayMs_retryCount0_returns1000() {
        assertEquals(1000L, retryDelayMs(retryCount = 0))
    }

    @Test
    fun retryDelayMs_retryCount1_returns2000() {
        assertEquals(2000L, retryDelayMs(retryCount = 1))
    }

    @Test
    fun retryDelayMs_retryCount2_returnsCap() {
        // 1s << 2 = 4000ms, but the cap is 2000ms.
        assertEquals(MAX_INTER_CELL_DELAY_MS, retryDelayMs(retryCount = 2))
    }

    @Test
    fun retryDelayMs_retryCount10_clampedToCap() {
        // 1s << 10 = ~1M ms, clamped to 2s.
        assertEquals(MAX_INTER_CELL_DELAY_MS, retryDelayMs(retryCount = 10))
    }

    // -------- cellsToEnqueue --------

    @Test
    fun cellsToEnqueue_emptyInputs_returnsEmpty() {
        assertTrue(
            cellsToEnqueue(
                cells = emptyList(),
                cachedIds = emptySet(),
                inFlightIds = emptySet(),
                pendingIds = emptySet(),
                failedIds = emptySet(),
            ).isEmpty(),
        )
    }

    @Test
    fun cellsToEnqueue_noDuplicates_returnsAll() {
        val a = cell(0, 0)
        val b = cell(1, 0)
        val result = cellsToEnqueue(
            cells = listOf(a, b),
            cachedIds = emptySet(),
            inFlightIds = emptySet(),
            pendingIds = emptySet(),
            failedIds = emptySet(),
        )
        assertEquals(2, result.size)
    }

    @Test
    fun cellsToEnqueue_cachedId_dropped() {
        val a = cell(0, 0) // id = "g_0_0"
        val b = cell(1, 0) // id = "g_1_0"
        val result = cellsToEnqueue(
            cells = listOf(a, b),
            cachedIds = setOf("g_0_0"),
            inFlightIds = emptySet(),
            pendingIds = emptySet(),
            failedIds = emptySet(),
        )
        assertEquals(1, result.size)
        assertEquals("g_1_0", result[0].id)
    }

    @Test
    fun cellsToEnqueue_inFlightId_dropped() {
        val a = cell(0, 0)
        val result = cellsToEnqueue(
            cells = listOf(a),
            cachedIds = emptySet(),
            inFlightIds = setOf("g_0_0"),
            pendingIds = emptySet(),
            failedIds = emptySet(),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun cellsToEnqueue_pendingId_dropped() {
        val a = cell(0, 0)
        val result = cellsToEnqueue(
            cells = listOf(a),
            cachedIds = emptySet(),
            inFlightIds = emptySet(),
            pendingIds = setOf("g_0_0"),
            failedIds = emptySet(),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun cellsToEnqueue_failedId_dropped() {
        val a = cell(0, 0)
        val result = cellsToEnqueue(
            cells = listOf(a),
            cachedIds = emptySet(),
            inFlightIds = emptySet(),
            pendingIds = emptySet(),
            failedIds = setOf("g_0_0"),
        )
        assertTrue(result.isEmpty())
    }

    // -------- evictableCachedCellIds --------

    @Test
    fun evictableCachedCellIds_emptyCache_returnsEmpty() {
        val result = evictableCachedCellIds(
            cache = emptyMap(),
            loadPlate = bounds(0.0, 0.0, 1.0, 1.0),
            intersects = { _, _ -> true },
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun evictableCachedCellIds_allIntersect_returnsEmpty() {
        val a = cachedCell(0, 0)
        val b = cachedCell(1, 0)
        val result = evictableCachedCellIds(
            cache = mapOf("g_0_0" to a, "g_1_0" to b),
            loadPlate = bounds(0.0, 0.0, 10.0, 10.0),
            intersects = { _, _ -> true },
        )
        assertTrue("all cells intersect, none evictable", result.isEmpty())
    }

    @Test
    fun evictableCachedCellIds_someOutside_returnsThoseIds() {
        val a = cachedCell(0, 0)
        val b = cachedCell(50, 50) // far away
        val result = evictableCachedCellIds(
            cache = mapOf("g_0_0" to a, "g_50_50" to b),
            loadPlate = bounds(0.0, 0.0, 1.0, 1.0),
            // Intersects only if both bboxes contain the origin (0,0) roughly.
            intersects = { cached, plate ->
                cached.southwest.latitude >= plate.southwest.latitude - 0.1 &&
                    cached.northeast.latitude <= plate.northeast.latitude + 0.1
            },
        )
        // 'a' intersects; 'b' is far away and should be evicted.
        assertEquals(listOf("g_50_50"), result)
    }

    // -------- rebuildMergedFeatures --------

    @Test
    fun rebuildMergedFeatures_emptyCache_returnsEmpty() {
        val result = rebuildMergedFeatures(cells = emptyMap())
        assertTrue(result.features.isEmpty())
        assertNull(result.region)
    }

    @Test
    fun rebuildMergedFeatures_oneCell_returnsItsFeatures() {
        val f = feature()
        val c = CachedCell(
            cellId = "g_0_0",
            features = listOf(f),
            fetchedAtMs = 0L,
            bounds = bounds(0.0, 0.0, 1.0, 1.0),
        )
        val result = rebuildMergedFeatures(cells = mapOf("g_0_0" to c))
        assertEquals(1, result.features.size)
        assertNotNull("region must be set when cache is non-empty", result.region)
    }

    @Test
    fun rebuildMergedFeatures_multipleCells_unionsFeatures() {
        val f1 = feature()
        val f2 = feature()
        val f3 = feature()
        val a = CachedCell("g_0_0", listOf(f1), 0L, bounds(0.0, 0.0, 1.0, 1.0))
        val b = CachedCell("g_2_0", listOf(f2, f3), 0L, bounds(2.0, 0.0, 3.0, 1.0))
        val result = rebuildMergedFeatures(cells = mapOf("g_0_0" to a, "g_2_0" to b))
        assertEquals(3, result.features.size)
        val region = result.region!!
        // The region's bounds span both cells.
        assertEquals(0.0, region.southwest.latitude, 0.0)
        assertEquals(3.0, region.northeast.latitude, 0.0)
    }

    // -------- helpers --------

    private fun cell(iLat: Int, iLon: Int): PoiGridCell {
        // Each cell is 1° x 1°; the id format matches PoiGridCell.id.
        return PoiGridCell(
            iLat = iLat,
            iLon = iLon,
            bounds = BoundingBox(
                southwest = Position(latitude = iLat.toDouble(), longitude = iLon.toDouble()),
                northeast = Position(latitude = (iLat + 1).toDouble(), longitude = (iLon + 1).toDouble()),
            ),
        )
    }

    private fun cachedCell(iLat: Int, iLon: Int): CachedCell {
        val grid = cell(iLat, iLon)
        return CachedCell(
            cellId = grid.id,
            features = emptyList(),
            fetchedAtMs = 0L,
            bounds = grid.bounds,
        )
    }

    private fun bounds(south: Double, west: Double, north: Double, east: Double): BoundingBox =
        BoundingBox(
            southwest = Position(latitude = south, longitude = west),
            northeast = Position(latitude = north, longitude = east),
        )

    private fun feature(): Feature<Point, JsonObject> =
        Feature(geometry = Point(Position(latitude = 0.0, longitude = 0.0)), properties = buildJsonObject { })
}
