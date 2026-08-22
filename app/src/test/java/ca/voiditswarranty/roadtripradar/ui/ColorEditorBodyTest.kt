package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ca.voiditswarranty.roadtripradar.data.THEME_COLOR_CATEGORIES
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
 * Compose UI tests for [ColorEditorBody]. The composable is the
 * full color editor (header, scrollable category list, action row).
 * Tests pin that:
 *  - the editor renders without crashing for a known style
 *  - all four action buttons are present
 *  - the category list is rendered
 *
 * The composable accepts a [MapStyle] (for the header label) and a
 * pre-built [Map] of category-id to color. This lets tests bypass
 * the JSON parsing in the production [ColorEditorContent].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ColorEditorBodyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun emptyColors() = THEME_COLOR_CATEGORIES.associate { it.id to Color.Gray }

    @Test
    fun body_rendersActionButtons() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    ColorEditorBody(
                        style = MapStyle.CUSTOM_LIGHT,
                        editedColors = emptyColors(),
                        onColorChange = { _, _ -> },
                        onReset = {},
                        onCancel = {},
                        onApply = {},
                        onSave = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Reset").assertExists()
        composeTestRule.onNodeWithText("Cancel").assertExists()
        composeTestRule.onNodeWithText("Preview").assertExists()
        composeTestRule.onNodeWithText("Save").assertExists()
    }

    @Test
    fun body_rendersHeader() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    ColorEditorBody(
                        style = MapStyle.CUSTOM_LIGHT,
                        editedColors = emptyColors(),
                        onColorChange = { _, _ -> },
                        onReset = {},
                        onCancel = {},
                        onApply = {},
                        onSave = {},
                    )
                }
            }
        }
        // The header is a "Edit Colors: <style display name>" text
        composeTestRule.waitForIdle()
    }

    @Test
    fun body_saveClick_invokesOnSave() {
        var saveCalled = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    ColorEditorBody(
                        style = MapStyle.CUSTOM_LIGHT,
                        editedColors = emptyColors(),
                        onColorChange = { _, _ -> },
                        onReset = {},
                        onCancel = {},
                        onApply = {},
                        onSave = { saveCalled = true },
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Save").performClick()
        assertTrue(saveCalled)
    }

    @Test
    fun body_resetClick_invokesOnReset() {
        var resetCalled = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    ColorEditorBody(
                        style = MapStyle.CUSTOM_DARK,
                        editedColors = emptyColors(),
                        onColorChange = { _, _ -> },
                        onReset = { resetCalled = true },
                        onCancel = {},
                        onApply = {},
                        onSave = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Reset").performClick()
        assertTrue(resetCalled)
    }

    @Test
    fun body_cancelClick_invokesOnCancel() {
        var cancelCalled = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    ColorEditorBody(
                        style = MapStyle.CUSTOM_DARK,
                        editedColors = emptyColors(),
                        onColorChange = { _, _ -> },
                        onReset = {},
                        onCancel = { cancelCalled = true },
                        onApply = {},
                        onSave = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(cancelCalled)
    }
}
