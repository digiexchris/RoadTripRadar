package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [BuiltInThemeCard]. The composable is one
 * card in the theme selector's horizontal scroll for a built-in
 * theme. Shows a swatch in the theme's representative color, the
 * theme name, and a check mark when selected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BuiltInThemeCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun card_liberty_rendersLabel() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    BuiltInThemeCard(
                        style = MapStyle.LIBERTY,
                        swatchColor = Color(0xFFF5F1EC),
                        selected = false,
                        onClick = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Liberty").assertExists()
    }

    @Test
    fun card_dark_rendersLabel() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    BuiltInThemeCard(
                        style = MapStyle.DARK,
                        swatchColor = Color(0xFF1A1A2E),
                        selected = false,
                        onClick = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Dark").assertExists()
    }

    @Test
    fun card_colorDark_rendersLabel() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    BuiltInThemeCard(
                        style = MapStyle.COLOR_DARK,
                        swatchColor = Color(0xFF1A1A2E),
                        selected = false,
                        onClick = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Dark (Small Roads)").assertExists()
    }

    @Test
    fun card_click_invokesCallback() {
        var clicked = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    BuiltInThemeCard(
                        style = MapStyle.LIBERTY,
                        swatchColor = Color(0xFFF5F1EC),
                        selected = false,
                        onClick = { clicked = true },
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Liberty").performClick()
        assertTrue(clicked)
    }

    @Test
    fun card_customLight_rendersLabel() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    BuiltInThemeCard(
                        style = MapStyle.CUSTOM_LIGHT,
                        swatchColor = Color(0xFFEEEEEE),
                        selected = false,
                        onClick = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Custom Light").assertExists()
    }

    @Test
    fun card_selected_renders() {
        // Render with selected=true and assert it doesn't crash.
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    BuiltInThemeCard(
                        style = MapStyle.LIBERTY,
                        swatchColor = Color(0xFFF5F1EC),
                        selected = true,
                        onClick = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Liberty").assertExists()
    }
}
