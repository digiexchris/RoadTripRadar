package ca.voiditswarranty.roadtripradar.car

import android.app.Presentation
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 * Bridges the Android Auto map [SurfaceContainer] to a MapLibre [CarMapContainer].
 *
 * Technique (from the MapLibre Android Auto sample): when the host hands us a surface, we
 * create a [VirtualDisplay] backed by that surface, show a [Presentation] on it whose content
 * view is the native MapView. The MapView then renders onto the virtual display, which the
 * host composites onto the car screen. Pan/pinch gestures from the host are forwarded to the
 * map (these arrive only while [androidx.car.app.model.Action.PAN] is in the map action strip).
 *
 * The renderer is a [DefaultLifecycleObserver] on the owning screen's [Lifecycle]: it
 * registers the surface callback on ON_CREATE (before the host calls `onGetTemplate`, so the
 * surface callback is in place when the host sees the map template) and tears the map down on
 * ON_DESTROY.
 */
class CarMapRenderer(
    private val carContext: CarContext,
    lifecycle: Lifecycle,
) : SurfaceCallback, DefaultLifecycleObserver {

    private val mapContainer = CarMapContainer(carContext, CarViewModelHolder.ensureInitialized(carContext.applicationContext))
    private var surfaceContainer: SurfaceContainer? = null
    private var presentation: Presentation? = null
    private var virtualDisplay: VirtualDisplay? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        runCatching {
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        }.onFailure { Log.e(LOG_TAG, "Could not set surface callback", it) }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        teardown()
        mapContainer.dispose()
        runCatching {
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
        }.onFailure { Log.e(LOG_TAG, "Could not remove surface callback", it) }
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        this.surfaceContainer = surfaceContainer
        Log.i(LOG_TAG, "onSurfaceAvailable: ${surfaceContainer.width}x${surfaceContainer.height} @ ${surfaceContainer.dpi}dpi")
        // If the surface is recreated (e.g. config change), tear down the previous presentation
        // before building a fresh one.
        teardownPresentation()
        val display = carContext.getSystemService(DisplayManager::class.java)
            .createVirtualDisplay(
                "RoadTripRadarCarDisplay",
                surfaceContainer.width,
                surfaceContainer.height,
                surfaceContainer.dpi,
                surfaceContainer.surface,
                0,
            ).also { virtualDisplay = it }
        Presentation(carContext, display.display).also { presentation = it }
            .apply { setContentView(mapContainer.setupMap()) }
            .show()
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        Log.i(LOG_TAG, "onSurfaceDestroyed")
        this.surfaceContainer = null
        teardownPresentation()
    }

    override fun onVisibleAreaChanged(visibleArea: android.graphics.Rect) {
        // The host's action-strip buttons reserve a top inset in the visible-area rect in
        // full-screen, which would push the map down below them. But the host composites our
        // surface into that top area too (the map fills it once the buttons auto-hide), so we
        // render across the FULL surface height and let the buttons overlay the top of the map.
        // We still honor the horizontal bounds so the map shrinks into the split region beside
        // the media panel (where the occluded right part is genuinely not ours to draw on).
        val sc = surfaceContainer
        val effective = if (sc != null && sc.height > 0) {
            android.graphics.Rect(visibleArea.left, 0, visibleArea.right, sc.height)
        } else visibleArea
        mapContainer.setVisibleArea(effective)
    }

    override fun onStableAreaChanged(stableArea: android.graphics.Rect) {
        Log.d(LOG_TAG, "onStableAreaChanged: $stableArea")
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        mapContainer.scrollBy(distanceX, distanceY)
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        mapContainer.onScale(focusX, focusY, scaleFactor)
    }

    /** Recenter on the device's last known position. Invoked from a toolbar action. */
    fun recenter() = mapContainer.recenter()

    /**
     * Re-resolve the map style against the car's current day/night mode and reload it if it
     * changed. Invoked from the session's car-configuration-changed callback (MR-1).
     */
    fun reloadStyleIfNeeded() = mapContainer.reloadStyleIfNeeded()

    private fun teardownPresentation() {
        presentation?.run { if (isShowing) dismiss() }
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
        mapContainer.cleanUpMap()
    }

    private fun teardown() {
        surfaceContainer = null
        teardownPresentation()
    }

    companion object {
        private const val LOG_TAG = "CarMapRenderer"
    }
}