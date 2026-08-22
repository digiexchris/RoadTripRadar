package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [GloveFriendlySlider]. The slider has a
 * [LocalSliderDragCallback] CompositionLocal that's used by the
 * Actions drawer to show a floating indicator while dragging.
 * Since Robolectric can't easily simulate slider drag events, these
 * tests verify the slider composes cleanly and the sizing constants
 * are exposed correctly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GloveFriendlySliderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun slider_rendersWithoutCrash() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                GloveFriendlySlider(
                    value = 0.5f,
                    onValueChange = {},
                )
            }
        }
        // Composition completes successfully — the test passes if no
        // exception is thrown.
    }

    @Test
    fun slider_customRange_rendersWithoutCrash() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                GloveFriendlySlider(
                    value = 5f,
                    onValueChange = {},
                    valueRange = 0f..10f,
                )
            }
        }
        // Composition completes successfully.
    }

    @Test
    fun slider_disabled_rendersWithoutCrash() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                GloveFriendlySlider(
                    value = 0.3f,
                    onValueChange = {},
                    enabled = false,
                )
            }
        }
        // Disabled slider still composes.
    }

    @Test
    fun slider_withDragCallback_rendersWithoutCrash() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalSliderDragCallback provides { _, _ -> },
            ) {
                RoadTripRadarTheme {
                    GloveFriendlySlider(
                        value = 0.42f,
                        onValueChange = {},
                    )
                }
            }
        }
        // Composition with the callback wired completes cleanly.
    }

    @Test
    fun drawerControlSizing_segmentedButtonHeight_isEighty() {
        // Glove-friendly segments are 80dp (vs default ~40dp).
        org.junit.Assert.assertEquals(
            80f,
            DrawerControlSizing.segmentedButtonHeight.value,
            0.1f,
        )
    }
}
