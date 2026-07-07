package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [CustomThemeActions]. The composable is the
 * full action row for a custom theme: an auto-mode switch, then a
 * row of icon buttons (Edit / Save / Maputnik / Share / Delete)
 * with labels underneath. Tests pin the visible labels and the
 * callback invocations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CustomThemeActionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun hostActions(
        autoEnabled: Boolean = false,
        shareEnabled: Boolean = true,
        onAutoModeChange: (Boolean) -> Unit = {},
        onEditColors: (MapStyle) -> Unit = {},
        onSaveToDownloads: (MapStyle) -> Unit = {},
        onOpenMaputnik: () -> Unit = {},
        onShare: () -> Unit = {},
        onDelete: (MapStyle) -> Unit = {},
    ) {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    CustomThemeActions(
                        style = MapStyle.CUSTOM_LIGHT,
                        autoEnabled = autoEnabled,
                        onAutoModeChange = onAutoModeChange,
                        onEditColors = onEditColors,
                        onSaveToDownloads = onSaveToDownloads,
                        onOpenMaputnik = onOpenMaputnik,
                        onShare = onShare,
                        onDelete = onDelete,
                        shareEnabled = shareEnabled,
                    )
                }
            }
        }
    }

    @Test
    fun actions_rendersAllActionLabels() {
        hostActions()
        composeTestRule.onNodeWithText("Edit").assertExists()
        composeTestRule.onNodeWithText("Save").assertExists()
        composeTestRule.onNodeWithText("Maputnik").assertExists()
        composeTestRule.onNodeWithText("Share").assertExists()
        composeTestRule.onNodeWithText("Delete").assertExists()
    }

    @Test
    fun actions_rendersAutoModeLabel() {
        hostActions()
        composeTestRule.onNodeWithText("Use in Auto mode").assertExists()
    }

    @Test
    fun actions_editColorsClick_invokesCallback() {
        var capturedStyle: MapStyle? = null
        hostActions(onEditColors = { style -> capturedStyle = style })
        composeTestRule.onNodeWithContentDescription("Edit Colors").performClick()
        assertEquals(MapStyle.CUSTOM_LIGHT, capturedStyle)
    }

    @Test
    fun actions_saveToDownloadsClick_invokesCallback() {
        var saveCalled = false
        hostActions(onSaveToDownloads = { _ -> saveCalled = true })
        composeTestRule.onNodeWithContentDescription("Save JSON to Downloads").performClick()
        assertTrue(saveCalled)
    }

    @Test
    fun actions_openMaputnikClick_invokesCallback() {
        var maputnikCalled = false
        hostActions(onOpenMaputnik = { maputnikCalled = true })
        composeTestRule.onNodeWithContentDescription("Open Maputnik").performClick()
        assertTrue(maputnikCalled)
    }

    @Test
    fun actions_shareClick_invokesCallback() {
        var shareCalled = false
        hostActions(shareEnabled = true, onShare = { shareCalled = true })
        composeTestRule.onNodeWithContentDescription("Share").performClick()
        assertTrue(shareCalled)
    }

    @Test
    fun actions_deleteClick_invokesCallback() {
        var capturedStyle: MapStyle? = null
        hostActions(onDelete = { style -> capturedStyle = style })
        composeTestRule.onNodeWithContentDescription("Delete custom theme").performClick()
        assertEquals(MapStyle.CUSTOM_LIGHT, capturedStyle)
    }

    @Test
    fun actions_customDarkStyle_passedToCallbacks() {
        var capturedStyle: MapStyle? = null
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) {
                    CustomThemeActions(
                        style = MapStyle.CUSTOM_DARK,
                        autoEnabled = false,
                        onAutoModeChange = {},
                        onEditColors = { style -> capturedStyle = style },
                        onSaveToDownloads = {},
                        onOpenMaputnik = {},
                        onShare = {},
                        onDelete = {},
                        shareEnabled = true,
                    )
                }
            }
        }
        composeTestRule.onNodeWithContentDescription("Edit Colors").performClick()
        assertEquals(MapStyle.CUSTOM_DARK, capturedStyle)
    }
}
