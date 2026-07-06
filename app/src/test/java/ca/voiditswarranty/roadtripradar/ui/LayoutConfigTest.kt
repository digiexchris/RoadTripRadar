package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [LayoutConfig.fromWindow]. The function derives a scale / padding
 * configuration from the AndroidX [WindowSizeClass]. Two breakpoints are
 * relevant: at or above (600dp, 480dp) is "tablet" (scaled-up UI); below
 * is "compact" (default phone UI).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LayoutConfigTest {

    @Test
    fun fromWindow_compactPhone_returnsDefaultConfig() {
        // 400dp wide, 300dp tall — both below the (600, 480) breakpoint.
        val sizeClass = WindowSizeClass.compute(400f, 300f)
        val config = LayoutConfig.fromWindow(sizeClass)
        // Default (compact) values.
        assertEquals(1.0f, config.widgetScale, 0.0001f)
        assertEquals(1.0f, config.fabScale, 0.0001f)
        assertEquals(16.dp, config.edgePadding)
        assertEquals(12.dp, config.controlSpacing)
    }

    @Test
    fun fromWindow_tablet_returnsScaledConfig() {
        // 800dp wide, 600dp tall — both above the (600, 480) breakpoint.
        val sizeClass = WindowSizeClass.compute(800f, 600f)
        val config = LayoutConfig.fromWindow(sizeClass)
        // Tablet values.
        assertEquals(1.2f, config.widgetScale, 0.0001f)
        assertEquals(1.15f, config.fabScale, 0.0001f)
        assertEquals(24.dp, config.edgePadding)
        assertEquals(16.dp, config.controlSpacing)
    }

    @Test
    fun fromWindow_atWidthBreakpoint_isCompact() {
        // 600dp wide exactly matches the width breakpoint; the helper
        // uses `isAtLeastBreakpoint` which is inclusive, so 600dp is
        // tablet-width. But height must also be ≥ 480 to qualify. Test
        // 600×400 (height below) — should be compact.
        val sizeClass = WindowSizeClass.compute(600f, 400f)
        val config = LayoutConfig.fromWindow(sizeClass)
        // Height is below the breakpoint, so it's still compact.
        assertEquals(1.0f, config.widgetScale, 0.0001f)
    }
}
