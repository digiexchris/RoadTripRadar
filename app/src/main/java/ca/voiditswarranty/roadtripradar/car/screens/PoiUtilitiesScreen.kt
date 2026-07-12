package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder

/**
 * POI-pipeline utilities, reached from [PoiScreen]'s "More" end-header action. A [ListTemplate]
 * with three rows:
 *
 * - **Search area** (`car_poi_search`) — parked-only; re-runs the POI pipeline against the
 *   phone's current map camera via [ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel.searchVisibleArea].
 *   Enabled only when there is a pending camera to search against
 *   (`vm.pendingCameraInfo != null`).
 * - **Clear** (`car_poi_clear`) — parked-only; drops all loaded POIs via
 *   [ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel.clearNearbyPois].
 * - **Retry failed** (`car_poi_retry`) — driving-ok; present only when
 *   [ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel.hasFailedCells], retries the
 *   permanently-failed pipeline cells via
 *   [ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel.retryFailedCells].
 *
 * These rows used to live on the HomeScreen menu; they moved here so the menu is a clean
 * Places + Settings hub and the pipeline controls sit next to the category grid that drives
 * them. Search area + Clear are parked-only because they re-run/erase the pipeline against
 * the phone's current map camera; Retry is driving-ok because it is a safe, resumable action.
 */
class PoiUtilitiesScreen(carContext: CarContext) : BaseCarScreen(
    carContext,
    CarViewModelHolder.ensureInitialized(carContext.applicationContext),
) {

    override fun buildTemplate(): ListTemplate {
        val itemList = ItemList.Builder().apply {
            // Search area — parked-only, enabled only when there is a camera to search.
            addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_poi_search))
                    .setOnClickListener(ParkedOnlyOnClickListener.create { vm.searchVisibleArea() })
                    .setEnabled(vm.pendingCameraInfo != null)
                    .build()
            )

            // Clear — parked-only.
            addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_poi_clear))
                    .setOnClickListener(ParkedOnlyOnClickListener.create { vm.clearNearbyPois() })
                    .build()
            )

            // Retry failed — driving-ok, only when something actually failed.
            if (vm.hasFailedCells) {
                addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.car_poi_retry))
                        .setOnClickListener { vm.retryFailedCells() }
                        .build()
                )
            }
        }.build()

        return ListTemplate.Builder()
            .setSingleList(itemList)
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.car_poi_utilities_title))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}