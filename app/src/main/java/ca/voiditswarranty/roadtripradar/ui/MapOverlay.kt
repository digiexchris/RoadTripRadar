package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.data.OpenMeteoSnapshot
import ca.voiditswarranty.roadtripradar.data.Waypoint
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import ca.voiditswarranty.roadtripradar.ui.tutorial.TutorialAnchors
import ca.voiditswarranty.roadtripradar.ui.tutorial.tutorialAnchor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.material3.CompassButton
import org.maplibre.spatialk.units.Length

/**
 * Equal-width slot for the top row: each slot gets `weight(1f)` so the slots' centres are
 * at fixed fractions of the row width, but [wrapContentSize(unbounded = true)] lets wide
 * content (e.g. the nav widget) extend past its slot without shifting the centre.
 */
@Composable
private fun RowScope.TopRowSlot(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.wrapContentSize(align = Alignment.Center, unbounded = true),
        ) {
            content()
        }
    }
}

@Composable
fun BoxScope.MapOverlay(
    config: LayoutConfig,
    vm: ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel,
    bearing: Double,
    hasLocation: Boolean,
    hasGpsFix: Boolean,
    speedMps: Double,
    poiInfo: Pair<Length, Double>?,
    cameraState: CameraState,
    scope: CoroutineScope,
    isLandscape: Boolean = false,
) {
    val state = MapOverlayState(
        useMetric = vm.useMetric,
        speedMps = speedMps,
        speedSize = vm.speedSize,
        useGps = vm.useGps,
        hasGpsFix = hasGpsFix,
        gpsIconOpacity = vm.gpsIconOpacity,
        networkStatus = vm.networkStatus,
        activeWaypoint = vm.activeWaypoint,
        activeIndex = vm.activeIndex,
        waypoints = vm.waypoints,
        navWidgetSize = vm.navWidgetSize,
        compassWidgetSize = vm.compassWidgetSize,
        hasFailedCells = vm.hasFailedCells,
        isNorthUp = vm.isNorthUp,
        weatherActive = vm.weatherActive,
        showTimeline = vm.showTimeline,
        radarFrameTimes = vm.radarFrameTimes,
        currentFrameIndex = vm.currentFrameIndex,
        weatherWidgetSize = vm.weatherWidgetSize,
        windEnabled = vm.windEnabled,
        openMeteoSnapshot = vm.openMeteoSnapshot,
        temperatureUnit = vm.temperatureUnit,
        windSpeedUnit = vm.windSpeedUnit,
        showLegend = vm.showLegend,
        hasRadarFrames = vm.radarFramePaths.isNotEmpty(),
        fabScale = config.fabScale,
        widgetScale = config.widgetScale,
        isTrackingCamera = vm.isTrackingCamera,
        isWeatherPlaying = vm.isWeatherPlaying,
    )
    MapOverlayContent(
        config = config,
        state = state,
        bearing = bearing,
        hasLocation = hasLocation,
        poiInfo = poiInfo,
        cameraState = cameraState,
        scope = scope,
        isLandscape = isLandscape,
        onOpenRouteEditor = vm::openRouteEditor,
        onAdvanceWaypoint = vm::advanceActiveWaypoint,
        onRegressWaypoint = vm::regressActiveWaypoint,
        onCompassToggle = { isNorthUp -> vm.isNorthUp = isNorthUp },
        onRetryFailedCells = vm::retryFailedCells,
        onZoomInLong = vm::advanceActiveWaypoint,
        onZoomOutLong = vm::regressActiveWaypoint,
        onToggleWeatherPlayPause = vm::toggleWeatherPlayPause,
        onWeatherOff = vm::turnOffWeather,
        onOpenMenu = vm::openActionsDrawer,
        onRecenter = { vm.isTrackingCamera = true },
        compassButton = {
            // Mirror of `MapOverlayContent`'s `compassSize` formula — both must
            // stay in sync. `MapOverlayContent` uses its value for the
            // fallback top-band height estimate; this one drives the actual
            // rendered compass.
            val compassSize = vm.compassWidgetSize.dp * config.widgetScale
            Box(modifier = Modifier.tutorialAnchor(TutorialAnchors.COMPASS)) {
                CompassButton(
                    cameraState = cameraState,
                    colors = ButtonDefaults.elevatedButtonColors(),
                    size = compassSize,
                    contentPadding = PaddingValues(8.dp),
                    shape = CircleShape,
                    getHomePosition = { current ->
                        val newNorthUp = !vm.isNorthUp
                        vm.isNorthUp = newNorthUp
                        if (newNorthUp) {
                            current.copy(bearing = 0.0, tilt = 0.0)
                        } else {
                            current.copy(tilt = 0.0)
                        }
                    },
                )
            }
        },
    )
}

