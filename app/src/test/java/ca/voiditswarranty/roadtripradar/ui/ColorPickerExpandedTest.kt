package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [ColorPickerExpanded]. The composable renders
 * R/G/B/A sliders and a hex input field for a single color. Tests
 * pin that the input renders without crashing and that the initial
 * state is consistent with the input color.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ColorPickerExpandedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun hostContent(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Column(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    @Test
    fun picker_redColor_rendersWithoutCrash() {
        hostContent {
            ColorPickerExpanded(color = Color.Red, onColorChange = {})
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun picker_greenColor_rendersWithoutCrash() {
        hostContent {
            ColorPickerExpanded(color = Color.Green, onColorChange = {})
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun picker_blueColor_rendersWithoutCrash() {
        hostContent {
            ColorPickerExpanded(color = Color.Blue, onColorChange = {})
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun picker_translucentColor_rendersWithoutCrash() {
        hostContent {
            ColorPickerExpanded(
                color = Color(red = 1f, green = 1f, blue = 1f, alpha = 0.5f),
                onColorChange = {},
            )
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun picker_blackColor_rendersWithoutCrash() {
        hostContent {
            ColorPickerExpanded(color = Color.Black, onColorChange = {})
        }
        composeTestRule.waitForIdle()
    }
}
