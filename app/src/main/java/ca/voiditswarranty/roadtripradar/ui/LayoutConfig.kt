package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

data class LayoutConfig(
    val navWidgetAlignment: Alignment,
    val widgetScale: Float,
    val fabScale: Float,
    val edgePadding: Dp,
    val controlSpacing: Dp,
    val compassSize: Dp,
    val legendHorizontal: Boolean,
    val timelineHorizontal: Boolean,
) {
    companion object {
        private const val WIDTH_MEDIUM_DP = 600
        private const val HEIGHT_MEDIUM_DP = 480

        fun fromWindow(
            windowSizeClass: WindowSizeClass,
        ): LayoutConfig {
            val isWide = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_MEDIUM_DP)
            val isTall = windowSizeClass.isHeightAtLeastBreakpoint(HEIGHT_MEDIUM_DP)
            val isTablet = windowSizeClass.isAtLeastBreakpoint(WIDTH_MEDIUM_DP, HEIGHT_MEDIUM_DP)

            val widgetScale = if (isTablet) 1.2f else 1.0f
            val fabScale = if (isTablet) 1.15f else 1.0f
            val edgePadding = if (isTablet) 24.dp else 16.dp
            val controlSpacing = if (isTablet) 16.dp else 12.dp
            val compassSize = if (isTablet) 120.dp else 96.dp

            return LayoutConfig(
                navWidgetAlignment = if (isWide && !isTall) Alignment.CenterStart else Alignment.TopStart,
                widgetScale = widgetScale,
                fabScale = fabScale,
                edgePadding = edgePadding,
                controlSpacing = controlSpacing,
                compassSize = compassSize,
                legendHorizontal = isWide && !isTall,
                timelineHorizontal = isWide && !isTall,
            )
        }
    }
}
