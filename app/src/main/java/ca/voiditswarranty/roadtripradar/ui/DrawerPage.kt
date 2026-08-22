package ca.voiditswarranty.roadtripradar.ui

/**
 * One page of the [ActionsDrawer]. Each variant holds the list of
 * [DrawerAction]s that page renders. Extracted from [ActionsDrawer] so each
 * page can be tested in isolation.
 *
 * The variant names mirror the existing private enum
 * `ActionsDrawerPage` in ActionsDrawer.kt (Main / Map / Weather / System /
 * Help). The public [ActionsDrawer] composable owns the page navigation and
 * constructs a [DrawerPage] from VM state.
 */
internal sealed class DrawerPage {
    abstract val title: String
    abstract val actions: List<DrawerAction>

    /** Top-level page: shows the main categories (Map, Weather, System, Help) plus Close + Quit. */
    data class Main(
        override val title: String,
        override val actions: List<DrawerAction>,
    ) : DrawerPage()

    /** Map-related actions: route editor, theme selector, layer toggles. */
    data class Map(
        override val title: String,
        override val actions: List<DrawerAction>,
    ) : DrawerPage()

    /** Weather-related actions: radar mode, layer toggles, units. */
    data class Weather(
        override val title: String,
        override val actions: List<DrawerAction>,
    ) : DrawerPage()

    /** System actions: app info, changelog, terms, theme selector, units. */
    data class System(
        override val title: String,
        override val actions: List<DrawerAction>,
    ) : DrawerPage()

    /** Help actions: tutorial restart, project link, version info. */
    data class Help(
        override val title: String,
        override val actions: List<DrawerAction>,
    ) : DrawerPage()
}