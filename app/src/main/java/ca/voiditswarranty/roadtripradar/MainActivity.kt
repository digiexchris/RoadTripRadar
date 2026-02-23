package ca.voiditswarranty.roadtripradar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.value.TextRotationAlignment
import org.maplibre.compose.layers.Anchor
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.LocationPuckSizes
import org.maplibre.compose.location.LocationTrackingEffect
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberNullLocationProvider
import org.maplibre.compose.location.rememberUserLocationState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.RasterSource
import org.maplibre.compose.sources.TileSetOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.turf.measurement.bearingTo
import org.maplibre.spatialk.turf.measurement.distance
import org.maplibre.spatialk.turf.transformation.circle
import org.maplibre.spatialk.units.Bearing
import org.maplibre.spatialk.units.Length
import org.maplibre.spatialk.units.extensions.inKilometers
import org.maplibre.spatialk.units.extensions.inMeters
import org.maplibre.spatialk.units.extensions.inMiles
import org.maplibre.spatialk.units.extensions.inDegrees
import org.maplibre.spatialk.units.extensions.kilometers
import org.maplibre.spatialk.units.extensions.meters
import java.net.URL
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            RoadTripRadarApp()
        }
    }
}

private enum class MapStyle {
    LIBERTY, DARK, LIBERTY_DARK;

    val isDark get() = this != LIBERTY

    val styleUri get() = when (this) {
        LIBERTY -> "https://tiles.openfreemap.org/styles/liberty"
        DARK -> "https://tiles.openfreemap.org/styles/dark"
        LIBERTY_DARK -> "asset://liberty_dark.json"
    }

    val displayName get() = when (this) {
        LIBERTY -> "Liberty"
        DARK -> "Dark"
        LIBERTY_DARK -> "Color Dark"
    }
}

private enum class WeatherMode { OFF, ON }

private data class SearchResult(
    val name: String,
    val subtitle: String,
    val position: Position,
    val distance: Length? = null,
)

private data class PoiCategory(val label: String, val query: String)

private val POI_CATEGORIES = listOf(
    PoiCategory("Gas Station", "fuel"),
    PoiCategory("EV Charging", "charging_station"),
    PoiCategory("Restaurant", "restaurant"),
    PoiCategory("Fast Food", "fast_food"),
    PoiCategory("Cafe / Coffee", "cafe"),
    PoiCategory("Bar / Pub", "pub"),
    PoiCategory("Supermarket", "supermarket"),
    PoiCategory("Convenience Store", "convenience"),
    PoiCategory("Pharmacy", "pharmacy"),
    PoiCategory("Hospital", "hospital"),
    PoiCategory("Hotel", "hotel"),
    PoiCategory("Motel", "motel"),
    PoiCategory("Campsite", "camp_site"),
    PoiCategory("Parking", "parking"),
    PoiCategory("Rest Area", "rest_area"),
    PoiCategory("ATM", "atm"),
    PoiCategory("Bank", "bank"),
    PoiCategory("Post Office", "post_office"),
    PoiCategory("Car Repair", "car_repair"),
    PoiCategory("Car Wash", "car_wash"),
    PoiCategory("Laundry", "laundry"),
    PoiCategory("Toilets", "toilets"),
    PoiCategory("Police", "police"),
    PoiCategory("Fire Station", "fire_station"),
    PoiCategory("Library", "library"),
    PoiCategory("Park", "park"),
    PoiCategory("Viewpoint", "viewpoint"),
    PoiCategory("Museum", "museum"),
    PoiCategory("Tourist Info", "information"),
)

private object PrefsDefaults {
    const val ZOOM_LEVEL = 9.0f
    const val RADAR_OPACITY = 0.6f
    const val USE_METRIC = true
    const val WEATHER_PLAYING = false
    const val WEATHER_MODE = "ON"
    const val SPEED_SIZE = 48f
    const val NAV_WIDGET_SIZE = 48f
    const val KEEP_SCREEN_ON = true
    const val PREFS_VERSION = 1
}

