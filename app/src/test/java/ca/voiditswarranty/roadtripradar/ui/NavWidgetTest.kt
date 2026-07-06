package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.spatialk.units.Length
import org.maplibre.spatialk.units.extensions.kilometers
import org.maplibre.spatialk.units.extensions.meters
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [NavWidget]. The widget is a self-contained composable
 * — the only inputs are distance/bearing/size/name/metric, so no [MapViewModel]
 * is needed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NavWidgetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun widget_withRoute_rendersDirectionIcon() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                NavWidget(
                    poiDistance = 1.5.kilometers,
                    poiBearingDeg = 45.0,
                    cameraBearing = 0.0,
                    navWidgetSize = 100f,
                    poiName = null,
                    useMetric = true,
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Direction to target location")
            .assertIsDisplayed()
    }

    @Test
    fun widget_metricUnder1km_rendersMeters() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                NavWidget(
                    poiDistance = 500.0.meters,
                    poiBearingDeg = 0.0,
                    cameraBearing = 0.0,
                    navWidgetSize = 100f,
                    poiName = null,
                    useMetric = true,
                )
            }
        }
        // 500 m → "500 m"
        composeTestRule.onNodeWithText("500 m").assertIsDisplayed()
    }

    @Test
    fun widget_metricOver1km_rendersKm() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                NavWidget(
                    poiDistance = 1.5.kilometers,
                    poiBearingDeg = 0.0,
                    cameraBearing = 0.0,
                    navWidgetSize = 100f,
                    poiName = null,
                    useMetric = true,
                )
            }
        }
        // 1.5 km → "1.5 km"
        composeTestRule.onNodeWithText("1.5 km").assertIsDisplayed()
    }

    @Test
    fun widget_imperial_rendersMiles() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                NavWidget(
                    poiDistance = 1.5.kilometers,
                    poiBearingDeg = 0.0,
                    cameraBearing = 0.0,
                    navWidgetSize = 100f,
                    poiName = null,
                    useMetric = false,
                )
            }
        }
        // 1.5 km ≈ 0.93 mi → "0.9 mi" (rounded to 1 decimal)
        composeTestRule.onNodeWithText("0.9 mi").assertIsDisplayed()
    }

    @Test
    fun widget_withLargeSize_andName_rendersName() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                NavWidget(
                    poiDistance = 2.0.kilometers,
                    poiBearingDeg = 0.0,
                    cameraBearing = 0.0,
                    navWidgetSize = 80f,
                    poiName = "Coffee Shop",
                    useMetric = true,
                )
            }
        }
        // Name is shown when navWidgetSize >= 50 and name is non-null.
        composeTestRule.onNodeWithText("Coffee Shop").assertIsDisplayed()
    }

    @Test
    fun widget_withSmallSize_andName_doesNotRenderName() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                NavWidget(
                    poiDistance = 2.0.kilometers,
                    poiBearingDeg = 0.0,
                    cameraBearing = 0.0,
                    navWidgetSize = 40f,
                    poiName = "Coffee Shop",
                    useMetric = true,
                )
            }
        }
        // Name is hidden when navWidgetSize < 50.
        composeTestRule.onNodeWithText("Coffee Shop").assertDoesNotExist()
    }

    @Test
    fun widget_withStopCount_andLargeSize_rendersStopCount() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                NavWidget(
                    poiDistance = 2.0.kilometers,
                    poiBearingDeg = 0.0,
                    cameraBearing = 0.0,
                    navWidgetSize = 80f,
                    poiName = null,
                    useMetric = true,
                    stopCount = 3,
                )
            }
        }
        // 3 stops → "3 stops"
        composeTestRule.onNodeWithText("3 stops").assertIsDisplayed()
    }

    @Test
    fun widget_withZeroStopCount_doesNotRenderStopCount() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                NavWidget(
                    poiDistance = 2.0.kilometers,
                    poiBearingDeg = 0.0,
                    cameraBearing = 0.0,
                    navWidgetSize = 80f,
                    poiName = null,
                    useMetric = true,
                    stopCount = 0,
                )
            }
        }
        // 0 stops → no row
        composeTestRule.onNodeWithText("0 stops").assertDoesNotExist()
    }
}
