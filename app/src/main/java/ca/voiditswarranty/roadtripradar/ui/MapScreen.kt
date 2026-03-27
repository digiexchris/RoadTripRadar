package ca.voiditswarranty.roadtripradar.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.data.isDarkForAppTheme
import ca.voiditswarranty.roadtripradar.data.resolvedStyleUri
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.model.buildRadarRingsData
import ca.voiditswarranty.roadtripradar.model.ringDistancesForZoom
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.layers.Anchor
import org.maplibre.compose.location.BearingUpdate
import org.maplibre.compose.location.LocationTrackingEffect
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberNullLocationProvider
import org.maplibre.compose.location.rememberUserLocationState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.turf.measurement.bearingTo
import org.maplibre.spatialk.turf.measurement.distance
import org.maplibre.spatialk.units.Bearing
import org.maplibre.spatialk.units.extensions.inDegrees

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    vm: MapViewModel,
    mapStyle: MapStyle,
    onStyleChange: (MapStyle) -> Unit,
    locationPermissionGranted: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Location
    val locationProvider = if (locationPermissionGranted && vm.useGps) {
        rememberDefaultLocationProvider()
    } else {
        rememberNullLocationProvider()
    }
    val locationState = rememberUserLocationState(locationProvider = locationProvider)
    val hasLocation = vm.useGps && locationState.location != null
    val hasGpsFix = hasLocation && locationState.location!!.accuracy < 50.0

    // Camera
    val configuration = LocalConfiguration.current
    val mapStyleUri = mapStyle.resolvedStyleUri(context)
    val mapOverlaysDark = mapStyle.isDarkForAppTheme(context)
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenHeight = configuration.screenHeightDp.dp
    val density = LocalDensity.current
    val safeInsets = WindowInsets.safeDrawing
    val topInset = with(density) { safeInsets.getTop(density).toDp() }
    val bottomInset = with(density) { safeInsets.getBottom(density).toDp() }
    val usableHeight = (screenHeight - topInset - bottomInset).coerceAtLeast(1.dp)
    val centerOffsetFraction =
        if (isLandscape) vm.mapCenterOffsetLandscapeFraction else vm.mapCenterOffsetPortraitFraction

    // Treat slider value as desired map-center distance from bottom of the usable map area.
    val desiredBottomOffset = usableHeight * centerOffsetFraction
    val computedTopPadding = (usableHeight - (desiredBottomOffset * 2f)).coerceAtLeast(0.dp)
    val computedBottomPadding = ((desiredBottomOffset * 2f) - usableHeight).coerceAtLeast(0.dp)
    val cameraPadding = PaddingValues(
        top = topInset + computedTopPadding,
        bottom = bottomInset + computedBottomPadding,
    )
    val savedZoom = remember { vm.prefsRepo.zoomLevel.toDouble() }
    val startPosition = remember { vm.prefsRepo.lastKnownPosition }

    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = startPosition,
            zoom = savedZoom,
            padding = cameraPadding,
        )
    )

    LaunchedEffect(centerOffsetFraction, isLandscape, screenHeight, topInset, bottomInset) {
        cameraState.animateTo(
            cameraState.position.copy(
                padding = cameraPadding,
            )
        )
    }

    LaunchedEffect(cameraState.moveReason) {
        if (cameraState.moveReason == CameraMoveReason.GESTURE) {
            vm.isTrackingCamera = false
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { cameraState.position.zoom }
            .collect { zoom -> vm.onZoomChanged(zoom.toFloat()) }
    }

    // Keep screen on
    LaunchedEffect(vm.keepScreenOn) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        if (vm.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Store location every 15 seconds for next startup
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            val pos = if (locationState.location != null && vm.useGps) {
                locationState.location!!.position
            } else {
                cameraState.position.target
            }
            vm.saveLastKnownPosition(pos)
        }
    }

    LocationTrackingEffect(
        locationState = locationState,
        enabled = vm.isTrackingCamera && hasLocation,
        trackBearing = !vm.isNorthUp,
    ) {
        cameraState.updateFromLocation(
            updateBearing = if (vm.isNorthUp) BearingUpdate.ALWAYS_NORTH else BearingUpdate.TRACK_LOCATION,
        )
    }

    // Derived state
    val zoomTier by remember {
        derivedStateOf {
            when {
                cameraState.position.zoom >= 16 -> 0
                cameraState.position.zoom >= 14 -> 1
                cameraState.position.zoom >= 12 -> 2
                cameraState.position.zoom >= 10 -> 3
                cameraState.position.zoom >= 8 -> 4
                cameraState.position.zoom >= 6 -> 5
                cameraState.position.zoom >= 4 -> 6
                else -> 7
            }
        }
    }

    val userPosition = locationState.location?.position
    LaunchedEffect(userPosition) {
        vm.setLocalWeatherAnchor(userPosition)
    }
    val bearing = cameraState.position.bearing
    val ringsCenter = if (hasLocation && userPosition != null) userPosition else cameraState.position.target
    val radarData = remember(ringsCenter.latitude, ringsCenter.longitude, zoomTier, bearing, vm.useMetric) {
        buildRadarRingsData(ringsCenter, ringDistancesForZoom(cameraState.position.zoom), bearing, vm.useMetric)
    }

    val poiInfo = remember(userPosition?.latitude, userPosition?.longitude, vm.poiPosition) {
        val user = userPosition ?: return@remember null
        val poi = vm.poiPosition ?: return@remember null
        val dist = distance(user, poi)
        val poiBearing = user.bearingTo(poi)
        val poiBearingDeg = (poiBearing - Bearing.North).inDegrees
        Pair(dist, poiBearingDeg)
    }

    // Feed camera info to ViewModel for search
    vm.userPositionForSearch = userPosition
    vm.screenWidthDp = configuration.screenWidthDp.toDouble()
    vm.screenHeightDp = configuration.screenHeightDp.toDouble()

    // Periodic POI cell coverage check (works for both panning and driving)
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            val pos = cameraState.position
            vm.onCameraSettled(pos.target.latitude, pos.target.longitude, pos.zoom)
        }
    }

    // UI
    Box(modifier = Modifier.fillMaxSize()) {
        key(mapStyleUri) {
            MaplibreMap(
                baseStyle = BaseStyle.Uri(mapStyleUri),
                cameraState = cameraState,
                modifier = Modifier.fillMaxSize(),
                options = MapOptions(
                    ornamentOptions = OrnamentOptions(
                        isScaleBarEnabled = false,
                        isCompassEnabled = false,
                    ),
                ),
                onMapLongClick = { position, _ ->
                    vm.setPoiFromLongPress(position)
                    ClickResult.Consume
                },
                onMapClick = { _, _ ->
                    ClickResult.Pass
                },
            ) {
                Anchor.Top {
                if (vm.weatherActive && vm.radarFramePaths.isNotEmpty()) {
                    WeatherRadarLayers(
                        radarFramePaths = vm.radarFramePaths,
                        currentFrameIndex = vm.currentFrameIndex,
                        radarOpacity = vm.radarOpacity,
                    )
                }

                RadarRingsLayers(
                    radarData = radarData,
                    isDarkStyle = mapOverlaysDark,
                )

                PoiLoadBoundsLayer(
                    bounds = vm.poiLoadBounds?.takeIf { vm.hasNearbyPoiFeatures },
                    isDarkStyle = mapOverlaysDark,
                    visible = vm.poiLoadBounds != null,
                )

                if (hasLocation) {
                    UserLocationPuck(
                        locationState = locationState,
                        cameraState = cameraState,
                    )
                }

                if (vm.poiPosition != null) {
                    PoiLayers(
                        poiPosition = vm.poiPosition!!,
                        userPosition = userPosition,
                    )
                }

                NearbyPoiLayers(
                    vm = vm,
                    enabledCategories = vm.enabledPoiCategories,
                    visible = vm.poiLoadBounds != null,
                    categoriesVersion = vm.poiCategoriesVersion,
                    onClusterClick = { pos ->
                        vm.isTrackingCamera = false
                        scope.launch {
                            cameraState.animateTo(
                                cameraState.position.copy(
                                    target = pos,
                                    zoom = cameraState.position.zoom + 2,
                                )
                            )
                        }
                    },
                )
            }
            }
        }

        SideEffect {
            vm.pendingCameraInfo = MapViewModel.CameraInfo(
                lat = cameraState.position.target.latitude,
                lon = cameraState.position.target.longitude,
                zoom = cameraState.position.zoom,
            )
            vm.updatePoiMapVisibleBounds(cameraState.projection?.queryVisibleBoundingBox())
        }

        if (vm.isLoadingPois) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.33f),
                contentAlignment = Alignment.BottomCenter,
            ) {
                androidx.compose.material3.SuggestionChip(
                    onClick = {},
                    label = { Text("Loading areas (${vm.cellsLoadingComplete}/${vm.cellsLoadingTotal})") },
                    icon = {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    },
                )
            }
        }

        val windowInfo = androidx.compose.material3.adaptive.currentWindowAdaptiveInfo()
        val layoutConfig = remember(windowInfo.windowSizeClass) {
            LayoutConfig.fromWindow(
                windowSizeClass = windowInfo.windowSizeClass,
            )
        }

        MapOverlay(
            config = layoutConfig,
            vm = vm,
            bearing = bearing,
            hasLocation = hasLocation,
            hasGpsFix = hasGpsFix,
            speedMps = locationState.location?.speed ?: 0.0,
            poiInfo = poiInfo,
            cameraState = cameraState,
            scope = scope,
        )

        ActionsDrawer(
            vm = vm,
            mapStyle = mapStyle,
            onStyleChange = onStyleChange,
        )

        // Legend detail sheet
        LegendDetailSheet(vm = vm)

        // POI search dialog
        PoiSearchDialog(vm = vm)

        // POI category picker
        PoiCategoryPicker(vm = vm)

        // Tapped POI info popup
        TappedPoiPopup(vm = vm)
    }
}

