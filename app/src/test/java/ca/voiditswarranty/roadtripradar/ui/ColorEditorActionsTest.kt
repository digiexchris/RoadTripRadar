package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [ColorEditorActions]. The composable is the
 * four-button row at the bottom of the color editor: Reset, Cancel,
 * Preview, Save. Tests pin that each button invokes the right
 * callback when clicked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ColorEditorActionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun hostContent(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Column(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    @Test
    fun actions_resetClick_invokesOnReset() {
        var resetCalled = false
        hostContent {
            ColorEditorActions(
                onReset = { resetCalled = true },
                onCancel = {},
                onApply = {},
                onSave = {},
            )
        }
        composeTestRule.onNodeWithText("Reset").performClick()
        assertTrue(resetCalled)
    }

    @Test
    fun actions_cancelClick_invokesOnCancel() {
        var cancelCalled = false
        hostContent {
            ColorEditorActions(
                onReset = {},
                onCancel = { cancelCalled = true },
                onApply = {},
                onSave = {},
            )
        }
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(cancelCalled)
    }

    @Test
    fun actions_previewClick_invokesOnApply() {
        var applyCalled = false
        hostContent {
            ColorEditorActions(
                onReset = {},
                onCancel = {},
                onApply = { applyCalled = true },
                onSave = {},
            )
        }
        composeTestRule.onNodeWithText("Preview").performClick()
        assertTrue(applyCalled)
    }

    @Test
    fun actions_saveClick_invokesOnSave() {
        var saveCalled = false
        hostContent {
            ColorEditorActions(
                onReset = {},
                onCancel = {},
                onApply = {},
                onSave = { saveCalled = true },
            )
        }
        composeTestRule.onNodeWithText("Save").performClick()
        assertTrue(saveCalled)
    }

    @Test
    fun actions_onlyResetClicked_doesNotInvokeOthers() {
        var resetCount = 0
        var cancelCount = 0
        var applyCount = 0
        var saveCount = 0
        hostContent {
            ColorEditorActions(
                onReset = { resetCount++ },
                onCancel = { cancelCount++ },
                onApply = { applyCount++ },
                onSave = { saveCount++ },
            )
        }
        composeTestRule.onNodeWithText("Reset").performClick()
        assertEquals(1, resetCount)
        assertEquals(0, cancelCount)
        assertEquals(0, applyCount)
        assertEquals(0, saveCount)
    }
}
