package ca.voiditswarranty.roadtripradar.car

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import ca.voiditswarranty.roadtripradar.data.GeocodingRepository
import ca.voiditswarranty.roadtripradar.data.OpenMeteoRepository
import ca.voiditswarranty.roadtripradar.data.OpenMeteoSnapshot
import ca.voiditswarranty.roadtripradar.data.PreferencesRepository
import ca.voiditswarranty.roadtripradar.data.WeatherRepository
import ca.voiditswarranty.roadtripradar.model.PrefsDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.spatialk.geojson.Position

class RoadTripRadarCarSession : Session() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val weatherRepo = WeatherRepository()
    private val openMeteoRepo = OpenMeteoRepository()
    val geocodingRepo = GeocodingRepository()

    lateinit var prefsRepo: PreferencesRepository
        private set

    var latestRadarPath: String? = null
        private set
    var radarOpacity: Float = PrefsDefaults.RADAR_OPACITY
        private set
    var isDarkMode: Boolean = false
        private set

    var poiPosition: Position? = null
        private set
    var poiName: String? = null
        private set
    var openMeteoSnapshot: OpenMeteoSnapshot? = null
        private set

    private var weatherPollingJob: Job? = null
    private var weatherDataJob: Job? = null
    private var navInfoJob: Job? = null
    private var lastGenerated: Long = 0L
    private var carMapScreen: CarMapScreen? = null
    private var selfWritingPoi = false
    private var locationManager: LocationManager? = null

    private val locationListener = LocationListener { location ->
        carMapScreen?.surfaceCallback?.onLocationUpdate(location)
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (selfWritingPoi) return@OnSharedPreferenceChangeListener
        when (key) {
            "poi_lat", "poi_lon", "poi_name" -> {
                poiPosition = prefsRepo.poiPosition
                poiName = prefsRepo.poiName
                carMapScreen?.onDestinationChanged()
                if (poiPosition != null) startNavInfoPolling() else stopNavInfoPolling()
            }
        }
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) {
            prefsRepo = PreferencesRepository(carContext)
            isDarkMode = carContext.isDarkMode
            radarOpacity = prefsRepo.radarOpacity
            poiPosition = prefsRepo.poiPosition
            poiName = prefsRepo.poiName
            prefsRepo.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
            startWeatherPolling()
            startWeatherDataPolling()
            startLocationUpdates()
        }

        override fun onDestroy(owner: LifecycleOwner) {
            prefsRepo.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
            locationManager?.removeUpdates(locationListener)
            scope.cancel()
        }
    }

    override fun onCreateScreen(intent: Intent): Screen {
        lifecycle.addObserver(lifecycleObserver)
        val screen = CarMapScreen(carContext, this)
        carMapScreen = screen
        handleNavigateIntent(intent)
        if (poiPosition != null) startNavInfoPolling()
        return screen
    }

    override fun onNewIntent(intent: Intent) {
        handleNavigateIntent(intent)
    }

    override fun onCarConfigurationChanged(newConfiguration: Configuration) {
        val wasDark = isDarkMode
        isDarkMode = carContext.isDarkMode
        if (isDarkMode != wasDark) {
            carMapScreen?.onDarkModeChanged(isDarkMode)
        }
    }

    fun setDestination(position: Position, name: String) {
        poiPosition = position
        poiName = name
        persistPoi()
        carMapScreen?.onDestinationChanged()
        startNavInfoPolling()
    }

    fun clearDestination() {
        poiPosition = null
        poiName = null
        persistPoi()
        carMapScreen?.onDestinationChanged()
        stopNavInfoPolling()
    }

    private fun startNavInfoPolling() {
        navInfoJob?.cancel()
        navInfoJob = scope.launch {
            while (true) {
                delay(NAV_INFO_POLL_MS)
                carMapScreen?.invalidate()
            }
        }
    }

    private fun stopNavInfoPolling() {
        navInfoJob?.cancel()
        navInfoJob = null
    }

    private fun persistPoi() {
        selfWritingPoi = true
        prefsRepo.poiPosition = poiPosition
        prefsRepo.poiName = poiName
        selfWritingPoi = false
    }

    private fun handleNavigateIntent(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme != "geo") return
        parseGeoUri(uri)?.let { (position, name) ->
            setDestination(position, name)
        }
    }

    private fun parseGeoUri(uri: Uri): Pair<Position, String>? {
        val ssp = uri.schemeSpecificPart ?: return null
        val queryName = uri.getQueryParameter("q")

        val coordPart = ssp.split("?").firstOrNull() ?: ssp
        val parts = coordPart.split(",")
        if (parts.size >= 2) {
            val lat = parts[0].trim().toDoubleOrNull()
            val lon = parts[1].trim().toDoubleOrNull()
            if (lat != null && lon != null && (lat != 0.0 || lon != 0.0)) {
                val name = queryName ?: "Navigation Target"
                return Pair(Position(latitude = lat, longitude = lon), name)
            }
        }
        return null
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        try {
            val lm = carContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return
            locationManager = lm
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                locationListener,
                Looper.getMainLooper(),
            )
        } catch (_: SecurityException) { }
    }

    private fun startWeatherPolling() {
        weatherPollingJob?.cancel()
        weatherPollingJob = scope.launch {
            while (true) {
                try {
                    val data = weatherRepo.fetchFrames(lastGenerated)
                    if (data != null) {
                        lastGenerated = data.generated
                        latestRadarPath = data.paths.lastOrNull()
                        carMapScreen?.onRadarUpdated()
                    }
                } catch (_: Exception) { }
                delay(RADAR_POLL_MS)
            }
        }
    }

    private fun startWeatherDataPolling() {
        weatherDataJob?.cancel()
        weatherDataJob = scope.launch {
            while (true) {
                try {
                    val pos = prefsRepo.lastKnownPosition
                    openMeteoRepo.fetchCurrent(pos.latitude, pos.longitude)
                        .onSuccess { snapshot ->
                            openMeteoSnapshot = snapshot
                            carMapScreen?.onWeatherDataUpdated()
                        }
                } catch (_: Exception) { }
                delay(WEATHER_DATA_POLL_MS)
            }
        }
    }

    companion object {
        private const val RADAR_POLL_MS = 60_000L
        private const val NAV_INFO_POLL_MS = 1_000L
        private const val WEATHER_DATA_POLL_MS = 300_000L
    }
}
