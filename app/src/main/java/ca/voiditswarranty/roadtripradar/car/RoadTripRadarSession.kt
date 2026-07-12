package ca.voiditswarranty.roadtripradar.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ca.voiditswarranty.roadtripradar.car.screens.CarNavMapScreen
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

/**
 * Car UI session. The host creates one session per projection and asks for the root
 * [Screen] via [onCreateScreen]. We bind the shared [MapViewModel] (process-wide
 * singleton) so the car surface and the phone Activity stay in sync.
 *
 * The session counts itself as an active surface (see [MapViewModel.onSurfaceActive]) so
 * the VM keeps polling while the car is projecting even if the phone app is backgrounded;
 * it counts itself out on [Lifecycle.Event.ON_DESTROY] so polling stops when neither
 * surface is active.
 *
 * On a car configuration change (e.g. day → night) the root map screen is asked to reload its
 * map style so the base map follows the car's day/night mode (MR-1).
 */
class RoadTripRadarSession : Session() {
    private var countedActive = false
    private var carMapScreen: CarNavMapScreen? = null

    private val vm: MapViewModel
        get() = CarViewModelHolder.ensureInitialized(carContext.applicationContext)

    override fun onCreateScreen(intent: Intent): Screen {
        if (!countedActive) {
            countedActive = true
            vm.onSurfaceActive()
            lifecycle.addObserver(LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) vm.onSurfaceInactive()
            })
        }
        return CarNavMapScreen(carContext).also { carMapScreen = it }
    }

    override fun onCarConfigurationChanged(configuration: android.content.res.Configuration) {
        carMapScreen?.reloadMapStyle()
    }
}