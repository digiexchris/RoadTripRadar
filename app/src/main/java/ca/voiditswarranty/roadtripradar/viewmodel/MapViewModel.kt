package ca.voiditswarranty.roadtripradar.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.voiditswarranty.roadtripradar.data.GeocodingRepository
import ca.voiditswarranty.roadtripradar.data.PreferencesRepository
import ca.voiditswarranty.roadtripradar.data.ViewBox
import ca.voiditswarranty.roadtripradar.data.WeatherRepository
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.model.PoiCategory
import ca.voiditswarranty.roadtripradar.model.PrefsDefaults
import ca.voiditswarranty.roadtripradar.model.SearchResult
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.spatialk.geojson.Position

class MapViewModel(
    val prefsRepo: PreferencesRepository,
    private val weatherRepo: WeatherRepository = WeatherRepository(),
    private val geocodingRepo: GeocodingRepository = GeocodingRepository(),
) : ViewModel() {

    // Weather
    var weatherMode by mutableStateOf(prefsRepo.weatherMode)
        private set
    var isWeatherPlaying by mutableStateOf(prefsRepo.isWeatherPlaying)
        private set
    var radarFramePaths by mutableStateOf(emptyList<String>())
        private set
    var radarFrameTimes by mutableStateOf(emptyList<Long>())
        private set
    var currentFrameIndex by mutableStateOf(0)
        private set
    var radarOpacity by mutableStateOf(prefsRepo.radarOpacity)
        private set

    // Settings
    var useMetric by mutableStateOf(prefsRepo.useMetric)
        private set
    var speedSize by mutableStateOf(prefsRepo.speedSize)
        private set
    var navWidgetSize by mutableStateOf(prefsRepo.navWidgetSize)
        private set
    var keepScreenOn by mutableStateOf(prefsRepo.keepScreenOn)
        private set

    // POI
    var poiPosition by mutableStateOf(prefsRepo.poiPosition)
        private set
    var poiName by mutableStateOf(prefsRepo.poiName ?: if (prefsRepo.poiPosition != null) "Dropped Pin" else null)
        private set

    // UI state
    var isTrackingCamera by mutableStateOf(true)
    var showSettings by mutableStateOf(false)
        private set
    var showResetConfirm by mutableStateOf(false)
        private set
    var showPoiSearch by mutableStateOf(false)
        private set
    var showHelp by mutableStateOf(false)
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
    private var weatherPollingJob: Job? = null
    private var weatherAnimationJob: Job? = null
    private var searchJob: Job? = null

    init {
        if (prefsRepo.acceptedTermsVersion != PrefsDefaults.TERMS_VERSION) {
            showTerms = true
            termsNeedAcceptance = true
        }
        startWeatherPollingIfActive()
        startWeatherAnimationIfPlaying()
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
                delay(60_000)
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

    fun updateKeepScreenOn(on: Boolean) {
        keepScreenOn = on
        prefsRepo.keepScreenOn = on
    }

    fun openSettings() { showSettings = true }
    fun closeSettings() { showSettings = false }
    fun openResetConfirm() { showResetConfirm = true }
    fun closeResetConfirm() { showResetConfirm = false }
    fun openPoiSearch() { showPoiSearch = true }
    fun closePoiSearch() { showPoiSearch = false }
    fun openHelp() { showHelp = true }
    fun closeHelp() { showHelp = false }

    fun viewTerms() {
        showHelp = false
        showTerms = true
    }

    fun acceptTerms() {
        prefsRepo.acceptedTermsVersion = PrefsDefaults.TERMS_VERSION
        termsNeedAcceptance = false
        showTerms = false
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
        radarOpacity = PrefsDefaults.RADAR_OPACITY
        useMetric = PrefsDefaults.USE_METRIC
        speedSize = PrefsDefaults.SPEED_SIZE
        navWidgetSize = PrefsDefaults.NAV_WIDGET_SIZE
        keepScreenOn = PrefsDefaults.KEEP_SCREEN_ON
        poiPosition = null
        poiName = null
        prefsRepo.resetToDefaults(systemDefault)
        showResetConfirm = false
        showSettings = false
        startWeatherPollingIfActive()
        startWeatherAnimationIfPlaying()
    }

    // --- Zoom ---

    fun onZoomChanged(zoom: Float) {
        prefsRepo.zoomLevel = zoom
    }
}