private fun migratePrefs(prefs: SharedPreferences) {
    val version = prefs.getInt("prefs_version", 0)
    if (version < 1) {
        when (prefs.getString("weather_mode", null)) {
            "PAUSED" -> prefs.edit()
                .putString("weather_mode", "ON")
                .putBoolean("weather_playing", false)
                .apply()
            "PLAY" -> prefs.edit()
                .putString("weather_mode", "ON")
                .putBoolean("weather_playing", true)
                .apply()
        }
        prefs.edit().putInt("prefs_version", PrefsDefaults.PREFS_VERSION).apply()
    }
}

@Composable
fun RoadTripRadarApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("map_prefs", Context.MODE_PRIVATE) }

    remember { migratePrefs(prefs); true }

    val systemDefault = if (isSystemInDarkTheme()) MapStyle.LIBERTY_DARK else MapStyle.LIBERTY
    var mapStyle by remember {
        val saved = prefs.getString("map_style", null)
        mutableStateOf(
            try { saved?.let { MapStyle.valueOf(it) } ?: systemDefault }
            catch (_: IllegalArgumentException) { systemDefault }
        )
    }

    RoadTripRadarTheme(darkTheme = mapStyle.isDark) {
        MapScreen(
            mapStyle = mapStyle,
            onStyleChange = { newStyle ->
                mapStyle = newStyle
                prefs.edit().putString("map_style", newStyle.name).apply()
            },
        )
    }
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

private fun formatDistanceLabel(distance: Length, useMetric: Boolean): String {
    return if (useMetric) {
        val km = distance.inKilometers
        if (km < 1.0) "${distance.inMeters.toInt()} m" else "${km.cleanString()} km"
    } else {
        "${distance.inMiles.cleanString()} mi"
    }
}

