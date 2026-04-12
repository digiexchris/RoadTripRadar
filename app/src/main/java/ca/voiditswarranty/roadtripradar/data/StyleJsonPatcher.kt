package ca.voiditswarranty.roadtripradar.data

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Defines a color editing category with the layer IDs it targets,
 * the paint property to modify, and a human-readable label.
 */
data class ThemeColorCategory(
    val id: String,
    val label: String,
    /** Layer IDs (or ID prefixes via [matchAsPrefix]) that belong to this category. */
    val layerIds: List<String>,
    /** Whether to match [layerIds] as ID prefix patterns. */
    val matchAsPrefix: Boolean = false,
    /** The paint property key to read/write. */
    val paintProperty: String,
)

val THEME_COLOR_CATEGORIES: List<ThemeColorCategory> = listOf(
    ThemeColorCategory(
        id = "background",
        label = "Background / Land",
        layerIds = listOf("background"),
        paintProperty = "background-color",
    ),
    ThemeColorCategory(
        id = "water",
        label = "Water",
        layerIds = listOf("water", "waterway"),
        matchAsPrefix = true,
        paintProperty = "fill-color",
    ),
    ThemeColorCategory(
        id = "parks",
        label = "Parks & Greenery",
        layerIds = listOf("park", "landcover_wood", "landcover_grass"),
        paintProperty = "fill-color",
    ),
    ThemeColorCategory(
        id = "buildings",
        label = "Buildings",
        layerIds = listOf("building"),
        matchAsPrefix = true,
        paintProperty = "fill-color",
    ),
    ThemeColorCategory(
        id = "road_motorway",
        label = "Motorway / Freeway",
        layerIds = listOf("road_motorway", "tunnel_motorway", "bridge_motorway"),
        matchAsPrefix = true,
        paintProperty = "line-color",
    ),
    ThemeColorCategory(
        id = "road_motorway_casing",
        label = "Motorway Casing",
        layerIds = listOf("road_motorway_casing", "tunnel_motorway_casing", "bridge_motorway_casing"),
        matchAsPrefix = true,
        paintProperty = "line-color",
    ),
    ThemeColorCategory(
        id = "road_trunk_primary",
        label = "Trunk & Primary Roads",
        layerIds = listOf("road_trunk_primary", "tunnel_trunk_primary", "bridge_trunk_primary"),
        matchAsPrefix = true,
        paintProperty = "line-color",
    ),
    ThemeColorCategory(
        id = "road_secondary_tertiary",
        label = "Secondary & Tertiary Roads",
        layerIds = listOf("road_secondary_tertiary", "tunnel_secondary_tertiary", "bridge_secondary_tertiary"),
        matchAsPrefix = true,
        paintProperty = "line-color",
    ),
    ThemeColorCategory(
        id = "road_minor",
        label = "Minor Roads",
        layerIds = listOf("road_minor", "tunnel_minor", "bridge_minor"),
        matchAsPrefix = true,
        paintProperty = "line-color",
    ),
    ThemeColorCategory(
        id = "road_service_track",
        label = "Service Roads & Tracks",
        layerIds = listOf("road_service_track", "tunnel_service_track", "bridge_service_track"),
        matchAsPrefix = true,
        paintProperty = "line-color",
    ),
    ThemeColorCategory(
        id = "road_service_track_casing",
        label = "Service Roads & Tracks Casing",
        layerIds = listOf("road_service_track_casing", "tunnel_service_track_casing", "bridge_service_track_casing"),
        matchAsPrefix = true,
        paintProperty = "line-color",
    ),
    ThemeColorCategory(
        id = "road_path_pedestrian",
        label = "Paths & Pedestrian",
        layerIds = listOf("road_path_pedestrian", "tunnel_path_pedestrian", "bridge_path_pedestrian"),
        matchAsPrefix = true,
        paintProperty = "line-color",
    ),
    ThemeColorCategory(
        id = "label_text",
        label = "Labels (Text)",
        layerIds = listOf("place_", "poi_", "road_label", "country_", "state_", "city_", "town_"),
        matchAsPrefix = true,
        paintProperty = "text-color",
    ),
    ThemeColorCategory(
        id = "label_halo",
        label = "Labels (Halo)",
        layerIds = listOf("place_", "poi_", "road_label", "country_", "state_", "city_", "town_"),
        matchAsPrefix = true,
        paintProperty = "text-halo-color",
    ),
)

