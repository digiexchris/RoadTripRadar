package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.car.CarMapRenderer
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder
import ca.voiditswarranty.roadtripradar.model.WeatherMode

/**
 * Root car screen: the live MapLibre map rendered onto the Android Auto surface via
 * [MapWithContentTemplate] + a [CarMapRenderer], with a compact content pane (weather + radar
 * status) layered alongside it and a toolbar for the primary controls.
 *
 * This is the POI-category / `MAP_TEMPLATES` path (not Navigation): we render our own base map
 * on the car surface (the host gives us a `Surface`), and overlay template content. The map is
 * interactive via [Action.PAN] in the map action strip (pan + pinch-zoom gestures forwarded by
 * the host). The toolbar holds the three primary controls — open the menu (the list-based hub
 * for POIs / route / search / settings), play/pause the radar animation, and recenter on the
 * device's last known position.
 *
 * Overlays (radar tiles, route line, user puck) are drawn on the surface by [CarMapRenderer]'s
 * container, mirroring the phone map, and the weather appears as a graphical card on the
 * surface (see CarWeatherWidget). [MapWithContentTemplate] requires a content template, so the
 * pane carries a single minimal row — a glance at the active waypoint (tappable to open the
 * route) — keeping the map dominant. POI pins are intentionally not drawn on the car surface
 * (the symbol-layer/icon pipeline is heavy to replicate on the native SDK); POIs remain
 * reachable via Menu → POI.
 */
class CarMapScreen(carContext: CarContext) : BaseCarScreen(
    carContext,
    CarViewModelHolder.ensureInitialized(carContext.applicationContext),
) {
    private val renderer = CarMapRenderer(carContext, lifecycle)

    /**
     * Re-resolve the map style against the car's current day/night mode and reload it on the
     * surface if it changed. Invoked from the session's car-configuration-changed callback (MR-1).
     */
    fun reloadMapStyle() = renderer.reloadStyleIfNeeded()

    override fun buildTemplate(): Template {
        // The weather card is drawn on the map surface (see CarWeatherWidget), and MapWithContentTemplate
        // mandates a content template, so the pane carries a single minimal row — the active waypoint
        // as a glance (tappable to open the route) — keeping the map dominant.
        val content = PaneTemplate.Builder(
            Pane.Builder().apply {
                addRow(activeWaypointRow())
            }.build()
        )
            .setHeader(Header.Builder().setStartHeaderAction(Action.APP_ICON).build())
            .build()

        return MapWithContentTemplate.Builder()
            .setMapController(
                MapController.Builder()
                    .setMapActionStrip(
                        ActionStrip.Builder()
                            .addAction(Action.PAN)
                            .build()
                    )
                    .build()
            )
            .setContentTemplate(content)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle(carContext.getString(R.string.car_action_menu))
                            .setOnClickListener { push(HomeScreen(carContext)) }
                            .build()
                    )
                    .addAction(
                        Action.Builder()
                            .setTitle(carContext.getString(radarPlayPauseLabel()))
                            .setOnClickListener { vm.toggleWeatherPlayPause() }
                            .build()
                    )
                    .addAction(
                        Action.Builder()
                            .setTitle(carContext.getString(R.string.car_action_recenter))
                            .setOnClickListener { renderer.recenter() }
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun activeWaypointRow(): Row {
        // PaneTemplate rows are informational only — RowConstraints rejects a click listener on a
        // Pane row (the original radar row built fine because it had none). This is a glance; the
        // Menu (HomeScreen) handles navigating to the route.
        val active = vm.activeWaypoint
        val title = if (active != null) {
            active.name ?: carContext.getString(R.string.dropped_pin_title)
        } else {
            carContext.getString(R.string.car_route_empty)
        }
        return Row.Builder()
            .setTitle(title)
            .build()
    }

    private fun radarPlayPauseLabel(): Int = when (vm.weatherMode) {
        WeatherMode.PLAYING -> R.string.car_action_pause
        else -> R.string.car_action_play
    }
}