/**
 * Parameterized delegation target of the public [MapOverlay]. Takes
 * [MapOverlayState] (every VM-derived value) and the caller-supplied
 * camera/location/POI values as direct params, so tests can drive the
 * overlay without instantiating a real [ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel].
 * Callbacks are wired by the public [MapOverlay] wrapper; the empty defaults
 * here serve as test seams so tests can drive the overlay without a real VM.
 */
@Composable
internal fun BoxScope.MapOverlayContent(
    config: LayoutConfig,
    state: MapOverlayState,
    bearing: Double,
    hasLocation: Boolean,
    poiInfo: Pair<Length, Double>?,
    cameraState: CameraState,
    scope: CoroutineScope,
    isLandscape: Boolean = false,
    onOpenRouteEditor: () -> Unit = {},
    onAdvanceWaypoint: () -> Unit = {},
    onRegressWaypoint: () -> Unit = {},
    onCompassToggle: (Boolean) -> Unit = {},
    onRetryFailedCells: () -> Unit = {},
    onZoomInLong: () -> Unit = {},
    onZoomOutLong: () -> Unit = {},
    onToggleWeatherPlayPause: () -> Unit = {},
    onWeatherOff: () -> Unit = {},
    onOpenMenu: () -> Unit = {},
    onRecenter: () -> Unit = {},
    compassButton: @Composable () -> Unit = {},
) {
    val sharedEdgeModifier = Modifier.padding(config.edgePadding)
    val density = LocalDensity.current
    var topRowHeightPx by remember { mutableIntStateOf(0) }
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomRowContentHeight = 96.dp * config.fabScale
    val compassSize = state.compassWidgetSize.dp * config.widgetScale
    val measuredTopBandHeight = with(density) { topRowHeightPx.toDp() }
    val estimatedTopBandHeight = compassSize + (config.edgePadding * 2)
    val topBandHeight = (if (topRowHeightPx > 0) measuredTopBandHeight else estimatedTopBandHeight) +
        config.controlSpacing
    val bottomBandHeight = bottomRowContentHeight + navBottomInset + (config.edgePadding * 2)
    var legendHeightPx by remember { mutableIntStateOf(0) }
    val legendHeight = with(density) { legendHeightPx.toDp() }

    MapOverlayTopRow(
        useMetric = state.useMetric,
        speedMps = state.speedMps,
        speedSize = state.speedSize,
        hasLocation = hasLocation,
        useGps = state.useGps,
        hasGpsFix = state.hasGpsFix,
        gpsIconOpacity = state.gpsIconOpacity,
        networkStatus = state.networkStatus,
        activeWaypoint = state.activeWaypoint,
        activeIndex = state.activeIndex,
        waypoints = state.waypoints,
        bearing = bearing,
        poiInfo = poiInfo,
        navWidgetSize = state.navWidgetSize,
        hasFailedCells = state.hasFailedCells,
        isNorthUp = state.isNorthUp,
        fabScale = config.fabScale,
        widgetScale = config.widgetScale,
        onOpenRouteEditor = onOpenRouteEditor,
        onAdvanceWaypoint = onAdvanceWaypoint,
        onRegressWaypoint = onRegressWaypoint,
        onCompassToggle = onCompassToggle,
        onRetryFailedCells = onRetryFailedCells,
        compassButton = compassButton,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .onSizeChanged { topRowHeightPx = it.height }
            .then(sharedEdgeModifier),
    )

    LeftContent(
        verticalArrangement = Arrangement.Bottom,
        modifier = Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .padding(
                start = config.edgePadding,
                top = topBandHeight,
                bottom = bottomBandHeight,
            ),
    ) {
        MapOverlayLeftContent(
            topBandHeight = topBandHeight,
            bottomBandHeight = bottomBandHeight,
            legendHeight = legendHeight,
            weatherActive = state.weatherActive,
            showTimeline = state.showTimeline,
            frameTimes = state.radarFrameTimes,
            currentFrameIndex = state.currentFrameIndex,
        )
    }

    RightContent(
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .padding(
                end = config.edgePadding,
                top = topBandHeight,
                bottom = bottomBandHeight,
            ),
    ) {
        MapOverlayRightContent(
            weatherWidgetSize = state.weatherWidgetSize * config.widgetScale,
            bearing = bearing,
            windEnabled = state.windEnabled,
            openMeteoSnapshot = state.openMeteoSnapshot,
            temperatureUnit = state.temperatureUnit,
            windSpeedUnit = state.windSpeedUnit,
            weatherActive = state.weatherActive,
            showLegend = state.showLegend,
            hasRadarFrames = state.hasRadarFrames,
            onHeightChanged = { legendHeightPx = it },
        )
    }

    BottomContent(
        onZoomIn = {
            scope.launch {
                cameraState.animateTo(
                    cameraState.position.copy(zoom = cameraState.position.zoom + 1)
                )
            }
        },
        onZoomOut = {
            scope.launch {
                cameraState.animateTo(
                    cameraState.position.copy(zoom = cameraState.position.zoom - 1)
                )
            }
        },
        onZoomInLong = onZoomInLong,
        onZoomOutLong = onZoomOutLong,
        isWeatherPlaying = state.isWeatherPlaying,
        weatherActive = state.weatherActive,
        onToggleWeatherPlayPause = onToggleWeatherPlayPause,
        onWeatherOff = onWeatherOff,
        onOpenMenu = onOpenMenu,
        aboveContent = {
            RecenterTextButton(
                hasLocation = hasLocation,
                isTrackingCamera = state.isTrackingCamera,
                onRecenter = onRecenter,
                scale = config.fabScale * 1.3f,
            )
        },
        isLandscape = isLandscape,
        scale = config.fabScale,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = config.edgePadding),
    )
}

