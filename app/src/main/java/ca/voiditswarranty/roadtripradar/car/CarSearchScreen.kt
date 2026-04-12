package ca.voiditswarranty.roadtripradar.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import ca.voiditswarranty.roadtripradar.data.ViewBox
import ca.voiditswarranty.roadtripradar.model.SearchResult
import ca.voiditswarranty.roadtripradar.model.formatDistanceLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CarSearchScreen(
    carContext: CarContext,
    private val session: RoadTripRadarCarSession,
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var searchJob: Job? = null
    private var results: List<SearchResult> = emptyList()
    private var isSearching = false

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        for (result in results) {
            val rowBuilder = Row.Builder()
                .setTitle(result.name)
            val subtitle = buildString {
                result.distance?.let { dist ->
                    append(formatDistanceLabel(dist, session.prefsRepo.useMetric))
                    if (result.subtitle.isNotEmpty()) append(" · ")
                }
                append(result.subtitle)
            }
            if (subtitle.isNotEmpty()) {
                rowBuilder.addText(subtitle)
            }
            rowBuilder.setOnClickListener {
                session.setDestination(result.position, result.name)
                screenManager.pop()
            }
            listBuilder.addItem(rowBuilder.build())
        }

        return SearchTemplate.Builder(
            object : SearchTemplate.SearchCallback {
                override fun onSearchTextChanged(searchText: String) {
                    triggerSearch(searchText)
                }

                override fun onSearchSubmitted(searchText: String) {
                    triggerSearch(searchText)
                }
            }
        )
            .setHeaderAction(Action.BACK)
            .setItemList(listBuilder.build())
            .setShowKeyboardByDefault(true)
            .setLoading(isSearching && results.isEmpty())
            .build()
    }

    private fun triggerSearch(query: String) {
        searchJob?.cancel()
        if (query.length < 2) {
            results = emptyList()
            invalidate()
            return
        }
        searchJob = scope.launch {
            isSearching = true
            delay(300L)
            val pos = session.prefsRepo.lastKnownPosition
            val zoom = session.prefsRepo.zoomLevel.toDouble()
            val viewBox = computeViewBox(pos.latitude, pos.longitude, zoom)
            results = session.geocodingRepo.searchByName(
                query = query,
                centerLat = pos.latitude,
                centerLon = pos.longitude,
                bbox = viewBox,
                userPosition = pos,
            )
            isSearching = false
            invalidate()
        }
    }

    private fun computeViewBox(lat: Double, lon: Double, zoom: Double): ViewBox {
        val latDelta = 360.0 / Math.pow(2.0, zoom) * 0.5
        val lonDelta = latDelta / Math.cos(Math.toRadians(lat))
        return ViewBox(
            west = lon - lonDelta,
            south = lat - latDelta,
            east = lon + lonDelta,
            north = lat + latDelta,
        )
    }
}