private fun Double.cleanString(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format("%.1f", this)

private data class RadarRingsData(
    val ringsFeatures: FeatureCollection<LineString, JsonObject>,
    val labelsFeatures: FeatureCollection<Point, JsonObject>,
)

private fun buildRadarRingsData(
    center: Position,
    distances: List<Length>,
    bearing: Double,
    useMetric: Boolean,
): RadarRingsData {
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
        val cosLat = Math.cos(Math.toRadians(center.latitude))
        val labelPoint = exteriorRing.minBy { pos ->
            val pointBearingRad = Math.atan2(
                (pos.longitude - center.longitude) * cosLat,
                pos.latitude - center.latitude
            )
            Math.abs(Math.atan2(
                Math.sin(pointBearingRad - targetBearingRad),
                Math.cos(pointBearingRad - targetBearingRad),
            ))
        }
        labelFeatures.add(Feature(
            geometry = Point(labelPoint),
            properties = buildJsonObject {
                put("label", formatDistanceLabel(distance, useMetric))
            }
        ))
    }

    return RadarRingsData(
        ringsFeatures = FeatureCollection(ringFeatures),
        labelsFeatures = FeatureCollection(labelFeatures),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
private fun MapScreen(mapStyle: MapStyle, onStyleChange: (MapStyle) -> Unit) {
    val context = LocalContext.current
    val systemIsDark = isSystemInDarkTheme()

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
    val savedZoom = remember { prefs.getFloat("zoom_level", PrefsDefaults.ZOOM_LEVEL).toDouble() }

    var weatherMode by remember {
        val saved = prefs.getString("weather_mode", null)
        mutableStateOf(
            try { saved?.let { WeatherMode.valueOf(it) } ?: WeatherMode.OFF }
            catch (_: IllegalArgumentException) { WeatherMode.OFF }
        )
    }
    var isWeatherPlaying by remember {
        mutableStateOf(prefs.getBoolean("weather_playing", PrefsDefaults.WEATHER_PLAYING))
    }
    var radarFramePaths by remember { mutableStateOf(emptyList<String>()) }
    var radarFrameTimes by remember { mutableStateOf(emptyList<Long>()) }
    var lastGenerated by remember { mutableStateOf(0L) }
    var currentFrameIndex by remember { mutableStateOf(0) }
    var radarOpacity by remember {
        mutableStateOf(prefs.getFloat("radar_opacity", PrefsDefaults.RADAR_OPACITY))
    }
    var useMetric by remember {
        mutableStateOf(prefs.getBoolean("use_metric", PrefsDefaults.USE_METRIC))
    }
    var speedSize by remember {
        mutableStateOf(prefs.getFloat("speed_size", PrefsDefaults.SPEED_SIZE))
    }
    var showSettings by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var poiPosition by remember {
        val lat = prefs.getString("poi_lat", null)?.toDoubleOrNull()
        val lon = prefs.getString("poi_lon", null)?.toDoubleOrNull()
        mutableStateOf(if (lat != null && lon != null) Position(latitude = lat, longitude = lon) else null)
    }
    var poiName by remember {
        mutableStateOf(prefs.getString("poi_name", null) ?: if (poiPosition != null) "Dropped Pin" else null)
    }
    var navWidgetSize by remember {
        mutableStateOf(prefs.getFloat("nav_widget_size", PrefsDefaults.NAV_WIDGET_SIZE))
    }
    var keepScreenOn by remember {
        mutableStateOf(prefs.getBoolean("keep_screen_on", PrefsDefaults.KEEP_SCREEN_ON))
    }
    var showPoiSearch by remember { mutableStateOf(false) }

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

        LaunchedEffect(poiPosition, poiName) {
            val poi = poiPosition
            if (poi != null) {
                prefs.edit()
                    .putString("poi_lat", poi.latitude.toString())
                    .putString("poi_lon", poi.longitude.toString())
                    .putString("poi_name", poiName)
                    .apply()
            } else {
                prefs.edit().remove("poi_lat").remove("poi_lon").remove("poi_name").apply()
            }
        }

        LaunchedEffect(keepScreenOn) {
            val window = (context as? Activity)?.window ?: return@LaunchedEffect
            if (keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        LocationTrackingEffect(
            locationState = locationState,
            enabled = isTrackingCamera,
            trackBearing = true,
        ) {
            cameraState.updateFromLocation()
        }

        val weatherActive = weatherMode == WeatherMode.ON
        LaunchedEffect(weatherActive) {
            if (!weatherActive) return@LaunchedEffect
            while (true) {
                try {
                    val jsonStr = withContext(Dispatchers.IO) {
                        URL("https://api.rainviewer.com/public/weather-maps.json").readText()
                    }
                    val json = Json.parseToJsonElement(jsonStr).jsonObject
                    val generated = json["generated"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                    if (generated != lastGenerated) {
                        lastGenerated = generated
                        val past = json["radar"]?.jsonObject?.get("past")?.jsonArray
                        val entries = past?.takeLast(10) ?: emptyList()
                        radarFramePaths = entries.map {
                            it.jsonObject["path"]!!.jsonPrimitive.content
                        }
                        radarFrameTimes = entries.map {
                            it.jsonObject["time"]!!.jsonPrimitive.content.toLong()
                        }
                        currentFrameIndex = radarFramePaths.lastIndex.coerceAtLeast(0)
                    }
                } catch (_: Exception) { }
                delay(60_000)
            }
        }

        LaunchedEffect(weatherActive, isWeatherPlaying) {
            if (!weatherActive || !isWeatherPlaying) return@LaunchedEffect
            while (true) {
                delay(500)
                if (radarFramePaths.isNotEmpty()) {
                    currentFrameIndex = (currentFrameIndex + 1) % radarFramePaths.size
                }
            }
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
        val radarData = remember(userPosition?.latitude, userPosition?.longitude, zoomTier, bearing, useMetric) {
            val center = userPosition ?: return@remember null
            buildRadarRingsData(center, ringDistancesForZoom(cameraState.position.zoom), bearing, useMetric)
        }

        val poiInfo = remember(userPosition?.latitude, userPosition?.longitude, poiPosition) {
            val user = userPosition ?: return@remember null
            val poi = poiPosition ?: return@remember null
            val dist = distance(user, poi)
            val poiBearing = user.bearingTo(poi)
            val poiBearingDeg = (poiBearing - Bearing.North).inDegrees
            Pair(dist, poiBearingDeg)
        }

        MaplibreMap(
            baseStyle = BaseStyle.Uri(mapStyle.styleUri),
            cameraState = cameraState,
            modifier = Modifier.fillMaxSize(),
            options = MapOptions(
                ornamentOptions = OrnamentOptions(
                    isScaleBarEnabled = false,
                    isCompassEnabled = true,
                ),
            ),
            onMapLongClick = { position, _ ->
                poiPosition = position
                poiName = "Dropped Pin"
                ClickResult.Consume
            },
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
                if (weatherActive && radarFramePaths.isNotEmpty()) {
                    radarFramePaths.forEachIndexed { index, path ->
                        key(path) {
                            val pathId = path.replace("/", "-")
                            val source = remember {
                                RasterSource(
                                    id = "rv$pathId",
                                    tiles = listOf("https://tilecache.rainviewer.com$path/512/{z}/{x}/{y}/2/1_1.png"),
                                    options = TileSetOptions(maxZoom = 7),
                                    tileSize = 512,
                                )
                            }
                            RasterLayer(
                                id = "rvl$pathId",
                                source = source,
                                visible = index == currentFrameIndex,
                                opacity = const(radarOpacity),
                            )
                        }
                    }
                }
                val ringColor = if (mapStyle.isDark) Color.LightGray else Color.Black
                val haloColor = if (mapStyle.isDark) Color.DarkGray else Color.White

                LineLayer(
                    id = "radar-rings",
                    source = ringsSource,
                    color = const(ringColor),
                    width = const(1.5.dp),
                    opacity = const(0.7f),
                    dasharray = const(listOf(4, 3)),
                )

                SymbolLayer(
                    id = "radar-labels",
                    source = labelsSource,
                    textField = format(span(feature["label"].asString())),
                    textFont = const(listOf("Noto Sans Regular")),
                    textColor = const(ringColor),
                    textHaloColor = const(haloColor),
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

                if (poiPosition != null) {
                    val poiPointData = remember(poiPosition) {
                        FeatureCollection(listOf(Feature(
                            geometry = Point(poiPosition!!),
                            properties = buildJsonObject {},
                        )))
                    }
                    val poiSource = rememberGeoJsonSource(
                        data = GeoJsonData.Features(poiPointData),
                    )
                    CircleLayer(
                        id = "poi-marker",
                        source = poiSource,
                        radius = const(12.dp),
                        color = const(Color.Red),
                        strokeColor = const(Color.White),
                        strokeWidth = const(2.dp),
                    )

                    if (userPosition != null) {
                        val poiLineData = remember(
                            userPosition.latitude, userPosition.longitude, poiPosition,
                        ) {
                            FeatureCollection(listOf(Feature(
                                geometry = LineString(listOf(userPosition, poiPosition!!)),
                                properties = buildJsonObject {},
                            )))
                        }
                        val poiLineSource = rememberGeoJsonSource(
                            data = GeoJsonData.Features(poiLineData),
                        )
                        LineLayer(
                            id = "poi-line",
                            source = poiLineSource,
                            color = const(Color.Blue),
                            width = const(2.dp),
                            opacity = const(0.8f),
                        )
                    }
                }
            }
        }

        // Speed readout
        if (locationState.location != null) {
            val speedMps = locationState.location?.speed ?: 0.0
            val displaySpeed = if (useMetric) (speedMps * 3.6).toInt() else (speedMps * 2.23694).toInt()
            val unitLabel = if (useMetric) "km/h" else "mph"
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = displaySpeed.coerceAtLeast(0).toString(),
                    fontSize = speedSize.sp,
                    lineHeight = speedSize.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = unitLabel,
                    fontSize = (speedSize / 3).sp,
                    lineHeight = (speedSize / 3).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // POI direction indicator + clear button
        if (poiPosition != null && poiInfo != null) {
            val (poiDist, poiBearingDeg) = poiInfo
            val arrowRotation = (poiBearingDeg - bearing).toFloat()
            val iconSize = navWidgetSize.dp
            val distFontSize = (navWidgetSize * 0.35f).sp
            val showPoiName = navWidgetSize >= 50f && poiName != null
            val nameFontSize = (navWidgetSize * 0.25f).sp
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Direction to POI",
                        modifier = Modifier
                            .size(iconSize)
                            .rotate(arrowRotation),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = formatDistanceLabel(poiDist, useMetric),
                        fontSize = distFontSize,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (showPoiName) {
                        Text(
                            text = poiName!!,
                            fontSize = nameFontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = (navWidgetSize * 2.5f).dp),
                        )
                    }
                }
                FloatingActionButton(
                    onClick = {
                        poiPosition = null
                        poiName = null
                    },
                    modifier = Modifier.border(
                        1.dp, MaterialTheme.colorScheme.outline, CircleShape,
                    ),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear POI",
                    )
                }
            }
        } else {
            FloatingActionButton(
                onClick = { showPoiSearch = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 16.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search for POI")
            }
        }

        val fabBorder = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)

        // Bottom-left: timeline, play/pause, gear
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (weatherActive && radarFramePaths.isNotEmpty()) {
                val frameCount = radarFramePaths.size
                Column(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val dateFormat = remember { java.text.SimpleDateFormat("H:mm", java.util.Locale.getDefault()) }
                    for (i in 0 until frameCount) {
                        val label = dateFormat.format(java.util.Date(radarFrameTimes[i] * 1000L))
                        val isActive = i == currentFrameIndex
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                lineHeight = 10.sp,
                                textAlign = TextAlign.End,
                                color = if (isActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.width(34.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .height(if (isActive) 4.dp else 2.dp)
                                    .width(if (isActive) 14.dp else 8.dp)
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        RoundedCornerShape(1.dp),
                                    ),
                            )
                        }
                    }
                }
            }

            if (weatherActive) {
                LargeFloatingActionButton(
                    onClick = {
                        isWeatherPlaying = !isWeatherPlaying
                        prefs.edit().putBoolean("weather_playing", isWeatherPlaying).apply()
                        if (!isWeatherPlaying) {
                            currentFrameIndex = radarFramePaths.lastIndex.coerceAtLeast(0)
                        }
                    },
                    modifier = fabBorder,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = if (isWeatherPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle weather animation",
                    )
                }
            }

            LargeFloatingActionButton(
                onClick = { showSettings = true },
                modifier = fabBorder,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                )
            }
        }

        // Bottom-right: recenter, zoom in, zoom out
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isTrackingCamera && locationState.location != null) {
                LargeFloatingActionButton(
                    onClick = { isTrackingCamera = true },
                    modifier = fabBorder,
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
                modifier = fabBorder,
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
                modifier = fabBorder,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom out",
                )
            }
        }

        // Settings bottom sheet
        if (showSettings) {
            ModalBottomSheet(onDismissRequest = { showSettings = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Quit
                    OutlinedButton(
                        onClick = {
                            (context as? Activity)?.finishAffinity()
                            System.exit(0)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Quit")
                    }

                    // Map Style
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Map Style", style = MaterialTheme.typography.titleSmall)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            MapStyle.entries.forEachIndexed { index, style ->
                                SegmentedButton(
                                    selected = mapStyle == style,
                                    onClick = { onStyleChange(style) },
                                    shape = SegmentedButtonDefaults.itemShape(index, MapStyle.entries.size),
                                ) {
                                    Text(style.displayName)
                                }
                            }
                        }
                    }

                    // Weather Radar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Weather Radar", style = MaterialTheme.typography.titleSmall)
                        Switch(
                            checked = weatherActive,
                            onCheckedChange = { on ->
                                weatherMode = if (on) WeatherMode.ON else WeatherMode.OFF
                                prefs.edit().putString("weather_mode", weatherMode.name).apply()
                                if (on) {
                                    currentFrameIndex = radarFramePaths.lastIndex.coerceAtLeast(0)
                                }
                            },
                        )
                    }

                    // Radar Opacity (visible when weather is on)
                    if (weatherActive) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Radar Opacity", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${(radarOpacity * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Slider(
                                value = radarOpacity,
                                onValueChange = { radarOpacity = it },
                                onValueChangeFinished = {
                                    prefs.edit().putFloat("radar_opacity", radarOpacity).apply()
                                },
                                valueRange = 0.1f..1.0f,
                            )
                        }
                    }

                    // Units
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Units", style = MaterialTheme.typography.titleSmall)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = useMetric,
                                onClick = {
                                    useMetric = true
                                    prefs.edit().putBoolean("use_metric", true).apply()
                                },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                            ) {
                                Text("Metric")
                            }
                            SegmentedButton(
                                selected = !useMetric,
                                onClick = {
                                    useMetric = false
                                    prefs.edit().putBoolean("use_metric", false).apply()
                                },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                            ) {
                                Text("Imperial")
                            }
                        }
                    }

                    // Keep Screen On
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Keep Screen On", style = MaterialTheme.typography.titleSmall)
                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = { on ->
                                keepScreenOn = on
                                prefs.edit().putBoolean("keep_screen_on", on).apply()
                            },
                        )
                    }

                    // Speed Size
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Speed Size", style = MaterialTheme.typography.titleSmall)
                        Slider(
                            value = speedSize,
                            onValueChange = { speedSize = it },
                            onValueChangeFinished = {
                                prefs.edit().putFloat("speed_size", speedSize).apply()
                            },
                            valueRange = 24f..96f,
                        )
                    }

                    // Nav Widget Size
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Nav Widget Size", style = MaterialTheme.typography.titleSmall)
                        Slider(
                            value = navWidgetSize,
                            onValueChange = { navWidgetSize = it },
                            onValueChangeFinished = {
                                prefs.edit().putFloat("nav_widget_size", navWidgetSize).apply()
                            },
                            valueRange = 24f..96f,
                        )
                    }

                    // Reset to Defaults
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showResetConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Reset to Defaults")
                    }
                }
            }
        }

        // Reset confirmation dialog
        if (showResetConfirm) {
            AlertDialog(
                onDismissRequest = { showResetConfirm = false },
                title = { Text("Reset to Defaults") },
                text = { Text("All settings will be reset to their default values.") },
                confirmButton = {
                    TextButton(onClick = {
                        val systemDefault = if (systemIsDark) MapStyle.LIBERTY_DARK else MapStyle.LIBERTY
                        onStyleChange(systemDefault)
                        weatherMode = WeatherMode.valueOf(PrefsDefaults.WEATHER_MODE)
                        isWeatherPlaying = PrefsDefaults.WEATHER_PLAYING
                        radarOpacity = PrefsDefaults.RADAR_OPACITY
                        useMetric = PrefsDefaults.USE_METRIC
                        speedSize = PrefsDefaults.SPEED_SIZE
                        navWidgetSize = PrefsDefaults.NAV_WIDGET_SIZE
                        keepScreenOn = PrefsDefaults.KEEP_SCREEN_ON
                        prefs.edit()
                            .putString("map_style", systemDefault.name)
                            .putString("weather_mode", PrefsDefaults.WEATHER_MODE)
                            .putBoolean("weather_playing", PrefsDefaults.WEATHER_PLAYING)
                            .putFloat("radar_opacity", PrefsDefaults.RADAR_OPACITY)
                            .putBoolean("use_metric", PrefsDefaults.USE_METRIC)
                            .putFloat("speed_size", PrefsDefaults.SPEED_SIZE)
                            .putFloat("nav_widget_size", PrefsDefaults.NAV_WIDGET_SIZE)
                            .putBoolean("keep_screen_on", PrefsDefaults.KEEP_SCREEN_ON)
                            .putFloat("zoom_level", PrefsDefaults.ZOOM_LEVEL)
                            .remove("poi_lat").remove("poi_lon").remove("poi_name")
                            .apply()
                        poiPosition = null
                        poiName = null
                        scope.launch {
                            cameraState.animateTo(
                                cameraState.position.copy(zoom = PrefsDefaults.ZOOM_LEVEL.toDouble())
                            )
                        }
                        showResetConfirm = false
                        showSettings = false
                    }) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirm = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        if (showPoiSearch) {
            var searchQuery by remember { mutableStateOf("") }
            var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
            var searchByCategory by remember { mutableStateOf(false) }
            var isSearching by remember { mutableStateOf(false) }
            var selectedCategory by remember { mutableStateOf<PoiCategory?>(null) }
            val filteredCategories = remember(searchQuery) {
                if (searchQuery.isBlank()) POI_CATEGORIES
                else POI_CATEGORIES.filter { it.label.contains(searchQuery, ignoreCase = true) }
            }

            LaunchedEffect(selectedCategory) {
                val cat = selectedCategory ?: return@LaunchedEffect
                isSearching = true
                try {
                    val camPos = cameraState.position
                    val lat = camPos.target.latitude
                    val lon = camPos.target.longitude
                    val latDelta = 360.0 / Math.pow(2.0, camPos.zoom) * 0.5
                    val lonDelta = latDelta / Math.cos(Math.toRadians(lat))
                    val url = buildString {
                        append("https://nominatim.openstreetmap.org/search?format=jsonv2")
                        append("&amenity=")
                        append(URLEncoder.encode(cat.query, "UTF-8"))
                        append("&limit=20&addressdetails=1")
                        append("&viewbox=${lon - lonDelta},${lat + latDelta},${lon + lonDelta},${lat - latDelta}")
                        append("&bounded=1")
                    }
                    val jsonStr = withContext(Dispatchers.IO) {
                        val conn = URL(url).openConnection()
                        conn.setRequestProperty("User-Agent", "RoadTripRadar/1.0")
                        conn.getInputStream().bufferedReader().readText()
                    }
                    val results = Json.parseToJsonElement(jsonStr).jsonArray
                    searchResults = results.mapNotNull { element ->
                        val obj = element.jsonObject
                        val lat = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                        val lon = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                        val displayName = obj["display_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val name = obj["name"]?.jsonPrimitive?.content?.ifEmpty { null }
                            ?: displayName.substringBefore(",")
                        val subtitle = displayName.substringAfter(",").trim()
                        val pos = Position(longitude = lon, latitude = lat)
                        val dist = userPosition?.let { distance(Point(it), Point(pos)) }
                        SearchResult(name = name, subtitle = subtitle, position = pos, distance = dist)
                    }.let { list ->
                        if (userPosition != null) list.sortedBy { it.distance?.inMeters ?: Double.MAX_VALUE }
                        else list
                    }
                } catch (_: Exception) {
                    searchResults = emptyList()
                }
                isSearching = false
            }

            LaunchedEffect(searchQuery, searchByCategory) {
                if (searchByCategory) return@LaunchedEffect
                if (searchQuery.length < 2) {
                    searchResults = emptyList()
                    return@LaunchedEffect
                }
                isSearching = true
                delay(300L)
                try {
                    val camPos = cameraState.position
                    val lat = camPos.target.latitude
                    val lon = camPos.target.longitude
                    val latD = 360.0 / Math.pow(2.0, camPos.zoom) * 0.5
                    val lonD = latD / Math.cos(Math.toRadians(lat))
                    val url = buildString {
                        append("https://photon.komoot.io/api/?q=")
                        append(URLEncoder.encode(searchQuery, "UTF-8"))
                        append("&limit=10")
                        append("&lat=$lat&lon=$lon")
                        append("&bbox=${lon - lonD},${lat - latD},${lon + lonD},${lat + latD}")
                    }
                    val jsonStr = withContext(Dispatchers.IO) { URL(url).readText() }
                    val json = Json.parseToJsonElement(jsonStr).jsonObject
                    val features = json["features"]?.jsonArray ?: emptyList()
                    searchResults = features.mapNotNull { element ->
                        val obj = element.jsonObject
                        val coords = obj["geometry"]?.jsonObject
                            ?.get("coordinates")?.jsonArray ?: return@mapNotNull null
                        val lon = coords[0].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
                        val lat = coords[1].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
                        val props = obj["properties"]?.jsonObject ?: return@mapNotNull null
                        val name = props["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val streetParts = listOfNotNull(
                            props["housenumber"]?.jsonPrimitive?.content,
                            props["street"]?.jsonPrimitive?.content,
                        )
                        val street = streetParts.joinToString(" ").ifEmpty { null }
                        val parts = listOfNotNull(
                            street,
                            props["city"]?.jsonPrimitive?.content,
                            props["state"]?.jsonPrimitive?.content,
                            props["country"]?.jsonPrimitive?.content,
                        )
                        val pos = Position(longitude = lon, latitude = lat)
                        val dist = userPosition?.let { distance(Point(it), Point(pos)) }
                        SearchResult(name = name, subtitle = parts.joinToString(", "), position = pos, distance = dist)
                    }
                } catch (_: Exception) {
                    searchResults = emptyList()
                }
                isSearching = false
            }

            AlertDialog(
                onDismissRequest = { showPoiSearch = false },
                title = { Text("Search Location") },
                text = {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (searchByCategory) "Category" else "Name",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Switch(
                                checked = searchByCategory,
                                onCheckedChange = {
                                    searchByCategory = it
                                    searchResults = emptyList()
                                    selectedCategory = null
                                    searchQuery = ""
                                },
                            )
                        }
                        Text(
                            "Searching within the visible map area",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                if (searchByCategory) {
                                    selectedCategory = null
                                    searchResults = emptyList()
                                }
                            },
                            label = { Text(if (searchByCategory) "Filter categories" else "Address or place name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (isSearching) {
                            Text(
                                "Searching...",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (searchByCategory && selectedCategory == null) {
                            LazyColumn {
                                items(filteredCategories) { cat ->
                                    Text(
                                        text = cat.label,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedCategory = cat }
                                            .padding(vertical = 10.dp),
                                    )
                                }
                            }
                        } else {
                            if (searchByCategory && selectedCategory != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = selectedCategory!!.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    TextButton(onClick = {
                                        selectedCategory = null
                                        searchResults = emptyList()
                                    }) {
                                        Text("Change")
                                    }
                                }
                            }
                            LazyColumn {
                                items(searchResults) { result ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                poiPosition = result.position
                                                poiName = result.name
                                                showPoiSearch = false
                                            }
                                            .padding(vertical = 8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top,
                                        ) {
                                            Text(
                                                text = result.name,
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (result.distance != null) {
                                                Text(
                                                    text = formatDistanceLabel(result.distance, useMetric),
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(start = 8.dp),
                                                )
                                            }
                                        }
                                        if (result.subtitle.isNotEmpty()) {
                                            Text(
                                                text = result.subtitle,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showPoiSearch = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}
