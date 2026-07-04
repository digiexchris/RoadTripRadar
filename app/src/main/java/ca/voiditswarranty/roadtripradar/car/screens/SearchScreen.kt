package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder
import ca.voiditswarranty.roadtripradar.data.InsertPosition
import ca.voiditswarranty.roadtripradar.model.SearchResult
import ca.voiditswarranty.roadtripradar.model.formatDistanceLabel

/**
 * Name search (uses the same Geocoding repository + debounce as the phone). Submit the
 * query, results render as rows; tapping a row opens [SearchResultDetailScreen] with
 * add-to-route actions. The search keyboard is presented by the host (parked-only per
 * host policy).
 */
class SearchScreen(carContext: CarContext) : BaseCarScreen(
    carContext,
    CarViewModelHolder.ensureInitialized(carContext.applicationContext),
) {

    override fun buildTemplate(): SearchTemplate {
        val results = vm.searchResults
        val list = ItemList.Builder().apply {
            if (results.isEmpty() && !vm.isSearching && vm.searchQuery.length >= 2) {
                setNoItemsMessage(carContext.getString(R.string.car_search_no_results))
            } else if (vm.isSearching && results.isEmpty()) {
                setNoItemsMessage(carContext.getString(R.string.search_searching))
            }
            for (result in results) {
                val builder = Row.Builder()
                    .setTitle(result.name)
                    .setOnClickListener { push(SearchResultDetailScreen(carContext, result)) }
                if (result.subtitle.isNotBlank()) builder.addText(result.subtitle)
                result.distance?.let { builder.addText(formatDistanceLabel(it, vm.useMetric)) }
                addItem(builder.build())
            }
        }.build()

        return SearchTemplate.Builder(
            object : SearchTemplate.SearchCallback {
                override fun onSearchSubmitted(query: String) {
                    vm.updateSearchQuery(query)
                }
            }
        )
            .setSearchHint(carContext.getString(R.string.car_search_hint))
            .setItemList(list)
            .setLoading(vm.isSearching)
            .setHeaderAction(Action.BACK)
            .build()
    }
}

/**
 * Detail screen for a chosen search result: add it to the route at a chosen position.
 * Driving-ok (simple additive actions).
 */
class SearchResultDetailScreen(
    carContext: CarContext,
    private val result: SearchResult,
) : BaseCarScreen(
    carContext,
    CarViewModelHolder.ensureInitialized(carContext.applicationContext),
) {

    override fun buildTemplate(): PaneTemplate {
        val pane = Pane.Builder().apply {
            addRow(
                Row.Builder()
                    .setTitle(result.name)
                    .addText(result.subtitle)
                    .build()
            )
            addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_search_add_start))
                    .setOnClickListener { addAndPop(InsertPosition.Start) }
                    .build()
            )
            addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_search_add_before_active))
                    .setOnClickListener { addAndPop(InsertPosition.BeforeLast) }
                    .build()
            )
            addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_search_add_end))
                    .setOnClickListener { addAndPop(InsertPosition.End) }
                    .build()
            )
            if (vm.activeWaypoint != null) {
                addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(R.string.car_search_replace_active))
                        .setOnClickListener {
                            vm.activeWaypoint?.id?.let { id -> addAndPop(InsertPosition.ReplaceId(id)) }
                        }
                        .build()
                )
            }
        }.build()

        return PaneTemplate.Builder(pane)
            .setTitle(carContext.getString(R.string.car_search_title))
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun addAndPop(at: InsertPosition) {
        vm.selectSearchResult(result)
        vm.addWaypointFromTapped(at)
        pop()
    }
}