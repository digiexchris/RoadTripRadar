package ca.voiditswarranty.roadtripradar.data

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [StyleJsonPatcher] — the MapLibre style-JSON color editor. The patcher is
 * pure-Kotlin (no IO, no Android Views), so this is plain JUnit 4. The 14 categories
 * defined in `THEME_COLOR_CATEGORIES` are pinned so a future add / remove is a deliberate
 * test diff.
 *
 * Test coverage:
 * - `THEME_COLOR_CATEGORIES` list (count, IDs, layer IDs, paint properties)
 * - `parseColorString` — hex, rgb, rgba, hsl, hsla, shorthand, invalid
 * - `colorToHex` — round-trip
 * - `extractColor` — match by layer ID, by prefix, no match, missing paint
 * - `applyColors` — single category, multiple categories, partial update, no match
 */
class StyleJsonPatcherTest {

    private val waterCategory = THEME_COLOR_CATEGORIES.first { it.id == "water" }
    private val roadMotorwayCategory = THEME_COLOR_CATEGORIES.first { it.id == "road_motorway" }
    private val backgroundCategory = THEME_COLOR_CATEGORIES.first { it.id == "background" }

    // -------- THEME_COLOR_CATEGORIES --------

    @Test
    fun themeColorCategories_hasExpectedCount() {
        // Pin the 14-entry list. A future add / remove is intentional only if the test
        // is updated alongside it.
        assertEquals(14, THEME_COLOR_CATEGORIES.size)
    }

    @Test
    fun themeColorCategories_idsAreUnique() {
        val ids = THEME_COLOR_CATEGORIES.map { it.id }
        assertEquals("category IDs must be unique", ids.size, ids.toSet().size)
    }

    @Test
    fun themeColorCategories_eachEntryHasLabelAndPaintProperty() {
        for (c in THEME_COLOR_CATEGORIES) {
            // Every category must declare a labelRes and a paint property.
            assertTrue(
                "category ${c.id} must have a paintProperty",
                c.paintProperty.isNotEmpty(),
            )
            assertTrue(
                "category ${c.id} must have at least one layerId",
                c.layerIds.isNotEmpty(),
            )
        }
    }

    @Test
    fun themeColorCategories_water_usesPrefixMatch() {
        // The water category matches `water*` and `waterway*` prefixes. The `matchAsPrefix`
        // flag is critical to its behavior — a layer with id "waterway_tunnel" must match
        // "waterway" via prefix, not exact match.
        assertTrue(
            "water category must use prefix match (catches waterway_tunnel, etc.)",
            waterCategory.matchAsPrefix,
        )
        assertTrue(
            "water category must include waterway prefix",
            waterCategory.layerIds.contains("waterway"),
        )
    }

    @Test
    fun themeColorCategories_background_usesExactMatch() {
        // The background category matches the single layer "background" exactly.
        assertEquals(false, backgroundCategory.matchAsPrefix)
        assertEquals(listOf("background"), backgroundCategory.layerIds)
        assertEquals("background-color", backgroundCategory.paintProperty)
    }

    @Test
    fun themeColorCategories_roadMotorway_usesLineColor() {
        // The road categories edit the line-color paint property.
        assertEquals("line-color", roadMotorwayCategory.paintProperty)
    }

    // -------- parseColorString --------

    @Test
    fun parseColorString_hexWithoutHash_returnsNull() {
        // The function dispatches to parseHex only when the input starts with `#`. A bare
        // 6-digit hex string (e.g. `ff0000`) is unrecognized and returns null. Pin the
        // contract so a future loosening surfaces as a test diff.
        assertNull("hex without leading # is not recognized", StyleJsonPatcher.parseColorString("ff0000"))
    }

    @Test
    fun parseColorString_hexWithHash_returnsIntColor() {
        val color = StyleJsonPatcher.parseColorString("#ff0000")
        assertNotNull(color)
        assertEquals(1.0f, color!!.red, 0.0f)
        assertEquals(0.0f, color.green, 0.0f)
        assertEquals(0.0f, color.blue, 0.0f)
    }

