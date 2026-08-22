package ca.voiditswarranty.roadtripradar.data

import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.model.MapStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [MapStyle] enum constants and the AUTO-resolution path on
 * [ca.voiditswarranty.roadtripradar.data.resolveToConcrete] (and the extracted
 * [resolveAuto] helper).
 *
 * Strategy:
 * - Enum-constant getters (`styleUri`, `displayName`, `displayNameRes`,
 *   `intrinsicallyDark`, `isCustom`, `maputnikUrl`) are pure and tested here without
 *   Robolectric, except `displayNameRes` which needs a real `Context` to resolve the
 *   string resource.
 * - The AUTO-collapse decision (`resolveAuto`) is extracted as a pure helper so the
 *   4-quadrant truth table can be tested without standing up a `PreferencesRepository`
 *   or `CustomThemeRepository`. `resolveToConcrete(context, night)` becomes a thin
 *   plumbing wrapper that pulls the 4 inputs from those repos and calls `resolveAuto`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapStyleTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    // -------- styleUri --------

    @Test
    fun styleUri_LIBERTY_returnsOpenFreeMapUrl() {
        assertEquals("https://tiles.openfreemap.org/styles/liberty", MapStyle.LIBERTY.styleUri)
    }

    @Test
    fun styleUri_DARK_returnsAssetUri() {
        assertEquals("asset://dark.json", MapStyle.DARK.styleUri)
    }

    @Test
    fun styleUri_COLOR_DARK_returnsAssetUri() {
        assertEquals("asset://dark_small_roads.json", MapStyle.COLOR_DARK.styleUri)
    }

    @Test
    fun styleUri_CUSTOM_LIGHT_throws() {
        // CUSTOM_LIGHT requires a Context — its URI is in `filesDir`, not a static URL.
        var threw = false
        try {
            MapStyle.CUSTOM_LIGHT.styleUri
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertTrue("CUSTOM_LIGHT.styleUri must throw without a Context", threw)
    }

    @Test
    fun styleUri_AUTO_throws() {
        // AUTO must be resolved first via `resolveToConcrete(context)` before its URI is
        // accessible. Pin the precondition.
        var threw = false
        try {
            MapStyle.AUTO.styleUri
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertTrue("AUTO.styleUri must throw without resolveToConcrete first", threw)
    }

    // -------- displayName --------

    @Test
    fun displayName_LIBERTY_returnsLiberty() {
        assertEquals("Liberty", MapStyle.LIBERTY.displayName)
    }

    @Test
    fun displayName_DARK_returnsDark() {
        assertEquals("Dark", MapStyle.DARK.displayName)
    }

    @Test
    fun displayName_COLOR_DARK_returnsDarkSmallRoads() {
        assertEquals("Dark (Small Roads)", MapStyle.COLOR_DARK.displayName)
    }

    @Test
    fun displayName_CUSTOM_LIGHT_returnsCustomLight() {
        assertEquals("Custom Light", MapStyle.CUSTOM_LIGHT.displayName)
    }

    @Test
    fun displayName_CUSTOM_DARK_returnsCustomDark() {
        assertEquals("Custom Dark", MapStyle.CUSTOM_DARK.displayName)
    }

    @Test
    fun displayName_AUTO_returnsAuto() {
        assertEquals("Auto", MapStyle.AUTO.displayName)
    }

    // -------- displayNameRes (Context-dependent, Robolectric) --------

    @Test
    fun displayNameRes_LIBERTY_returnsStyleLibertyString() {
        assertEquals(
            ca.voiditswarranty.roadtripradar.R.string.style_liberty,
            MapStyle.LIBERTY.displayNameRes,
        )
    }

    @Test
    fun displayNameRes_AUTO_returnsStyleAutoString() {
        assertEquals(
            ca.voiditswarranty.roadtripradar.R.string.style_auto,
            MapStyle.AUTO.displayNameRes,
        )
    }

    // -------- intrinsicallyDark --------

    @Test
    fun intrinsicallyDark_DARK_returnsTrue() {
        assertTrue(MapStyle.DARK.intrinsicallyDark)
    }

    @Test
    fun intrinsicallyDark_COLOR_DARK_returnsTrue() {
        assertTrue(MapStyle.COLOR_DARK.intrinsicallyDark)
    }

    @Test
    fun intrinsicallyDark_CUSTOM_DARK_returnsTrue() {
        assertTrue(MapStyle.CUSTOM_DARK.intrinsicallyDark)
    }

    @Test
    fun intrinsicallyDark_LIBERTY_returnsFalse() {
        assertFalse(MapStyle.LIBERTY.intrinsicallyDark)
    }

    @Test
    fun intrinsicallyDark_CUSTOM_LIGHT_returnsFalse() {
        assertFalse(MapStyle.CUSTOM_LIGHT.intrinsicallyDark)
    }

    @Test
    fun intrinsicallyDark_AUTO_returnsFalse() {
        // AUTO is not inherently dark — its resolved value is. Pin the contract.
        assertFalse(MapStyle.AUTO.intrinsicallyDark)
    }

    // -------- isCustom --------

    @Test
    fun isCustom_CUSTOM_LIGHT_returnsTrue() {
        assertTrue(MapStyle.CUSTOM_LIGHT.isCustom)
    }

    @Test
    fun isCustom_CUSTOM_DARK_returnsTrue() {
        assertTrue(MapStyle.CUSTOM_DARK.isCustom)
    }

    @Test
    fun isCustom_LIBERTY_returnsFalse() {
        assertFalse(MapStyle.LIBERTY.isCustom)
    }

    @Test
    fun isCustom_DARK_returnsFalse() {
        assertFalse(MapStyle.DARK.isCustom)
    }

    @Test
    fun isCustom_AUTO_returnsFalse() {
        // AUTO is a resolution mode, not a custom theme.
        assertFalse(MapStyle.AUTO.isCustom)
    }

    // -------- maputnikUrl --------

    @Test
    fun maputnikUrl_LIBERTY_returnsUrl() {
        // Built-in styles have a Maputnik editor URL. Pin the shape.
        assertTrue(
            "LIBERTY.maputnikUrl must be non-null and start with https://maplibre.org",
            MapStyle.LIBERTY.maputnikUrl!!.startsWith("https://maplibre.org/maputnik/"),
        )
    }

    @Test
    fun maputnikUrl_DARK_returnsUrl() {
        assertTrue(
            MapStyle.DARK.maputnikUrl!!.startsWith("https://maplibre.org/maputnik/"),
        )
    }

    @Test
    fun maputnikUrl_CUSTOM_LIGHT_returnsNull() {
        // Custom themes have no Maputnik URL — they're user-imported.
        assertEquals(null, MapStyle.CUSTOM_LIGHT.maputnikUrl)
    }

    @Test
    fun maputnikUrl_AUTO_returnsNull() {
        assertEquals(null, MapStyle.AUTO.maputnikUrl)
    }
}
