package ca.voiditswarranty.roadtripradar.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.voiditswarranty.roadtripradar.data.GeocodingRepository
import ca.voiditswarranty.roadtripradar.data.OpenMeteoRepository
import ca.voiditswarranty.roadtripradar.data.OpenMeteoSnapshot
import ca.voiditswarranty.roadtripradar.data.OverpassRepository
import ca.voiditswarranty.roadtripradar.data.PostpassServerException
import ca.voiditswarranty.roadtripradar.data.PreferencesRepository
import ca.voiditswarranty.roadtripradar.data.ViewBox
import ca.voiditswarranty.roadtripradar.data.WeatherRepository
import ca.voiditswarranty.roadtripradar.model.MAX_POI_CATEGORIES
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.model.NetworkStatus
import ca.voiditswarranty.roadtripradar.model.NetworkTransport
import ca.voiditswarranty.roadtripradar.model.PoiCategory
import ca.voiditswarranty.roadtripradar.model.PoiGridCell
import ca.voiditswarranty.roadtripradar.model.PrefsDefaults
import ca.voiditswarranty.roadtripradar.model.PoiViewportChunks
import ca.voiditswarranty.roadtripradar.model.SearchResult
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.turf.measurement.distance
import org.maplibre.spatialk.units.extensions.inMeters
import kotlin.math.max
import kotlin.math.min

