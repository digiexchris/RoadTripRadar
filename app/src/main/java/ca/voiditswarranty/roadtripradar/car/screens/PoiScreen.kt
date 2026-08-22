package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.Badge
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.core.graphics.drawable.IconCompat
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder
import ca.voiditswarranty.roadtripradar.car.MakiIcons
import ca.voiditswarranty.roadtripradar.model.MAX_POI_CATEGORIES
import ca.voiditswarranty.roadtripradar.model.POI_CATEGORIES

/**
 * POI category picker as a grid of maki icons. Tap an icon to toggle that category (up to
 * [MAX_POI_CATEGORIES]); selected categories show a primary dot badge and a filled blue
 * icon. Categories beyond the cap are shown grayed with "(max 5)" and are non-tappable.
 * Toggling re-runs the pipeline against the phone's current map camera.
 *
 * The header carries two icon-only end actions: **Search** (push the place-search screen) and
 * **More** (push [PoiUtilitiesScreen], which holds the POI-pipeline utilities — Search area,
 * Clear, Retry failed). Header end actions are icon-only (no custom title), so two icon-only
 * actions fit GridTemplate's 2-action limit. Pipeline status is shown in the title.
 */
class PoiScreen(carContext: CarContext) : BaseCarScreen(
    carContext,
    CarViewModelHolder.ensureInitialized(carContext.applicationContext),
) {

    override fun buildTemplate(): GridTemplate {
        val selectedBadge = Badge.Builder()
            .setHasDot(true)
            .setBackgroundColor(CarColor.PRIMARY)
            .build()

        val itemList = ItemList.Builder().apply {
            for (category in POI_CATEGORIES) {
                val enabled = category.query in vm.enabledPoiCategories
                val atCap = !enabled && vm.enabledPoiCategories.size >= MAX_POI_CATEGORIES
                val icon = MakiIcons.forName(carContext, category.iconName, enabled)
                val builder = GridItem.Builder()
                    .setTitle(carContext.getString(category.labelRes))
                if (enabled) {
                    builder.setImage(icon, selectedBadge)
                    builder.setOnClickListener { vm.togglePoiCategory(category) }
                } else if (!atCap) {
                    builder.setImage(icon)
                    builder.setOnClickListener {
                        // Toggling re-runs the pipeline against the phone's current camera.
                        vm.togglePoiCategory(category)
                    }
                } else {
                    // At the cap: show why it's disabled and leave it non-tappable.
                    builder.setImage(icon)
                    builder.setText(carContext.getString(R.string.car_poi_max_reached))
                }
                addItem(builder.build())
            }
        }.build()

        return GridTemplate.Builder()
            .setSingleList(itemList)
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.car_poi_title) + " · " + poiStatusText())
                    .setStartHeaderAction(Action.BACK)
                    .addEndHeaderAction(
                        Action.Builder()
                            .setIcon(carIcon(R.drawable.ic_car_search))
                            .setOnClickListener { push(SearchScreen(carContext)) }
                            .build()
                    )
                    .addEndHeaderAction(
                        Action.Builder()
                            .setIcon(carIcon(R.drawable.ic_car_more))
                            .setOnClickListener { push(PoiUtilitiesScreen(carContext)) }
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun carIcon(drawableRes: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, drawableRes)).build()

    private fun poiStatusText(): String {
        val count = vm.nearbyPoiFeatures.features.size
        return when {
            vm.isLoadingPois -> carContext.getString(R.string.car_poi_loading, count)
            vm.hasFailedCells -> carContext.getString(R.string.car_poi_failed, count)
            vm.poiPipelineActive -> carContext.getString(R.string.car_poi_ready, count)
            vm.pendingCameraInfo == null -> carContext.getString(R.string.car_poi_no_map)
            else -> carContext.getString(R.string.car_poi_idle, count)
        }
    }
}