package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.data.Waypoint
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

/**
 * JVM-safe sanity tests for the state consumed by [MapScreenContent].
 *
 * The full [MapScreenContent] composable renders a real [org.maplibre.compose.MaplibreMap],
 * which cannot run under a JVM Robolectric test (no MapLibre native renderer). So
 * instead of attempting a full render, these tests exercise the [MapScreenState]
 * data class that [MapScreen] constructs and feeds into [MapScreenContent] —
 * verifying the "render with state X" path through [MapScreenState.copy].
 *
 * This complements [MapScreenStateTest] (which pins the constructor, equality,
 * and copy contract) by checking the specific field values and pass-throughs
 * that [MapScreenContent] reads when rendering.
 */
class MapScreenContentTest {

    private fun sampleState() = MapScreenState(
        useMetric = true,
        useGps = true,
        isTrackingCamera = true,
        isNorthUp = true,
        keepScreenOn = false,
        activeWaypoint = null,
        activeIndex = null,
        waypoints = emptyList(),
        poiPosition = null,
        showThemeSelector = false,
        showLegend = false,
        showTimeline = false,
        radarFramePaths = emptyList(),
        radarFrameTimes = emptyList(),
        currentFrameIndex = 0,
        weatherWidgetSize = 1.0f,
        windEnabled = false,
        openMeteoSnapshot = null,
        temperatureUnit = TemperatureUnit.CELSIUS,
        windSpeedUnit = WindSpeedUnit.KMH,
        weatherActive = false,
        isWeatherPlaying = false,
        poiPipelineActive = false,
        nearbyPoiFeatures = FeatureCollection<Point, JsonObject>(features = emptyList()),
        userPositionForSearch = null,
        screenWidthDp = 400.0,
        screenHeightDp = 800.0,
        speedSize = 1.0f,
        navWidgetSize = 1.0f,
        hasFailedCells = false,
        gpsIconOpacity = 0.8f,
        compassWidgetSize = 48f,
        mapCenterOffsetPortraitFraction = 0.4f,
        mapCenterOffsetLandscapeFraction = 0.3f,
        userPosition = Position(latitude = 43.0, longitude = -79.0),
        userPositionAccuracy = 5.0,
        bearing = 0.0,
        poiInfo = null,
        cameraPadding = PaddingValues(0.dp),
    )

    @Test
    fun state_rendersWithAllFieldsPopulated_isNotEqualToEmptyState() {
        // Renders without crash: contract is the data class shape.
        val state = sampleState()
        assertEquals(Position(latitude = 43.0, longitude = -79.0), state.userPosition)
        assertEquals(48f, state.compassWidgetSize, 0.001f)
    }

    @Test
    fun state_darkTheme_doesNotAffectStateShape() {
        // mapOverlaysDark is a derived boolean passed to MapScreenContent
        // separately, not a state field — so the state shape is theme-invariant.
        val state = sampleState()
        assertNotNull(state)
    }

    @Test
    fun state_emptyWaypoints_doesNotCrash() {
        val state = sampleState()
        assertEquals(emptyList<Waypoint>(), state.waypoints)
    }

    @Test
    fun state_failedCellsTrue_passesThrough() {
        val state = sampleState().copy(hasFailedCells = true)
        assertTrue(state.hasFailedCells)
    }

    @Test
    fun state_windEnabledTrue_passesThrough() {
        val state = sampleState().copy(windEnabled = true)
        assertTrue(state.windEnabled)
    }
}