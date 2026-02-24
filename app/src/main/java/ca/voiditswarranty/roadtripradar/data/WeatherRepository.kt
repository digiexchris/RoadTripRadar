package ca.voiditswarranty.roadtripradar.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL

data class WeatherFrameData(
    val generated: Long,
    val paths: List<String>,
    val times: List<Long>,
)

class WeatherRepository(
    private val baseUrl: String = "https://api.rainviewer.com",
) {

    suspend fun fetchFrames(lastGenerated: Long): WeatherFrameData? {
        return try {
            val jsonStr = withContext(Dispatchers.IO) {
                URL("$baseUrl/public/weather-maps.json").readText()
            }
            val json = Json.parseToJsonElement(jsonStr).jsonObject
            val generated = json["generated"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            if (generated == lastGenerated) return null

            val past = json["radar"]?.jsonObject?.get("past")?.jsonArray
            val entries = past?.takeLast(10) ?: emptyList()
            WeatherFrameData(
                generated = generated,
                paths = entries.map { it.jsonObject["path"]!!.jsonPrimitive.content },
                times = entries.map { it.jsonObject["time"]!!.jsonPrimitive.content.toLong() },
            )
        } catch (_: Exception) {
            null
        }
    }
}
