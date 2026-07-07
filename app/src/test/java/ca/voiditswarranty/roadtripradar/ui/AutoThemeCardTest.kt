package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * Compose UI tests for [AutoThemeCard]. The composable is the AUTO
 * card in the theme selector's horizontal scroll. It shows a
 * light-to-dark gradient swatch and the "Auto" label. Selecting it
 * shows a check mark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoThemeCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun card_rendersLabel() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    AutoThemeCard(selected = false, onClick = {})
                }
            }
        }
        composeTestRule.onNodeWithText("Auto").assertExists()
    }

    @Test
    fun card_click_invokesCallback() {
        var clicked = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    AutoThemeCard(selected = false, onClick = { clicked = true })
                }
            }
        }
        composeTestRule.onNodeWithText("Auto").performClick()
        assertTrue(clicked)
    }

    @Test
    fun card_selected_renders() {
        // Just assert the composable renders without crashing for the
        // selected state (the check mark icon has no text we can pin).
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    AutoThemeCard(selected = true, onClick = {})
                }
            }
        }
        composeTestRule.onNodeWithText("Auto").assertExists()
    }
}
