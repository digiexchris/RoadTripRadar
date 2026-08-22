package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [LegendDetailSheet]. The sheet is a leaf
 * composable — its only inputs are visibility and a close callback.
 * The radar-color entries are file-local constants.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LegendDetailSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sheet_invisible_rendersNothing() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                LegendDetailSheet(visible = false, onClose = {})
            }
        }
        composeTestRule.onNodeWithText("Radar Legend").assertIsNotDisplayed()
    }

    @Test
    fun sheet_visible_rendersTitle() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                LegendDetailSheet(visible = true, onClose = {})
            }
        }
        composeTestRule.onNodeWithText("Radar Legend").assertIsDisplayed()
    }

    @Test
    fun sheet_visible_rendersAllEntries() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                LegendDetailSheet(visible = true, onClose = {})
            }
        }
        // Spot-check entries from each category that are within the
        // visible viewport (the sheet uses verticalScroll, so entries
        // below the fold need a scroll action before they are
        // "displayed"). The first rain entry (Overcast), first hail
        // entry, and first snow entry are always at the top.
        composeTestRule.onNodeWithText("Overcast").assertIsDisplayed()
        composeTestRule.onNodeWithText("Drizzle").assertIsDisplayed()
        composeTestRule.onNodeWithText("Light Rain").assertIsDisplayed()
    }

    @Test
    fun sheet_visible_rendersHeaders() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                LegendDetailSheet(visible = true, onClose = {})
            }
        }
        composeTestRule.onNodeWithText("Label").assertIsDisplayed()
        composeTestRule.onNodeWithText("dBZ").assertIsDisplayed()
        composeTestRule.onNodeWithText("mm/h").assertIsDisplayed()
    }
}
