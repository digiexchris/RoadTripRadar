package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.R
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
    val sharedEdgeModifier = Modifier.padding(config.edgePadding)
    val density = LocalDensity.current
    var topRowHeightPx by remember { mutableIntStateOf(0) }
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomRowContentHeight = 96.dp * config.fabScale
    val compassSize = vm.compassWidgetSize.dp * config.widgetScale
    val measuredTopBandHeight = with(density) { topRowHeightPx.toDp() }
    val estimatedTopBandHeight = compassSize + (config.edgePadding * 2)
    val topBandHeight = (if (topRowHeightPx > 0) measuredTopBandHeight else estimatedTopBandHeight) +
        config.controlSpacing
    val bottomBandHeight = bottomRowContentHeight + navBottomInset + (config.edgePadding * 2)
    var legendHeightPx by remember { mutableIntStateOf(0) }
    val legendHeight = with(density) { legendHeightPx.toDp() }

    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .onSizeChanged { topRowHeightPx = it.height }
            .then(sharedEdgeModifier),
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
                        useMetric = vm.useMetric,
                        speedSize = vm.speedSize * config.widgetScale,
                        modifier = Modifier.tutorialAnchor(TutorialAnchors.SPEED),
                    )
                }
            }
            TopRowSlot {
                NetworkStatusIcon(
                    status = vm.networkStatus,
                    opacity = vm.gpsIconOpacity,
                )
            }
            TopRowSlot {
                val activeWp = vm.activeWaypoint
                val activeIdx = vm.activeIndex
                if (activeWp != null && activeIdx != null && poiInfo != null) {
                    val (poiDist, poiBearingDeg) = poiInfo
                    val label = waypointDisplayLabel(activeWp, activeIdx)
                    val showChevrons = vm.waypoints.size >= 2
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showChevrons) {
                            IconButton(
                                onClick = { vm.regressActiveWaypoint() },
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
                            navWidgetSize = vm.navWidgetSize * config.widgetScale,
                            poiName = label,
                            stopCount = vm.waypoints.size,
                            useMetric = vm.useMetric,
                            modifier = Modifier.tutorialAnchor(TutorialAnchors.NAV_WIDGET),
                            onClick = { vm.openRouteEditor() },
                        )
                        if (showChevrons) {
                            IconButton(
                                onClick = { vm.advanceActiveWaypoint() },
                                enabled = activeIdx < vm.waypoints.size - 1,
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
                if (vm.useGps) {
                    GpsStatusIcon(
                        hasGpsFix = hasGpsFix,
                        opacity = vm.gpsIconOpacity,
                    )
                }
            }
            TopRowSlot {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.tutorialAnchor(TutorialAnchors.COMPASS),
                ) {
                    CompassButton(
                        cameraState = cameraState,
                        colors = ButtonDefaults.elevatedButtonColors(),
                        size = compassSize,
                        contentPadding = PaddingValues(8.dp),
                        shape = CircleShape,
                        getHomePosition = { current ->
                            vm.isNorthUp = !vm.isNorthUp
                            if (vm.isNorthUp) {
                                current.copy(bearing = 0.0, tilt = 0.0)
                            } else {
                                current.copy(tilt = 0.0)
                            }
                        },
                    )
                }
            }
        }
        RetryFailedButton(
            hasFailedCells = vm.hasFailedCells,
            onRetry = { vm.retryFailedCells() },
            scale = config.fabScale * 1.3f,
        )
    }

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
        if (vm.weatherActive && vm.showTimeline && vm.radarFramePaths.isNotEmpty()) {
            val timelineMod = if (legendHeightPx > 0) {
                Modifier.height(legendHeight)
            } else {
                Modifier
            }
            WeatherTimeline(
                frameTimes = vm.radarFrameTimes,
                currentFrameIndex = vm.currentFrameIndex,
                horizontal = false,
                modifier = timelineMod,
            )
        }
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
        if (vm.windEnabled) {
            WeatherWidget(
                snapshot = vm.openMeteoSnapshot,
                temperatureUnit = vm.temperatureUnit,
                windSpeedUnit = vm.windSpeedUnit,
                weatherWidgetSize = vm.weatherWidgetSize * config.widgetScale,
                cameraBearing = bearing,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (vm.weatherActive && vm.showLegend && vm.radarFramePaths.isNotEmpty()) {
            WeatherLegend(
                horizontal = false,
                modifier = Modifier.onSizeChanged { legendHeightPx = it.height },
            )
        }
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
        onZoomInLong = { vm.advanceActiveWaypoint() },
        onZoomOutLong = { vm.regressActiveWaypoint() },
        isWeatherPlaying = vm.isWeatherPlaying,
        weatherActive = vm.weatherActive,
        onToggleWeatherPlayPause = { vm.toggleWeatherPlayPause() },
        onWeatherOff = { vm.turnOffWeather() },
        onOpenMenu = { vm.openActionsDrawer() },
        aboveContent = {
            RecenterTextButton(
                hasLocation = hasLocation,
                isTrackingCamera = vm.isTrackingCamera,
                onRecenter = { vm.isTrackingCamera = true },
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
