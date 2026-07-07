package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ca.voiditswarranty.roadtripradar.model.ChangelogRelease
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [WhatsNewChangelogSheet] and [FullChangelogSheet].
 * Both sheets are parameterized over visibility, releases, and a
 * single dismiss callback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChangelogSheetsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleReleases = listOf(
        ChangelogRelease(
            versionName = "1.16.0",
            versionCode = 17,
            items = listOf("Fix wind icon", "Add auto-route-advance"),
        ),
        ChangelogRelease(
            versionName = "1.15.0",
            versionCode = 16,
            items = listOf("Initial release"),
        ),
    )

    // --- WhatsNewChangelogSheet ---

    @Test
    fun whatsNew_invisible_rendersNothing() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WhatsNewChangelogSheet(
                    visible = false,
                    releases = sampleReleases,
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("What's new").assertIsNotDisplayed()
    }

    @Test
    fun whatsNew_visible_rendersTitleAndReleases() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WhatsNewChangelogSheet(
                    visible = true,
                    releases = sampleReleases,
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("What's new").assertIsDisplayed()
        composeTestRule.onNodeWithText("1.16.0 (17)").assertIsDisplayed()
        // Changelog items render with a bullet prefix, so use
        // substring matching to find the text within "• Fix wind icon".
        composeTestRule.onNodeWithText("Fix wind icon", substring = true)
            .assertIsDisplayed()
        // The button is part of the bottom sheet — assertExists is
        // enough because the test layout can render it below the
        // viewport (the scrollable content above pushes it down).
        composeTestRule.onNodeWithText("Got it").assertExists()
    }

    @Test
    fun whatsNew_visibleEmpty_rendersTitleAndNoReleases() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WhatsNewChangelogSheet(
                    visible = true,
                    releases = emptyList(),
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("What's new").assertIsDisplayed()
        // No version lines, but the Got it button is still there.
        composeTestRule.onNodeWithText("Got it").assertIsDisplayed()
    }

    @Test
    fun whatsNew_gotItClick_invokesCallback() {
        var dismissed = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WhatsNewChangelogSheet(
                    visible = true,
                    releases = sampleReleases,
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Got it").performClick()
        assertTrue(dismissed)
    }

    // --- FullChangelogSheet ---

    @Test
    fun fullChangelog_invisible_rendersNothing() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                FullChangelogSheet(
                    visible = false,
                    releases = sampleReleases,
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Changelog").assertIsNotDisplayed()
    }

    @Test
    fun fullChangelog_visible_rendersTitleAndReleases() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                FullChangelogSheet(
                    visible = true,
                    releases = sampleReleases,
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Changelog").assertIsDisplayed()
        composeTestRule.onNodeWithText("1.16.0 (17)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fix wind icon", substring = true)
            .assertIsDisplayed()
        // The Close button may be pushed below the test viewport by
        // the scrollable content above; assertExists is sufficient.
        composeTestRule.onNodeWithText("Close").assertExists()
    }

    @Test
    fun fullChangelog_visibleEmpty_rendersNoEntries() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                FullChangelogSheet(
                    visible = true,
                    releases = emptyList(),
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Changelog").assertIsDisplayed()
        composeTestRule.onNodeWithText("No changelog entries.").assertIsDisplayed()
    }

    @Test
    fun fullChangelog_closeClick_invokesCallback() {
        var dismissed = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                FullChangelogSheet(
                    visible = true,
                    releases = sampleReleases,
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Close").performClick()
        assertTrue(dismissed)
    }
}
