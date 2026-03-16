package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.sp
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
    val speedReadoutHeight = remember(vm.speedSize, config.widgetScale, density) {
        with(density) {
            (vm.speedSize * config.widgetScale).sp.toDp() +
                ((vm.speedSize / 3f) * config.widgetScale).sp.toDp() +
                16.dp
        }
    }
    val bottomRowContentHeight = maxOf(96.dp * config.fabScale, speedReadoutHeight)
    val measuredTopBandHeight = with(density) { topRowHeightPx.toDp() }
    val estimatedTopBandHeight = config.compassSize + (config.edgePadding * 2)
    val topBandHeight = (if (topRowHeightPx > 0) measuredTopBandHeight else estimatedTopBandHeight) +
        config.controlSpacing
    val bottomBandHeight = bottomRowContentHeight + navBottomInset + (config.edgePadding * 2)

    if (vm.poiPosition != null && poiInfo != null) {
        val (poiDist, poiBearingDeg) = poiInfo
        NavWidget(
            poiDistance = poiDist,
            poiBearingDeg = poiBearingDeg,
            cameraBearing = bearing,
            navWidgetSize = vm.navWidgetSize * config.widgetScale,
            poiName = vm.poiName,
            useMetric = vm.useMetric,
            modifier = Modifier
                .align(config.navWidgetAlignment)
                .then(sharedEdgeModifier),
        )
    }

    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .onSizeChanged { topRowHeightPx = it.height }
            .then(sharedEdgeModifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            NetworkStatusIcon(
                status = vm.networkStatus,
                opacity = vm.gpsIconOpacity,
            )
            if (vm.useGps) {
                GpsStatusIcon(
                    hasGpsFix = hasGpsFix,
                    opacity = vm.gpsIconOpacity,
                )
            }
        }

        CompassButton(
            cameraState = cameraState,
            colors = ButtonDefaults.elevatedButtonColors(),
            size = config.compassSize,
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

    Column(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .padding(
                start = config.edgePadding,
                top = topBandHeight,
                bottom = bottomBandHeight,
            ),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.Start,
    ) {
        if (vm.weatherActive && vm.showTimeline && vm.radarFramePaths.isNotEmpty()) {
            WeatherTimeline(
                frameTimes = vm.radarFrameTimes,
                currentFrameIndex = vm.currentFrameIndex,
                horizontal = config.timelineHorizontal,
            )
        }
    }

    Column(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .padding(
                end = config.edgePadding,
                top = topBandHeight,
                bottom = bottomBandHeight,
            ),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
    ) {
        if (vm.weatherActive && vm.showLegend && vm.radarFramePaths.isNotEmpty()) {
            WeatherLegend(
                horizontal = config.legendHorizontal,
            )
        }

        RecenterFab(
            hasLocation = hasLocation,
            isTrackingCamera = vm.isTrackingCamera,
            onRecenter = { vm.isTrackingCamera = true },
            scale = config.fabScale,
        )
    }

    SpeedZoomFabs(
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
        speedContent = {
            if (hasLocation) {
                SpeedReadout(
                    speedMps = speedMps,
                    useMetric = vm.useMetric,
                    speedSize = vm.speedSize * config.widgetScale,
                )
            }
        },
        scale = config.fabScale,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(config.edgePadding),
    )
}
