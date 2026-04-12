package ca.voiditswarranty.roadtripradar.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Distance
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.core.graphics.drawable.IconCompat
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.turf.measurement.bearingTo
import org.maplibre.spatialk.turf.measurement.distance
import org.maplibre.spatialk.units.Bearing
import org.maplibre.spatialk.units.extensions.inDegrees
import org.maplibre.spatialk.units.extensions.inKilometers
import org.maplibre.spatialk.units.extensions.inMeters
import org.maplibre.spatialk.units.extensions.inMiles

class CarMapScreen(
    carContext: CarContext,
    private val session: RoadTripRadarCarSession,
) : Screen(carContext) {

    val surfaceCallback = CarMapSurfaceCallback(carContext, session)

    private var cachedArrowIcon: CarIcon? = null
    private var cachedArrowRotation: Float = Float.NaN

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
    }

    override fun onGetTemplate(): Template {
        val builder = NavigationTemplate.Builder()

        val actionStripBuilder = ActionStrip.Builder()
            .addAction(Action.APP_ICON)
            .addAction(
                Action.Builder()
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_search)
                        ).build()
                    )
                    .setOnClickListener {
                        screenManager.push(CarSearchScreen(carContext, session))
                    }
                    .build()
            )

        if (session.poiPosition != null) {
            actionStripBuilder.addAction(
                Action.Builder()
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_close_clear_cancel)
                        ).build()
                    )
                    .setOnClickListener { session.clearDestination() }
                    .build()
            )
        }
        builder.setActionStrip(actionStripBuilder.build())

        builder.setMapActionStrip(
            ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setIcon(
                            CarIcon.Builder(
                                IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_mylocation)
                            ).build()
                        )
                        .setOnClickListener { surfaceCallback.recenter() }
                        .build()
                )
                .addAction(
                    Action.Builder()
                        .setIcon(
                            CarIcon.Builder(
                                IconCompat.createWithResource(carContext, android.R.drawable.btn_plus)
                            ).build()
                        )
                        .setOnClickListener { surfaceCallback.zoomIn() }
                        .build()
                )
                .addAction(
                    Action.Builder()
                        .setIcon(
                            CarIcon.Builder(
                                IconCompat.createWithResource(carContext, android.R.drawable.btn_minus)
                            ).build()
                        )
                        .setOnClickListener { surfaceCallback.zoomOut() }
                        .build()
                )
                .build()
        )

        val poiPos = session.poiPosition
        val poiName = session.poiName
        if (poiPos != null && poiName != null) {
            val userPos = surfaceCallback.getUserPosition()
                ?: session.prefsRepo.lastKnownPosition
            val dist = distance(Point(userPos), Point(poiPos))
            val cameraBearing = surfaceCallback.getCameraBearing()

            val poiBearing = userPos.bearingTo(poiPos)
            val poiBearingDeg = (poiBearing - Bearing.North).inDegrees
            val arrowRotation = (poiBearingDeg - cameraBearing).toFloat()

            val useMetric = session.prefsRepo.useMetric
            val carDistance = if (useMetric) {
                val km = dist.inKilometers
                if (km < 1.0) {
                    Distance.create(dist.inMeters, Distance.UNIT_METERS)
                } else {
                    Distance.create(km, Distance.UNIT_KILOMETERS_P1)
                }
            } else {
                Distance.create(dist.inMiles, Distance.UNIT_MILES_P1)
            }

            val arrowIcon = getCachedArrowIcon(arrowRotation)

            val maneuver = Maneuver.Builder(Maneuver.TYPE_DESTINATION)
                .setIcon(arrowIcon)
                .build()

            val step = Step.Builder(poiName)
                .setManeuver(maneuver)
                .build()

            builder.setNavigationInfo(
                RoutingInfo.Builder()
                    .setCurrentStep(step, carDistance)
                    .build()
            )
        }

        return builder.build()
    }

    private fun getCachedArrowIcon(rotationDeg: Float): CarIcon {
        val cached = cachedArrowIcon
        if (cached != null && Math.abs(rotationDeg - cachedArrowRotation) < 3f) {
            return cached
        }
        val icon = createBearingArrowIcon(rotationDeg)
        cachedArrowIcon = icon
        cachedArrowRotation = rotationDeg
        return icon
    }

    private fun createBearingArrowIcon(rotationDeg: Float): CarIcon {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.save()
        canvas.rotate(rotationDeg, size / 2f, size / 2f)
        val path = Path().apply {
            moveTo(size / 2f, 8f)
            lineTo(size - 16f, size - 16f)
            lineTo(size / 2f, size - 28f)
            lineTo(16f, size - 16f)
            close()
        }
        canvas.drawPath(path, paint)
        canvas.restore()
        return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }

    fun onRadarUpdated() {
        surfaceCallback.updateRadar(session.latestRadarPath, session.radarOpacity)
    }

    fun onDarkModeChanged(isDark: Boolean) {
        surfaceCallback.updateMapStyle(isDark)
    }

    fun onDestinationChanged() {
        cachedArrowIcon = null
        cachedArrowRotation = Float.NaN
        surfaceCallback.updateDestination(session.poiPosition)
        invalidate()
    }

    fun onWeatherDataUpdated() {
        surfaceCallback.updateWeatherOverlay(session.openMeteoSnapshot)
    }
}
