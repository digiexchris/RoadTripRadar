package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import ca.voiditswarranty.roadtripradar.data.Waypoint
import ca.voiditswarranty.roadtripradar.model.NetworkStatus
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.spatialk.units.extensions.meters
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [MapOverlayTopRow]. The composable is the
 * top horizontal bar that contains the speed readout, network
 * status, nav widget, GPS status, compass, and the retry-failed
 * button. It accepts a wide list of primitive inputs covering all
 * sub-widgets; the call site in [MapOverlay] wires the VM state
 * through.
 *
 * The actual widget rendering is exercised by the individual
 * widget tests (SpeedReadout, NetworkStatusIcon, etc.). These
 * tests pin the wrapping behavior: the composable accepts its
 * inputs without crashing and lays out the sub-widgets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapOverlayTopRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun hostContent(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    private val emptyWaypoints = emptyList<Waypoint>()

    @Test
    fun topRow_noActiveWaypoint_rendersWithoutNav() {
        hostContent {
            MapOverlayTopRow(
                useMetric = true,
                speedMps = 0.0,
                speedSize = 100f,
                hasLocation = true,
                useGps = true,
                hasGpsFix = true,
                gpsIconOpacity = 1f,
                networkStatus = NetworkStatus(),
                activeWaypoint = null,
                activeIndex = null,
                waypoints = emptyWaypoints,
                bearing = 0.0,
                poiInfo = null,
                navWidgetSize = 100f,
                hasFailedCells = false,
                isNorthUp = true,
                fabScale = 1f,
                widgetScale = 1f,
                onOpenRouteEditor = {},
                onAdvanceWaypoint = {},
                onRegressWaypoint = {},
                onCompassToggle = {},
                onRetryFailedCells = {},
                compassButton = {},
            )
        }
    }

    @Test
    fun topRow_withActiveWaypointAndPoiInfo_renders() {
        val wp = Waypoint(id = "wp1", name = "Target", lat = 45.0, lon = -75.0, subtitle = null)
        hostContent {
            MapOverlayTopRow(
                useMetric = true,
                speedMps = 15.0,
                speedSize = 100f,
                hasLocation = true,
                useGps = true,
                hasGpsFix = true,
                gpsIconOpacity = 1f,
                networkStatus = NetworkStatus(),
                activeWaypoint = wp,
                activeIndex = 0,
                waypoints = listOf(wp),
                bearing = 0.0,
                poiInfo = 100.0.meters to 90.0,
                navWidgetSize = 100f,
                hasFailedCells = false,
                isNorthUp = true,
                fabScale = 1f,
                widgetScale = 1f,
                onOpenRouteEditor = {},
                onAdvanceWaypoint = {},
                onRegressWaypoint = {},
                onCompassToggle = {},
                onRetryFailedCells = {},
                compassButton = {},
            )
        }
    }

    @Test
    fun topRow_failedCells_rendersRetryButton() {
        hostContent {
            MapOverlayTopRow(
                useMetric = true,
                speedMps = 0.0,
                speedSize = 100f,
                hasLocation = false,
                useGps = false,
                hasGpsFix = false,
                gpsIconOpacity = 1f,
                networkStatus = NetworkStatus(),
                activeWaypoint = null,
                activeIndex = null,
                waypoints = emptyWaypoints,
                bearing = 0.0,
                poiInfo = null,
                navWidgetSize = 100f,
                hasFailedCells = true,
                isNorthUp = true,
                fabScale = 1f,
                widgetScale = 1f,
                onOpenRouteEditor = {},
                onAdvanceWaypoint = {},
                onRegressWaypoint = {},
                onCompassToggle = {},
                onRetryFailedCells = {},
                compassButton = {},
            )
        }
    }

    @Test
    fun topRow_multipleWaypoints_enablesChevrons() {
        val wp1 = Waypoint(id = "wp1", name = "A", lat = 45.0, lon = -75.0, subtitle = null)
        val wp2 = Waypoint(id = "wp2", name = "B", lat = 45.1, lon = -75.1, subtitle = null)
        hostContent {
            MapOverlayTopRow(
                useMetric = true,
                speedMps = 0.0,
                speedSize = 100f,
                hasLocation = true,
                useGps = true,
                hasGpsFix = true,
                gpsIconOpacity = 1f,
                networkStatus = NetworkStatus(),
                activeWaypoint = wp1,
                activeIndex = 0,
                waypoints = listOf(wp1, wp2),
                bearing = 0.0,
                poiInfo = 100.0.meters to 90.0,
                navWidgetSize = 100f,
                hasFailedCells = false,
                isNorthUp = true,
                fabScale = 1f,
                widgetScale = 1f,
                onOpenRouteEditor = {},
                onAdvanceWaypoint = {},
                onRegressWaypoint = {},
                onCompassToggle = {},
                onRetryFailedCells = {},
                compassButton = {},
            )
        }
    }
}