object StyleJsonPatcher {

    private val json = Json { ignoreUnknownKeys = true }

    /** Returns the color currently used by the first matching layer for the given category. */
    fun extractColor(styleJson: String, category: ThemeColorCategory): Color? {
        val root = runCatching { json.parseToJsonElement(styleJson).jsonObject }.getOrNull() ?: return null
        val layers = root["layers"]?.jsonArray ?: return null

        for (layer in layers) {
            val obj = layer.jsonObject
            val layerId = obj["id"]?.jsonPrimitive?.content ?: continue
            if (!matchesCategory(layerId, category)) continue
            val paint = obj["paint"]?.jsonObject ?: continue
            val value = paint[category.paintProperty] ?: continue
            return extractFirstColor(value)
        }
        return null
    }

    /**
     * Applies [newColors] to the style JSON (keyed by category ID), returning the patched JSON string.
     * Layers that match a category's ID patterns have their relevant paint property replaced.
     */
    fun applyColors(styleJson: String, newColors: Map<String, Color>): String {
        val root = runCatching { json.parseToJsonElement(styleJson).jsonObject }.getOrNull()
            ?: return styleJson

        val categoryById = THEME_COLOR_CATEGORIES.associateBy { it.id }
        val layers = root["layers"]?.jsonArray ?: return styleJson

        val patchedLayers = buildJsonArray {
            for (layer in layers) {
                val obj = layer.jsonObject
                val layerId = obj["id"]?.jsonPrimitive?.content
                if (layerId == null) {
                    add(layer)
                    continue
                }

                var patched = obj
                for ((catId, color) in newColors) {
                    val category = categoryById[catId] ?: continue
                    if (!matchesCategory(layerId, category)) continue
                    val paint = patched["paint"]?.jsonObject ?: continue
                    if (!paint.containsKey(category.paintProperty)) continue
                    val newPaintValue = replaceColorsInElement(paint[category.paintProperty]!!, color)
                    patched = JsonObject(patched.toMutableMap().also { map ->
                        map["paint"] = JsonObject(paint.toMutableMap().also { paintMap ->
                            paintMap[category.paintProperty] = newPaintValue
                        })
                    })
                }
                add(patched)
            }
        }

        val patchedRoot = JsonObject(root.toMutableMap().also { it["layers"] = patchedLayers })
        return patchedRoot.toString()
    }

    private fun matchesCategory(layerId: String, category: ThemeColorCategory): Boolean =
        if (category.matchAsPrefix) {
            category.layerIds.any { prefix -> layerId.startsWith(prefix) }
        } else {
            layerId in category.layerIds
        }

    /** Recursively replaces all color strings in a JsonElement with [newColor]. */
    private fun replaceColorsInElement(element: JsonElement, newColor: Color): JsonElement =
        when (element) {
            is JsonPrimitive -> {
                val content = element.content
                if (element.isString && looksLikeColor(content)) {
                    JsonPrimitive(colorToHex(newColor))
                } else {
                    element
                }
            }
            is JsonArray -> buildJsonArray {
                for (child in element) {
                    add(replaceColorsInElement(child, newColor))
                }
            }
            is JsonObject -> JsonObject(element.mapValues { (_, v) ->
                replaceColorsInElement(v, newColor)
            })
        }

