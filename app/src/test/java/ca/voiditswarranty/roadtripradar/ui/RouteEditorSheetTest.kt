package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * Compose UI tests for [RouteEditorSheet]. The sheet is parameterized
 * over visibility, waypoints, activeWaypointId, and callbacks for
 * set-active / remove / move-commit / clear-route / close. The
 * AndroidView-backed RecyclerView (which renders the waypoint rows)
 * is not directly testable in a JVM unit test; the tests focus on
 * the title, the clear-route flow, and the confirmation dialog.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RouteEditorSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sheet_invisible_rendersNothing() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                RouteEditorSheet(
                    visible = false,
                    waypoints = emptyList(),
                    activeWaypointId = null,
                    onSetActive = {},
                    onRemove = {},
                    onMoveCommit = { _, _ -> },
                    onClearRoute = {},
                    onClose = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Route").assertIsNotDisplayed()
    }

    @Test
    fun sheet_visible_rendersTitle() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                RouteEditorSheet(
                    visible = true,
                    waypoints = emptyList(),
                    activeWaypointId = null,
                    onSetActive = {},
                    onRemove = {},
                    onMoveCommit = { _, _ -> },
                    onClearRoute = {},
                    onClose = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Route").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear Route").assertIsDisplayed()
    }

    @Test
    fun sheet_clearButtonClick_opensConfirmDialog() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                RouteEditorSheet(
                    visible = true,
                    waypoints = emptyList(),
                    activeWaypointId = null,
                    onSetActive = {},
                    onRemove = {},
                    onMoveCommit = { _, _ -> },
                    onClearRoute = {},
                    onClose = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Clear Route").performClick()
        // The confirm dialog appears with the question text.
        composeTestRule.onNodeWithText("Remove all waypoints?").assertIsDisplayed()
    }

    @Test
    fun sheet_confirmClearClick_invokesCallback() {
        var cleared = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                RouteEditorSheet(
                    visible = true,
                    waypoints = emptyList(),
                    activeWaypointId = null,
                    onSetActive = {},
                    onRemove = {},
                    onMoveCommit = { _, _ -> },
                    onClearRoute = { cleared = true },
                    onClose = {},
                )
            }
        }
        // Open the confirm dialog.
        composeTestRule.onNodeWithText("Clear Route").performClick()
        // Three "Clear Route" nodes now: sheet button, dialog title,
        // dialog confirm. Use filterToOne to grab the one with a
        // Role.Button (the confirm TextButton), or fall back to
        // the last index. The confirm is index 2.
        composeTestRule.onAllNodesWithText("Clear Route")[2].performClick()
        assertTrue(cleared)
    }

    @Test
    fun sheet_cancelClick_doesNotInvokeCallback() {
        var cleared = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                RouteEditorSheet(
                    visible = true,
                    waypoints = emptyList(),
                    activeWaypointId = null,
                    onSetActive = {},
                    onRemove = {},
                    onMoveCommit = { _, _ -> },
                    onClearRoute = { cleared = true },
                    onClose = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Clear Route").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()
        // After cancel, the dialog dismisses but clearRoute is not
        // invoked. The first "Clear Route" button is back to being
        // the only one in the tree.
        assertTrue(!cleared)
    }
}