/**
 * Left-edge overlay content: the vertical radar timeline. Renders
 * the timeline only when weather is active, the timeline is
 * requested, and frame times are available. Extracted from
 * [MapOverlay] to be testable in isolation.
 */
@Composable
fun MapOverlayLeftContent(
    topBandHeight: Dp,
    bottomBandHeight: Dp,
    legendHeight: Dp,
    weatherActive: Boolean,
    showTimeline: Boolean,
    frameTimes: List<Long>,
    currentFrameIndex: Int,
) {
    if (weatherActive && showTimeline && frameTimes.isNotEmpty()) {
        val timelineMod = if (legendHeight > 0.dp) {
            Modifier.height(legendHeight)
        } else {
            Modifier
        }
        WeatherTimeline(
            frameTimes = frameTimes,
            currentFrameIndex = currentFrameIndex,
            horizontal = false,
            modifier = timelineMod,
        )
    }
}

/**
 * Right-edge overlay content: the weather widget (when wind is
 * enabled) and the weather legend (when weather radar is active
 * and frames are available). The legend reports its measured
 * height back via [onHeightChanged] so the parent can size the
 * left-side timeline to match. Extracted from [MapOverlay] to
 * be testable in isolation.
 */
@Composable
fun ColumnScope.MapOverlayRightContent(
    weatherWidgetSize: Float,
    bearing: Double,
    windEnabled: Boolean,
    openMeteoSnapshot: OpenMeteoSnapshot?,
    temperatureUnit: TemperatureUnit,
    windSpeedUnit: WindSpeedUnit,
    weatherActive: Boolean,
    showLegend: Boolean,
    hasRadarFrames: Boolean,
    onHeightChanged: (Int) -> Unit,
) {
    if (windEnabled) {
        WeatherWidget(
            snapshot = openMeteoSnapshot,
            temperatureUnit = temperatureUnit,
            windSpeedUnit = windSpeedUnit,
            weatherWidgetSize = weatherWidgetSize,
            cameraBearing = bearing,
        )
    }

    Spacer(modifier = Modifier.weight(1f))

    if (weatherActive && showLegend && hasRadarFrames) {
        WeatherLegend(
            horizontal = false,
            modifier = Modifier.onSizeChanged { onHeightChanged(it.height) },
        )
    }
}

