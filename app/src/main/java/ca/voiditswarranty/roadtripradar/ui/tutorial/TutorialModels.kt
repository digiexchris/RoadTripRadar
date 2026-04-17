package ca.voiditswarranty.roadtripradar.ui.tutorial

import androidx.annotation.StringRes
import ca.voiditswarranty.roadtripradar.R

/**
 * Groups of tutorial steps that fire at distinct interaction points and are persisted
 * independently in [ca.voiditswarranty.roadtripradar.data.PreferencesRepository.completedTutorialGroups].
 */
enum class TutorialGroup {
    /** Map-screen gestures and controls; fires after T&C acceptance on first launch. */
    MAP,

    /** Quick-actions drawer main page; fires the first time the drawer is opened. */
    MENU_MAIN,
}

/**
 * Stable id of a registered anchor (a piece of UI that can be spotlighted).
 * `null` means a full-screen step (no spotlight cutout).
 */
typealias TutorialAnchorId = String?

/**
 * A single tutorial coachmark step.
 *
 * @param anchorId Id previously registered via `Modifier.tutorialAnchor`; `null` for a
 *                 full-screen step that only shows the caption card.
 * @param spotlightShape Shape of the punched-out spotlight; ignored when [anchorId] is `null`.
 */
data class TutorialStep(
    val anchorId: TutorialAnchorId,
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
    val spotlightShape: SpotlightShape = SpotlightShape.ROUNDED_RECT,
)

enum class SpotlightShape { ROUNDED_RECT, CIRCLE }

// ---- Anchor ids ----

object TutorialAnchors {
    const val SPEED = "speed"
    const val COMPASS = "compass"
    const val MAP_BODY = "map_body"
    const val ZOOM_OUT = "zoom_out"
    const val ZOOM_IN = "zoom_in"
    const val WEATHER_FAB = "weather_fab"
    const val MENU_FAB = "menu_fab"

    // MENU_MAIN anchors are the primary drawer cells.
    const val MENU_WEATHER_TOGGLE = "menu_weather_toggle"
    const val MENU_NORTH_UP = "menu_north_up"
    const val MENU_NEARBY_PLACES = "menu_nearby_places"
    const val MENU_LOCATION_SEARCH = "menu_location_search"
    const val MENU_SUBMENU_MAP = "menu_submenu_map"
    const val MENU_SUBMENU_WEATHER = "menu_submenu_weather"
    const val MENU_SUBMENU_SYSTEM = "menu_submenu_system"
    const val MENU_SUBMENU_HELP = "menu_submenu_help"
}

// ---- Step definitions ----

val MAP_TUTORIAL_STEPS: List<TutorialStep> = listOf(
    TutorialStep(
        anchorId = null,
        titleRes = R.string.tutorial_map_welcome_title,
        bodyRes = R.string.tutorial_map_welcome_body,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.SPEED,
        titleRes = R.string.tutorial_map_speed_title,
        bodyRes = R.string.tutorial_map_speed_body,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.COMPASS,
        titleRes = R.string.tutorial_map_compass_title,
        bodyRes = R.string.tutorial_map_compass_body,
        spotlightShape = SpotlightShape.CIRCLE,
    ),
    TutorialStep(
        anchorId = null,
        titleRes = R.string.tutorial_map_drop_pin_title,
        bodyRes = R.string.tutorial_map_drop_pin_body,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.ZOOM_OUT,
        titleRes = R.string.tutorial_map_zoom_out_title,
        bodyRes = R.string.tutorial_map_zoom_out_body,
        spotlightShape = SpotlightShape.CIRCLE,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.ZOOM_IN,
        titleRes = R.string.tutorial_map_zoom_in_title,
        bodyRes = R.string.tutorial_map_zoom_in_body,
        spotlightShape = SpotlightShape.CIRCLE,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.WEATHER_FAB,
        titleRes = R.string.tutorial_map_weather_title,
        bodyRes = R.string.tutorial_map_weather_body,
        spotlightShape = SpotlightShape.CIRCLE,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.MENU_FAB,
        titleRes = R.string.tutorial_map_menu_title,
        bodyRes = R.string.tutorial_map_menu_body,
        spotlightShape = SpotlightShape.CIRCLE,
    ),
)

val MENU_MAIN_TUTORIAL_STEPS: List<TutorialStep> = listOf(
    TutorialStep(
        anchorId = TutorialAnchors.MENU_WEATHER_TOGGLE,
        titleRes = R.string.tutorial_menu_weather_toggle_title,
        bodyRes = R.string.tutorial_menu_weather_toggle_body,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.MENU_NORTH_UP,
        titleRes = R.string.tutorial_menu_north_up_title,
        bodyRes = R.string.tutorial_menu_north_up_body,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.MENU_NEARBY_PLACES,
        titleRes = R.string.tutorial_menu_nearby_places_title,
        bodyRes = R.string.tutorial_menu_nearby_places_body,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.MENU_LOCATION_SEARCH,
        titleRes = R.string.tutorial_menu_location_search_title,
        bodyRes = R.string.tutorial_menu_location_search_body,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.MENU_SUBMENU_MAP,
        titleRes = R.string.tutorial_menu_submenu_map_title,
        bodyRes = R.string.tutorial_menu_submenu_map_body,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.MENU_SUBMENU_WEATHER,
        titleRes = R.string.tutorial_menu_submenu_weather_title,
        bodyRes = R.string.tutorial_menu_submenu_weather_body,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.MENU_SUBMENU_SYSTEM,
        titleRes = R.string.tutorial_menu_submenu_system_title,
        bodyRes = R.string.tutorial_menu_submenu_system_body,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.MENU_SUBMENU_HELP,
        titleRes = R.string.tutorial_menu_submenu_help_title,
        bodyRes = R.string.tutorial_menu_submenu_help_body,
    ),
)

fun stepsFor(group: TutorialGroup): List<TutorialStep> = when (group) {
    TutorialGroup.MAP -> MAP_TUTORIAL_STEPS
    TutorialGroup.MENU_MAIN -> MENU_MAIN_TUTORIAL_STEPS
}
