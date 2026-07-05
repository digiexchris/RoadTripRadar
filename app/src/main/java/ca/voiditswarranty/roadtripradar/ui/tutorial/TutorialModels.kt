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

    /** Route chip + waypoint long-press shortcuts; fires the first time the user has a route. */
    ROUTE_EDITOR,

    /** Map-settings page in the actions drawer; fires the first time that page is opened. */
    MAP_SETTINGS,
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
    const val NAV_WIDGET = "nav_widget"

    // MAP_SETTINGS anchors are the controls in the Map sub-page of the actions drawer.
    const val MAP_SETTING_THEME = "map_setting_theme"
    const val MAP_SETTING_UNITS = "map_setting_units"
    const val MAP_SETTING_CENTER_OFFSET_PORTRAIT = "map_setting_center_offset_portrait"
    const val MAP_SETTING_CENTER_OFFSET_LANDSCAPE = "map_setting_center_offset_landscape"
    const val MAP_SETTING_CENTER_OFFSET_CAR = "map_setting_center_offset_car"
    const val MAP_SETTING_POI_OPACITY = "map_setting_poi_opacity"
    const val MAP_SETTING_AUTO_ADVANCE_TOGGLE = "map_setting_auto_advance_toggle"
    const val MAP_SETTING_AUTO_ADVANCE_THRESHOLD = "map_setting_auto_advance_threshold"

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

val ROUTE_EDITOR_TUTORIAL_STEPS: List<TutorialStep> = listOf(
    TutorialStep(
        anchorId = TutorialAnchors.NAV_WIDGET,
        titleRes = R.string.tutorial_route_nav_widget_title,
        bodyRes = R.string.tutorial_route_nav_widget_body,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.ZOOM_OUT,
        titleRes = R.string.tutorial_route_long_press_out_title,
        bodyRes = R.string.tutorial_route_long_press_out_body,
        spotlightShape = SpotlightShape.CIRCLE,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.ZOOM_IN,
        titleRes = R.string.tutorial_route_long_press_in_title,
        bodyRes = R.string.tutorial_route_long_press_in_body,
        spotlightShape = SpotlightShape.CIRCLE,
    ),
)

val MAP_SETTINGS_TUTORIAL_STEPS: List<TutorialStep> = listOf(
    TutorialStep(
        anchorId = TutorialAnchors.MAP_SETTING_THEME,
        titleRes = R.string.tutorial_map_setting_theme_title,
        bodyRes = R.string.tutorial_map_setting_theme_body,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.MAP_SETTING_UNITS,
        titleRes = R.string.tutorial_map_setting_units_title,
        bodyRes = R.string.tutorial_map_setting_units_body,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.MAP_SETTING_CENTER_OFFSET_PORTRAIT,
        titleRes = R.string.tutorial_map_setting_center_offset_portrait_title,
        bodyRes = R.string.tutorial_map_setting_center_offset_portrait_body,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.MAP_SETTING_CENTER_OFFSET_LANDSCAPE,
        titleRes = R.string.tutorial_map_setting_center_offset_landscape_title,
        bodyRes = R.string.tutorial_map_setting_center_offset_landscape_body,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.MAP_SETTING_CENTER_OFFSET_CAR,
        titleRes = R.string.tutorial_map_setting_center_offset_car_title,
        bodyRes = R.string.tutorial_map_setting_center_offset_car_body,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.MAP_SETTING_POI_OPACITY,
        titleRes = R.string.tutorial_map_setting_poi_opacity_title,
        bodyRes = R.string.tutorial_map_setting_poi_opacity_body,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.MAP_SETTING_AUTO_ADVANCE_TOGGLE,
        titleRes = R.string.tutorial_map_setting_auto_advance_toggle_title,
        bodyRes = R.string.tutorial_map_setting_auto_advance_toggle_body,
    ),
    TutorialStep(
        anchorId = TutorialAnchors.MAP_SETTING_AUTO_ADVANCE_THRESHOLD,
        titleRes = R.string.tutorial_map_setting_auto_advance_threshold_title,
        bodyRes = R.string.tutorial_map_setting_auto_advance_threshold_body,
    ),
)

fun stepsFor(group: TutorialGroup): List<TutorialStep> = when (group) {
    TutorialGroup.MAP -> MAP_TUTORIAL_STEPS
    TutorialGroup.MENU_MAIN -> MENU_MAIN_TUTORIAL_STEPS
    TutorialGroup.ROUTE_EDITOR -> ROUTE_EDITOR_TUTORIAL_STEPS
    TutorialGroup.MAP_SETTINGS -> MAP_SETTINGS_TUTORIAL_STEPS
}
