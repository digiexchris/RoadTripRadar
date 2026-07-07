package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [BuiltInThemeActions]. The composable is the
 * action row shown beneath the theme card for built-in themes
 * (LIBERTY, DARK, COLOR_DARK). It offers "Customize in Maputnik"
 * and "Use as Custom Light/Dark Base" actions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BuiltInThemeActionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun actions_liberty_rendersUseAsCustomLightButton() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    BuiltInThemeActions(
                        style = MapStyle.LIBERTY,
                        customTargetExists = false,
                        onOpenMaputnik = {},
                        onUseAsCustomBase = {},
                    )
                }
            }
        }
        // Light built-in: button says "Use as Custom Light Base" since none exists
        composeTestRule.onNodeWithText("Use as Custom Light Base").assertExists()
    }

    @Test
    fun actions_libertyWithExistingCustom_rendersOverwriteButton() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    BuiltInThemeActions(
                        style = MapStyle.LIBERTY,
                        customTargetExists = true,
                        onOpenMaputnik = {},
                        onUseAsCustomBase = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Overwrite Custom Light").assertExists()
    }

    @Test
    fun actions_colorDark_rendersUseAsCustomDarkBase() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    BuiltInThemeActions(
                        style = MapStyle.COLOR_DARK,
                        customTargetExists = false,
                        onOpenMaputnik = {},
                        onUseAsCustomBase = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Use as Custom Dark Base").assertExists()
    }

    @Test
    fun actions_colorDarkWithExistingCustom_rendersOverwrite() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    BuiltInThemeActions(
                        style = MapStyle.COLOR_DARK,
                        customTargetExists = true,
                        onOpenMaputnik = {},
                        onUseAsCustomBase = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Overwrite Custom Dark").assertExists()
    }

    @Test
    fun actions_maputnikClick_invokesCallback() {
        var openedUrl: String? = null
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    BuiltInThemeActions(
                        style = MapStyle.LIBERTY,
                        customTargetExists = false,
                        onOpenMaputnik = { url -> openedUrl = url },
                        onUseAsCustomBase = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Customize in Maputnik").performClick()
        assertTrue(openedUrl != null)
    }

    @Test
    fun actions_useAsBaseClick_invokesCallbackWithTarget() {
        var capturedTarget: MapStyle? = null
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    BuiltInThemeActions(
                        style = MapStyle.LIBERTY,
                        customTargetExists = false,
                        onOpenMaputnik = {},
                        onUseAsCustomBase = { target -> capturedTarget = target },
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Use as Custom Light Base").performClick()
        assertEquals(MapStyle.CUSTOM_LIGHT, capturedTarget)
    }

    @Test
    fun actions_darkStyle_passesCustomDarkTarget() {
        var capturedTarget: MapStyle? = null
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    BuiltInThemeActions(
                        style = MapStyle.COLOR_DARK,
                        customTargetExists = false,
                        onOpenMaputnik = {},
                        onUseAsCustomBase = { target -> capturedTarget = target },
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Use as Custom Dark Base").performClick()
        assertEquals(MapStyle.CUSTOM_DARK, capturedTarget)
    }
}