    /** Extracts the first color string found in a JsonElement. */
    private fun extractFirstColor(element: JsonElement): Color? =
        when (element) {
            is JsonPrimitive -> {
                if (element.isString && looksLikeColor(element.content)) {
                    parseColorString(element.content)
                } else null
            }
            is JsonArray -> element.firstNotNullOfOrNull { extractFirstColor(it) }
            is JsonObject -> null
        }

    private fun looksLikeColor(s: String): Boolean =
        s.startsWith("#") || s.startsWith("hsl") || s.startsWith("rgb") || s.startsWith("hsla")

    /** Converts a CSS color string to a Compose [Color], or null if unrecognized. */
    fun parseColorString(s: String): Color? {
        val trimmed = s.trim()
        if (trimmed.startsWith("#")) return parseHex(trimmed)
        if (trimmed.startsWith("hsl(") || trimmed.startsWith("hsla(")) return parseHsl(trimmed)
        if (trimmed.startsWith("rgba(") || trimmed.startsWith("rgb(")) return parseRgb(trimmed)
        return null
    }

    private fun parseHex(hex: String): Color? {
        val cleaned = hex.trimStart('#')
        return runCatching {
            when (cleaned.length) {
                3 -> {
                    val r = cleaned[0].toString().repeat(2).toInt(16)
                    val g = cleaned[1].toString().repeat(2).toInt(16)
                    val b = cleaned[2].toString().repeat(2).toInt(16)
                    Color(r, g, b)
                }
                6 -> Color(
                    cleaned.substring(0, 2).toInt(16),
                    cleaned.substring(2, 4).toInt(16),
                    cleaned.substring(4, 6).toInt(16),
                )
                8 -> Color(
                    cleaned.substring(0, 2).toInt(16),
                    cleaned.substring(2, 4).toInt(16),
                    cleaned.substring(4, 6).toInt(16),
                    cleaned.substring(6, 8).toInt(16),
                )
                else -> null
            }
        }.getOrNull()
    }

    private fun parseHsl(hsl: String): Color? = runCatching {
        val isHsla = hsl.startsWith("hsla")
        val inside = hsl.substringAfter('(').substringBefore(')')
        val parts = inside.split(',').map { it.trim().trimEnd('%') }
        val h = parts[0].toFloat()
        val s = parts[1].toFloat() / 100f
        val l = parts[2].toFloat() / 100f
        val a = if (isHsla && parts.size >= 4) parts[3].toFloat() else 1f
        hslToColor(h, s, l, a)
    }.getOrNull()

    private fun parseRgb(rgb: String): Color? = runCatching {
        val inside = rgb.substringAfter('(').substringBefore(')')
        val parts = inside.split(',').map { it.trim() }
        val r = parts[0].toInt()
        val g = parts[1].toInt()
        val b = parts[2].toInt()
        val a = if (parts.size >= 4) (parts[3].toFloat() * 255).toInt() else 255
        Color(r, g, b, a)
    }.getOrNull()

    private fun hslToColor(h: Float, s: Float, l: Float, a: Float): Color {
        val c = (1f - Math.abs(2f * l - 1f)) * s
        val x = c * (1f - Math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r1, g1, b1) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(
            red = ((r1 + m) * 255).toInt().coerceIn(0, 255),
            green = ((g1 + m) * 255).toInt().coerceIn(0, 255),
            blue = ((b1 + m) * 255).toInt().coerceIn(0, 255),
            alpha = (a * 255).toInt().coerceIn(0, 255),
        )
    }

    fun colorToHex(color: Color): String {
        val r = (color.red * 255).toInt().coerceIn(0, 255)
        val g = (color.green * 255).toInt().coerceIn(0, 255)
        val b = (color.blue * 255).toInt().coerceIn(0, 255)
        val a = (color.alpha * 255).toInt().coerceIn(0, 255)
        return if (a == 255) {
            "#%02x%02x%02x".format(r, g, b)
        } else {
            "#%02x%02x%02x%02x".format(r, g, b, a)
        }
    }
}
