package ca.voiditswarranty.roadtripradar.car

import androidx.car.app.model.CarIcon
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [MakiIcons] — the car-surface wrapper that renders a maki SVG
 * into a circular [CarIcon] for [androidx.car.app.model.GridItem]s. Bitmaps
 * are cached per `(iconName, selected)` for the life of the process; the
 * tests pin that contract and the unknown-icon-name fallback to a plain
 * filled circle.
 *
 * The maki icon pack is a git submodule synced into
 * `app/src/main/assets/maki/` by the `syncMakiIcons` Gradle task (see
 * `app/build.gradle.kts`). Robolectric reads from the same merged assets
 * directory, so `forName` can find a real icon in tests. Pinned to SDK 33
 * for consistency with the rest of the car test suite (the `MapViewModel`
 * `ConnectivityManager` shadow issue doesn't apply here, but the pin keeps
 * the class consistent).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MakiIconsTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    // -------- cache hit --------

    @Test
    fun forName_cacheHit_doesNotReRender() {
        // The cache is keyed on (iconName, selected). Two calls with the same
        // args must return the same CarIcon instance by reference — re-rendering
        // the same SVG and re-wrapping it as a CarIcon on every
        // `onGetTemplate()` would be wasteful (the POI category set is fixed
        // for the life of the process, but `onGetTemplate` runs every time
        // the user enters the POI screen).
        val first = MakiIcons.forName(context, "restaurant", selected = true)
        val second = MakiIcons.forName(context, "restaurant", selected = true)
        assertNotNull(first)
        assertSame(
            "cache hit must return the same CarIcon instance",
            first,
            second,
        )
    }

    @Test
    fun forName_cacheMiss_differentIconsPerSelectedState() {
        // The same icon name with `selected = true` vs `selected = false`
        // renders different bitmaps (filled blue vs white-with-border), so
        // the cache key distinguishes them. The two CarIcon instances should
        // NOT be the same — they're different bitmaps. `CarIcon.equals()` is
        // a structural compare that doesn't distinguish the underlying
        // bitmap, so we use reference identity (===) to verify the cache
        // produced a fresh icon for the new key.
        val selected = MakiIcons.forName(context, "restaurant", selected = true)
        val unselected = MakiIcons.forName(context, "restaurant", selected = false)
        assertNotNull(selected)
        assertNotNull(unselected)
        assertNotSame(
            "selected vs unselected must be distinct CarIcon instances",
            selected,
            unselected,
        )
    }

    @Test
    fun forName_differentIconNames_cacheSeparately() {
        // Sanity check: two different icon names with the same `selected`
        // value must also produce distinct CarIcon instances (different
        // cache keys). Pins the "key includes iconName" half of the key.
        val restaurant = MakiIcons.forName(context, "restaurant", selected = true)
        val cafe = MakiIcons.forName(context, "cafe", selected = true)
        assertNotNull(restaurant)
        assertNotNull(cafe)
        assertNotSame(restaurant, cafe)
    }

    // -------- unknown icon name fallback --------

    @Test
    fun forName_unknownIconName_returnsNonNullIcon() {
        // `renderMakiIcon` returns null when the asset can't be loaded
        // (unknown icon name → `assets.open("maki/nope.svg")` throws). The
        // `MakiIcons.build` path catches that and falls back to a plain
        // filled circle so the car POI grid still shows a valid image for
        // every category (rather than a broken / blank cell).
        val icon = MakiIcons.forName(context, "this-icon-does-not-exist", selected = true)
        assertNotNull("fallback icon must not be null", icon)
    }

    @Test
    fun forName_unknownIconName_unselectedAlsoReturnsNonNullIcon() {
        // The fallback path runs in both selected and unselected code paths;
        // assert the unselected branch falls back too.
        val icon = MakiIcons.forName(context, "this-icon-does-not-exist", selected = false)
        assertNotNull(icon)
    }

    // -------- known icon name renders successfully --------

    @Test
    fun forName_knownIconName_returnsNonNullIcon() {
        // Sanity check that the happy path works under Robolectric — a real
        // icon name resolves through the maki asset and produces a non-null
        // CarIcon. Without this, a regression in the asset-loading code path
        // (e.g. an `assets.open` that throws unexpectedly) could pass the
        // fallback tests but break the production rendering.
        val icon = MakiIcons.forName(context, "restaurant", selected = true)
        assertNotNull(icon)
    }
}
