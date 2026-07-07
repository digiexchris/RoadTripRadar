package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import ca.voiditswarranty.roadtripradar.model.SearchResult
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.units.extensions.kilometers
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [PoiSearchDialog]. Parameterized over the same
 * inputs the VM would supply: visibility, query, isSearching, results,
 * useMetric, plus four callbacks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PoiSearchDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dialog_invisible_rendersNothing() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                PoiSearchDialog(
                    visible = false,
                    onClose = {},
                    query = "",
                    onQueryChange = {},
                    isSearching = false,
                    results = emptyList(),
                    useMetric = true,
                    onSelectResult = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Search Location").assertIsNotDisplayed()
    }

    @Test
    fun dialog_visible_rendersTitleAndSubtitle() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                PoiSearchDialog(
                    visible = true,
                    onClose = {},
                    query = "",
                    onQueryChange = {},
                    isSearching = false,
                    results = emptyList(),
                    useMetric = true,
                    onSelectResult = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Search Location").assertIsDisplayed()
        composeTestRule.onNodeWithText("Searching within the visible map area")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun dialog_isSearchingTrue_rendersSearchingLabel() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                PoiSearchDialog(
                    visible = true,
                    onClose = {},
                    query = "Ott",
                    onQueryChange = {},
                    isSearching = true,
                    results = emptyList(),
                    useMetric = true,
                    onSelectResult = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Searching…").assertIsDisplayed()
    }

    @Test
    fun dialog_queryFieldChange_invokesCallback() {
        var captured: String? = null
        composeTestRule.setContent {
            RoadTripRadarTheme {
                PoiSearchDialog(
                    visible = true,
                    onClose = {},
                    query = "",
                    onQueryChange = { captured = it },
                    isSearching = false,
                    results = emptyList(),
                    useMetric = true,
                    onSelectResult = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Address or place name").performTextInput("Toronto")
        assertEquals("Toronto", captured)
    }

    @Test
    fun dialog_resultsRendered_showsNameAndSubtitle() {
        val result = SearchResult(
            name = "Toronto",
            subtitle = "Ontario, Canada",
            position = Position(longitude = -79.3832, latitude = 43.6532),
            distance = 1.5.kilometers,
        )
        composeTestRule.setContent {
            RoadTripRadarTheme {
                PoiSearchDialog(
                    visible = true,
                    onClose = {},
                    query = "Tor",
                    onQueryChange = {},
                    isSearching = false,
                    results = listOf(result),
                    useMetric = true,
                    onSelectResult = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Toronto").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ontario, Canada").assertIsDisplayed()
    }

    @Test
    fun dialog_resultClick_invokesSelectCallback() {
        val result = SearchResult(
            name = "Toronto",
            subtitle = "Ontario, Canada",
            position = Position(longitude = -79.3832, latitude = 43.6532),
        )
        var selected: SearchResult? = null
        composeTestRule.setContent {
            RoadTripRadarTheme {
                PoiSearchDialog(
                    visible = true,
                    onClose = {},
                    query = "Tor",
                    onQueryChange = {},
                    isSearching = false,
                    results = listOf(result),
                    useMetric = true,
                    onSelectResult = { selected = it },
                )
            }
        }
        composeTestRule.onNodeWithText("Toronto").performClick()
        assertEquals(result, selected)
    }

    @Test
    fun dialog_cancelClick_invokesClose() {
        var closed = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                PoiSearchDialog(
                    visible = true,
                    onClose = { closed = true },
                    query = "",
                    onQueryChange = {},
                    isSearching = false,
                    results = emptyList(),
                    useMetric = true,
                    onSelectResult = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(closed)
    }

    @Test
    fun dialog_resultWithDistance_metricUnder1km_showsMeters() {
        val result = SearchResult(
            name = "Coffee Shop",
            subtitle = "",
            position = Position(longitude = -79.0, latitude = 43.0),
            distance = 0.5.kilometers,
        )
        composeTestRule.setContent {
            RoadTripRadarTheme {
                PoiSearchDialog(
                    visible = true,
                    onClose = {},
                    query = "",
                    onQueryChange = {},
                    isSearching = false,
                    results = listOf(result),
                    useMetric = true,
                    onSelectResult = {},
                )
            }
        }
        composeTestRule.onNodeWithText("500 m").assertIsDisplayed()
    }

    @Test
    fun dialog_resultWithDistance_metricOver1km_showsKm() {
        val result = SearchResult(
            name = "Coffee Shop",
            subtitle = "",
            position = Position(longitude = -79.0, latitude = 43.0),
            distance = 1.5.kilometers,
        )
        composeTestRule.setContent {
            RoadTripRadarTheme {
                PoiSearchDialog(
                    visible = true,
                    onClose = {},
                    query = "",
                    onQueryChange = {},
                    isSearching = false,
                    results = listOf(result),
                    useMetric = true,
                    onSelectResult = {},
                )
            }
        }
        composeTestRule.onNodeWithText("1.5 km").assertIsDisplayed()
    }
}