/**
 * Top-edge overlay content: the horizontal bar with the speed
 * readout, network status, nav widget (chevron controls), GPS
 * status, and compass. Below the bar is the retry-failed-cells
 * button. The compass is a [compassButton] slot because the
 * real [CompassButton] requires a [CameraState] that's only
 * available in the production [MapOverlay] context. Tests can
 * pass an empty slot. Extracted from [MapOverlay] to be
 * testable in isolation.
 */
@Composable
fun MapOverlayTopRow(
    useMetric: Boolean,
    speedMps: Double,
    speedSize: Float,
    hasLocation: Boolean,
    useGps: Boolean,
    hasGpsFix: Boolean,
    gpsIconOpacity: Float,
    networkStatus: ca.voiditswarranty.roadtripradar.model.NetworkStatus,
    activeWaypoint: Waypoint?,
    activeIndex: Int?,
    waypoints: List<Waypoint>,
    bearing: Double,
    poiInfo: Pair<org.maplibre.spatialk.units.Length, Double>?,
    navWidgetSize: Float,
    hasFailedCells: Boolean,
    isNorthUp: Boolean,
    fabScale: Float,
    widgetScale: Float,
    onOpenRouteEditor: () -> Unit,
    onAdvanceWaypoint: () -> Unit,
    onRegressWaypoint: () -> Unit,
    onCompassToggle: (Boolean) -> Unit,
    onRetryFailedCells: () -> Unit,
    compassButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            TopRowSlot {
                if (hasLocation) {
                    SpeedReadout(
                        speedMps = speedMps,
                        useMetric = useMetric,
                        speedSize = speedSize * widgetScale,
                        modifier = Modifier.tutorialAnchor(TutorialAnchors.SPEED),
                    )
                }
            }
            TopRowSlot {
                NetworkStatusIcon(
                    status = networkStatus,
                    opacity = gpsIconOpacity,
                )
            }
            TopRowSlot {
                val activeWp = activeWaypoint
                val activeIdx = activeIndex
                if (activeWp != null && activeIdx != null && poiInfo != null) {
                    val (poiDist, poiBearingDeg) = poiInfo
                    val label = waypointDisplayLabel(activeWp, activeIdx)
                    val showChevrons = waypoints.size >= 2
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showChevrons) {
                            IconButton(
                                onClick = onRegressWaypoint,
                                enabled = activeIdx > 0,
                            ) {
                                Icon(
                                    Icons.Default.ChevronLeft,
                                    contentDescription = stringResource(R.string.cd_prev_waypoint),
                                )
                            }
                        }
                        NavWidget(
                            poiDistance = poiDist,
                            poiBearingDeg = poiBearingDeg,
                            cameraBearing = bearing,
                            navWidgetSize = navWidgetSize * widgetScale,
                            poiName = label,
                            stopCount = waypoints.size,
                            useMetric = useMetric,
                            modifier = Modifier.tutorialAnchor(TutorialAnchors.NAV_WIDGET),
                            onClick = onOpenRouteEditor,
                        )
                        if (showChevrons) {
                            IconButton(
                                onClick = onAdvanceWaypoint,
                                enabled = activeIdx < waypoints.size - 1,
                            ) {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = stringResource(R.string.cd_next_waypoint),
                                )
                            }
                        }
                    }
                }
            }
            TopRowSlot {
                if (useGps) {
                    GpsStatusIcon(
                        hasGpsFix = hasGpsFix,
                        opacity = gpsIconOpacity,
                    )
                }
            }
            TopRowSlot {
                compassButton()
            }
        }
        RetryFailedButton(
            hasFailedCells = hasFailedCells,
            onRetry = onRetryFailedCells,
            scale = fabScale * 1.3f,
        )
    }
}
