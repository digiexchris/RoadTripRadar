package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ca.voiditswarranty.roadtripradar.data.InsertPosition
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.spatialk.geojson.Position
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [TappedPoiPopup]. Parameterized over the
 * tapped POI, its origin (NearbyPoi / LongPress / Search /
 * NavigationTarget), the current waypoints list, the user position
 * for distance computation, and the metric/imperial toggle. Callbacks
 * for dismiss / back / center / add-waypoint / remove-navigation-target
 * are passed in as primitive lambdas — the call site in `MapScreen`
 * wires the VM properties through. The internal `EmptyRouteActions` /
 * `ExistingRouteActions` / `NavigationTargetActions` / `OverflowMenu`
 * are exercised through the public composable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TappedPoiPopupTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun samplePoi() = MapViewModel.TappedPoiInfo(
        name = "Sample Cafe",
        subtitle = "123 Main St",
        categoryLabel = "Cafe",
        iconName = "cafe",
        position = Position(latitude = 45.0, longitude = -75.0),
        openingHours = null,
    )

    @Test
    fun popup_noPoi_rendersNothing() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TappedPoiPopup(
                    poi = null,
                    origin = null,
                    waypoints = emptyList(),
                    userPosition = null,
                    useMetric = true,
                    onDismiss = {},
                    onBack = {},
                    onCenterOnMap = {},
                    onAddWaypoint = { _ -> },
                    onRemoveNavigationTarget = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Sample Cafe").assertDoesNotExist()
    }

    @Test
    fun popup_withPoi_rendersNameAndCategory() {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TappedPoiPopup(
                    poi = samplePoi(),
                    origin = MapViewModel.TappedPoiOrigin.NearbyPoi,
                    waypoints = emptyList(),
                    userPosition = null,
                    useMetric = true,
                    onDismiss = {},
                    onBack = {},
                    onCenterOnMap = {},
                    onAddWaypoint = { _ -> },
                    onRemoveNavigationTarget = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Sample Cafe").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cafe").assertIsDisplayed()
        composeTestRule.onNodeWithText("123 Main St").assertIsDisplayed()
    }

    @Test
    fun popup_closeButtonClick_invokesDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TappedPoiPopup(
                    poi = samplePoi(),
                    origin = MapViewModel.TappedPoiOrigin.NearbyPoi,
                    waypoints = emptyList(),
                    userPosition = null,
                    useMetric = true,
                    onDismiss = { dismissed = true },
                    onBack = {},
                    onCenterOnMap = {},
                    onAddWaypoint = { _ -> },
                    onRemoveNavigationTarget = {},
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun popup_navigationTarget_showsRemoveButton() {
        var removed = false
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TappedPoiPopup(
                    poi = samplePoi(),
                    origin = MapViewModel.TappedPoiOrigin.NavigationTarget,
                    waypoints = emptyList(),
                    userPosition = null,
                    useMetric = true,
                    onDismiss = {},
                    onBack = {},
                    onCenterOnMap = {},
                    onAddWaypoint = { _ -> },
                    onRemoveNavigationTarget = { removed = true },
                )
            }
        }
        // NavigationTarget gets Center + Remove buttons, NOT Navigate.
        composeTestRule.onNodeWithText("Remove").assertIsDisplayed()
        composeTestRule.onNodeWithText("Navigate Here").assertIsNotDisplayed()
        composeTestRule.onNodeWithText("Remove").performClick()
        assertTrue(removed)
    }

    @Test
    fun popup_searchOrigin_emptyRoute_showsBackAndNavigate() {
        var back = false
        var addedTo: InsertPosition? = null
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TappedPoiPopup(
                    poi = samplePoi(),
                    origin = MapViewModel.TappedPoiOrigin.Search,
                    waypoints = emptyList(),
                    userPosition = null,
                    useMetric = true,
                    onDismiss = {},
                    onBack = { back = true },
                    onCenterOnMap = {},
                    onAddWaypoint = { pos -> addedTo = pos },
                    onRemoveNavigationTarget = {},
                )
            }
        }
        // Back + Navigate (no Center)
        composeTestRule.onNodeWithText("Back").assertIsDisplayed()
        composeTestRule.onNodeWithText("Navigate Here").assertIsDisplayed()
        composeTestRule.onNodeWithText("Back").performClick()
        assertTrue(back)
        composeTestRule.onNodeWithText("Navigate Here").performClick()
        assertEquals(InsertPosition.End, addedTo)
    }

    @Test
    fun popup_searchOrigin_existingRoute_showsBackAndExistingActions() {
        var back = false
        val waypoint = ca.voiditswarranty.roadtripradar.data.Waypoint(
            id = "wp1", name = "Origin", lat = 44.0, lon = -74.0, subtitle = null,
        )
        val waypoint2 = ca.voiditswarranty.roadtripradar.data.Waypoint(
            id = "wp2", name = "Destination", lat = 45.0, lon = -75.0, subtitle = null,
        )
        composeTestRule.setContent {
            RoadTripRadarTheme {
                TappedPoiPopup(
                    poi = samplePoi(),
                    origin = MapViewModel.TappedPoiOrigin.Search,
                    waypoints = listOf(waypoint, waypoint2),
                    userPosition = null,
                    useMetric = true,
                    onDismiss = {},
                    onBack = { back = true },
                    onCenterOnMap = {},
                    onAddWaypoint = { _ -> },
                    onRemoveNavigationTarget = {},
                )
            }
        }
        // Back icon button + existing route actions (size >= 2 branch).
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add to start").assertExists()
        composeTestRule.onNodeWithText("Insert before final stop").assertExists()
        composeTestRule.onNodeWithText("Add to end").assertExists()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(back)
    }
}
