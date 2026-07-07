package ca.voiditswarranty.roadtripradar.ui.tutorial

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
 * Compose UI tests for [TutorialOverlay]. Parameterized over the
 * active group, step index, and three callbacks (back / skip / next).
 * The anchor-rect plumbing (which positions the caption relative
 * to spotlight cutouts) is exercised indirectly: with the default
 * `LocalTutorialAnchors` (empty), every step renders as a centered
 * full-screen card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TutorialOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun overlay_noActiveGroup_rendersNothing() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TutorialOverlay(
                    activeGroup = null,
                    stepIndex = 0,
                    onBack = {},
                    onSkip = {},
                    onNext = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Skip").assertIsNotDisplayed()
    }

    @Test
    fun overlay_activeGroup_rendersCaptionAndProgress() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TutorialOverlay(
                    activeGroup = TutorialGroup.MAP,
                    stepIndex = 0,
                    onBack = {},
                    onSkip = {},
                    onNext = {},
                )
            }
        }
        // First MAP step — verify the caption renders.
        composeTestRule.onNodeWithText("Skip").assertIsDisplayed()
        composeTestRule.onNodeWithText("Step 1 of 8").assertIsDisplayed()
        composeTestRule.onNodeWithText("Next").assertIsDisplayed()
    }

    @Test
    fun overlay_firstStep_hidesBackButton() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TutorialOverlay(
                    activeGroup = TutorialGroup.MAP,
                    stepIndex = 0,
                    onBack = {},
                    onSkip = {},
                    onNext = {},
                )
            }
        }
        // No Back button on the first step.
        composeTestRule.onNodeWithText("Back").assertDoesNotExist()
    }

    @Test
    fun overlay_middleStep_showsBackButton() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TutorialOverlay(
                    activeGroup = TutorialGroup.MAP,
                    stepIndex = 1,
                    onBack = {},
                    onSkip = {},
                    onNext = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Back").assertIsDisplayed()
        composeTestRule.onNodeWithText("Step 2 of 8").assertIsDisplayed()
    }

    @Test
    fun overlay_lastStep_showsDoneButton() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TutorialOverlay(
                    activeGroup = TutorialGroup.MAP,
                    stepIndex = 7,
                    onBack = {},
                    onSkip = {},
                    onNext = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Got it").assertExists()
        composeTestRule.onNodeWithText("Step 8 of 8").assertIsDisplayed()
    }

    @Test
    fun overlay_nextClick_invokesCallback() {
        var nextCount = 0
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TutorialOverlay(
                    activeGroup = TutorialGroup.MAP,
                    stepIndex = 0,
                    onBack = {},
                    onSkip = {},
                    onNext = { nextCount++ },
                )
            }
        }
        composeTestRule.onNodeWithText("Next").performClick()
        assertTrue(nextCount == 1)
    }

    @Test
    fun overlay_skipClick_invokesCallback() {
        var skipped = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TutorialOverlay(
                    activeGroup = TutorialGroup.MAP,
                    stepIndex = 0,
                    onBack = {},
                    onSkip = { skipped = true },
                    onNext = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Skip").performClick()
        assertTrue(skipped)
    }

    @Test
    fun overlay_backClick_invokesCallback() {
        var backCount = 0
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TutorialOverlay(
                    activeGroup = TutorialGroup.MAP,
                    stepIndex = 1,
                    onBack = { backCount++ },
                    onSkip = {},
                    onNext = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Back").performClick()
        assertTrue(backCount == 1)
    }
}
