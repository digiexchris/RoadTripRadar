package ca.voiditswarranty.roadtripradar.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

enum class MapStyle {
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

enum class WeatherMode { OFF, ON }

data class SearchResult(
    val name: String,
    val subtitle: String,
    val position: Position,
    val distance: Length? = null,
)

data class PoiCategory(val label: String, val query: String)

val POI_CATEGORIES = listOf(
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

object PrefsDefaults {
    const val ZOOM_LEVEL = 9.0f
    const val RADAR_OPACITY = 0.6f
    const val USE_METRIC = true
    const val WEATHER_PLAYING = true
    const val WEATHER_MODE = "ON"
    const val SPEED_SIZE = 48f
    const val NAV_WIDGET_SIZE = 48f
    const val KEEP_SCREEN_ON = true
    const val PREFS_VERSION = 1
    const val TERMS_VERSION = 1
}

const val TERMS_AND_CONDITIONS_TEXT = """
Terms and Conditions

Last updated: 2026-02-23

By using RoadTripRadar ("the App"), you agree to the following terms. If you do not agree, you must decline and discontinue use of the App immediately.

1. Safety

Do NOT operate or interact with this App while driving, cycling, or in any situation where doing so could compromise your safety or the safety of others. The App is intended for use by passengers or while the vehicle is safely parked. You accept full responsibility for ensuring that your use of the App does not create a dangerous situation.

2. Accuracy of Information

The information displayed by this App — including but not limited to weather radar imagery, navigation directions, distances, points of interest, and location data — may be inaccurate, incomplete, outdated, or unavailable. You should always verify information with trusted and authoritative sources before making decisions based on data shown in this App.

3. Third-Party Data Sources

This App relies on data provided by third-party services. The authors of this App have no control over the accuracy, reliability, or availability of third-party data and accept no responsibility for errors or omissions originating from these sources.

4. No Warranty

This App is provided "as is" and "as available" without warranties of any kind, whether express or implied, including but not limited to the implied warranties of merchantability, fitness for a particular purpose, and non-infringement.

5. Limitation of Liability

To the fullest extent permitted by applicable law, the authors and contributors of this App shall not be liable for any direct, indirect, incidental, special, consequential, or punitive damages, or any loss of data, use, or profits, arising out of or in connection with your use of or inability to use this App.

6. Acceptance

By tapping "Accept," you acknowledge that you have read, understood, and agree to be bound by these Terms and Conditions.
"""

data class RadarRingsData(
    val ringsFeatures: FeatureCollection<LineString, JsonObject>,
    val labelsFeatures: FeatureCollection<Point, JsonObject>,
)

fun ringDistancesForZoom(zoom: Double): List<Length> = when {
    zoom >= 16 -> listOf(250.meters, 500.meters, 1.kilometers, 2.kilometers)
    zoom >= 14 -> listOf(500.meters, 1.kilometers, 2.kilometers, 5.kilometers)
    zoom >= 12 -> listOf(1.kilometers, 2.kilometers, 5.kilometers, 10.kilometers)
    zoom >= 10 -> listOf(2.kilometers, 5.kilometers, 10.kilometers, 25.kilometers)
    zoom >= 8 -> listOf(10.kilometers, 25.kilometers, 50.kilometers)
    zoom >= 6 -> listOf(25.kilometers, 50.kilometers, 100.kilometers, 200.kilometers)
    zoom >= 4 -> listOf(100.kilometers, 250.kilometers, 500.kilometers, 1000.kilometers)
    else -> listOf(200.kilometers, 500.kilometers, 1000.kilometers)
}

fun formatDistanceLabel(distance: Length, useMetric: Boolean): String {
    return if (useMetric) {
        val km = distance.inKilometers
        if (km < 1.0) "${distance.inMeters.toInt()} m" else "${km.cleanString()} km"
    } else {
        "${distance.inMiles.cleanString()} mi"
    }
}

fun Double.cleanString(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format("%.1f", this)

fun buildRadarRingsData(
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
