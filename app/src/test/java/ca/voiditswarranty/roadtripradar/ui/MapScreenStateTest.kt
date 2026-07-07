package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

/**
 * Sanity tests for [MapScreenState] data class. Pins the constructor, equality,
 * copy. Trivial but pins the contract.
 */
class MapScreenStateTest {

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
        windEnabled = true,
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
    fun state_constructor_spotChecksFields() {
        val state = sampleState()
        assertEquals(true, state.useMetric)
        assertEquals(true, state.useGps)
        assertEquals(TemperatureUnit.CELSIUS, state.temperatureUnit)
        assertEquals(WindSpeedUnit.KMH, state.windSpeedUnit)
        assertEquals(0.8f, state.gpsIconOpacity, 0.001f)
        assertEquals(48f, state.compassWidgetSize, 0.001f)
    }

    @Test
    fun state_equality_basedOnAllFields() {
        val a = sampleState()
        val b = sampleState()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun state_copy_singleFieldChange_inequality() {
        val a = sampleState()
        val b = a.copy(useMetric = false)
        assertNotEquals(a, b)
        assertEquals(false, b.useMetric)
        assertEquals(a.useGps, b.useGps)
    }
}