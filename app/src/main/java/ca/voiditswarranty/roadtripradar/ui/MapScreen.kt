package ca.voiditswarranty.roadtripradar.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.model.buildRadarRingsData
import ca.voiditswarranty.roadtripradar.model.ringDistancesForZoom
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import kotlinx.coroutines.delay
import org.maplibre.compose.material3.CompassButton
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Point
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Permissions
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        hasLocationPermission = granted
        if (!granted) {
            vm.updateUseGps(false)
        }
    }
    LaunchedEffect(vm.useGps) {
        if (vm.useGps && !hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }
    }

    // Location
    val locationProvider = if (hasLocationPermission && vm.useGps) {
        rememberDefaultLocationProvider()
    } else {
        rememberNullLocationProvider()
    }
    val locationState = rememberUserLocationState(locationProvider = locationProvider)
    val hasLocation = vm.useGps && locationState.location != null
    val hasGpsFix = hasLocation && locationState.location!!.accuracy < 50.0

    // Camera
    val configuration = LocalConfiguration.current
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
    vm.pendingCameraInfo = MapViewModel.CameraInfo(
        lat = cameraState.position.target.latitude,
        lon = cameraState.position.target.longitude,
        zoom = cameraState.position.zoom,
    )

    // UI
    Box(modifier = Modifier.fillMaxSize()) {
        MaplibreMap(
            baseStyle = BaseStyle.Uri(mapStyle.styleUri),
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
                    isDarkStyle = mapStyle.isDark,
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
            }
        }

        // Portrait top-left: speed readout with nav widget directly beneath it
        if (!isLandscape && (hasLocation || (vm.poiPosition != null && poiInfo != null))) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                if (hasLocation) {
                    SpeedReadout(
                        speedMps = locationState.location?.speed ?: 0.0,
                        useMetric = vm.useMetric,
                        speedSize = vm.speedSize,
                    )
                }
                if (vm.poiPosition != null && poiInfo != null) {
                    val (poiDist, poiBearingDeg) = poiInfo
                    NavWidget(
                        poiDistance = poiDist,
                        poiBearingDeg = poiBearingDeg,
                        cameraBearing = bearing,
                        navWidgetSize = vm.navWidgetSize,
                        poiName = vm.poiName,
                        useMetric = vm.useMetric,
                    )
                }
            }
        }

        if (isLandscape) {
            // Top-left horizontal rail: timeline, speed
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .navigationBarsPadding()
                    .padding(top = 16.dp, start = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (hasLocation) {
                    SpeedReadout(
                        speedMps = locationState.location?.speed ?: 0.0,
                        useMetric = vm.useMetric,
                        speedSize = vm.speedSize,
                    )
                }
            }

            // Top-right: legend + compass, search/clear
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .navigationBarsPadding()
                    .padding(top = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (vm.weatherActive && vm.radarFramePaths.isNotEmpty()) {
                    WeatherLegend(horizontal = true)
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CompassButton(
                        cameraState = cameraState,
                        colors = ButtonDefaults.elevatedButtonColors(),
                        size = 96.dp,
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

            PoiSearchClearFab(
                hasPoi = vm.poiPosition != null,
                onClearPoi = { vm.clearPoi() },
                onOpenSearch = { vm.openPoiSearch() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
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

            // Bottom-left controls: pause, settings
            BottomLeftFabsRow(
                weatherActive = vm.weatherActive,
                isWeatherPlaying = vm.isWeatherPlaying,
                onToggleWeatherPlaying = { vm.toggleWeatherPlaying() },
                onOpenSettings = { vm.openSettings() },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )

            if (vm.poiPosition != null && poiInfo != null) {
                val (poiDist, poiBearingDeg) = poiInfo
                NavWidget(
                    poiDistance = poiDist,
                    poiBearingDeg = poiBearingDeg,
                    cameraBearing = bearing,
                    navWidgetSize = vm.navWidgetSize,
                    poiName = vm.poiName,
                    useMetric = vm.useMetric,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .navigationBarsPadding()
                        .padding(start = 16.dp),
                )
            }

            // Bottom-center timeline (landscape only)
            if (vm.weatherActive && vm.radarFramePaths.isNotEmpty()) {
                WeatherTimeline(
                    frameTimes = vm.radarFrameTimes,
                    currentFrameIndex = vm.currentFrameIndex,
                    horizontal = true,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                )
            }

            // Bottom-right horizontal rail: recenter, zoom in, zoom out
            BottomRightFabsRow(
                isTrackingCamera = vm.isTrackingCamera,
                hasLocation = hasLocation,
                onRecenter = { vm.isTrackingCamera = true },
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
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        } else {
            // Portrait: keep controls split between top-right, center-right, and bottom-right.
            if (vm.weatherActive && vm.radarFramePaths.isNotEmpty()) {
                WeatherTimeline(
                    frameTimes = vm.radarFrameTimes,
                    currentFrameIndex = vm.currentFrameIndex,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BottomLeftFabs(
                    weatherActive = vm.weatherActive,
                    isWeatherPlaying = vm.isWeatherPlaying,
                    onToggleWeatherPlaying = { vm.toggleWeatherPlaying() },
                    onOpenSettings = { vm.openSettings() },
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CompassButton(
                    cameraState = cameraState,
                    colors = ButtonDefaults.elevatedButtonColors(),
                    size = 96.dp,
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
                PoiSearchClearFab(
                    hasPoi = vm.poiPosition != null,
                    onClearPoi = { vm.clearPoi() },
                    onOpenSearch = { vm.openPoiSearch() },
                )
            }

            // Portrait right-side legend, vertically centered
            if (vm.weatherActive && vm.radarFramePaths.isNotEmpty()) {
                WeatherLegend(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp),
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
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

            BottomRightFabs(
                isTrackingCamera = vm.isTrackingCamera,
                hasLocation = hasLocation,
                onRecenter = { vm.isTrackingCamera = true },
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
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding(),
            )
        }

        // Settings + Reset dialogs
        SettingsSheet(
            vm = vm,
            mapStyle = mapStyle,
            onStyleChange = onStyleChange,
        )

        // Help sheet
        HelpSheet(vm = vm)

        // Legend detail sheet
        LegendDetailSheet(vm = vm)

        // POI search dialog
        PoiSearchDialog(vm = vm)
    }
}

