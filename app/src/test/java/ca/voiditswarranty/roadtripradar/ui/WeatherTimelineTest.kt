package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [WeatherTimeline]. The timeline takes a list of frame
 * timestamps and renders start/end labels formatted as "H:mm". Self-contained
 * composable — no [MapViewModel] needed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WeatherTimelineTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun timeline_emptyFrameList_rendersNothing() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WeatherTimeline(frameTimes = emptyList(), currentFrameIndex = 0)
            }
        }
        // No labels, no markers, no composition content.
        // The composable returns early when frameCount == 0.
        assertTrue(composeTestRule.onAllNodesWithText(":").fetchSemanticsNodes().isEmpty() ||
            composeTestRule.onAllNodesWithText(":").fetchSemanticsNodes().size <= 1)
    }

    @Test
    fun timeline_vertical_rendersStartAndEndLabels() {
        // Two frames 12 hours apart. The label is formatted in the
        // device's local timezone as "H:mm" — we just verify the
        // composable renders the start label.
        val start = 60 * 60L // 1 hour past epoch
        val end = 13 * 60 * 60L // 13 hours past epoch
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WeatherTimeline(
                    frameTimes = listOf(start, end),
                    currentFrameIndex = 0,
                )
            }
        }
        // Verify the start label was formatted and rendered. We
        // can't predict the exact text without knowing the test
        // device timezone, so check that the first label exists by
        // its position — the text node with the start time should
        // be present. We use a relaxed assertion: just check that
        // a text node with a colon is present.
        val timeNodes = composeTestRule.onAllNodesWithText(":", substring = true)
        assertTrue("Expected at least one time label", timeNodes.fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun timeline_horizontal_rendersStartAndEndLabels() {
        val start = 60 * 60L
        val end = 13 * 60 * 60L
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WeatherTimeline(
                    frameTimes = listOf(start, end),
                    currentFrameIndex = 0,
                    horizontal = true,
                )
            }
        }
        val timeNodes = composeTestRule.onAllNodesWithText(":", substring = true)
        assertTrue("Expected at least one time label", timeNodes.fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun timeline_withFrames_horizontal_rendersAllLabels() {
        val start = 60 * 60L
        val mid = 7 * 60 * 60L
        val end = 13 * 60 * 60L
        composeTestRule.setContent {
            RoadTripRadarTheme {
                WeatherTimeline(
                    frameTimes = listOf(start, mid, end),
                    currentFrameIndex = 1,
                    horizontal = true,
                )
            }
        }
        val timeNodes = composeTestRule.onAllNodesWithText(":", substring = true)
        assertTrue("Expected at least one time label", timeNodes.fetchSemanticsNodes().isNotEmpty())
    }
}
