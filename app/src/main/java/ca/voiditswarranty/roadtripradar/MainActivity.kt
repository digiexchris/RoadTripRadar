package ca.voiditswarranty.roadtripradar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.content.ContextCompat
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.LocationPuckSizes
import org.maplibre.compose.location.LocationTrackingEffect
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberNullLocationProvider
import org.maplibre.compose.location.rememberUserLocationState
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.value.TextRotationAlignment
import org.maplibre.compose.layers.Anchor
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.turf.transformation.circle
import org.maplibre.spatialk.units.Length
import org.maplibre.spatialk.units.extensions.inKilometers
import org.maplibre.spatialk.units.extensions.inMeters
import org.maplibre.spatialk.units.extensions.inMiles
import org.maplibre.spatialk.units.extensions.kilometers
import org.maplibre.spatialk.units.extensions.meters
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            RoadTripRadarTheme {
                RoadTripRadarApp()
            }
        }
    }
}

@Composable
fun RoadTripRadarApp() {
    MapScreen()
}

private fun ringDistancesForZoom(zoom: Double): List<Length> = when {
    zoom >= 16 -> listOf(250.meters, 500.meters, 1.kilometers, 2.kilometers)
    zoom >= 14 -> listOf(500.meters, 1.kilometers, 2.kilometers, 5.kilometers)
    zoom >= 12 -> listOf(1.kilometers, 2.kilometers, 5.kilometers, 10.kilometers)
    zoom >= 10 -> listOf(2.kilometers, 5.kilometers, 10.kilometers, 25.kilometers)
    zoom >= 8 -> listOf(10.kilometers, 25.kilometers, 50.kilometers)
    zoom >= 6 -> listOf(25.kilometers, 50.kilometers, 100.kilometers, 200.kilometers)
    zoom >= 4 -> listOf(100.kilometers, 250.kilometers, 500.kilometers, 1000.kilometers)
    else -> listOf(200.kilometers, 500.kilometers, 1000.kilometers)
}

private fun formatDistanceLabel(distance: Length): String {
    val km = distance.inKilometers
    val mi = distance.inMiles
    val kmPart = if (km < 1.0) "${distance.inMeters.toInt()} m" else "${km.cleanString()} km"
    val miPart = "${mi.cleanString()} mi"
    return "$kmPart / $miPart"
}

