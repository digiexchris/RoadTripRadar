package ca.voiditswarranty.roadtripradar.data

import ca.voiditswarranty.roadtripradar.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure helpers for the custom theme importer — extracted from `CustomThemeRepository` so
 * they can be unit-tested without standing up a [android.content.Context]. The repo
 * instance is still responsible for the file-system side (write, delete, export URI);
 * the helpers here are pure JSON transformations.
 */

private val styleJson = Json { ignoreUnknownKeys = true }
private val prettyJson = Json { prettyPrint = true }
private const val OFM_PLANET = "https://tiles.openfreemap.org/planet"
private const val OFM_GLYPHS = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf"
private const val OFM_SPRITE = "https://tiles.openfreemap.org/sprites/ofm_f384/ofm"

// Matches quoted font names like "Nunito Bold" but not "Noto Sans Bold".
// Captures the weight/style suffix for mapping.
private val nonNotoFontRegex =
    Regex(""""(?!Noto Sans )[A-Za-z ]+?(Extra Bold|ExtraBold|Semi Bold|SemiBold|Bold Italic|Bold|Italic|Medium|Light|Regular)"""")

/**
 * Validates that [json] is a well-formed MapLibre style document.
 * @throws [InvalidStyleJsonException] describing the problem if validation fails.
 */
fun validateMapLibreStyle(json: String) {
    val root = try {
        styleJson.parseToJsonElement(json).jsonObject
    } catch (e: Exception) {
        throw InvalidStyleJsonException(R.string.theme_import_error_not_json, cause = e)
    }
    val version = root["version"]?.jsonPrimitive?.content
    if (version != "8") {
        throw InvalidStyleJsonException(
            R.string.theme_import_error_unsupported_version,
            formatArg = version ?: "null",
        )
    }
    if (!root.containsKey("layers")) {
        throw InvalidStyleJsonException(R.string.theme_import_error_missing_layers)
    }
    if (!root.containsKey("sources")) {
        throw InvalidStyleJsonException(R.string.theme_import_error_missing_sources)
    }
}

/**
 * Rewrites tile source, glyph, sprite URLs and font names in the style JSON to use
 * OpenFreeMap. Maputnik templates commonly default to MapTiler sources (which require a
 * paid API key) and fonts not hosted by OpenFreeMap.
 */
fun rewriteTileSources(json: String): String {
    val root = styleJson.parseToJsonElement(json).jsonObject
    val mutable = root.toMutableMap()

    // Rewrite glyphs
    val glyphs = root["glyphs"]?.jsonPrimitive?.content
    if (glyphs != null && !glyphs.contains("openfreemap.org")) {
        mutable["glyphs"] = JsonPrimitive(OFM_GLYPHS)
    }

    // Rewrite sprite
    val sprite = root["sprite"]?.jsonPrimitive?.content
    if (sprite != null && !sprite.contains("openfreemap.org")) {
        mutable["sprite"] = JsonPrimitive(OFM_SPRITE)
    }

    // Rewrite sources: any vector source with a "url" not pointing at OpenFreeMap
    val sources = root["sources"]?.jsonObject
    if (sources != null) {
        val rewrittenSources = buildJsonObject {
            for ((name, sourceElement) in sources) {
                val sourceObj = sourceElement.jsonObject
                val type = sourceObj["type"]?.jsonPrimitive?.content
                val url = sourceObj["url"]?.jsonPrimitive?.content
                if (type == "vector" && url != null && !url.contains("openfreemap.org")) {
                    val patched = buildJsonObject {
                        for ((k, v) in sourceObj) {
                            if (k == "url") put(k, JsonPrimitive(OFM_PLANET))
                            else put(k, v)
                        }
                    }
                    put(name, patched)
                } else {
                    put(name, sourceElement)
                }
            }
        }
        mutable["sources"] = rewrittenSources
    }

    val serialized = prettyJson.encodeToString(JsonObject.serializer(), JsonObject(mutable))
    return rewriteFonts(serialized)
}

/**
 * Replaces non-Noto-Sans font names with the closest Noto Sans equivalent.
 * OpenFreeMap only hosts Noto Sans Regular/Bold/Italic glyphs.
 */
fun rewriteFonts(json: String): String =
    nonNotoFontRegex.replace(json) { match ->
        val weight = match.groupValues[1]
        val noto = when (weight) {
            "Italic" -> "Noto Sans Italic"
            "Bold", "Semi Bold", "SemiBold", "Extra Bold", "ExtraBold", "Medium" -> "Noto Sans Bold"
            else -> "Noto Sans Regular"
        }
        "\"$noto\""
    }

/** Thrown when a user-supplied file is not a valid MapLibre style document. */
class InvalidStyleJsonException(
    @androidx.annotation.StringRes val messageRes: Int,
    val formatArg: String? = null,
    cause: Throwable? = null,
) : Exception(cause)
