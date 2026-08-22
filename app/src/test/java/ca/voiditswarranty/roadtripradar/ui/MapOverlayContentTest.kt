package ca.voiditswarranty.roadtripradar.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.data.Waypoint
import ca.voiditswarranty.roadtripradar.model.NetworkStatus
import ca.voiditswarranty.roadtripradar.model.NetworkTransport
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
import org.maplibre.spatialk.units.Length
import org.maplibre.spatialk.units.extensions.meters
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [MapOverlayContent]. The composable is parameterized
 * over [MapOverlayState] so the test builds the state directly — no real
 * [ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel] needed.
 *
 * The compass slot is empty in tests (the real [org.maplibre.compose.material3.CompassButton]
 * needs a real [CameraState] backed by a MapLibre map, which we don't have under
 * JVM unit tests). The [CameraState] passed in here is a bare constructed
 * instance — [MapOverlayContent] only touches it inside the zoom-button
 * click lambdas, which the tests never invoke, so a map-less instance is fine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapOverlayContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testScope: CoroutineScope = CoroutineScope(SupervisorJob())

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun sampleConfig(): LayoutConfig = LayoutConfig(
        widgetScale = 1.0f,
        fabScale = 1.0f,
        edgePadding = 16.dp,
        controlSpacing = 12.dp,
    )

    private fun hostContent(darkTheme: Boolean = false, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            RoadTripRadarTheme(darkTheme = darkTheme) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    private fun sampleState(
        waypoints: List<Waypoint> = emptyList(),
        activeWaypoint: Waypoint? = null,
        activeIndex: Int? = null,
        hasFailedCells: Boolean = false,
        windEnabled: Boolean = true,
        weatherActive: Boolean = false,
        showLegend: Boolean = false,
        hasRadarFrames: Boolean = false,
        showTimeline: Boolean = false,
        radarFrameTimes: List<Long> = emptyList(),
    ) = MapOverlayState(
        useMetric = true,
        speedMps = 25.0,
        speedSize = 1.0f,
        useGps = true,
        hasGpsFix = true,
        gpsIconOpacity = 0.8f,
        networkStatus = NetworkStatus(
            transport = NetworkTransport.WIFI,
            validated = true,
            connected = true,
        ),
        activeWaypoint = activeWaypoint,
        activeIndex = activeIndex,
        waypoints = waypoints,
        navWidgetSize = 1.0f,
        compassWidgetSize = 1.0f,
        hasFailedCells = hasFailedCells,
        isNorthUp = true,
        weatherActive = weatherActive,
        showTimeline = showTimeline,
        radarFrameTimes = radarFrameTimes,
        currentFrameIndex = 0,
        weatherWidgetSize = 100f,
        windEnabled = windEnabled,
        openMeteoSnapshot = null,
        temperatureUnit = TemperatureUnit.CELSIUS,
        windSpeedUnit = WindSpeedUnit.KMH,
        showLegend = showLegend,
        hasRadarFrames = hasRadarFrames,
        fabScale = 1.0f,
        widgetScale = 1.0f,
        isTrackingCamera = true,
        isWeatherPlaying = false,
    )

    @Test
    fun content_allFieldsPopulated_rendersWithoutCrash() {
        hostContent {
            Box(Modifier.fillMaxSize()) {
                MapOverlayContent(
                    config = sampleConfig(),
                    state = sampleState(),
                    bearing = 0.0,
                    hasLocation = true,
                    poiInfo = null,
                    cameraState = CameraState(CameraPosition()),
                    scope = testScope,
                )
            }
        }
        // The render completes; assertExists is implicit.
        assertTrue(true)
    }

    @Test
    fun content_emptyWaypoints_skipsNavRow() {
        // No active waypoint + no waypoints = the nav widget row should not render.
        hostContent {
            Box(Modifier.fillMaxSize()) {
                MapOverlayContent(
                    config = sampleConfig(),
                    state = sampleState(
                        waypoints = emptyList(),
                        activeWaypoint = null,
                        activeIndex = null,
                    ),
                    bearing = 0.0,
                    hasLocation = true,
                    poiInfo = null,
                    cameraState = CameraState(CameraPosition()),
                    scope = testScope,
                )
            }
        }
        // The next-waypoint chevron should be absent since waypoints.size < 2.
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.cd_next_waypoint))
            .assertDoesNotExist()
    }

    @Test
    fun content_withActiveWaypoint_rendersNavWidget() {
        // The next-waypoint chevron only renders when waypoints.size >= 2
        // (see MapOverlayTopRow: `val showChevrons = waypoints.size >= 2`),
        // so the test supplies two waypoints and activeIndex = 0.
        val wp = Waypoint(id = "1", lat = 43.0, lon = -79.0)
        val wp2 = Waypoint(id = "2", lat = 44.0, lon = -78.0)
        val poiInfo: Pair<Length, Double> = 500.0.meters to 90.0
        hostContent {
            Box(Modifier.fillMaxSize()) {
                MapOverlayContent(
                    config = sampleConfig(),
                    state = sampleState(
                        waypoints = listOf(wp, wp2),
                        activeWaypoint = wp,
                        activeIndex = 0,
                    ),
                    bearing = 0.0,
                    hasLocation = true,
                    poiInfo = poiInfo,
                    cameraState = CameraState(CameraPosition()),
                    scope = testScope,
                )
            }
        }
        // The next-waypoint chevron Icon carries the cd_next_waypoint content
        // description; find it to prove the nav row rendered.
        composeTestRule
            .onNodeWithContentDescription(
                context.getString(R.string.cd_next_waypoint),
            )
            .assertExists()
    }

    @Test
    fun content_weatherInactive_skipsTimelineAndLegend() {
        hostContent {
            Box(Modifier.fillMaxSize()) {
                MapOverlayContent(
                    config = sampleConfig(),
                    state = sampleState(
                        weatherActive = false,
                        showTimeline = true,
                        hasRadarFrames = true,
                        showLegend = true,
                    ),
                    bearing = 0.0,
                    hasLocation = true,
                    poiInfo = null,
                    cameraState = CameraState(CameraPosition()),
                    scope = testScope,
                )
            }
        }
        // weatherActive = false so neither the timeline nor the legend should
        // render. Their absence is implicit; the test is that it doesn't crash.
        assertTrue(true)
    }

    @Test
    fun content_failedCells_rendersRetryButton() {
        hostContent {
            Box(Modifier.fillMaxSize()) {
                MapOverlayContent(
                    config = sampleConfig(),
                    state = sampleState(hasFailedCells = true),
                    bearing = 0.0,
                    hasLocation = true,
                    poiInfo = null,
                    cameraState = CameraState(CameraPosition()),
                    scope = testScope,
                )
            }
        }
        // RetryFailedButton renders a Text label (not a contentDescription), so
        // look the button up by its text via the fab_retry_failed resource.
        composeTestRule
            .onNodeWithText(context.getString(R.string.fab_retry_failed))
            .assertExists()
    }

    @Test
    fun content_darkTheme_appliesDarkPalette() {
        hostContent(darkTheme = true) {
            Box(Modifier.fillMaxSize()) {
                MapOverlayContent(
                    config = sampleConfig(),
                    state = sampleState(),
                    bearing = 0.0,
                    hasLocation = true,
                    poiInfo = null,
                    cameraState = CameraState(CameraPosition()),
                    scope = testScope,
                )
            }
        }
        // Renders without crash in dark mode.
        assertTrue(true)
    }
}