package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [SettingsDrawerContent]. The composable is
 * parameterized over all VM-derived state plus the change/commit
 * callbacks for the sliders and switches. The `LanguagePicker` is
 * an internal sub-composable that doesn't touch the VM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsDrawerContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun content_rendersDisplayAndMiscSections() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                SettingsDrawerContent(
                    useGps = false,
                    gpsIconOpacity = 0.5f,
                    onGpsIconOpacityChange = {},
                    onGpsIconOpacityCommit = {},
                    speedSize = 48f,
                    onSpeedSizeChange = {},
                    onSpeedSizeCommit = {},
                    compassWidgetSize = 48f,
                    onCompassWidgetSizeChange = {},
                    onCompassWidgetSizeCommit = {},
                    navWidgetSize = 48f,
                    onNavWidgetSizeChange = {},
                    onNavWidgetSizeCommit = {},
                    keepScreenOn = false,
                    onKeepScreenOnChange = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Display").assertIsDisplayed()
        composeTestRule.onNodeWithText("Speedometer size").assertIsDisplayed()
        composeTestRule.onNodeWithText("Compass size").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nav widget size").assertIsDisplayed()
        // Misc and below are below the test viewport (4 sliders
        // take up most of the available height). assertExists is
        // sufficient — they're rendered, just not in view.
        composeTestRule.onNodeWithText("Misc").assertExists()
        composeTestRule.onNodeWithText("Keep Screen On").assertExists()
        composeTestRule.onNodeWithText("Language").assertExists()
    }

    @Test
    fun content_useGpsTrue_rendersStatusIconOpacity() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                SettingsDrawerContent(
                    useGps = true,
                    gpsIconOpacity = 0.5f,
                    onGpsIconOpacityChange = {},
                    onGpsIconOpacityCommit = {},
                    speedSize = 48f,
                    onSpeedSizeChange = {},
                    onSpeedSizeCommit = {},
                    compassWidgetSize = 48f,
                    onCompassWidgetSizeChange = {},
                    onCompassWidgetSizeCommit = {},
                    navWidgetSize = 48f,
                    onNavWidgetSizeChange = {},
                    onNavWidgetSizeCommit = {},
                    keepScreenOn = false,
                    onKeepScreenOnChange = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Status Icon Opacity").assertIsDisplayed()
        composeTestRule.onNodeWithText("50%").assertIsDisplayed()
    }

    @Test
    fun content_useGpsFalse_hidesStatusIconOpacity() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                SettingsDrawerContent(
                    useGps = false,
                    gpsIconOpacity = 0.5f,
                    onGpsIconOpacityChange = {},
                    onGpsIconOpacityCommit = {},
                    speedSize = 48f,
                    onSpeedSizeChange = {},
                    onSpeedSizeCommit = {},
                    compassWidgetSize = 48f,
                    onCompassWidgetSizeChange = {},
                    onCompassWidgetSizeCommit = {},
                    navWidgetSize = 48f,
                    onNavWidgetSizeChange = {},
                    onNavWidgetSizeCommit = {},
                    keepScreenOn = false,
                    onKeepScreenOnChange = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Status Icon Opacity").assertDoesNotExist()
    }
}
