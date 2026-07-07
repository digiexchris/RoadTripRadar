package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [ColorSwatch]. The composable is a small
 * rounded rectangle filled with a color and outlined. It's used as
 * the visible color indicator in the theme editor rows and the hex
 * input. The tests pin that the swatch renders without crashing for
 * a variety of input colors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ColorSwatchTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun swatch_redColor_renders() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.size(32.dp)) {
                    ColorSwatch(color = Color.Red, modifier = Modifier.size(32.dp))
                }
            }
        }
    }

    @Test
    fun swatch_transparentColor_renders() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.size(32.dp)) {
                    ColorSwatch(
                        color = Color(red = 1f, green = 1f, blue = 1f, alpha = 0f),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }

    @Test
    fun swatch_customSize_renders() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box {
                    ColorSwatch(color = Color.Blue, modifier = Modifier.size(64.dp))
                }
            }
        }
    }
}
