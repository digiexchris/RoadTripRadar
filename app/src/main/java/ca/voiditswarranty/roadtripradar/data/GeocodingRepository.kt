package ca.voiditswarranty.roadtripradar.data

import ca.voiditswarranty.roadtripradar.model.PoiCategory
import ca.voiditswarranty.roadtripradar.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.turf.measurement.distance
import org.maplibre.spatialk.units.extensions.inMeters
import java.net.URL
import java.net.URLEncoder

data class ViewBox(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
)

class GeocodingRepository {

    suspend fun searchByName(
        query: String,
        centerLat: Double,
        centerLon: Double,
        bbox: ViewBox,
        userPosition: Position?,
    ): List<SearchResult> {
        return try {
            val url = buildString {
                append("https://photon.komoot.io/api/?q=")
                append(URLEncoder.encode(query, "UTF-8"))
                append("&limit=10")
                append("&lat=$centerLat&lon=$centerLon")
                append("&bbox=${bbox.west},${bbox.south},${bbox.east},${bbox.north}")
            }
            val jsonStr = withContext(Dispatchers.IO) { URL(url).readText() }
            val json = Json.parseToJsonElement(jsonStr).jsonObject
            val features = json["features"]?.jsonArray ?: emptyList()
            features.mapNotNull { element ->
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
            emptyList()
        }
    }

    suspend fun searchByCategory(
        category: PoiCategory,
        viewbox: ViewBox,
        userPosition: Position?,
    ): List<SearchResult> {
        return try {
            val url = buildString {
                append("https://nominatim.openstreetmap.org/search?format=jsonv2")
                append("&amenity=")
                append(URLEncoder.encode(category.query, "UTF-8"))
                append("&limit=20&addressdetails=1")
                append("&viewbox=${viewbox.west},${viewbox.north},${viewbox.east},${viewbox.south}")
                append("&bounded=1")
            }
            val jsonStr = withContext(Dispatchers.IO) {
                val conn = URL(url).openConnection()
                conn.setRequestProperty("User-Agent", "RoadTripRadar/1.0")
                conn.getInputStream().bufferedReader().readText()
            }
            val results = Json.parseToJsonElement(jsonStr).jsonArray
            results.mapNotNull { element ->
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
            emptyList()
        }
    }

}
