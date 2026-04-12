package ca.voiditswarranty.roadtripradar.car

import android.location.Location
import android.app.Presentation
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import ca.voiditswarranty.roadtripradar.data.OpenMeteoSnapshot
import ca.voiditswarranty.roadtripradar.model.MapStyle
import ca.voiditswarranty.roadtripradar.model.TemperatureUnit
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import ca.voiditswarranty.roadtripradar.model.buildRadarRingsData
import ca.voiditswarranty.roadtripradar.model.ringDistancesForZoom
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.spatialk.geojson.Position

class CarMapSurfaceCallback(
    private val carContext: CarContext,
    private val session: RoadTripRadarCarSession,
) : SurfaceCallback {

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var mapView: MapView? = null
    private var mapLibreMap: MapLibreMap? = null
    private var isTracking = true
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var stableArea: Rect? = null
    private var weatherOverlay: TextView? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentRadarPath: String? = null
    private var currentPoiPosition: Position? = null

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        MapLibre.getInstance(carContext)

        val surface = surfaceContainer.surface ?: return
        val width = surfaceContainer.width
        val height = surfaceContainer.height
        val dpi = surfaceContainer.dpi

        val dm = carContext.getSystemService(DisplayManager::class.java)
        val vd = dm.createVirtualDisplay(
            "RoadTripRadarCarMap",
            width, height, dpi,
            surface,
            0,
        )
        virtualDisplay = vd

        val pres = Presentation(carContext, vd.display)
        presentation = pres

        val mv = MapView(carContext)
        mapView = mv

        val container = FrameLayout(carContext)
        container.addView(mv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        val overlay = TextView(carContext).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(180, 30, 30, 30))
                cornerRadius = 16f
            }
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(24, 16, 24, 16)
            text = "—"
        }
        val overlayParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END,
        )
        container.addView(overlay, overlayParams)
        weatherOverlay = overlay

        pres.setContentView(container)
        pres.show()

        mv.onCreate(null)
        mv.onStart()
        mv.onResume()

        surfaceWidth = width
        surfaceHeight = height

        val styleUri = if (session.isDarkMode) MapStyle.COLOR_DARK.styleUri else MapStyle.LIBERTY.styleUri
        val lastPos = session.prefsRepo.lastKnownPosition
        val zoom = session.prefsRepo.zoomLevel.toDouble()

        mv.getMapAsync { map ->
            mapLibreMap = map
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(lastPos.latitude, lastPos.longitude))
                .zoom(zoom)
                .build()
            map.setStyle(Style.Builder().fromUri(styleUri)) { style ->
                map.uiSettings.isCompassEnabled = true
                map.uiSettings.setCompassFadeFacingNorth(false)
                map.uiSettings.isLogoEnabled = false
                map.uiSettings.isAttributionEnabled = false

                setupLocationComponent(map, style)

                val path = session.latestRadarPath
                if (path != null) {
                    addRadarLayer(style, path, session.radarOpacity)
                }

                addRingLayers(style)

                val poi = session.poiPosition
                if (poi != null) {
                    addPoiLayers(style, poi)
                }

                map.addOnCameraIdleListener { updateRings() }
            }
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        mainHandler.post {
            mapView?.onPause()
            mapView?.onStop()
            mapView?.onDestroy()
        }
        mapView = null
        mapLibreMap = null

        presentation?.dismiss()
        presentation = null

        virtualDisplay?.release()
        virtualDisplay = null

        currentRadarPath = null
        currentPoiPosition = null
        stableArea = null
        surfaceWidth = 0
        surfaceHeight = 0
        weatherOverlay = null
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) { }

    override fun onStableAreaChanged(stableArea: Rect) {
        this.stableArea = stableArea
        applyCameraPadding()
        updateOverlayPosition()
    }

    private fun applyCameraPadding() {
        val map = mapLibreMap ?: return
        val sa = stableArea ?: return
        val offsetFraction = session.prefsRepo.carMapCenterOffsetFraction
        val topPadding = (sa.top + sa.height() * (1.0 - 2.0 * offsetFraction)).coerceAtLeast(0.0)
        val bottomPadding = (surfaceHeight - sa.bottom).toDouble().coerceAtLeast(0.0)
        map.moveCamera(CameraUpdateFactory.paddingTo(0.0, topPadding, 0.0, bottomPadding))
    }

    private fun updateOverlayPosition() {
        val sa = stableArea ?: return
        mainHandler.post {
            val lp = weatherOverlay?.layoutParams as? FrameLayout.LayoutParams ?: return@post
            lp.topMargin = sa.top + 16
            lp.rightMargin = (surfaceWidth - sa.right) + 16
            weatherOverlay?.layoutParams = lp
        }
    }

    fun updateWeatherOverlay(snapshot: OpenMeteoSnapshot?) {
        mainHandler.post {
            weatherOverlay?.text = formatWeatherText(snapshot)
        }
    }

    private fun formatWeatherText(snapshot: OpenMeteoSnapshot?): String {
        if (snapshot == null) return "—"
        val tempUnit = session.prefsRepo.temperatureUnit
        val windUnit = session.prefsRepo.windSpeedUnit

        val temp = formatTemp(snapshot.temperatureCelsius, tempUnit)
        val trend = snapshot.tempTrendCelsius?.let { " (${formatTrend(it, tempUnit)})" } ?: ""
        val arrow = windDirectionArrow(snapshot.windDirectionDeg)
        val wind = windValue(snapshot.windSpeedKmh, windUnit)
        val gusts = windValue(snapshot.windGustsKmh, windUnit)
        val unit = windUnitLabel(windUnit)
        return "$temp$trend\n$arrow $wind↑$gusts $unit"
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        if (isTracking) {
            isTracking = false
            try { mapLibreMap?.locationComponent?.cameraMode = CameraMode.NONE } catch (_: Exception) { }
        }
        val map = mapLibreMap ?: return
        map.scrollBy(-distanceX, -distanceY)
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        val map = mapLibreMap ?: return
        val currentZoom = map.cameraPosition.zoom
        val zoomDelta = Math.log(scaleFactor.toDouble()) / Math.log(2.0)
        map.moveCamera(CameraUpdateFactory.zoomTo(currentZoom + zoomDelta))
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        if (isTracking) {
            isTracking = false
            try { mapLibreMap?.locationComponent?.cameraMode = CameraMode.NONE } catch (_: Exception) { }
        }
    }

    fun getCameraBearing(): Double = mapLibreMap?.cameraPosition?.bearing ?: 0.0

    fun getUserPosition(): Position? {
        val loc = mapLibreMap?.locationComponent?.lastKnownLocation ?: return null
        return Position(longitude = loc.longitude, latitude = loc.latitude)
    }

    fun zoomIn() {
        val map = mapLibreMap ?: return
        map.animateCamera(CameraUpdateFactory.zoomIn())
    }

    fun zoomOut() {
        val map = mapLibreMap ?: return
        map.animateCamera(CameraUpdateFactory.zoomOut())
    }

    fun recenter() {
        isTracking = true
        val map = mapLibreMap ?: return
        try {
            map.locationComponent.cameraMode = CameraMode.TRACKING_GPS
        } catch (_: Exception) { }
    }

    fun updateRadar(path: String?, opacity: Float) {
        val map = mapLibreMap ?: return
        val style = map.style ?: return

        if (path == currentRadarPath) return

        removeRadarLayer(style)

        if (path != null) {
            addRadarLayer(style, path, opacity)
        }
    }

    fun updateMapStyle(isDark: Boolean) {
        val mv = mapView ?: return
        val styleUri = if (isDark) MapStyle.COLOR_DARK.styleUri else MapStyle.LIBERTY.styleUri
        mv.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(styleUri)) { style ->
                setupLocationComponent(map, style)
                val path = session.latestRadarPath
                if (path != null) {
                    addRadarLayer(style, path, session.radarOpacity)
                }
                addRingLayers(style)
                val poi = session.poiPosition
                if (poi != null) {
                    addPoiLayers(style, poi)
                } else {
                    currentPoiPosition = null
                }
            }
        }
    }

    fun updateDestination(position: Position?) {
        val map = mapLibreMap ?: return
        val style = map.style ?: return

        removePoiLayers(style)

        if (position != null) {
            addPoiLayers(style, position)
        }
    }

    fun onLocationUpdate(location: Location) {
        val map = mapLibreMap ?: return
        try {
            map.locationComponent.forceLocationUpdate(location)
        } catch (_: Exception) { }
    }

    private fun setupLocationComponent(map: MapLibreMap, style: Style) {
        try {
            val lc = map.locationComponent
            lc.activateLocationComponent(
                org.maplibre.android.location.LocationComponentActivationOptions
                    .builder(carContext, style)
                    .useDefaultLocationEngine(false)
                    .build()
            )
            lc.isLocationComponentEnabled = true
            lc.renderMode = RenderMode.GPS
            if (isTracking) {
                lc.cameraMode = CameraMode.TRACKING_GPS
            }
        } catch (_: SecurityException) { }
    }

    // --- Radar layers ---

    private fun addRadarLayer(style: Style, path: String, opacity: Float) {
        val tileUrl = "https://tilecache.rainviewer.com$path/512/{z}/{x}/{y}/2/1_1.png"
        val tileSet = TileSet("2.1.0", tileUrl)
        tileSet.maxZoom = 7f
        val source = RasterSource(RADAR_SOURCE_ID, tileSet, 512)
        style.addSource(source)

        val layer = RasterLayer(RADAR_LAYER_ID, RADAR_SOURCE_ID)
        layer.setProperties(PropertyFactory.rasterOpacity(opacity))
        style.addLayer(layer)

        currentRadarPath = path
    }

    private fun removeRadarLayer(style: Style) {
        style.removeLayer(RADAR_LAYER_ID)
        style.removeSource(RADAR_SOURCE_ID)
        currentRadarPath = null
    }

    // --- Range ring layers ---

    private fun getRingsCenter(): Position {
        val loc = mapLibreMap?.locationComponent?.lastKnownLocation
        return if (loc != null) {
            Position(longitude = loc.longitude, latitude = loc.latitude)
        } else {
            val target = mapLibreMap?.cameraPosition?.target
            if (target != null) Position(longitude = target.longitude, latitude = target.latitude)
            else session.prefsRepo.lastKnownPosition
        }
    }

    private fun buildRingsGeoJson(center: Position, zoom: Double): Pair<String, String> {
        val useMetric = session.prefsRepo.useMetric
        val data = buildRadarRingsData(center, ringDistancesForZoom(zoom), 0.0, useMetric)

        val ringsBuilder = StringBuilder("""{"type":"FeatureCollection","features":[""")
        data.ringsFeatures.features.forEachIndexed { i, feature ->
            if (i > 0) ringsBuilder.append(",")
            ringsBuilder.append("""{"type":"Feature","geometry":{"type":"LineString","coordinates":[""")
            feature.geometry.coordinates.forEachIndexed { j, pos ->
                if (j > 0) ringsBuilder.append(",")
                ringsBuilder.append("[${pos.longitude},${pos.latitude}]")
            }
            ringsBuilder.append("""]},"properties":{}}""")
        }
        ringsBuilder.append("]}")

        val labelsBuilder = StringBuilder("""{"type":"FeatureCollection","features":[""")
        data.labelsFeatures.features.forEachIndexed { i, feature ->
            if (i > 0) labelsBuilder.append(",")
            val pos = feature.geometry.coordinates
            val label = feature.properties?.get("label")?.jsonPrimitive?.content ?: ""
            labelsBuilder.append("""{"type":"Feature","geometry":{"type":"Point","coordinates":[${pos.longitude},${pos.latitude}]},"properties":{"label":"$label"}}""")
        }
        labelsBuilder.append("]}")

        return Pair(ringsBuilder.toString(), labelsBuilder.toString())
    }

    private fun addRingLayers(style: Style) {
        val center = getRingsCenter()
        val zoom = mapLibreMap?.cameraPosition?.zoom ?: session.prefsRepo.zoomLevel.toDouble()
        val (ringsJson, labelsJson) = buildRingsGeoJson(center, zoom)

        style.addSource(GeoJsonSource(RINGS_SOURCE_ID, ringsJson))
        style.addSource(GeoJsonSource(RINGS_LABELS_SOURCE_ID, labelsJson))

        val isDark = session.isDarkMode
        val ringColor = if (isDark) Color.LTGRAY else Color.BLACK
        val haloColor = if (isDark) Color.DKGRAY else Color.WHITE

        val ringsLayer = LineLayer(RINGS_LAYER_ID, RINGS_SOURCE_ID)
        ringsLayer.setProperties(
            PropertyFactory.lineColor(ringColor),
            PropertyFactory.lineWidth(3.5f),
            PropertyFactory.lineOpacity(0.7f),
            PropertyFactory.lineDasharray(arrayOf(4f, 3f)),
        )
        style.addLayer(ringsLayer)

        val labelsLayer = SymbolLayer(RINGS_LABELS_LAYER_ID, RINGS_LABELS_SOURCE_ID)
        labelsLayer.setProperties(
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textColor(ringColor),
            PropertyFactory.textHaloColor(haloColor),
            PropertyFactory.textHaloWidth(3f),
            PropertyFactory.textSize(16f),
            PropertyFactory.textRotationAlignment(Property.TEXT_ROTATION_ALIGNMENT_VIEWPORT),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
        )
        style.addLayer(labelsLayer)
    }

    private fun updateRings() {
        val map = mapLibreMap ?: return
        val style = map.style ?: return

        val center = getRingsCenter()
        val zoom = map.cameraPosition.zoom
        val (ringsJson, labelsJson) = buildRingsGeoJson(center, zoom)

        style.getSourceAs<GeoJsonSource>(RINGS_SOURCE_ID)?.setGeoJson(ringsJson)
        style.getSourceAs<GeoJsonSource>(RINGS_LABELS_SOURCE_ID)?.setGeoJson(labelsJson)
    }

    // --- POI destination layers ---

    private fun addPoiLayers(style: Style, poiPos: Position) {
        val pointGeoJson = """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${poiPos.longitude},${poiPos.latitude}]},"properties":{}}]}"""
        val pointSource = GeoJsonSource(POI_POINT_SOURCE_ID, pointGeoJson)
        style.addSource(pointSource)

        val marker = CircleLayer(POI_MARKER_LAYER_ID, POI_POINT_SOURCE_ID)
        marker.setProperties(
            PropertyFactory.circleRadius(12f),
            PropertyFactory.circleColor(Color.RED),
            PropertyFactory.circleStrokeColor(Color.WHITE),
            PropertyFactory.circleStrokeWidth(2f),
        )
        style.addLayer(marker)

        val userLocation = mapLibreMap?.locationComponent?.lastKnownLocation
        if (userLocation != null) {
            val lineGeoJson = """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[[${userLocation.longitude},${userLocation.latitude}],[${poiPos.longitude},${poiPos.latitude}]]},"properties":{}}]}"""
            val lineSource = GeoJsonSource(POI_LINE_SOURCE_ID, lineGeoJson)
            style.addSource(lineSource)

            val line = LineLayer(POI_LINE_LAYER_ID, POI_LINE_SOURCE_ID)
            line.setProperties(
                PropertyFactory.lineColor(Color.BLUE),
                PropertyFactory.lineWidth(2f),
                PropertyFactory.lineOpacity(0.8f),
            )
            style.addLayerBelow(line, POI_MARKER_LAYER_ID)
        }

        currentPoiPosition = poiPos
    }

    private fun removePoiLayers(style: Style) {
        style.removeLayer(POI_MARKER_LAYER_ID)
        style.removeSource(POI_POINT_SOURCE_ID)
        style.removeLayer(POI_LINE_LAYER_ID)
        style.removeSource(POI_LINE_SOURCE_ID)
        currentPoiPosition = null
    }

    // --- Weather text formatting ---

    private fun formatTemp(celsius: Double, unit: TemperatureUnit): String {
        val value = when (unit) {
            TemperatureUnit.CELSIUS -> celsius
            TemperatureUnit.FAHRENHEIT -> celsius * 9.0 / 5.0 + 32
            TemperatureUnit.KELVIN -> celsius + 273.15
        }
        val rounded = kotlin.math.round(value).toInt()
        return when (unit) {
            TemperatureUnit.CELSIUS -> "$rounded°C"
            TemperatureUnit.FAHRENHEIT -> "$rounded°F"
            TemperatureUnit.KELVIN -> "$rounded K"
        }
    }

    private fun formatTrend(deltaCelsius: Double, unit: TemperatureUnit): String {
        val converted = when (unit) {
            TemperatureUnit.CELSIUS -> deltaCelsius
            TemperatureUnit.FAHRENHEIT -> deltaCelsius * 9.0 / 5.0
            TemperatureUnit.KELVIN -> deltaCelsius
        }
        val rounded = kotlin.math.round(converted * 10.0) / 10.0
        val sign = if (rounded > 0) "+" else if (rounded < 0) "-" else ""
        val mag = kotlin.math.abs(rounded)
        val num = String.format(java.util.Locale.US, "%.1f", mag)
        val suffix = if (unit == TemperatureUnit.KELVIN) "" else "°"
        return "$sign$num$suffix/h"
    }

    private fun windValue(kmh: Double, unit: WindSpeedUnit): Int = when (unit) {
        WindSpeedUnit.KMH -> kmh.toInt()
        WindSpeedUnit.MPH -> (kmh * 0.621371).toInt()
        WindSpeedUnit.KNOTS -> (kmh * 0.539957).toInt()
    }

    private fun windUnitLabel(unit: WindSpeedUnit): String = when (unit) {
        WindSpeedUnit.KMH -> "km/h"
        WindSpeedUnit.MPH -> "mph"
        WindSpeedUnit.KNOTS -> "kn"
    }

    private fun windDirectionArrow(deg: Int): String {
        val adjusted = (deg + 180) % 360
        val arrows = arrayOf("↑", "↗", "→", "↘", "↓", "↙", "←", "↖")
        val index = ((adjusted + 22.5) / 45.0).toInt() % 8
        return arrows[index]
    }

    companion object {
        private const val RADAR_SOURCE_ID = "car-radar-source"
        private const val RADAR_LAYER_ID = "car-radar-layer"
        private const val RINGS_SOURCE_ID = "car-rings-source"
        private const val RINGS_LAYER_ID = "car-rings-layer"
        private const val RINGS_LABELS_SOURCE_ID = "car-rings-labels-source"
        private const val RINGS_LABELS_LAYER_ID = "car-rings-labels-layer"
        private const val POI_POINT_SOURCE_ID = "car-poi-point-source"
        private const val POI_MARKER_LAYER_ID = "car-poi-marker"
        private const val POI_LINE_SOURCE_ID = "car-poi-line-source"
        private const val POI_LINE_LAYER_ID = "car-poi-line"
    }
}
