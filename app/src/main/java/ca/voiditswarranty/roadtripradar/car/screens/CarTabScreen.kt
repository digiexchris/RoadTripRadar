package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.car.CarMapRenderer
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder
import ca.voiditswarranty.roadtripradar.model.WeatherMode

/**
 * Root car screen: a [TabTemplate] with two tabs.
 *
 *  - **Map tab** — a [NavigationTemplate] that renders our own MapLibre map on the Android Auto
 *    surface (via [CarMapRenderer] / `CarMapContainer`). The weather card floats on the right of
 *    the surface and the route card on the center-left (only when a waypoint is active), both
 *    drawn by `CarMapContainer` — `NavigationTemplate` has no content pane, so the surface is the
 *    natural home for those overlays. The template's action strip holds the three primary
 *    controls (open the Menu tab, play/pause the radar, recenter); its map action strip holds
 *    [Action.PAN] so the host forwards pan/pinch gestures to our `SurfaceCallback`.
 *  - **Menu tab** — the [menuListTemplate] hub (weather / route / POIs / settings rows) that
 *    pushes the existing sub-screens.
 *
 * The [CarMapRenderer] is owned by *this* root screen (lifecycle-bound here, not to a tab), so
 * the surface callback persists across tab switches — only the active tab content changes when
 * the user switches tabs. The host may tear the surface down while the Menu tab is active and
 * re-provide it on return; the renderer already handles `onSurfaceAvailable` re-setup (the map
 * re-centers on the user at the persisted car zoom).
 *
 * Note on the toolbar: the tab-template guide cautions that headers/action strips of embedded
 * templates "SHOULD NOT be relied on" (the tab bar takes the header space). In practice, on the
 * target head unit the top `setActionStrip` *does* render (horizontal, top-right) even with the
 * `NavigationTemplate` embedded in a tab, so the three primary controls live there. The map
 * action strip is left with only `Action.PAN` (needed for the host to forward pan/pinch to our
 * `SurfaceCallback`); duplicating the controls there just stacks a redundant vertical column on
 * the bottom-right of the surface. If a different head unit ever hides the top strip, the
 * fallback is to move the controls into `setMapActionStrip` alongside `PAN` (4 icon slots, which
 * `ACTIONS_CONSTRAINTS_MAP` allows).
 */
class CarTabScreen(carContext: CarContext) : BaseCarScreen(
    carContext,
    CarViewModelHolder.ensureInitialized(carContext.applicationContext),
) {
    private val renderer = CarMapRenderer(carContext, lifecycle)

    private var activeTabId: String = TAB_MAP

    /**
     * Re-resolve the map style against the car's current day/night mode and reload it on the
     * surface if it changed. Invoked from the session's car-configuration-changed callback (MR-1).
     */
    fun reloadMapStyle() = renderer.reloadStyleIfNeeded()

    /** Switch to the Menu tab (called from the Map tab's "Menu" toolbar action). */
    fun switchToMenuTab() {
        if (activeTabId != TAB_MENU) {
            activeTabId = TAB_MENU
            invalidate()
        }
    }

    override fun buildTemplate(): Template {
        val builder = TabTemplate.Builder(TabCallback())
            .addTab(
                Tab.Builder()
                    .setContentId(TAB_MAP)
                    .setTitle(carContext.getString(R.string.car_tab_map))
                    .setIcon(carIcon(R.drawable.ic_car_tab_map))
                    .build()
            )
            .addTab(
                Tab.Builder()
                    .setContentId(TAB_MENU)
                    .setTitle(carContext.getString(R.string.car_tab_menu))
                    .setIcon(carIcon(R.drawable.ic_car_menu))
                    .build()
            )
            .setActiveTabContentId(activeTabId)
            .setHeaderAction(Action.APP_ICON)

        builder.setTabContents(
            TabContents.Builder(
                when (activeTabId) {
                    TAB_MENU -> menuListTemplate(carContext, vm, asTabContent = true)
                    else -> mapTabTemplate()
                }
            ).build()
        )
        return builder.build()
    }

    /**
     * The Map tab: a [NavigationTemplate] with no content pane (weather + route are drawn on the
     * surface by `CarMapContainer`), no turn-by-turn `NavigationInfo` (we don't have a
     * turn-by-turn engine — the Navigation category's NF-1 gate is intentionally not yet
     * satisfied).
     *
     * `NavigationTemplate` **requires** a top [setActionStrip] (build throws if it's null), so we
     * put the three primary controls there — Menu / Play-Pause / Recenter. On the user's head unit
     * the top strip DOES render (horizontal, top-right) even with the template embedded in a tab,
     * so it's the right home for the controls. The map action strip is left with only
     * [Action.PAN], which is required for the host to forward pan/pinch gestures to our
     * `SurfaceCallback`; putting the controls there too just duplicates them as a vertical column
     * on the bottom-right of the surface.
     */
    private fun mapTabTemplate(): NavigationTemplate =
        NavigationTemplate.Builder()
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setIcon(carIcon(R.drawable.ic_car_menu))
                            .setOnClickListener { switchToMenuTab() }
                            .build()
                    )
                    .addAction(
                        Action.Builder()
                            .setIcon(carIcon(radarPlayPauseIcon()))
                            .setOnClickListener { vm.toggleWeatherPlayPause() }
                            .build()
                    )
                    .addAction(
                        Action.Builder()
                            .setIcon(carIcon(R.drawable.ic_car_recenter))
                            .setOnClickListener { renderer.recenter() }
                            .build()
                    )
                    .build()
            )
            .setMapActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.PAN)
                    .build()
            )
            .build()

    /** Pause icon while the radar is playing, play icon otherwise — mirroring the phone FAB. */
    private fun radarPlayPauseIcon(): Int = when (vm.weatherMode) {
        WeatherMode.PLAYING -> R.drawable.ic_car_pause
        else -> R.drawable.ic_car_play
    }

    private fun carIcon(drawableRes: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, drawableRes)).build()

    private inner class TabCallback : TabTemplate.TabCallback {
        override fun onTabSelected(contentId: String) {
            // The host fires this on the main thread when the user taps a tab. Switch the active
            // tab content and re-build so onGetTemplate returns the new tab's template.
            activeTabId = contentId
            invalidate()
        }
    }

    companion object {
        private const val TAB_MAP = "tab_map"
        private const val TAB_MENU = "tab_menu"
    }
}