package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.material3.CompassButton
import org.maplibre.spatialk.units.Length

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

    TopContent(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .onSizeChanged { topRowHeightPx = it.height }
            .then(sharedEdgeModifier),
        leftContent = {
            if (hasLocation) {
                SpeedReadout(
                    speedMps = speedMps,
                    useMetric = vm.useMetric,
                    speedSize = vm.speedSize * config.widgetScale,
                )
            }
        },
        centerContent = {
            if (vm.poiPosition != null && poiInfo != null) {
                val (poiDist, poiBearingDeg) = poiInfo
                NavWidget(
                    poiDistance = poiDist,
                    poiBearingDeg = poiBearingDeg,
                    cameraBearing = bearing,
                    navWidgetSize = vm.navWidgetSize * config.widgetScale,
                    poiName = vm.poiName,
                    useMetric = vm.useMetric,
                )
            }
        },
        rightContent = {
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
        },
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
        navContent = {
            StatusAndRecenterPanel(
                networkStatus = vm.networkStatus,
                hasGpsFix = hasGpsFix,
                useGps = vm.useGps,
                gpsIconOpacity = vm.gpsIconOpacity,
                hasLocation = hasLocation,
                isTrackingCamera = vm.isTrackingCamera,
                onRecenter = { vm.isTrackingCamera = true },
                recenterScale = config.fabScale * 1.3f,
            )
        },
        scale = config.fabScale,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(config.edgePadding),
    )
}