private fun Double.cleanString(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format("%.1f", this)

private data class RadarRingsData(
    val ringsFeatures: FeatureCollection<LineString, JsonObject>,
    val labelsFeatures: FeatureCollection<Point, JsonObject>,
)

private fun buildRadarRingsData(center: Position, distances: List<Length>, bearing: Double): RadarRingsData {
    val ringFeatures = mutableListOf<Feature<LineString, JsonObject>>()
    val labelFeatures = mutableListOf<Feature<Point, JsonObject>>()

    for (distance in distances) {
        val polygon = circle(center, distance, steps = 72)
        val exteriorRing = polygon.coordinates[0]

        ringFeatures.add(Feature(
            geometry = LineString(exteriorRing),
            properties = buildJsonObject {
                put("distance", distance.inMeters)
            }
        ))

        val targetBearingRad = Math.toRadians(bearing)
        val labelPoint = exteriorRing.minBy { pos ->
            val pointBearingRad = Math.atan2(
                pos.longitude - center.longitude,
                pos.latitude - center.latitude
            )
            val diff = Math.abs(pointBearingRad - targetBearingRad)
            Math.min(diff, 2 * Math.PI - diff)
        }
        labelFeatures.add(Feature(
            geometry = Point(labelPoint),
            properties = buildJsonObject {
                put("label", formatDistanceLabel(distance))
            }
        ))
    }

    return RadarRingsData(
        ringsFeatures = FeatureCollection(ringFeatures),
        labelsFeatures = FeatureCollection(labelFeatures),
    )
}

@SuppressLint("MissingPermission")
@Composable
fun MapScreen() {
    val context = LocalContext.current

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

    val locationProvider = if (hasLocationPermission) {
        rememberDefaultLocationProvider()
    } else {
        rememberNullLocationProvider()
    }

    val locationState = rememberUserLocationState(locationProvider = locationProvider)

    var isTrackingCamera by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    val prefs = remember { context.getSharedPreferences("map_prefs", Context.MODE_PRIVATE) }
    val savedZoom = remember { prefs.getFloat("zoom_level", 14.0f).toDouble() }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    Box(modifier = Modifier.fillMaxSize()) {
        val bottomPadding = screenHeight / 3

        val cameraState = rememberCameraState(
            firstPosition = CameraPosition(
                target = Position(longitude = -75.6972, latitude = 45.4215),
                zoom = savedZoom,
                padding = PaddingValues(top = bottomPadding),
            )
        )

        LaunchedEffect(cameraState.moveReason) {
            if (cameraState.moveReason == CameraMoveReason.GESTURE) {
                isTrackingCamera = false
            }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { cameraState.position.zoom }
                .collect { zoom ->
                    prefs.edit().putFloat("zoom_level", zoom.toFloat()).apply()
                }
        }

        LocationTrackingEffect(
            locationState = locationState,
            enabled = isTrackingCamera,
            trackBearing = true,
        ) {
            cameraState.updateFromLocation()
        }

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
        val radarData = remember(userPosition?.latitude, userPosition?.longitude, zoomTier, bearing) {
            val center = userPosition ?: return@remember null
            buildRadarRingsData(center, ringDistancesForZoom(cameraState.position.zoom), bearing)
        }

        MaplibreMap(
            baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
            cameraState = cameraState,
            modifier = Modifier.fillMaxSize(),
            options = MapOptions(ornamentOptions = OrnamentOptions(isScaleBarEnabled = false)),
        ) {
            val ringsSource = rememberGeoJsonSource(
                data = GeoJsonData.Features(
                    radarData?.ringsFeatures ?: FeatureCollection<LineString, JsonObject>()
                )
            )
            val labelsSource = rememberGeoJsonSource(
                data = GeoJsonData.Features(
                    radarData?.labelsFeatures ?: FeatureCollection<Point, JsonObject>()
                )
            )

            Anchor.Top {
                LineLayer(
                    id = "radar-rings",
                    source = ringsSource,
                    color = const(Color.Black),
                    width = const(1.5.dp),
                    opacity = const(0.7f),
                    dasharray = const(listOf(4, 3)),
                )

                SymbolLayer(
                    id = "radar-labels",
                    source = labelsSource,
                    textField = format(span(feature["label"].asString())),
                    textFont = const(listOf("Noto Sans Regular")),
                    textColor = const(Color.Black),
                    textHaloColor = const(Color.White),
                    textHaloWidth = const(3.dp),
                    textSize = const(1.2f.em),
                    textRotationAlignment = const(TextRotationAlignment.Viewport),
                    textAllowOverlap = const(true),
                    textIgnorePlacement = const(true),
                )

                if (locationState.location != null) {
                    LocationPuck(
                        idPrefix = "user-location",
                        locationState = locationState,
                        cameraState = cameraState,
                        showBearing = true,
                        sizes = LocationPuckSizes(bearingSize = 16.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isTrackingCamera && locationState.location != null) {
                LargeFloatingActionButton(
                    onClick = { isTrackingCamera = true },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Re-center on location",
                    )
                }
            }

            LargeFloatingActionButton(
                onClick = {
                    scope.launch {
                        cameraState.animateTo(
                            cameraState.position.copy(zoom = cameraState.position.zoom + 1)
                        )
                    }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom in",
                )
            }

            LargeFloatingActionButton(
                onClick = {
                    scope.launch {
                        cameraState.animateTo(
                            cameraState.position.copy(zoom = cameraState.position.zoom - 1)
                        )
                    }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom out",
                )
            }
        }
    }
}