package ca.voiditswarranty.roadtripradar.data

import kotlinx.serialization.Serializable
import org.maplibre.spatialk.geojson.Position
import java.util.UUID

@Serializable
enum class WaypointSource { DROPPED_PIN, SEARCH, NEARBY_PLACE }

@Serializable
data class Waypoint(
    val id: String,
    val lat: Double,
    val lon: Double,
    val name: String? = null,
    val subtitle: String? = null,
    val source: WaypointSource = WaypointSource.DROPPED_PIN,
    /** Maki icon name (without extension) for NEARBY_PLACE waypoints; null for sources without icon. */
    val iconName: String? = null,
) {
    val position: Position
        get() = Position(latitude = lat, longitude = lon)

    companion object {
        fun create(
            position: Position,
            name: String? = null,
            subtitle: String? = null,
            source: WaypointSource = WaypointSource.DROPPED_PIN,
            iconName: String? = null,
            id: String = UUID.randomUUID().toString(),
        ): Waypoint = Waypoint(
            id = id,
            lat = position.latitude,
            lon = position.longitude,
            name = name,
            subtitle = subtitle,
            source = source,
            iconName = iconName,
        )
    }
}