class MapViewModel(
    appContext: Context,
    val prefsRepo: PreferencesRepository,
    private val weatherRepo: WeatherRepository = WeatherRepository(),
    private val openMeteoRepo: OpenMeteoRepository = OpenMeteoRepository(),
    val overpassRepo: OverpassRepository = OverpassRepository(),
) : ViewModel() {
    private val geocodingRepo: GeocodingRepository = GeocodingRepository(overpassRepo)

    // Weather
    var weatherMode by mutableStateOf(prefsRepo.weatherMode)
        private set
    var isWeatherPlaying by mutableStateOf(prefsRepo.isWeatherPlaying)
        private set
    var showLegend by mutableStateOf(prefsRepo.showLegend)
        private set
    var showTimeline by mutableStateOf(prefsRepo.showTimeline)
        private set
    var radarFramePaths by mutableStateOf(emptyList<String>())
        private set
    var radarFrameTimes by mutableStateOf(emptyList<Long>())
        private set
    var currentFrameIndex by mutableStateOf(0)
        private set
    var radarOpacity by mutableStateOf(prefsRepo.radarOpacity)
        private set

    /** Open-Meteo (temperature, WMO code, wind); updated by [setLocalWeatherAnchor] polling. */
    var openMeteoSnapshot by mutableStateOf<OpenMeteoSnapshot?>(null)
        private set

    // Settings
    var useMetric by mutableStateOf(prefsRepo.useMetric)
        private set
    var speedSize by mutableStateOf(prefsRepo.speedSize)
        private set
    var navWidgetSize by mutableStateOf(prefsRepo.navWidgetSize)
        private set
    var weatherWidgetSize by mutableStateOf(prefsRepo.weatherWidgetSize)
        private set
    var compassWidgetSize by mutableStateOf(prefsRepo.compassWidgetSize)
        private set
    var windEnabled by mutableStateOf(prefsRepo.windEnabled)
        private set
    var windSpeedUnit by mutableStateOf(prefsRepo.windSpeedUnit)
        private set
    var temperatureUnit by mutableStateOf(prefsRepo.temperatureUnit)
        private set
    var keepScreenOn by mutableStateOf(prefsRepo.keepScreenOn)
        private set
    var useGps by mutableStateOf(prefsRepo.useGps)
        private set
    var gpsIconOpacity by mutableStateOf(prefsRepo.gpsIconOpacity)
        private set
    var mapCenterOffsetPortraitFraction by mutableStateOf(prefsRepo.mapCenterOffsetPortraitFraction)
        private set
    var mapCenterOffsetLandscapeFraction by mutableStateOf(prefsRepo.mapCenterOffsetLandscapeFraction)
        private set

    // Network
    var networkStatus by mutableStateOf(NetworkStatus())
        private set

    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val transport = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
                else -> null
            }
            networkStatus = NetworkStatus(
                transport = transport,
                validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                connected = true,
            )
        }

        override fun onLost(network: Network) {
            networkStatus = NetworkStatus()
        }
    }

    // POI
    var poiPosition by mutableStateOf(prefsRepo.poiPosition)
        private set
    var poiName by mutableStateOf(
        prefsRepo.poiName ?: if (prefsRepo.poiPosition != null) "Dropped Pin" else null
    )
        private set

    // Nearby POIs — cell-based pipeline
    data class CachedCell(
        val cellId: String,
        val features: List<Feature<Point, JsonObject>>,
        val fetchedAtMs: Long,
        val bounds: BoundingBox,
    )

    private val cellCache = mutableMapOf<String, CachedCell>()
    private val failedCells = mutableMapOf<String, Long>()

    var enabledPoiCategories by mutableStateOf(prefsRepo.enabledPoiCategories)
        private set

    var showPoiCategoryPicker by mutableStateOf(false)
        private set
    var poiCategoriesVersion by mutableStateOf(0)
        private set
    var tappedPoi by mutableStateOf<TappedPoiInfo?>(null)
        private set

    /** True when the cell pipeline has been activated by the user (Search Visible Area / Refresh). */
    var poiPipelineActive by mutableStateOf(false)
        private set

    var cellsLoadingTotal by mutableStateOf(0)
        private set
    var cellsLoadingComplete by mutableStateOf(0)
        private set

    val isLoadingPois: Boolean
        get() = cellsLoadingTotal > 0 && cellsLoadingComplete < cellsLoadingTotal

    /** Merged features from all cached cells. */
    var nearbyPoiFeatures by mutableStateOf<FeatureCollection<Point, JsonObject>>(FeatureCollection(emptyList()))
        private set
    var poiFetchRegion: BoundingBox? = null
        private set

    /** Union bounding box of all cached cells (shown as dotted outline). */
    var poiLoadBounds: BoundingBox? by mutableStateOf(null)
        private set

    val hasNearbyPoiFeatures: Boolean
        get() = nearbyPoiFeatures.features.isNotEmpty()

    private var interCellDelayMs: Long = 150L
    private companion object {
        const val FAILED_CELL_COOLDOWN_MS = 30_000L
        const val EVICTION_AGE_MS = 60_000L
        const val MAX_INTER_CELL_DELAY_MS = 2_000L
        const val RADAR_POLL_MS = 60_000L
        const val LOCAL_WEATHER_SUCCESS_MS = 600_000L
        const val LOCAL_WEATHER_RETRY_MS = 60_000L
    }

    data class TappedPoiInfo(
        val name: String,
        val subtitle: String,
        val categoryLabel: String,
        val iconName: String,
        val position: Position,
        val openingHours: String? = null,
    )

    // UI state
    var isTrackingCamera by mutableStateOf(true)
    var isNorthUp by mutableStateOf(!prefsRepo.useGps)
    var showSettings by mutableStateOf(false)
        private set
    var showActionsDrawer by mutableStateOf(false)
        private set
    var showResetConfirm by mutableStateOf(false)
        private set
    var showPoiSearch by mutableStateOf(false)
        private set
    var showHelp by mutableStateOf(false)
        private set
    var showQuickHelp by mutableStateOf(false)
        private set
    var showLegendDetail by mutableStateOf(false)
        private set
    var showTerms by mutableStateOf(false)
        private set
    var termsNeedAcceptance by mutableStateOf(false)
        private set

    // Search state
    var searchQuery by mutableStateOf("")
        private set
    var searchResults by mutableStateOf(emptyList<SearchResult>())
        private set
    var searchByCategory by mutableStateOf(false)
        private set
    var isSearching by mutableStateOf(false)
        private set
    var selectedCategory by mutableStateOf<PoiCategory?>(null)
        private set

    val weatherActive get() = weatherMode == WeatherMode.ON

    private var lastGenerated = 0L
    private var localWeatherAnchor: Position? = null
    private var weatherPollingJob: Job? = null
    private var localWeatherPollJob: Job? = null
    private var weatherAnimationJob: Job? = null
    private var searchJob: Job? = null
    private var poiFetchJob: Job? = null

    init {
        if (prefsRepo.acceptedTermsVersion != PrefsDefaults.TERMS_VERSION) {
            showTerms = true
            termsNeedAcceptance = true
        } else if (prefsRepo.showStartupHelp) {
            showQuickHelp = true
        }
        startWeatherPollingIfActive()
        startWeatherAnimationIfPlaying()
        startLocalWeatherPolling()
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    fun setLocalWeatherAnchor(position: Position?) {
        localWeatherAnchor = position
    }

    override fun onCleared() {
        super.onCleared()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    // --- Weather ---

    fun updateWeatherMode(mode: WeatherMode) {
        weatherMode = mode
        prefsRepo.weatherMode = mode
        if (mode == WeatherMode.ON) {
            currentFrameIndex = radarFramePaths.lastIndex.coerceAtLeast(0)
        }
        startWeatherPollingIfActive()
    }

    fun toggleWeatherPlaying() {
        isWeatherPlaying = !isWeatherPlaying
        prefsRepo.isWeatherPlaying = isWeatherPlaying
        if (!isWeatherPlaying) {
            currentFrameIndex = radarFramePaths.lastIndex.coerceAtLeast(0)
        }
        startWeatherAnimationIfPlaying()
    }

    fun updateShowLegend(show: Boolean) {
        showLegend = show
        prefsRepo.showLegend = show
    }

    fun updateShowTimeline(show: Boolean) {
        showTimeline = show
        prefsRepo.showTimeline = show
    }

    fun updateRadarOpacity(opacity: Float) {
        radarOpacity = opacity
    }

    fun saveRadarOpacity() {
        prefsRepo.radarOpacity = radarOpacity
    }

    private fun startWeatherPollingIfActive() {
        weatherPollingJob?.cancel()
        if (!weatherActive) return
        weatherPollingJob = viewModelScope.launch {
            while (true) {
                val data = weatherRepo.fetchFrames(lastGenerated)
                if (data != null) {
                    lastGenerated = data.generated
                    radarFramePaths = data.paths
                    radarFrameTimes = data.times
                    currentFrameIndex = radarFramePaths.lastIndex.coerceAtLeast(0)
                }
                delay(RADAR_POLL_MS)
            }
        }
    }

    private fun startLocalWeatherPolling() {
        localWeatherPollJob?.cancel()
        localWeatherPollJob = viewModelScope.launch {
            while (true) {
                val pos = localWeatherAnchor
                if (pos == null) {
                    delay(2_000L)
                    continue
                }
                openMeteoRepo.fetchCurrent(pos.latitude, pos.longitude)
                    .onSuccess { snap ->
                        openMeteoSnapshot = snap
                        delay(LOCAL_WEATHER_SUCCESS_MS)
                    }
                    .onFailure {
                        delay(LOCAL_WEATHER_RETRY_MS)
                    }
            }
        }
    }

    private fun startWeatherAnimationIfPlaying() {
        weatherAnimationJob?.cancel()
        if (!weatherActive || !isWeatherPlaying) return
        weatherAnimationJob = viewModelScope.launch {
            while (true) {
                delay(500)
                if (radarFramePaths.isNotEmpty()) {
                    currentFrameIndex = (currentFrameIndex + 1) % radarFramePaths.size
                }
            }
        }
    }

    // --- Settings ---

    fun updateUseMetric(metric: Boolean) {
        useMetric = metric
        prefsRepo.useMetric = metric
    }

    fun updateSpeedSize(size: Float) {
        speedSize = size
    }

    fun saveSpeedSize() {
        prefsRepo.speedSize = speedSize
    }

    fun updateNavWidgetSize(size: Float) {
        navWidgetSize = size
    }

    fun saveNavWidgetSize() {
        prefsRepo.navWidgetSize = navWidgetSize
    }

    fun updateWeatherWidgetSize(size: Float) {
        weatherWidgetSize = size
    }

    fun saveWeatherWidgetSize() {
        prefsRepo.weatherWidgetSize = weatherWidgetSize
    }

    fun updateCompassWidgetSize(size: Float) {
        compassWidgetSize = size
    }

    fun saveCompassWidgetSize() {
        prefsRepo.compassWidgetSize = compassWidgetSize
    }

    fun updateWindEnabled(on: Boolean) {
        windEnabled = on
        prefsRepo.windEnabled = on
    }

    fun updateWindSpeedUnit(unit: WindSpeedUnit) {
        windSpeedUnit = unit
        prefsRepo.windSpeedUnit = unit
    }

    fun updateTemperatureUnit(unit: TemperatureUnit) {
        temperatureUnit = unit
        prefsRepo.temperatureUnit = unit
    }

    fun updateKeepScreenOn(on: Boolean) {
        keepScreenOn = on
        prefsRepo.keepScreenOn = on
    }

    fun updateUseGps(on: Boolean) {
        useGps = on
        prefsRepo.useGps = on
        if (!on) {
            isTrackingCamera = false
        } else {
            isTrackingCamera = true
        }
    }

    fun updateGpsIconOpacity(opacity: Float) {
        gpsIconOpacity = opacity
    }

    fun saveGpsIconOpacity() {
        prefsRepo.gpsIconOpacity = gpsIconOpacity
    }

    fun updateMapCenterOffsetPortraitFraction(value: Float) {
        mapCenterOffsetPortraitFraction = value
    }

    fun saveMapCenterOffsetPortraitFraction() {
        prefsRepo.mapCenterOffsetPortraitFraction = mapCenterOffsetPortraitFraction
    }

    fun updateMapCenterOffsetLandscapeFraction(value: Float) {
        mapCenterOffsetLandscapeFraction = value
    }

    fun saveMapCenterOffsetLandscapeFraction() {
        prefsRepo.mapCenterOffsetLandscapeFraction = mapCenterOffsetLandscapeFraction
    }

    fun saveLastKnownPosition(pos: Position) {
        prefsRepo.lastKnownPosition = pos
    }

    fun openSettings() { showSettings = true }
    fun closeSettings() { showSettings = false }
    fun openActionsDrawer() { showActionsDrawer = true }
    fun closeActionsDrawer() { showActionsDrawer = false }
    fun openResetConfirm() { showResetConfirm = true }
    fun closeResetConfirm() { showResetConfirm = false }
    fun openPoiSearch() { showPoiSearch = true }
    fun closePoiSearch() { showPoiSearch = false }
    fun openHelp() { showHelp = true }
    fun closeHelp() { showHelp = false }
    fun closeQuickHelp() { showQuickHelp = false }
    fun disableStartupQuickHelp() {
        prefsRepo.showStartupHelp = false
        showQuickHelp = false
    }

    fun openLegendDetail() {
        showHelp = false
        showLegendDetail = true
    }
    fun closeLegendDetail() { showLegendDetail = false }

    fun viewTerms() {
        showHelp = false
        showTerms = true
    }

    fun acceptTerms() {
        prefsRepo.acceptedTermsVersion = PrefsDefaults.TERMS_VERSION
        termsNeedAcceptance = false
        showTerms = false
        showQuickHelp = prefsRepo.showStartupHelp
    }

    fun dismissTerms() {
        showTerms = false
    }

    // --- POI ---

    fun setPoiFromLongPress(position: Position) {
        poiPosition = position
        poiName = "Dropped Pin"
        persistPoi()
    }

    fun setPoiFromSearch(position: Position, name: String) {
        poiPosition = position
        poiName = name
        persistPoi()
        showPoiSearch = false
    }

    fun clearPoi() {
        poiPosition = null
        poiName = null
        persistPoi()
    }

    private fun persistPoi() {
        prefsRepo.poiPosition = poiPosition
        prefsRepo.poiName = poiName
    }

    // --- Nearby POIs (cell-based pipeline) ---

    fun togglePoiCategory(category: PoiCategory) {
        val current = enabledPoiCategories.toMutableSet()
        if (current.contains(category.query)) {
            current.remove(category.query)
        } else {
            if (current.size >= MAX_POI_CATEGORIES) return
            current.add(category.query)
        }
        enabledPoiCategories = current
        prefsRepo.enabledPoiCategories = current
        poiCategoriesVersion++
        // If pipeline is active, re-trigger with new categories
        if (poiPipelineActive) {
            if (enabledPoiCategories.isEmpty()) {
                clearNearbyPois()
            } else {
                cellCache.clear()
                failedCells.clear()
                nearbyPoiFeatures = FeatureCollection(emptyList())
                triggerPipelineForCurrentViewport()
            }
        }
    }

    fun searchVisibleArea() {
        if (enabledPoiCategories.isEmpty()) return
        val cam = pendingCameraInfo ?: return
        val viewBounds = viewportBoundsForPoi(cam.lat, cam.lon, cam.zoom)
        val (loadBounds, cells) = PoiViewportChunks.gridCellsForManualLoad(viewBounds)
        val centerLat = (viewBounds.southwest.latitude + viewBounds.northeast.latitude) / 2.0
        val viewHeightKm = (viewBounds.northeast.latitude - viewBounds.southwest.latitude) * 111.0
        val viewWidthKm = (viewBounds.northeast.longitude - viewBounds.southwest.longitude) * 111.0 * kotlin.math.cos(Math.toRadians(centerLat))
        val loadHeightKm = (loadBounds.northeast.latitude - loadBounds.southwest.latitude) * 111.0
        val loadWidthKm = (loadBounds.northeast.longitude - loadBounds.southwest.longitude) * 111.0 * kotlin.math.cos(Math.toRadians(centerLat))
        android.util.Log.d("POI_DEBUG", "searchVisibleArea: zoom=${cam.zoom}, screen=${screenWidthDp.toInt()}x${screenHeightDp.toInt()}dp")
        android.util.Log.d("POI_DEBUG", "viewport: ${viewHeightKm.toInt()}km × ${viewWidthKm.toInt()}km, padded load: ${loadHeightKm.toInt()}km × ${loadWidthKm.toInt()}km, cells=${cells.size}")
        android.util.Log.d("POI_DEBUG", "loadBounds=$loadBounds")
        // Fresh start: clear all caches
        cellCache.clear()
        failedCells.clear()
        nearbyPoiFeatures = FeatureCollection(emptyList())
        poiFetchRegion = null
        poiLoadBounds = null
        poiPipelineActive = true
        interCellDelayMs = 150L
        poiFetchJob?.cancel()
        val missingCells = cellsNeedingFetch(loadBounds)
        val cameraCenter = Position(latitude = cam.lat, longitude = cam.lon)
        poiFetchJob = viewModelScope.launch {
            fetchCellPipeline(missingCells, cameraCenter)
        }
        poiCategoriesVersion++
    }

    fun clearNearbyPois() {
        poiFetchJob?.cancel()
        cellCache.clear()
        failedCells.clear()
        nearbyPoiFeatures = FeatureCollection(emptyList())
        poiFetchRegion = null
        poiLoadBounds = null
        poiPipelineActive = false
        cellsLoadingTotal = 0
        cellsLoadingComplete = 0
        poiCategoriesVersion++
    }

    fun openPoiCategoryPicker() { showPoiCategoryPicker = true }
    fun closePoiCategoryPicker() { showPoiCategoryPicker = false }

    fun showTappedPoi(info: TappedPoiInfo) { tappedPoi = info }
    fun dismissTappedPoi() { tappedPoi = null }

    fun navigateToTappedPoi() {
        val poi = tappedPoi ?: return
        setPoiFromSearch(poi.position, poi.name)
        tappedPoi = null
    }

    // --- Cell pipeline helpers ---

    private fun rebuildMergedFeatures() {
        val allFeatures = cellCache.values.flatMap { it.features }
        nearbyPoiFeatures = FeatureCollection(allFeatures)
        if (cellCache.isEmpty()) {
            poiLoadBounds = null
            poiFetchRegion = null
        } else {
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE
            var maxLon = -Double.MAX_VALUE
            for (cell in cellCache.values) {
                val sw = cell.bounds.southwest
                val ne = cell.bounds.northeast
                minLat = min(minLat, sw.latitude)
                maxLat = max(maxLat, ne.latitude)
                minLon = min(minLon, sw.longitude)
                maxLon = max(maxLon, ne.longitude)
            }
            val unionBounds = BoundingBox(
                southwest = Position(latitude = minLat, longitude = minLon),
                northeast = Position(latitude = maxLat, longitude = maxLon),
            )
            poiLoadBounds = unionBounds
            poiFetchRegion = unionBounds
        }
    }

    private fun cellsNeedingFetch(loadBounds: BoundingBox): List<PoiGridCell> {
        val now = System.currentTimeMillis()
        return PoiViewportChunks.worldGridCellsIntersecting(loadBounds).filter { cell ->
            if (cellCache.containsKey(cell.id)) return@filter false
            val failedAt = failedCells[cell.id]
            if (failedAt != null && (now - failedAt) < FAILED_CELL_COOLDOWN_MS) return@filter false
            true
        }
    }

    private suspend fun fetchCellPipeline(cells: List<PoiGridCell>, cameraCenter: Position) {
        if (cells.isEmpty()) return
        // Sort by distance from camera center (nearest first)
        val sorted = cells.sortedBy { cell ->
            val cellCenter = Position(
                latitude = (cell.bounds.southwest.latitude + cell.bounds.northeast.latitude) / 2.0,
                longitude = (cell.bounds.southwest.longitude + cell.bounds.northeast.longitude) / 2.0,
            )
            distance(cameraCenter, cellCenter).inMeters
        }
        cellsLoadingTotal = sorted.size
        cellsLoadingComplete = 0
        android.util.Log.d("POI_DEBUG", "fetchCellPipeline: ${sorted.size} cells to fetch")
        // Fetch up to 2 cells concurrently per batch
        val batches = sorted.chunked(2)
        var completedSoFar = 0
        for (batch in batches) {
            if (!networkStatus.connected) {
                android.util.Log.d("POI_DEBUG", "fetchCellPipeline: offline, breaking")
                break
            }
            coroutineScope {
                batch.map { cell ->
                    async {
                        try {
                            val result = overpassRepo.fetchPoisForTile(cell.bounds, enabledPoiCategories)
                            android.util.Log.d("POI_DEBUG", "Cell ${cell.id}: ${result.features.size} features")
                            synchronized(cellCache) {
                                cellCache[cell.id] = CachedCell(
                                    cellId = cell.id,
                                    features = result.features,
                                    fetchedAtMs = System.currentTimeMillis(),
                                    bounds = cell.bounds,
                                )
                                failedCells.remove(cell.id)
                            }
                        } catch (e: PostpassServerException) {
                            android.util.Log.e("POI_DEBUG", "Cell ${cell.id} server error: ${e.message}")
                            failedCells[cell.id] = System.currentTimeMillis()
                            interCellDelayMs = min(interCellDelayMs * 2, MAX_INTER_CELL_DELAY_MS)
                            android.util.Log.d("POI_DEBUG", "Backoff: interCellDelayMs=$interCellDelayMs")
                        } catch (e: Exception) {
                            android.util.Log.e("POI_DEBUG", "Cell ${cell.id} fetch failed: ${e.message}")
                            failedCells[cell.id] = System.currentTimeMillis()
                        }
                    }
                }.forEach { it.await() }
            }
            completedSoFar += batch.size
            cellsLoadingComplete = completedSoFar
            rebuildMergedFeatures()
            if (completedSoFar < sorted.size) {
                delay(interCellDelayMs)
            }
        }
        cellsLoadingTotal = 0
        cellsLoadingComplete = 0
    }

    /**
     * Called from camera-settle watcher to extend coverage as user pans.
     * Evicts stale cells, then fetches new ones.
     */
    fun onCameraSettled(lat: Double, lon: Double, zoom: Double) {
        if (!poiPipelineActive) return
        if (zoom < 9.0) return
        if (!networkStatus.connected) return

        // Check if viewport has reached the edge of existing coverage
        val viewBounds = viewportBoundsForPoi(lat, lon, zoom)
        val currentLoadBounds = poiLoadBounds
        if (currentLoadBounds != null) {
            val fullyContained =
                viewBounds.southwest.latitude >= currentLoadBounds.southwest.latitude &&
                viewBounds.northeast.latitude <= currentLoadBounds.northeast.latitude &&
                viewBounds.southwest.longitude >= currentLoadBounds.southwest.longitude &&
                viewBounds.northeast.longitude <= currentLoadBounds.northeast.longitude
            if (fullyContained) {
                android.util.Log.d("POI_DEBUG", "onCameraSettled: viewport fully contained, skipping")
                return
            }
        }

        val (loadBounds, _) = PoiViewportChunks.gridCellsForManualLoad(viewBounds)
        android.util.Log.d("POI_DEBUG", "onCameraSettled: viewport outside coverage, computing cells")

        // Evict cells outside 1.5× loadBounds that are older than 60s
        val evictionBounds = PoiViewportChunks.padBounds(loadBounds, 1.5)
        val now = System.currentTimeMillis()
        val toEvict = cellCache.entries.filter { entry ->
            val cached = entry.value
            val outside = cached.bounds.northeast.latitude < evictionBounds.southwest.latitude ||
                cached.bounds.southwest.latitude > evictionBounds.northeast.latitude ||
                cached.bounds.northeast.longitude < evictionBounds.southwest.longitude ||
                cached.bounds.southwest.longitude > evictionBounds.northeast.longitude
            outside && (now - cached.fetchedAtMs) > EVICTION_AGE_MS
        }
        if (toEvict.isNotEmpty()) {
            for (entry in toEvict) cellCache.remove(entry.key)
            rebuildMergedFeatures()
        }

        val missingCells = cellsNeedingFetch(loadBounds)
        if (missingCells.isEmpty()) return

        android.util.Log.d("POI_DEBUG", "onCameraSettled: ${missingCells.size} new cells needed")
        interCellDelayMs = 150L
        val cameraCenter = Position(latitude = lat, longitude = lon)
        poiFetchJob?.cancel()
        poiFetchJob = viewModelScope.launch {
            fetchCellPipeline(missingCells, cameraCenter)
        }
    }

    private fun triggerPipelineForCurrentViewport() {
        val cam = pendingCameraInfo ?: return
        val viewBounds = viewportBoundsForPoi(cam.lat, cam.lon, cam.zoom)
        val (loadBounds, _) = PoiViewportChunks.gridCellsForManualLoad(viewBounds)
        val missingCells = cellsNeedingFetch(loadBounds)
        if (missingCells.isEmpty()) return
        interCellDelayMs = 150L
        val cameraCenter = Position(latitude = cam.lat, longitude = cam.lon)
        poiFetchJob?.cancel()
        poiFetchJob = viewModelScope.launch {
            fetchCellPipeline(missingCells, cameraCenter)
        }
    }

    // --- Search ---

    fun updateSearchQuery(query: String) {
        searchQuery = query
        if (searchByCategory) {
            selectedCategory = null
            searchResults = emptyList()
        } else {
            triggerNameSearch()
        }
    }

    fun updateSearchByCategory(byCategory: Boolean) {
        searchByCategory = byCategory
        searchResults = emptyList()
        selectedCategory = null
        searchQuery = ""
    }

    fun clearSelectedCategory() {
        selectedCategory = null
        searchResults = emptyList()
    }

    // Stored externally since ViewModel doesn't own location
    var userPositionForSearch: Position? = null

    private fun triggerNameSearch() {
        searchJob?.cancel()
        if (searchQuery.length < 2) {
            searchResults = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            isSearching = true
            delay(300L)
            val cam = pendingCameraInfo ?: return@launch
            val viewbox = computeViewBox(cam.lat, cam.lon, cam.zoom)
            searchResults = geocodingRepo.searchByName(
                query = searchQuery,
                centerLat = cam.lat,
                centerLon = cam.lon,
                bbox = viewbox,
                userPosition = userPositionForSearch,
            )
            isSearching = false
        }
    }

    data class CameraInfo(val lat: Double, val lon: Double, val zoom: Double)
    var pendingCameraInfo: CameraInfo? = null

    /**
     * Last visible geographic bounds from [org.maplibre.compose.camera.CameraProjection.queryVisibleBoundingBox];
     * updated from composition when the map is ready. When null, POI loading falls back to [PoiViewportChunks.approximateViewportBounds].
     */
    var poiMapVisibleBounds: BoundingBox? by mutableStateOf(null)
        private set

    /** Called from [MapScreen] [androidx.compose.runtime.SideEffect] when projection is available. */
    fun updatePoiMapVisibleBounds(bounds: BoundingBox?) {
        poiMapVisibleBounds = bounds
    }

    /** Screen dimensions in dp, set from composition. */
    var screenWidthDp: Double = 360.0
    var screenHeightDp: Double = 800.0

    private fun viewportBoundsForPoi(lat: Double, lon: Double, zoom: Double): BoundingBox =
        poiMapVisibleBounds
            ?: PoiViewportChunks.approximateViewportBounds(lat, lon, zoom, screenWidthDp, screenHeightDp)

    fun selectCategoryWithCamera(category: PoiCategory) {
        selectedCategory = category
        val cam = pendingCameraInfo ?: return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            isSearching = true
            val viewbox = computeViewBox(cam.lat, cam.lon, cam.zoom)
            searchResults = geocodingRepo.searchByCategory(category, viewbox, userPositionForSearch)
            isSearching = false
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

    // --- Reset ---

    fun resetToDefaults(systemDefault: MapStyle, onStyleChange: (MapStyle) -> Unit) {
        onStyleChange(systemDefault)
        weatherMode = WeatherMode.valueOf(PrefsDefaults.WEATHER_MODE)
        isWeatherPlaying = PrefsDefaults.WEATHER_PLAYING
        showLegend = PrefsDefaults.SHOW_LEGEND
        showTimeline = PrefsDefaults.SHOW_TIMELINE
        radarOpacity = PrefsDefaults.RADAR_OPACITY
        useMetric = PrefsDefaults.USE_METRIC
        speedSize = PrefsDefaults.SPEED_SIZE
        navWidgetSize = PrefsDefaults.NAV_WIDGET_SIZE
        weatherWidgetSize = PrefsDefaults.WEATHER_WIDGET_SIZE
        compassWidgetSize = PrefsDefaults.COMPASS_WIDGET_SIZE
        windEnabled = PrefsDefaults.WIND_ENABLED
        windSpeedUnit = WindSpeedUnit.valueOf(PrefsDefaults.WIND_SPEED_UNIT)
        temperatureUnit = TemperatureUnit.valueOf(PrefsDefaults.TEMPERATURE_UNIT)
        keepScreenOn = PrefsDefaults.KEEP_SCREEN_ON
        useGps = PrefsDefaults.USE_GPS
        gpsIconOpacity = PrefsDefaults.GPS_ICON_OPACITY
        mapCenterOffsetPortraitFraction = PrefsDefaults.MAP_CENTER_OFFSET_PORTRAIT_FRACTION
        mapCenterOffsetLandscapeFraction = PrefsDefaults.MAP_CENTER_OFFSET_LANDSCAPE_FRACTION
        isTrackingCamera = true
        isNorthUp = false
        poiPosition = null
        poiName = null
        enabledPoiCategories = emptySet()
        poiFetchJob?.cancel()
        cellCache.clear()
        failedCells.clear()
        poiPipelineActive = false
        cellsLoadingTotal = 0
        cellsLoadingComplete = 0
        nearbyPoiFeatures = FeatureCollection(emptyList())
        poiFetchRegion = null
        poiLoadBounds = null
        poiCategoriesVersion++
        prefsRepo.resetToDefaults(systemDefault)
        showResetConfirm = false
        showSettings = false
        showActionsDrawer = false
        startWeatherPollingIfActive()
        startWeatherAnimationIfPlaying()
    }

    // --- Zoom ---

    fun onZoomChanged(zoom: Float) {
        prefsRepo.zoomLevel = zoom
    }
}
