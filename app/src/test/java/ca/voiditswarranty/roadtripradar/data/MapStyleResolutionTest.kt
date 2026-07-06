package ca.voiditswarranty.roadtripradar.data

import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.model.MapStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [resolveToConcrete] (the AUTO-collapse logic for [MapStyle.AUTO]) and its
 * extracted pure helper [resolveAuto].
 *
 * The current production code is one big `resolveToConcrete(context, night)` that pulls
 * `customLightAutoEnabled` / `customDarkAutoEnabled` from `PreferencesRepository` and
 * `hasCustomLight()` / `hasCustomDark()` from `CustomThemeRepository`. To test the
 * 4-quadrant truth table without standing up those repos, the production code is
 * refactored to delegate to a pure [resolveAuto] helper:
 *
 * ```kotlin
 * internal fun resolveAuto(
 *     night: Boolean,
 *     customLightAutoEnabled: Boolean,
 *     customDarkAutoEnabled: Boolean,
 *     hasCustomLight: Boolean,
 *     hasCustomDark: Boolean,
 * ): MapStyle
 * ```
 *
 * [resolveToConcrete] becomes a thin wrapper that reads those four values from the repos
 * and calls [resolveAuto].
 *
 * Uses Robolectric for the Context-dependent wrappers (default night-mode check,
 * `isDarkForAppTheme`); the [resolveAuto] helper itself is pure and could be tested
 * without Robolectric, but it's tested in the same suite for cohesion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapStyleResolutionTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    // -------- resolveAuto: pure helper, 4-quadrant truth table --------

    @Test
    fun resolveAuto_nightFalse_noCustomLight_returnsLiberty() {
        // Day mode, no custom-light available → fall back to the built-in light style.
        assertEquals(
            MapStyle.LIBERTY,
            resolveAuto(
                night = false,
                customLightAutoEnabled = false,
                customDarkAutoEnabled = false,
                hasCustomLight = false,
                hasCustomDark = false,
            ),
        )
    }

    @Test
    fun resolveAuto_nightFalse_customLightPresent_andEnabled_returnsCustomLight() {
        assertEquals(
            MapStyle.CUSTOM_LIGHT,
            resolveAuto(
                night = false,
                customLightAutoEnabled = true,
                customDarkAutoEnabled = false,
                hasCustomLight = true,
                hasCustomDark = false,
            ),
        )
    }

    @Test
    fun resolveAuto_nightFalse_customLightPresent_butDisabled_returnsLiberty() {
        // The user explicitly disabled the "auto use custom light" pref → fall back to
        // LIBERTY even though the custom theme is present.
        assertEquals(
            MapStyle.LIBERTY,
            resolveAuto(
                night = false,
                customLightAutoEnabled = false,
                customDarkAutoEnabled = false,
                hasCustomLight = true,
                hasCustomDark = false,
            ),
        )
    }

    @Test
    fun resolveAuto_nightTrue_noCustomDark_returnsColorDark() {
        // Night mode, no custom-dark available → fall back to the built-in dark style.
        assertEquals(
            MapStyle.COLOR_DARK,
            resolveAuto(
                night = true,
                customLightAutoEnabled = false,
                customDarkAutoEnabled = false,
                hasCustomLight = false,
                hasCustomDark = false,
            ),
        )
    }

    @Test
    fun resolveAuto_nightTrue_customDarkPresent_andEnabled_returnsCustomDark() {
        assertEquals(
            MapStyle.CUSTOM_DARK,
            resolveAuto(
                night = true,
                customLightAutoEnabled = false,
                customDarkAutoEnabled = true,
                hasCustomLight = false,
                hasCustomDark = true,
            ),
        )
    }

    @Test
    fun resolveAuto_nightTrue_customDarkPresent_butDisabled_returnsColorDark() {
        assertEquals(
            MapStyle.COLOR_DARK,
            resolveAuto(
                night = true,
                customLightAutoEnabled = false,
                customDarkAutoEnabled = false,
                hasCustomLight = false,
                hasCustomDark = true,
            ),
        )
    }

    // -------- resolveToConcrete: Context-dependent wrapper --------

    @Test
    fun resolveToConcrete_nonAutoStyle_returnsItself() {
        // Only AUTO collapses; LIBERTY/DARK/COLOR_DARK/CUSTOM_LIGHT/CUSTOM_DARK are
        // returned as-is.
        assertEquals(MapStyle.LIBERTY, MapStyle.LIBERTY.resolveToConcrete(context, night = true))
        assertEquals(MapStyle.DARK, MapStyle.DARK.resolveToConcrete(context, night = false))
        assertEquals(MapStyle.COLOR_DARK, MapStyle.COLOR_DARK.resolveToConcrete(context, night = true))
    }

    @Test
    fun resolveToConcrete_AUTO_nightFalse_returnsLiberty() {
        // Default prefs, day mode → LIBERTY.
        val resolved = MapStyle.AUTO.resolveToConcrete(context, night = false)
        assertEquals(MapStyle.LIBERTY, resolved)
    }

    @Test
    fun resolveToConcrete_AUTO_nightTrue_returnsColorDark() {
        // Default prefs, night mode → COLOR_DARK.
        val resolved = MapStyle.AUTO.resolveToConcrete(context, night = true)
        assertEquals(MapStyle.COLOR_DARK, resolved)
    }

    // -------- isDarkForAppTheme --------

    @Test
    fun isDarkForAppTheme_LIBERTY_returnsFalse() {
        assertEquals(false, MapStyle.LIBERTY.isDarkForAppTheme(context, night = true))
    }

    @Test
    fun isDarkForAppTheme_DARK_returnsTrue() {
        assertEquals(true, MapStyle.DARK.isDarkForAppTheme(context, night = false))
    }

    @Test
    fun isDarkForAppTheme_COLOR_DARK_returnsTrue() {
        assertEquals(true, MapStyle.COLOR_DARK.isDarkForAppTheme(context, night = false))
    }

    @Test
    fun isDarkForAppTheme_AUTO_nightTrue_returnsTrue() {
        // Resolved AUTO under night flag is dark.
        assertEquals(true, MapStyle.AUTO.isDarkForAppTheme(context, night = true))
    }

    @Test
    fun isDarkForAppTheme_AUTO_nightFalse_returnsFalse() {
        assertEquals(false, MapStyle.AUTO.isDarkForAppTheme(context, night = false))
    }

    // -------- resolvedStyleUri --------

    @Test
    fun resolvedStyleUri_LIBERTY_returnsHttpsUrl() {
        // The non-AUTO, non-CUSTOM case delegates to styleUri.
        assertEquals(
            "https://tiles.openfreemap.org/styles/liberty",
            MapStyle.LIBERTY.resolvedStyleUri(context),
        )
    }

    @Test
    fun resolvedStyleUri_DARK_returnsAssetUri() {
        assertEquals(
            "asset://dark.json",
            MapStyle.DARK.resolvedStyleUri(context),
        )
    }

    @Test
    fun resolvedStyleUri_AUTO_nightFalse_returnsLiberty() {
        // Resolved AUTO under day flag → LIBERTY's URI.
        assertEquals(
            "https://tiles.openfreemap.org/styles/liberty",
            MapStyle.AUTO.resolvedStyleUri(context),
        )
    }
}
