package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

/**
 * Base class for the car screens. Each subclass overrides [buildTemplate] to produce its
 * [Template]; this base handles registering a refresh listener that re-renders the screen
 * (via [invalidate] on the car host's main executor) whenever the shared [MapViewModel]
 * mutates, so the car UI reflects phone-side changes and vice-versa.
 *
 * The refresh bus fires on whatever thread the mutation ran on (often a background IO
 * thread), so we hop to [CarContext.getMainExecutor] before calling [invalidate] — car
 * templates must be touched on the main thread. The listener is tied to the screen's
 * [Lifecycle] (registered on ON_CREATE, removed on ON_DESTROY) so popped screens don't
 * leak.
 */
abstract class BaseCarScreen(
    carContext: CarContext,
    protected val vm: MapViewModel,
) : Screen(carContext) {

    private val refreshListener: (MapViewModel) -> Unit = {
        carContext.mainExecutor.execute { invalidate() }
    }

    init {
        lifecycle.addObserver(LifecycleEventObserver { _: LifecycleOwner, event: Lifecycle.Event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> vm.addRefreshListener(refreshListener)
                Lifecycle.Event.ON_DESTROY -> vm.removeRefreshListener(refreshListener)
                else -> Unit
            }
        })
    }

    final override fun onGetTemplate(): Template = buildTemplate()

    protected abstract fun buildTemplate(): Template

    /** Push a new screen onto the car screen stack. */
    protected fun push(screen: Screen) {
        carContext.getCarService(ScreenManager::class.java).push(screen)
    }

    /** Pop this screen off the car screen stack. */
    protected fun pop() {
        carContext.getCarService(ScreenManager::class.java).pop()
    }
}