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
 * Compose UI tests for [SpeedReadout]. Self-contained: takes a speed + unit
 * toggle + size, renders the number and the unit label.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SpeedReadoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun readout_metricZero_showsZero() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                SpeedReadout(speedMps = 0.0, useMetric = true, speedSize = 24f)
            }
        }
        composeTestRule.onNodeWithText("0").assertIsDisplayed()
        composeTestRule.onNodeWithText("km/h").assertIsDisplayed()
    }

    @Test
    fun readout_metricTenMps_shows36() {
        // 10 m/s = 36 km/h
        composeTestRule.setContent {
            RoadTripRadarTheme {
                SpeedReadout(speedMps = 10.0, useMetric = true, speedSize = 24f)
            }
        }
        composeTestRule.onNodeWithText("36").assertIsDisplayed()
    }

    @Test
    fun readout_imperialTenMps_showsMph() {
        // 10 m/s = 22.3694 mph → 22
        composeTestRule.setContent {
            RoadTripRadarTheme {
                SpeedReadout(speedMps = 10.0, useMetric = false, speedSize = 24f)
            }
        }
        composeTestRule.onNodeWithText("22").assertIsDisplayed()
        composeTestRule.onNodeWithText("mph").assertIsDisplayed()
    }

    @Test
    fun readout_negativeSpeed_clampsToZero() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                SpeedReadout(speedMps = -5.0, useMetric = true, speedSize = 24f)
            }
        }
        // Negative speeds are clamped to 0.
        composeTestRule.onNodeWithText("0").assertIsDisplayed()
    }
}