    @Test
    fun parseColorString_hexWithAlpha_returnsColorWithAlpha() {
        // 8-digit hex (RRGGBBAA) — alpha channel. #ff000080 → alpha 0x80/255 ≈ 0.502.
        val color = StyleJsonPatcher.parseColorString("#ff000080")
        assertNotNull(color)
        assertEquals(1.0f, color!!.red, 0.0f)
        assertEquals(0x80 / 255f, color.alpha, 0.01f)
    }

    @Test
    fun parseColorString_hexShorthand_returnsExpanded() {
        // #f00 → #ff0000.
        val color = StyleJsonPatcher.parseColorString("#f00")
        assertNotNull(color)
        assertEquals(1.0f, color!!.red, 0.0f)
        assertEquals(0.0f, color.green, 0.0f)
        assertEquals(0.0f, color.blue, 0.0f)
    }

    @Test
    fun parseColorString_rgb_returnsIntColor() {
        val color = StyleJsonPatcher.parseColorString("rgb(255,0,0)")
        assertNotNull(color)
        assertEquals(1.0f, color!!.red, 0.0f)
        assertEquals(0.0f, color.green, 0.0f)
        assertEquals(0.0f, color.blue, 0.0f)
        assertEquals(1.0f, color.alpha, 0.0f)
    }

    @Test
    fun parseColorString_rgba_returnsIntColorWithAlpha() {
        // rgba(255,0,0,0.5) → alpha ≈ 0.5.
        val color = StyleJsonPatcher.parseColorString("rgba(255,0,0,0.5)")
        assertNotNull(color)
        assertEquals(1.0f, color!!.red, 0.0f)
        assertEquals(0.0f, color.green, 0.0f)
        assertEquals(0.0f, color.blue, 0.0f)
        assertEquals(0.5f, color.alpha, 0.01f)
    }

    @Test
    fun parseColorString_hsl_returnsIntColor() {
        // hsl(0, 100%, 50%) → red.
        val color = StyleJsonPatcher.parseColorString("hsl(0,100%,50%)")
        assertNotNull(color)
        assertEquals(1.0f, color!!.red, 0.0f)
        assertEquals(0.0f, color.green, 0.0f)
        assertEquals(0.0f, color.blue, 0.0f)
    }

    @Test
    fun parseColorString_hsla_returnsIntColorWithAlpha() {
        val color = StyleJsonPatcher.parseColorString("hsla(0,100%,50%,0.5)")
        assertNotNull(color)
        assertEquals(1.0f, color!!.red, 0.0f)
        assertEquals(0.5f, color.alpha, 0.01f)
    }

    @Test
    fun parseColorString_invalidInput_returnsNull() {
        // Not a recognized color format → null.
        assertNull(StyleJsonPatcher.parseColorString("not-a-color"))
        assertNull(StyleJsonPatcher.parseColorString(""))
        assertNull(StyleJsonPatcher.parseColorString("orange")) // CSS named color not supported
    }

    @Test
    fun parseColorString_malformedHex_returnsNull() {
        // Wrong length, non-hex chars.
        assertNull(StyleJsonPatcher.parseColorString("#zzzzzz"))
        assertNull(StyleJsonPatcher.parseColorString("#12345")) // 5 chars
    }

    // -------- colorToHex --------

    @Test
    fun colorToHex_red_returnsRedHex() {
        val hex = StyleJsonPatcher.colorToHex(Color(255, 0, 0))
        assertEquals("#ff0000", hex)
    }

    @Test
    fun colorToHex_black_returnsBlackHex() {
        assertEquals("#000000", StyleJsonPatcher.colorToHex(Color(0, 0, 0)))
    }

    @Test
    fun colorToHex_white_returnsWhiteHex() {
        assertEquals("#ffffff", StyleJsonPatcher.colorToHex(Color(255, 255, 255)))
    }

