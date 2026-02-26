package ca.voiditswarranty.roadtripradar.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
        hasLocationPermission =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    // Location
    val locationProvider = if (hasLocationPermission) {
        rememberDefaultLocationProvider()
    } else {
        rememberNullLocationProvider()
    }
    val locationState = rememberUserLocationState(locationProvider = locationProvider)

    // Camera
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val bottomPadding = screenHeight / 3
    val savedZoom = remember { vm.prefsRepo.zoomLevel.toDouble() }

    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(longitude = -75.6972, latitude = 45.4215),
            zoom = savedZoom,
            padding = PaddingValues(top = bottomPadding),
        )
    )

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

    LocationTrackingEffect(
        locationState = locationState,
        enabled = vm.isTrackingCamera,
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
    val radarData = remember(userPosition?.latitude, userPosition?.longitude, zoomTier, bearing, vm.useMetric) {
        val center = userPosition ?: return@remember null
        buildRadarRingsData(center, ringDistancesForZoom(cameraState.position.zoom), bearing, vm.useMetric)
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

                if (radarData != null) {
                    RadarRingsLayers(
                        radarData = radarData,
                        isDarkStyle = mapStyle.isDark,
                    )
                }

                if (locationState.location != null) {
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

        // Speed readout (top-left)
        if (locationState.location != null) {
            SpeedReadout(
                speedMps = locationState.location?.speed ?: 0.0,
                useMetric = vm.useMetric,
                speedSize = vm.speedSize,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
            )
        }

        // Nav widget (top-center)
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
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
            )
        }

        // Compass + Search/Clear POI (top-right)
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
                size = 56.dp,
                contentPadding = PaddingValues(8.dp),
                shape = CircleShape,
                getHomePosition = { current ->
                    vm.isNorthUp = !vm.isNorthUp
                    if (vm.isNorthUp) {
                        // Switch to north-up: snap bearing to 0
                        current.copy(bearing = 0.0, tilt = 0.0)
                    } else {
                        // Switch to bearing-up: GPS will provide bearing on next fix
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

        // Bottom-left: timeline, play/pause, gear
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.weatherActive && vm.radarFramePaths.isNotEmpty()) {
                WeatherTimeline(
                    frameTimes = vm.radarFrameTimes,
                    currentFrameIndex = vm.currentFrameIndex,
                )
            }
            BottomLeftFabs(
                weatherActive = vm.weatherActive,
                isWeatherPlaying = vm.isWeatherPlaying,
                onToggleWeatherPlaying = { vm.toggleWeatherPlaying() },
                onOpenSettings = { vm.openSettings() },
            )
        }

        // Bottom-right: recenter, zoom in, zoom out
        BottomRightFabs(
            isTrackingCamera = vm.isTrackingCamera,
            hasLocation = locationState.location != null,
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
            modifier = Modifier.align(Alignment.BottomEnd),
        )

        // Settings + Reset dialogs
        SettingsSheet(
            vm = vm,
            mapStyle = mapStyle,
            onStyleChange = onStyleChange,
        )

        // Help sheet
        HelpSheet(vm = vm)

        // POI search dialog
        PoiSearchDialog(vm = vm)
    }
}

