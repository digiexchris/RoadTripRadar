package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ca.voiditswarranty.roadtripradar.model.NetworkStatus
import ca.voiditswarranty.roadtripradar.model.NetworkTransport
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [RecenterFab], [RecenterTextButton], [RetryFailedButton],
 * [GpsStatusIcon], and [NetworkStatusIcon]. The FABs are parameterized over their
 * inputs (visibility flags, click handlers) — no [MapViewModel] needed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapFabsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun recenterFab_withLocationNotTracking_isDisplayed() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                RecenterFab(
                    hasLocation = true,
                    isTrackingCamera = false,
                    onRecenter = {},
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Re-center on location")
            .assertIsDisplayed()
    }

    @Test
    fun recenterFab_withoutLocation_isNotDisplayed() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                RecenterFab(
                    hasLocation = false,
                    isTrackingCamera = false,
                    onRecenter = {},
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Re-center on location")
            .assertIsNotDisplayed()
    }

    @Test
    fun recenterFab_whenTrackingCamera_isNotDisplayed() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                RecenterFab(
                    hasLocation = true,
                    isTrackingCamera = true,
                    onRecenter = {},
                )
            }
        }
        // When the camera is already tracking, the FAB is hidden.
        composeTestRule.onNodeWithContentDescription("Re-center on location")
            .assertIsNotDisplayed()
    }

    @Test
    fun recenterFab_click_invokesCallback() {
        var clicked = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                RecenterFab(
                    hasLocation = true,
                    isTrackingCamera = false,
                    onRecenter = { clicked = true },
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Re-center on location")
            .performClick()
        assertTrue(clicked)
    }

    @Test
    fun recenterTextButton_withLocation_isDisplayed() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                RecenterTextButton(
                    hasLocation = true,
                    isTrackingCamera = false,
                    onRecenter = {},
                )
            }
        }
        // String resource for the recenter text.
        composeTestRule.onNodeWithText("Recenter").assertIsDisplayed()
    }

    @Test
    fun recenterTextButton_withoutLocation_isNotDisplayed() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                RecenterTextButton(
                    hasLocation = false,
                    isTrackingCamera = false,
                    onRecenter = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Recenter").assertIsNotDisplayed()
    }

    @Test
    fun retryFailedButton_withFailedCells_isDisplayed() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                RetryFailedButton(
                    hasFailedCells = true,
                    onRetry = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Retry Failed").assertIsDisplayed()
    }

    @Test
    fun retryFailedButton_withoutFailedCells_isNotDisplayed() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                RetryFailedButton(
                    hasFailedCells = false,
                    onRetry = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Retry Failed").assertIsNotDisplayed()
    }

    @Test
    fun retryFailedButton_click_invokesCallback() {
        var clicked = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                RetryFailedButton(
                    hasFailedCells = true,
                    onRetry = { clicked = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Retry Failed").performClick()
        assertTrue(clicked)
    }

    @Test
    fun gpsStatusIcon_withFix_usesFixAcquiredDescription() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                GpsStatusIcon(hasGpsFix = true)
            }
        }
        composeTestRule.onNodeWithContentDescription("GPS fix acquired")
            .assertIsDisplayed()
    }

    @Test
    fun gpsStatusIcon_withoutFix_usesWaitingDescription() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                GpsStatusIcon(hasGpsFix = false)
            }
        }
        composeTestRule.onNodeWithContentDescription("Waiting for GPS fix")
            .assertIsDisplayed()
    }

    @Test
    fun networkStatusIcon_disconnected_usesPublicOff() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                NetworkStatusIcon(
                    status = NetworkStatus(connected = false, validated = false, transport = null),
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("No network").assertIsDisplayed()
    }

    @Test
    fun networkStatusIcon_wifiValidated_usesWifiDescription() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                NetworkStatusIcon(
                    status = NetworkStatus(connected = true, validated = true, transport = NetworkTransport.WIFI),
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("WiFi").assertIsDisplayed()
    }

    @Test
    fun networkStatusIcon_cellularValidated_usesCellularDescription() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                NetworkStatusIcon(
                    status = NetworkStatus(connected = true, validated = true, transport = NetworkTransport.CELLULAR),
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Cellular").assertIsDisplayed()
    }
}
