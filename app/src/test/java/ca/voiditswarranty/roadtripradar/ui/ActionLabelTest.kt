package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [ActionLabel]. The composable is the
 * small caption text under each action button in the custom
 * theme action row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ActionLabelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun label_rendersProvidedText() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    ActionLabel(text = "Edit")
                }
            }
        }
        composeTestRule.onNodeWithText("Edit").assertExists()
    }

    @Test
    fun label_rendersCustomText() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    ActionLabel(text = "My Action Label")
                }
            }
        }
        composeTestRule.onNodeWithText("My Action Label").assertExists()
    }
}
