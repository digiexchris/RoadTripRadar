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
 * Compose UI tests for [PoiCategoryPicker]. Parameterized over
 * visibility, the enabled-categories set, autostart toggle, and
 * three callbacks (toggle / close / search). The internal
 * [CategoryToggleButton] is exercised via the public composable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PoiCategoryPickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun picker_invisible_rendersNothing() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                PoiCategoryPicker(
                    visible = false,
                    enabledCategories = emptySet(),
                    autostartPoiLoadingOnLaunch = false,
                    onAutostartToggle = {},
                    onToggleCategory = {},
                    onClose = {},
                    onSearchVisibleArea = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Autoload Nearby Places").assertIsNotDisplayed()
    }

    @Test
    fun picker_visible_rendersHeaderAndCount() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                PoiCategoryPicker(
                    visible = true,
                    enabledCategories = setOf("fuel"),
                    autostartPoiLoadingOnLaunch = false,
                    onAutostartToggle = {},
                    onToggleCategory = {},
                    onClose = {},
                    onSearchVisibleArea = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Autoload Nearby Places").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start loading nearby places when the app starts")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("1 of 5").assertIsDisplayed()
    }

    @Test
    fun picker_searchVisibleAreaClick_invokesCallback() {
        var searched = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                PoiCategoryPicker(
                    visible = true,
                    enabledCategories = setOf("fuel"),
                    autostartPoiLoadingOnLaunch = false,
                    onAutostartToggle = {},
                    onToggleCategory = {},
                    onClose = {},
                    onSearchVisibleArea = { searched = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Search Visible Area").performClick()
        assertTrue(searched)
    }

    @Test
    fun picker_cancelClick_invokesCallback() {
        var closed = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                PoiCategoryPicker(
                    visible = true,
                    enabledCategories = setOf("fuel"),
                    autostartPoiLoadingOnLaunch = false,
                    onAutostartToggle = {},
                    onToggleCategory = {},
                    onClose = { closed = true },
                    onSearchVisibleArea = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(closed)
    }

    @Test
    fun picker_categoryToggle_invokesCallback() {
        // The picker renders one toggle button per POI_CATEGORIES
        // entry. The "Gas Station" category is the first one. Find
        // its label and click it; the parent button is the toggle.
        var toggleCount = 0
        composeTestRule.setContent {
            RoadTripRadarTheme {
                PoiCategoryPicker(
                    visible = true,
                    enabledCategories = emptySet(),
                    autostartPoiLoadingOnLaunch = false,
                    onAutostartToggle = {},
                    onToggleCategory = { toggleCount++ },
                    onClose = {},
                    onSearchVisibleArea = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Gas Station").performClick()
        assertTrue(toggleCount >= 1)
    }
}
