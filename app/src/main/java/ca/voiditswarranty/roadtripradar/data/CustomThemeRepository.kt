package ca.voiditswarranty.roadtripradar.data

import android.content.ContentValues
import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import ca.voiditswarranty.roadtripradar.model.MapStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

private val styleJson = Json { ignoreUnknownKeys = true }
private val prettyJson = Json { prettyPrint = true }
private const val OFM_PLANET = "https://tiles.openfreemap.org/planet"

// Matches quoted font names like "Nunito Bold" but not "Noto Sans Bold".
// Captures the weight/style suffix for mapping.
private val nonNotoFontRegex =
    Regex(""""(?!Noto Sans )[A-Za-z ]+?(Extra Bold|ExtraBold|Semi Bold|SemiBold|Bold Italic|Bold|Italic|Medium|Light|Regular)"""")
private const val OFM_GLYPHS = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf"
private const val OFM_SPRITE = "https://tiles.openfreemap.org/sprites/ofm_f384/ofm"

class CustomThemeRepository(context: Context) {

    private val appContext: Context = context.applicationContext

    private fun fileFor(style: MapStyle): File = when (style) {
        MapStyle.CUSTOM_LIGHT -> File(appContext.filesDir, "custom_light.json")
        MapStyle.CUSTOM_DARK -> File(appContext.filesDir, "custom_dark.json")
        else -> error("Not a custom style: $style")
    }

    fun hasCustomLight(): Boolean = fileFor(MapStyle.CUSTOM_LIGHT).exists()
    fun hasCustomDark(): Boolean = fileFor(MapStyle.CUSTOM_DARK).exists()
    fun hasTheme(style: MapStyle): Boolean = fileFor(style).exists()

    /** Returns a file:// URI string for use with MapLibre, or null if no custom theme is imported. */
    fun themeUri(style: MapStyle): String? {
        val file = fileFor(style)
        return if (file.exists()) "file://${file.absolutePath}" else null
    }

    fun readThemeJson(style: MapStyle): String? {
        val file = fileFor(style)
        return if (file.exists()) file.readText() else null
    }

    fun writeThemeJson(style: MapStyle, json: String) {
        fileFor(style).writeText(json)
    }

    fun deleteTheme(style: MapStyle) {
        fileFor(style).delete()
    }

    /**
     * Copies the JSON at [sourceUri] into app-internal storage as the given slot.
     *
     * Validates the JSON before committing. If invalid, throws [InvalidStyleJsonException] with
     * a human-readable message and leaves any existing theme file untouched.
     */
    fun importTheme(sourceUri: Uri, target: MapStyle) {
        val inputStream = appContext.contentResolver.openInputStream(sourceUri)
            ?: throw InvalidStyleJsonException("Could not open the selected file.")

        // Write to a temp file so an existing theme is not corrupted on failure.
        val tempFile = File(appContext.filesDir, "${fileFor(target).name}.tmp")
        try {
            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val json = tempFile.readText()
            validateMapLibreStyle(json)
            val rewritten = rewriteTileSources(json)
            tempFile.writeText(rewritten)
            tempFile.renameTo(fileFor(target))
        } catch (e: InvalidStyleJsonException) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            throw InvalidStyleJsonException("Could not read the selected file: ${e.message}")
        }
    }

    /**
     * Validates that [json] is a well-formed MapLibre style document.
     * @throws [InvalidStyleJsonException] describing the problem if validation fails.
     */
    fun validateMapLibreStyle(json: String) {
        val root = try {
            styleJson.parseToJsonElement(json).jsonObject
        } catch (e: Exception) {
            throw InvalidStyleJsonException("Not valid JSON: ${e.message}")
        }
        val version = root["version"]?.jsonPrimitive?.content
        if (version != "8") {
            throw InvalidStyleJsonException(
                "Missing or unsupported style version (expected 8, got $version). " +
                    "Make sure you export a MapLibre GL style from Maputnik.",
            )
        }
        if (!root.containsKey("layers")) {
            throw InvalidStyleJsonException("Style is missing the required 'layers' field.")
        }
        if (!root.containsKey("sources")) {
            throw InvalidStyleJsonException("Style is missing the required 'sources' field.")
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
    private fun rewriteFonts(json: String): String =
        nonNotoFontRegex.replace(json) { match ->
            val weight = match.groupValues[1]
            val noto = when (weight) {
                "Italic" -> "Noto Sans Italic"
                "Bold", "Semi Bold", "SemiBold", "Extra Bold", "ExtraBold", "Medium" -> "Noto Sans Bold"
                else -> "Noto Sans Regular"
            }
            "\"$noto\""
        }

    /** Returns a sharable FileProvider URI for the custom theme file, or null if it doesn't exist. */
    fun exportThemeUri(style: MapStyle): Uri? {
        val file = fileFor(style)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file,
        )
    }

    /**
     * Returns the base JSON for the given custom style slot sourced from bundled assets.
     * Returns null for built-in styles that are remote (must be downloaded separately).
     */
    fun builtInBaseJson(source: MapStyle, assetManager: AssetManager): String? = when (source) {
        MapStyle.DARK -> assetManager.open("dark.json").use { it.readBytes().decodeToString() }
        MapStyle.COLOR_DARK -> assetManager.open("dark_small_roads.json").use { it.readBytes().decodeToString() }
        else -> null
    }

    /**
     * Saves the custom theme JSON to the Downloads folder via MediaStore.
     * Returns the display name of the saved file, or null on failure.
     */
    fun saveToDownloads(style: MapStyle): String? {
        val file = fileFor(style)
        if (!file.exists()) return null
        val displayName = when (style) {
            MapStyle.CUSTOM_LIGHT -> "roadtripradar_custom_light.json"
            MapStyle.CUSTOM_DARK -> "roadtripradar_custom_dark.json"
            else -> return null
        }
        val resolver = appContext.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
        return displayName
    }

    /** Copies a bundled asset into the custom slot using [source] as the base theme. */
    fun initFromAsset(source: MapStyle, target: MapStyle, assetManager: AssetManager) {
        val json = builtInBaseJson(source, assetManager)
            ?: error("No bundled asset for $source")
        writeThemeJson(target, json)
    }
}

/** Thrown when a user-supplied file is not a valid MapLibre style document. */
class InvalidStyleJsonException(message: String) : Exception(message)
