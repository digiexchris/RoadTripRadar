package ca.voiditswarranty.roadtripradar.ui

import ca.voiditswarranty.roadtripradar.data.OpenMeteoSnapshot
import ca.voiditswarranty.roadtripradar.data.Waypoint
import ca.voiditswarranty.roadtripradar.model.NetworkStatus
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

/**
 * State for the [MapOverlayContent] composable. Holds every VM-derived value
 * the overlay reads so the content composable can be parameterized and tested
 * without instantiating a real [MapViewModel]. Caller-supplied values
 * (the camera bearing, location availability, and the tapped POI info)
 * stay as direct parameters to [MapOverlayContent] instead.
 *
 * Produced by the public [MapOverlay] composable, which reads from the VM
 * and constructs this class. Tests build it directly.
 */
internal data class MapOverlayState(
    val useMetric: Boolean,
    val speedMps: Double,
    val speedSize: Float,
    val useGps: Boolean,
    val hasGpsFix: Boolean,
    val gpsIconOpacity: Float,
    val networkStatus: NetworkStatus,
    val activeWaypoint: Waypoint?,
    val activeIndex: Int?,
    val waypoints: List<Waypoint>,
    val navWidgetSize: Float,
    val compassWidgetSize: Float,
    val hasFailedCells: Boolean,
    val isNorthUp: Boolean,
    val weatherActive: Boolean,
    val showTimeline: Boolean,
    val radarFrameTimes: List<Long>,
    val currentFrameIndex: Int,
    val weatherWidgetSize: Float,
    val windEnabled: Boolean,
    val openMeteoSnapshot: OpenMeteoSnapshot?,
    val temperatureUnit: TemperatureUnit,
    val windSpeedUnit: WindSpeedUnit,
    val showLegend: Boolean,
    val hasRadarFrames: Boolean,
    val fabScale: Float,
    val widgetScale: Float,
    val isTrackingCamera: Boolean,
    val isWeatherPlaying: Boolean,
)