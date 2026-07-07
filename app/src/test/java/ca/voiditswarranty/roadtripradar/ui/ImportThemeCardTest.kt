package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [ImportThemeCard]. The composable is the
 * import card shown in the theme selector when no custom theme
 * exists for a slot. Clicking it invokes the import flow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImportThemeCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun card_rendersLabel() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    ImportThemeCard(label = "Custom Light", onClick = {})
                }
            }
        }
        composeTestRule.onNodeWithText("Custom Light").assertExists()
    }

    @Test
    fun card_click_invokesCallback() {
        var clicked = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    ImportThemeCard(label = "Custom Dark", onClick = { clicked = true })
                }
            }
        }
        composeTestRule.onNodeWithText("Custom Dark").performClick()
        assertTrue(clicked)
    }

    @Test
    fun card_rendersAnyProvidedLabel() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    ImportThemeCard(label = "Some Custom Label", onClick = {})
                }
            }
        }
        composeTestRule.onNodeWithText("Some Custom Label").assertExists()
    }
}
