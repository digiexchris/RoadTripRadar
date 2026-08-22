package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [WeatherLegend]. The legend renders the RainViewer color
 * scheme (11 entries across 3 groups: rain, hail, snow). It is a self-contained
 * composable — no [MapViewModel] needed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WeatherLegendTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun legend_vertical_rendersAllEntries() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WeatherLegend(horizontal = false)
            }
        }
        // 11 distinct labels across 3 groups.
        composeTestRule.onAllNodesWithText("Overcast").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Drizzle").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Rain?").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Rain.").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Rain!!").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Hail?").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Hail.").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Hail!!").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Snow?").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Snow.").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Snow!!").assertCountEquals(1)
    }

    @Test
    fun legend_horizontal_rendersAllEntries() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WeatherLegend(horizontal = true)
            }
        }
        // Same 11 labels in horizontal layout.
        composeTestRule.onAllNodesWithText("Overcast").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Drizzle").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Rain?").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Rain.").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Rain!!").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Hail?").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Hail.").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Hail!!").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Snow?").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Snow.").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Snow!!").assertCountEquals(1)
    }
}
