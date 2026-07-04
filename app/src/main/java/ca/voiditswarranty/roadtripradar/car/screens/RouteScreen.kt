package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder

/**
 * Route / waypoint list. Tap a row to set it active (driving-ok). Per-row actions
 * remove and move-up (parked-only). A "clear route" row at the top is parked-only.
 */
class RouteScreen(carContext: CarContext) : BaseCarScreen(
    carContext,
    CarViewModelHolder.ensureInitialized(carContext.applicationContext),
) {

    override fun buildTemplate(): ListTemplate {
        val itemList = ItemList.Builder().apply {
            if (vm.waypoints.isEmpty()) {
                addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.car_route_empty))
                        .build()
                )
            } else {
                addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.car_route_clear))
                        .setOnClickListener(
                            ParkedOnlyOnClickListener.create { vm.clearRoute() }
                        )
                        .build()
                )
                // Quick active-waypoint controls (driving-ok). These moved here from HomeScreen,
                // whose ListTemplate toolbar holds only a single primary action.
                if (vm.waypoints.size > 1) {
                    addItem(
                        Row.Builder()
                            .setTitle(carContext.getString(R.string.car_action_advance))
                            .setOnClickListener { vm.advanceActiveWaypoint() }
                            .build()
                    )
                    addItem(
                        Row.Builder()
                            .setTitle(carContext.getString(R.string.car_action_regress))
                            .setOnClickListener { vm.regressActiveWaypoint() }
                            .build()
                    )
                }
                for ((index, wp) in vm.waypoints.withIndex()) {
                    val isActive = wp.id == vm.activeWaypointId
                    val title = (if (isActive) carContext.getString(R.string.car_route_active_badge) else "") +
                        (wp.name ?: carContext.getString(R.string.dropped_pin_title))
                    addItem(
                        Row.Builder()
                            .setTitle(title.trim())
                            .addText(wp.subtitle ?: "")
                            .setOnClickListener { vm.setActiveWaypoint(wp.id) }
                            .addAction(
                                Action.Builder()
                                    .setTitle(carContext.getString(R.string.car_route_move_up))
                                    .setOnClickListener(
                                        ParkedOnlyOnClickListener.create {
                                            vm.moveWaypoint(index, (index - 1).coerceAtLeast(0))
                                        }
                                    )
                                    .build()
                            )
                            .addAction(
                                Action.Builder()
                                    .setTitle(carContext.getString(R.string.car_route_remove))
                                    .setOnClickListener(
                                        ParkedOnlyOnClickListener.create { vm.removeWaypoint(wp.id) }
                                    )
                                    .build()
                            )
                            .build()
                    )
                }
            }
        }.build()

        return ListTemplate.Builder()
            .setSingleList(itemList)
            .setTitle(carContext.getString(R.string.car_route_title))
            .setHeaderAction(Action.BACK)
            .build()
    }
}