    @Test
    fun colorToHex_withAlpha_returns8DigitHex() {
        val hex = StyleJsonPatcher.colorToHex(Color(255, 0, 0, 128))
        // Alpha != 255 → output is 8-digit hex.
        assertEquals("#ff000080", hex)
    }

    @Test
    fun colorToHex_fullAlpha_returns6DigitHex() {
        // Alpha == 255 → output is 6-digit hex (no alpha channel).
        val hex = StyleJsonPatcher.colorToHex(Color(255, 0, 0, 255))
        assertEquals("#ff0000", hex)
    }

    @Test
    fun colorToHex_parseColorString_roundTrip() {
        // Round-trip: parse → colorToHex → parse.
        val original = "#1a73e8"
        val color = StyleJsonPatcher.parseColorString(original)
        assertNotNull(color)
        val reHex = StyleJsonPatcher.colorToHex(color!!)
        assertEquals(original, reHex)
    }

    // -------- extractColor --------

    private val fixtureStyleJson = """
        {
          "version": 8,
          "layers": [
            {
              "id": "background",
              "type": "background",
              "paint": { "background-color": "#f8f4f0" }
            },
            {
              "id": "water",
              "type": "fill",
              "paint": { "fill-color": "#a0c8f0" }
            },
            {
              "id": "waterway_tunnel",
              "type": "line",
              "paint": { "line-color": "#7daedc" }
            },
            {
              "id": "road_motorway",
              "type": "line",
              "paint": { "line-color": "#ffd700" }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun extractColor_background_returnsBackgroundHex() {
        val color = StyleJsonPatcher.extractColor(fixtureStyleJson, backgroundCategory)
        assertNotNull("background layer must yield a color", color)
        assertEquals("#f8f4f0", StyleJsonPatcher.colorToHex(color!!))
    }

    @Test
    fun extractColor_water_returnsFirstWaterLayerColor() {
        // The water category matches both "water" and "waterway" by prefix. The first match
        // in the layers array wins — that's the `water` layer at "#a0c8f0".
        val color = StyleJsonPatcher.extractColor(fixtureStyleJson, waterCategory)
        assertNotNull(color)
        assertEquals("#a0c8f0", StyleJsonPatcher.colorToHex(color!!))
    }

    @Test
    fun extractColor_roadMotorway_returnsRoadColor() {
        val color = StyleJsonPatcher.extractColor(fixtureStyleJson, roadMotorwayCategory)
        assertNotNull(color)
        assertEquals("#ffd700", StyleJsonPatcher.colorToHex(color!!))
    }

    @Test
    fun extractColor_noMatchingLayer_returnsNull() {
        // buildings isn't in the fixture → null.
        val buildings = THEME_COLOR_CATEGORIES.first { it.id == "buildings" }
        assertNull(StyleJsonPatcher.extractColor(fixtureStyleJson, buildings))
    }

    @Test
    fun extractColor_malformedJson_returnsNull() {
        assertNull(StyleJsonPatcher.extractColor("not json at all", backgroundCategory))
    }

    @Test
    fun extractColor_missingLayersArray_returnsNull() {
        val json = """{ "version": 8 }"""
        assertNull(StyleJsonPatcher.extractColor(json, backgroundCategory))
    }

    // -------- applyColors --------

    @Test
    fun applyColors_replacesBackgroundColor() {
        val patched = StyleJsonPatcher.applyColors(
            fixtureStyleJson,
            mapOf("background" to Color(0, 0, 0)),
        )
        // Parse back and check the background color was replaced.
        val root = Json.parseToJsonElement(patched).jsonObject
        val bgLayer = root["layers"]!!.jsonArray.first { it.jsonObject["id"]!!.jsonPrimitive.content == "background" }
        val newColor = bgLayer.jsonObject["paint"]!!.jsonObject["background-color"]!!.jsonPrimitive.content
        assertEquals("#000000", newColor)
    }

    @Test
    fun applyColors_replacesWaterAndLeavesOthersAlone() {
        val patched = StyleJsonPatcher.applyColors(
            fixtureStyleJson,
            mapOf("water" to Color(255, 255, 255)),
        )
        val root = Json.parseToJsonElement(patched).jsonObject
        val layers = root["layers"]!!.jsonArray
        val water = layers.first { it.jsonObject["id"]!!.jsonPrimitive.content == "water" }
        val waterColor = water.jsonObject["paint"]!!.jsonObject["fill-color"]!!.jsonPrimitive.content
        assertEquals("#ffffff", waterColor)

        // Background should be untouched.
        val bg = layers.first { it.jsonObject["id"]!!.jsonPrimitive.content == "background" }
        val bgColor = bg.jsonObject["paint"]!!.jsonObject["background-color"]!!.jsonPrimitive.content
        assertEquals("#f8f4f0", bgColor)
    }

    @Test
    fun applyColors_multipleCategories_replacesEach() {
        val patched = StyleJsonPatcher.applyColors(
            fixtureStyleJson,
            mapOf(
                "background" to Color(0, 0, 0),
                "water" to Color(255, 0, 0),
                "road_motorway" to Color(0, 255, 0),
            ),
        )
        val root = Json.parseToJsonElement(patched).jsonObject
        val byId = root["layers"]!!.jsonArray.associateBy { it.jsonObject["id"]!!.jsonPrimitive.content }

        assertEquals(
            "#000000",
            byId["background"]!!.jsonObject["paint"]!!.jsonObject["background-color"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "#ff0000",
            byId["water"]!!.jsonObject["paint"]!!.jsonObject["fill-color"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "#00ff00",
            byId["road_motorway"]!!.jsonObject["paint"]!!.jsonObject["line-color"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun applyColors_unknownCategoryId_isIgnored() {
        // Unknown category IDs are silently ignored (the helper's behavior: `categoryById[catId]
        // ?: continue`).
        val patched = StyleJsonPatcher.applyColors(
            fixtureStyleJson,
            mapOf("totally-fake-category" to Color(255, 0, 0)),
        )
        // Background should be unchanged.
        val root = Json.parseToJsonElement(patched).jsonObject
        val bg = root["layers"]!!.jsonArray.first { it.jsonObject["id"]!!.jsonPrimitive.content == "background" }
        val bgColor = bg.jsonObject["paint"]!!.jsonObject["background-color"]!!.jsonPrimitive.content
        assertEquals("#f8f4f0", bgColor)
    }

    @Test
    fun applyColors_malformedJson_returnsOriginal() {
        // On parse failure, the original string is returned unchanged.
        val original = "not json at all"
        val patched = StyleJsonPatcher.applyColors(original, mapOf("background" to Color(0, 0, 0)))
        assertEquals(original, patched)
    }

    @Test
    fun applyColors_handlesLineColorArray() {
        // line-color can be a step expression (an array of stops, e.g. for zoom-based colors).
        // The patcher must recursively descend into arrays.
        val json = """
            {
              "version": 8,
              "layers": [
                {
                  "id": "road_motorway",
                  "type": "line",
                  "paint": {
                    "line-color": [
                      "interpolate",
                      ["linear"],
                      ["zoom"],
                      5, "#cccccc",
                      10, "#ff8800"
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        val patched = StyleJsonPatcher.applyColors(json, mapOf("road_motorway" to Color(0, 128, 255)))
        val root = Json.parseToJsonElement(patched).jsonObject
        val layer = root["layers"]!!.jsonArray.first()
        val lineColor = layer.jsonObject["paint"]!!.jsonObject["line-color"]!!
        // Both color stops in the array should be replaced.
        val arr = lineColor as JsonArray
        val stringEntries = arr.filter { it.toString().startsWith("\"#") }
        assertEquals("both color stops must be replaced", 2, stringEntries.size)
        for (entry in stringEntries) {
            assertEquals(
                "expected #0080ff, got $entry",
                "\"#0080ff\"",
                entry.toString(),
            )
        }
    }
}
