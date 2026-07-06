package ca.voiditswarranty.roadtripradar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests for the extracted custom-theme helpers — `validateMapLibreStyle`,
 * `rewriteTileSources`, and `rewriteFonts`. Originally instance methods / private file-
 * level function on `CustomThemeRepository.kt`; promoted to top-level `internal`/`public`
 * functions in the new `CustomThemeHelpers.kt` so this test can drive them without a
 * `Context`.
 */
class CustomThemeHelpersTest {

    // -------- validateMapLibreStyle --------

    @Test
    fun validateMapLibreStyle_minimalValidJson_succeeds() {
        val json = """{ "version": 8, "sources": {}, "layers": [] }"""
        // No throw.
        validateMapLibreStyle(json)
    }

    @Test
    fun validateMapLibreStyle_missingVersion_throws() {
        val json = """{ "sources": {}, "layers": [] }"""
        try {
            validateMapLibreStyle(json)
            fail("expected InvalidStyleJsonException for missing version")
        } catch (e: InvalidStyleJsonException) {
            assertNotNull(e)
        }
    }

    @Test
    fun validateMapLibreStyle_wrongVersion_throws() {
        // version 7 is too old.
        val json = """{ "version": 7, "sources": {}, "layers": [] }"""
        try {
            validateMapLibreStyle(json)
            fail("expected InvalidStyleJsonException for version != 8")
        } catch (e: InvalidStyleJsonException) {
            assertEquals(
                "expected unsupported-version message, got ${e.messageRes} (${e.formatArg})",
                ca.voiditswarranty.roadtripradar.R.string.theme_import_error_unsupported_version,
                e.messageRes,
            )
            assertEquals("formatArg should be the actual version string", "7", e.formatArg)
        }
    }

    @Test
    fun validateMapLibreStyle_missingLayers_throws() {
        val json = """{ "version": 8, "sources": {} }"""
        try {
            validateMapLibreStyle(json)
            fail("expected InvalidStyleJsonException for missing layers")
        } catch (e: InvalidStyleJsonException) {
            assertEquals(
                ca.voiditswarranty.roadtripradar.R.string.theme_import_error_missing_layers,
                e.messageRes,
            )
        }
    }

    @Test
    fun validateMapLibreStyle_missingSources_throws() {
        val json = """{ "version": 8, "layers": [] }"""
        try {
            validateMapLibreStyle(json)
            fail("expected InvalidStyleJsonException for missing sources")
        } catch (e: InvalidStyleJsonException) {
            assertEquals(
                ca.voiditswarranty.roadtripradar.R.string.theme_import_error_missing_sources,
                e.messageRes,
            )
        }
    }

    @Test
    fun validateMapLibreStyle_malformedJson_throws() {
        try {
            validateMapLibreStyle("not json at all")
            fail("expected InvalidStyleJsonException for malformed JSON")
        } catch (e: InvalidStyleJsonException) {
            assertEquals(
                ca.voiditswarranty.roadtripradar.R.string.theme_import_error_not_json,
                e.messageRes,
            )
        }
    }

    // -------- rewriteFonts --------

    @Test
    fun rewriteFonts_nunitoBold_replacedWithNotoBold() {
        val input = """{ "text-font": ["Nunito Bold"], "name": "x" }"""
        val output = rewriteFonts(input)
        assertTrue(
            "expected Noto Sans Bold, got: $output",
            output.contains("\"Noto Sans Bold\""),
        )
        assertTrue(
            "expected Nunito to be gone, got: $output",
            !output.contains("Nunito"),
        )
    }

    @Test
    fun rewriteFonts_notoSansRegular_preserved() {
        // The regex uses a negative lookahead `(?!Noto Sans )` to skip Noto Sans
        // variants. Pin that.
        val input = """{ "text-font": ["Noto Sans Regular"], "name": "x" }"""
        val output = rewriteFonts(input)
        assertTrue(
            "Noto Sans Regular must be preserved as-is, got: $output",
            output.contains("\"Noto Sans Regular\""),
        )
    }

    @Test
    fun rewriteFonts_openSansLight_replacedWithNotoRegular() {
        // "Light" weight has no Noto Bold equivalent — falls into the else branch → Regular.
        val input = """{ "text-font": ["Open Sans Light"] }"""
        val output = rewriteFonts(input)
        assertTrue(
            "expected Noto Sans Regular, got: $output",
            output.contains("\"Noto Sans Regular\""),
        )
    }

    @Test
    fun rewriteFonts_italic_replacedWithNotoSansItalic() {
        val input = """{ "text-font": ["Open Sans Italic"] }"""
        val output = rewriteFonts(input)
        assertTrue(
            "expected Noto Sans Italic, got: $output",
            output.contains("\"Noto Sans Italic\""),
        )
    }

    @Test
    fun rewriteFonts_emptyString_returnsEmpty() {
        assertEquals("", rewriteFonts(""))
    }

    // -------- rewriteTileSources --------

    @Test
    fun rewriteTileSources_replacesMaptilerSource() {
        val input = """
            {
              "version": 8,
              "glyphs": "https://api.maptiler.com/fonts/{fontstack}/{range}.pbf",
              "sprite": "https://api.maptiler.com/sprites/basic",
              "sources": {
                "basemap": { "type": "vector", "url": "https://api.maptiler.com/tiles/v3/tiles.json" }
              },
              "layers": []
            }
        """.trimIndent()
        val output = rewriteTileSources(input)
        // glyphs rewritten
        assertTrue(
            "expected OFM glyphs URL, got: $output",
            output.contains("https://tiles.openfreemap.org/fonts/"),
        )
        // sprite rewritten
        assertTrue(
            "expected OFM sprite URL, got: $output",
            output.contains("https://tiles.openfreemap.org/sprites/"),
        )
        // source url rewritten
        assertTrue(
            "expected OFM planet URL in sources, got: $output",
            output.contains("https://tiles.openfreemap.org/planet"),
        )
    }

    @Test
    fun rewriteTileSources_alreadyOpenFreeMapSource_notReplaced() {
        // If the source already points at openfreemap.org, it should be left alone.
        val input = """
            {
              "version": 8,
              "sources": {
                "basemap": { "type": "vector", "url": "https://tiles.openfreemap.org/planet" }
              },
              "layers": []
            }
        """.trimIndent()
        val output = rewriteTileSources(input)
        // The url should still be present, and the source's other keys (type) are preserved.
        assertTrue("must preserve openfreemap URL", output.contains("https://tiles.openfreemap.org/planet"))
    }

    @Test
    fun rewriteTileSources_rasterSourceNotRewritten() {
        // Only vector sources get the URL replaced; raster sources are left alone.
        val input = """
            {
              "version": 8,
              "sources": {
                "satellite": { "type": "raster", "url": "https://api.maptiler.com/tiles/satellite/tiles.json" }
              },
              "layers": []
            }
        """.trimIndent()
        val output = rewriteTileSources(input)
        // Raster source's maptiler url is preserved (not rewritten to openfreemap planet).
        assertTrue(
            "raster source's maptiler URL must be preserved, got: $output",
            output.contains("api.maptiler.com"),
        )
    }
}
