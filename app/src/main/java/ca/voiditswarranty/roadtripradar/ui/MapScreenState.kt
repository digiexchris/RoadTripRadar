package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.PaddingValues
import ca.voiditswarranty.roadtripradar.data.OpenMeteoSnapshot
import ca.voiditswarranty.roadtripradar.data.Waypoint
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import kotlinx.serialization.json.JsonObject
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.units.Length

/**
 * State for the [MapScreenContent] composable. Holds every VM-derived value
 * the screen reads so the content composable can be parameterized and tested
 * without instantiating a real
 * [ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel].
 *
 * The public [MapScreen] composable owns the `LaunchedEffect`s (VM-side
 * effects) and constructs this state class. Tests build it directly.
 */
internal data class MapScreenState(
    val useMetric: Boolean,
    val useGps: Boolean,
    val isTrackingCamera: Boolean,
    val isNorthUp: Boolean,
    val keepScreenOn: Boolean,
    val activeWaypoint: Waypoint?,
    val activeIndex: Int?,
    val waypoints: List<Waypoint>,
    val poiPosition: Position?,
    val showThemeSelector: Boolean,
    val showLegend: Boolean,
    val showTimeline: Boolean,
    val radarFramePaths: List<String>,
    val radarFrameTimes: List<Long>,
    val currentFrameIndex: Int,
    val weatherWidgetSize: Float,
    val windEnabled: Boolean,
    val openMeteoSnapshot: OpenMeteoSnapshot?,
    val temperatureUnit: TemperatureUnit,
    val windSpeedUnit: WindSpeedUnit,
    val weatherActive: Boolean,
    val isWeatherPlaying: Boolean,
    val poiPipelineActive: Boolean,
    val nearbyPoiFeatures: FeatureCollection<Point, JsonObject>,
    val userPositionForSearch: Position?,
    val screenWidthDp: Double,
    val screenHeightDp: Double,
    val speedSize: Float,
    val navWidgetSize: Float,
    val hasFailedCells: Boolean,
    val gpsIconOpacity: Float,
    val compassWidgetSize: Float,
    val mapCenterOffsetPortraitFraction: Float,
    val mapCenterOffsetLandscapeFraction: Float,
    val userPosition: Position?,
    val userPositionAccuracy: Double?,
    val bearing: Double,
    val poiInfo: Pair<Length, Double>?,
    val cameraPadding: PaddingValues,
)