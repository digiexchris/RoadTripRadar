package ca.voiditswarranty.roadtripradar.ui

import ca.voiditswarranty.roadtripradar.model.NetworkStatus
import ca.voiditswarranty.roadtripradar.model.NetworkTransport
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Sanity tests for [MapOverlayState] data class. Constructor pins, equality,
 * copy. Trivial but pins the contract — a future "let's add a field" change
 * will show as a test diff here.
 */
class MapOverlayStateTest {

    private fun sampleState() = MapOverlayState(
        useMetric = true,
        speedMps = 25.0,
        speedSize = 1.0f,
        useGps = true,
        hasGpsFix = true,
        gpsIconOpacity = 0.8f,
        networkStatus = NetworkStatus(transport = NetworkTransport.WIFI, validated = true, connected = true),
        activeWaypoint = null,
        activeIndex = null,
        waypoints = emptyList(),
        navWidgetSize = 1.0f,
        compassWidgetSize = 1.0f,
        hasFailedCells = false,
        isNorthUp = true,
        weatherActive = false,
        showTimeline = false,
        radarFrameTimes = emptyList(),
        currentFrameIndex = 0,
        weatherWidgetSize = 1.0f,
        windEnabled = true,
        openMeteoSnapshot = null,
        temperatureUnit = TemperatureUnit.CELSIUS,
        windSpeedUnit = WindSpeedUnit.KMH,
        showLegend = false,
        hasRadarFrames = false,
        fabScale = 1.0f,
        widgetScale = 1.0f,
        isTrackingCamera = true,
        isWeatherPlaying = false,
    )

    @Test
    fun state_constructor_spotChecksFields() {
        val state = sampleState()
        assertEquals(true, state.useMetric)
        assertEquals(25.0, state.speedMps, 0.001)
        assertEquals(NetworkStatus(transport = NetworkTransport.WIFI, validated = true, connected = true), state.networkStatus)
        assertEquals(TemperatureUnit.CELSIUS, state.temperatureUnit)
        assertEquals(WindSpeedUnit.KMH, state.windSpeedUnit)
        assertEquals(true, state.isTrackingCamera)
        assertEquals(1.0f, state.fabScale, 0.001f)
        assertEquals(1.0f, state.widgetScale, 0.001f)
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
        // other fields preserved (spot-checked; equality test covers the rest)
        assertEquals(a.speedMps, b.speedMps, 0.001)
        assertEquals(a.networkStatus, b.networkStatus)
    }
}