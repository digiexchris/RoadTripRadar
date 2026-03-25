package ca.voiditswarranty.roadtripradar.data

import ca.voiditswarranty.roadtripradar.model.POI_CATEGORIES
import ca.voiditswarranty.roadtripradar.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.turf.measurement.distance
import org.maplibre.spatialk.units.extensions.inMeters
import java.net.URL

/** Thrown when Postpass fails with a server-side error (429 or 5xx) after retries. */
class PostpassServerException(message: String, cause: Throwable? = null) : java.io.IOException(message, cause)

class OverpassRepository {

    private val semaphore = Semaphore(2)

    private val categoryTagMap: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        for (cat in POI_CATEGORIES) {
            val tag = when (cat.query) {
                "camp_site", "viewpoint", "information" -> "tourism"
                "parking", "rest_area" -> "amenity"
                "park" -> "leisure"
                else -> "amenity"
            }
            map[cat.query] = tag
        }
        map
    }

    suspend fun fetchPoisForTile(
        bounds: BoundingBox,
        categories: Set<String>,
    ): FeatureCollection<Point, JsonObject> = semaphore.withPermit {
        if (categories.isEmpty()) return@withPermit FeatureCollection(emptyList())
        val jsonStr = executeQuery(buildPostpassQuery(bounds, categories))
        val json = Json.parseToJsonElement(jsonStr).jsonObject
        val features = json["features"]?.jsonArray?.mapNotNull {
            parsePostpassFeature(it.jsonObject, categories)
        } ?: emptyList()
        FeatureCollection(features)
    }

    suspend fun searchByCategory(
        category: String,
        viewbox: ViewBox,
        userPosition: Position?,
    ): List<SearchResult> = semaphore.withPermit {
        try {
            val bounds = BoundingBox(
                southwest = Position(latitude = viewbox.south, longitude = viewbox.west),
                northeast = Position(latitude = viewbox.north, longitude = viewbox.east),
            )
            val jsonStr = executeQuery(buildPostpassQuery(bounds, setOf(category)))
            val json = Json.parseToJsonElement(jsonStr).jsonObject
            val features = json["features"]?.jsonArray ?: emptyList()
            features.mapNotNull { el ->
                val geom = el.jsonObject["geometry"]?.jsonObject ?: return@mapNotNull null
                val coords = geom["coordinates"]?.jsonArray ?: return@mapNotNull null
                val lon = coords[0].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
                val lat = coords[1].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
                val props = el.jsonObject["properties"]?.jsonObject ?: return@mapNotNull null
                val tags = props["tags"]?.jsonObject ?: return@mapNotNull null
                val name = tags["name"]?.jsonPrimitive?.content?.ifEmpty { null }
                    ?: tags["brand"]?.jsonPrimitive?.content
                    ?: tags["operator"]?.jsonPrimitive?.content
                    ?: return@mapNotNull null
                val pos = Position(latitude = lat, longitude = lon)
                val dist = userPosition?.let { distance(Point(it), Point(pos)) }
                SearchResult(name = name, subtitle = buildSubtitle(tags), position = pos, distance = dist)
            }.let { list ->
                if (userPosition != null) list.sortedBy { it.distance?.inMeters ?: Double.MAX_VALUE }
                else list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildPostpassQuery(bounds: BoundingBox, categories: Set<String>): String {
        val w = bounds.southwest.longitude
        val s = bounds.southwest.latitude
        val e = bounds.northeast.longitude
        val n = bounds.northeast.latitude
        val tagConditions = categories.joinToString(" OR ") { cat ->
            val tag = categoryTagMap[cat] ?: "amenity"
            "tags->>'$tag' = '$cat'"
        }
        return buildString {
            append("SELECT osm_id, osm_type, tags, ST_Centroid(geom) AS geom\n")
            append("FROM postpass_pointpolygon\n")
            append("WHERE ($tagConditions)\n")
            append("  AND geom && ST_MakeEnvelope($w, $s, $e, $n, 4326)\n")
            append("LIMIT 2000")
        }
    }

    private val endpoint = "https://postpass.geofabrik.de/api/0.2/interpreter"

    private suspend fun executeQuery(query: String): String = withContext(Dispatchers.IO) {
        android.util.Log.d("POI_DEBUG", "Postpass query:\n$query")
        val attempts = listOf(5_000, 10_000)
        var lastException: Exception? = null
        for ((index, timeout) in attempts.withIndex()) {
            if (index > 0) kotlinx.coroutines.delay(1_000)
            try {
                android.util.Log.d("POI_DEBUG", "Trying Postpass (timeout=${timeout}ms)")
                val conn = URL(endpoint).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = minOf(5_000, timeout)
                conn.readTimeout = timeout
                conn.setRequestProperty("User-Agent", "RoadTripRadar/1.0")
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.doOutput = true
                conn.getOutputStream().use { os ->
                    os.write("data=${java.net.URLEncoder.encode(query, "UTF-8")}".toByteArray())
                }
                val code = conn.responseCode
                if (code == 200) {
                    return@withContext conn.inputStream.bufferedReader().readText()
                }
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: "(no body)"
                android.util.Log.e("POI_DEBUG", "HTTP $code from Postpass: $errorBody")
                conn.disconnect()
                lastException = java.io.IOException("Postpass returned HTTP $code")
                if (code == 429 || code >= 500) {
                    continue
                } else {
                    throw lastException
                }
            } catch (e: java.io.IOException) {
                android.util.Log.e("POI_DEBUG", "IO error from Postpass: ${e.message}")
                lastException = e
            }
        }
        val ex = lastException ?: java.io.IOException("Postpass endpoint failed")
        // Wrap server-side errors so callers can distinguish them for backoff
        if (ex.message?.contains("HTTP 429") == true || ex.message?.matches(Regex(".*HTTP [5-9]\\d{2}.*")) == true) {
            throw PostpassServerException(ex.message ?: "Server error", ex)
        }
        throw ex
    }

    private fun parsePoi(
        lat: Double,
        lon: Double,
        tags: JsonObject,
        enabledCategories: Set<String>,
    ): Feature<Point, JsonObject>? {
        val matchedCategory = enabledCategories.firstOrNull { cat ->
            val tag = categoryTagMap[cat] ?: "amenity"
            tags[tag]?.jsonPrimitive?.content == cat
        } ?: return null
        val poiCat = POI_CATEGORIES.firstOrNull { it.query == matchedCategory } ?: return null
        val name = tags["name"]?.jsonPrimitive?.content?.ifEmpty { null }
            ?: tags["brand"]?.jsonPrimitive?.content
            ?: tags["operator"]?.jsonPrimitive?.content
            ?: poiCat.label
        return Feature(
            geometry = Point(Position(latitude = lat, longitude = lon)),
            properties = buildJsonObject {
                put("name", name)
                put("subtitle", buildSubtitle(tags))
                put("categoryQuery", matchedCategory)
                put("categoryLabel", poiCat.label)
                put("iconName", poiCat.iconName)
                tags["opening_hours"]?.jsonPrimitive?.content?.let { put("openingHours", it) }
            }
        )
    }

    private fun parsePostpassFeature(
        feature: JsonObject,
        enabledCategories: Set<String>,
    ): Feature<Point, JsonObject>? {
        val props = feature["properties"]?.jsonObject ?: return null
        val geom = feature["geometry"]?.jsonObject ?: return null
        val coords = geom["coordinates"]?.jsonArray ?: return null
        val lon = coords[0].jsonPrimitive.content.toDoubleOrNull() ?: return null
        val lat = coords[1].jsonPrimitive.content.toDoubleOrNull() ?: return null
        val tags = props["tags"]?.jsonObject ?: return null
        return parsePoi(lat, lon, tags, enabledCategories)
    }

    private fun buildSubtitle(tags: JsonObject): String {
        val houseNumber = tags["addr:housenumber"]?.jsonPrimitive?.content
        val street = tags["addr:street"]?.jsonPrimitive?.content
        val city = tags["addr:city"]?.jsonPrimitive?.content
        val streetPart = if (houseNumber != null && street != null) "$houseNumber $street"
            else street ?: houseNumber
        return listOfNotNull(streetPart, city).joinToString(", ")
    }
}
