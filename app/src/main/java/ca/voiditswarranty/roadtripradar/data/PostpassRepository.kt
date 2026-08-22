package ca.voiditswarranty.roadtripradar.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import java.net.URL

/** Thrown internally when Postpass fails with a server-side error (429 or 5xx) after retries. */
private class PostpassServerException(message: String, cause: Throwable? = null) : java.io.IOException(message, cause)

sealed interface TileFetchResult {
    data class Success(val features: FeatureCollection<Point, JsonObject>) : TileFetchResult
    data class ServerError(val message: String) : TileFetchResult
    data class Failed(val message: String) : TileFetchResult
}

/**
 * Geofabrik Postpass SQL API for OSM-derived POIs; not the public Overpass API.
 *
 * Operational notes: [postpass-ops](https://github.com/woodpeck/postpass-ops) documents this
 * public instance but does not publish fixed numeric rate limits. Use modest concurrency,
 * backoff on errors, and avoid oversized queries to stay within fair use.
 *
 * [baseUrl] is the Postpass interpreter endpoint. The default is the public Geofabrik
 * instance; the constructor accepts an override so unit tests can point at a local
 * MockWebServer.
 */
class PostpassRepository(
    context: Context,
    private val baseUrl: String = "https://postpass.geofabrik.de/api/0.2/interpreter",
) {

    private val appContext: Context = context.applicationContext

    suspend fun fetchPoisForTile(
        bounds: BoundingBox,
        categories: Set<String>,
    ): TileFetchResult {
        if (categories.isEmpty()) return TileFetchResult.Success(FeatureCollection(emptyList()))
        return try {
            val jsonStr = executeQuery(buildPostpassQuery(bounds, categories))
            val json = Json.parseToJsonElement(jsonStr).jsonObject
            val features = json["features"]?.jsonArray?.mapNotNull {
                parsePostpassFeature(it.jsonObject, categories)
            } ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            TileFetchResult.Success(FeatureCollection(features) as FeatureCollection<Point, JsonObject>)
        } catch (e: PostpassServerException) {
            TileFetchResult.ServerError(e.message ?: "Server error")
        } catch (e: kotlinx.serialization.SerializationException) {
            TileFetchResult.Failed(e.message ?: "Parse error")
        } catch (e: java.io.IOException) {
            TileFetchResult.Failed(e.message ?: "Network error")
        }
    }

    private suspend fun executeQuery(query: String): String = withContext(Dispatchers.IO) {
        android.util.Log.d("POI_DEBUG", "Postpass query:\n$query")
        val attempts = listOf(5_000, 10_000)
        var lastException: Exception? = null
        for ((index, timeout) in attempts.withIndex()) {
            if (index > 0) kotlinx.coroutines.delay(1_000)
            try {
                android.util.Log.d("POI_DEBUG", "Trying Postpass (timeout=${timeout}ms)")
                val conn = URL(baseUrl).openConnection() as java.net.HttpURLConnection
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
        val matchedCategory = findMatchedCategory(tags, enabledCategories) ?: return null
        return buildPoiFeature(
            lat = lat,
            lon = lon,
            tags = tags,
            matchedCategory = matchedCategory,
            categoryLabelFor = { cat -> appContext.getString(cat.labelRes) },
        )
    }
}
