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
import ca.voiditswarranty.roadtripradar.data.PreferencesRepository
import ca.voiditswarranty.roadtripradar.data.ViewBox
import ca.voiditswarranty.roadtripradar.data.WeatherRepository
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.model.NetworkStatus
import ca.voiditswarranty.roadtripradar.model.NetworkTransport
import ca.voiditswarranty.roadtripradar.model.PoiCategory
import ca.voiditswarranty.roadtripradar.model.PrefsDefaults
import ca.voiditswarranty.roadtripradar.model.SearchResult
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.spatialk.geojson.Position

class MapViewModel(
    appContext: Context,
    val prefsRepo: PreferencesRepository,
    private val weatherRepo: WeatherRepository = WeatherRepository(),
    private val openMeteoRepo: OpenMeteoRepository = OpenMeteoRepository(),
    private val geocodingRepo: GeocodingRepository = GeocodingRepository(),
) : ViewModel() {

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

    private companion object {
        private const val RADAR_POLL_MS = 60_000L
        private const val LOCAL_WEATHER_SUCCESS_MS = 600_000L
        private const val LOCAL_WEATHER_RETRY_MS = 60_000L
    }

    private var lastGenerated = 0L
    private var localWeatherAnchor: Position? = null
    private var weatherPollingJob: Job? = null
    private var localWeatherPollJob: Job? = null
    private var weatherAnimationJob: Job? = null
    private var searchJob: Job? = null

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
