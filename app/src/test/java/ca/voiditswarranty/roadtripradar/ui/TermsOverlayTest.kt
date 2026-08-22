package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
 * Compose UI tests for [TermsOverlay]. Parameterized over visibility,
 * needsAcceptance, and three callbacks (accept / decline / dismiss).
 * The decline callback is responsible for terminating the Activity
 * (it triggers `finishAffinity` + `System.exit(0)` at the call site);
 * the overlay just invokes it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TermsOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun overlay_invisible_rendersNothing() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TermsOverlay(
                    visible = false,
                    needsAcceptance = true,
                    onAccept = {},
                    onDecline = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Accept").assertIsNotDisplayed()
    }

    @Test
    fun overlay_visibleAcceptanceTrue_rendersAcceptAndDecline() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TermsOverlay(
                    visible = true,
                    needsAcceptance = true,
                    onAccept = {},
                    onDecline = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Accept").assertIsDisplayed()
        composeTestRule.onNodeWithText("Decline").assertIsDisplayed()
    }

    @Test
    fun overlay_visibleAcceptanceFalse_rendersClose() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TermsOverlay(
                    visible = true,
                    needsAcceptance = false,
                    onAccept = {},
                    onDecline = {},
                    onDismiss = {},
                )
            }
        }
        // When the terms are already accepted, there's no Accept/Decline
        // pair — just a Close button to dismiss the read-only view.
        composeTestRule.onNodeWithText("Close").assertIsDisplayed()
        composeTestRule.onNodeWithText("Accept").assertIsNotDisplayed()
    }

    @Test
    fun overlay_acceptButton_disabledAtTop_doesNotInvokeCallback() {
        var accepted = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TermsOverlay(
                    visible = true,
                    needsAcceptance = true,
                    onAccept = { accepted = true },
                    onDecline = {},
                    onDismiss = {},
                )
            }
        }
        // The terms text is taller than the scrollable region, so the
        // Accept button is disabled until the user scrolls to the
        // bottom. A click on the disabled button is a no-op.
        composeTestRule.onNodeWithText("Accept").performClick()
        assertTrue(!accepted)
    }

    @Test
    fun overlay_scrollHint_visibleAtTop() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TermsOverlay(
                    visible = true,
                    needsAcceptance = true,
                    onAccept = {},
                    onDecline = {},
                    onDismiss = {},
                )
            }
        }
        // The "Scroll down to read the rest" hint is shown when the
        // terms are not yet at the bottom.
        composeTestRule.onNodeWithText("Scroll down to read the rest, then you can accept.")
            .assertIsDisplayed()
    }

    @Test
    fun overlay_declineClick_invokesCallback() {
        var declined = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TermsOverlay(
                    visible = true,
                    needsAcceptance = true,
                    onAccept = {},
                    onDecline = { declined = true },
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Decline").performClick()
        assertTrue(declined)
    }

    @Test
    fun overlay_closeClick_invokesDismissCallback() {
        var dismissed = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TermsOverlay(
                    visible = true,
                    needsAcceptance = false,
                    onAccept = {},
                    onDecline = {},
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Close").performClick()
        assertTrue(dismissed)
    }
}
