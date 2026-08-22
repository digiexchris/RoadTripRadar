package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.CarContext
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder

/**
 * The menu hub: a lean two-row list — **Places** and **Settings**.
 *
 * The car surface is deliberately glanceable. Everything the driver needs at a glance
 * (current weather, active waypoint, radar status) lives on the map widgets and toolbar of
 * [CarNavMapScreen], not here. Everything adjustable (radar mode/opacity/timeline, route
 * management) is phone-only, and the POI-pipeline utilities (Search area / Clear / Retry
 * failed) live behind [PoiScreen]'s "More" action. So this menu reduces to a clean hub: a
 * link to the POI category grid and a link to car Settings.
 *
 * The rows themselves are built by [carMenuRows]. Pushed from the map screen's toolbar menu
 * action, with a BACK header that pops back to the caller.
 *
 * Implemented as a [ListTemplate] so each row is a tap-to-navigate link unconstrained by
 * PaneTemplate's action-strip limits.
 */
class HomeScreen(carContext: CarContext) : BaseCarScreen(
    carContext,
    CarViewModelHolder.ensureInitialized(carContext.applicationContext),
) {

    override fun buildTemplate(): ListTemplate =
        menuListTemplate(carContext)
}

/**
 * Build the menu [ItemList] — Places and Settings. Row click listeners push the relevant
 * sub-screen onto the car screen stack.
 */
fun carMenuRows(carContext: CarContext): ItemList =
    ItemList.Builder().apply {
        // Places row
        addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.car_poi_title))
                .setOnClickListener {
                    carContext.getCarService(ScreenManager::class.java)
                        .push(PoiScreen(carContext))
                }
                .build()
        )

        // Settings row
        addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.car_settings_title))
                .setOnClickListener {
                    carContext.getCarService(ScreenManager::class.java)
                        .push(SettingsScreen(carContext))
                }
                .build()
        )
    }.build()

/**
 * Build the menu [ListTemplate]. A BACK header action pops back to the caller (the map screen).
 */
fun menuListTemplate(carContext: CarContext): ListTemplate {
    val header = Header.Builder()
        .setTitle(carContext.getString(R.string.car_home_title))
        .setStartHeaderAction(Action.BACK)
        .build()
    return ListTemplate.Builder()
        .setSingleList(carMenuRows(carContext))
        .setHeader(header)
        .build()
}