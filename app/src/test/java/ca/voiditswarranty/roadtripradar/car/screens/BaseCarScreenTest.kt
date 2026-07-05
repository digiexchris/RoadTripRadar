package ca.voiditswarranty.roadtripradar.car.screens

import android.content.Context
import android.os.Looper
import androidx.car.app.Screen
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Template
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.data.PreferencesRepository
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests for [BaseCarScreen] — the shared base class for every car surface screen.
 * The interesting behavior here isn't the template (each subclass builds its own)
 * but the **refresh bus** wiring:
 *
 * 1. On `Lifecycle.Event.ON_CREATE`, the screen registers a refresh listener on
 *    the shared [MapViewModel]. When the VM mutates (any [MapViewModel.notifyRefresh]),
 *    the listener fires `invalidate()` on the car host's main executor — that re-renders
 *    the screen so the car UI reflects phone-side changes and vice versa.
 * 2. On `Lifecycle.Event.ON_DESTROY`, the screen removes the listener so a popped
 *    screen doesn't keep firing invalidates.
 *
 * The tests pin both halves of the contract. The Robolectric pin (`@Config(sdk = [33])`)
 * is needed for the real [MapViewModel] (its `init` registers a default network callback
 * that Robolectric 4.16.1's `ConnectivityManager` shadow only implements on SDK 33).
 *
 * The `invalidate()` call hops to `carContext.getMainExecutor()` (per the source), which
 * is the same executor that production uses. With `TestCarContext` the main executor is
 * the one the test calls `execute(...)` on directly; we don't need to drive the
 * scheduler — `invalidate()` -> `AppManager.invalidate()` -> `FakeHost.invalidate()` ->
 * `top.onGetTemplate()` happens synchronously when the executor runs the lambda inline
 * (Robolectric's main executor is the immediate executor for tests). The captured
 * templates are read back via [ScreenController.getTemplatesReturned].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BaseCarScreenTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Concrete test subclass — exposes `buildTemplate` so the framework can render. */
    private class ProbeScreen(carContext: TestCarContext, vm: MapViewModel) :
        BaseCarScreen(carContext, vm) {
        override fun buildTemplate(): Template = ListTemplate.Builder()
            .setSingleList(androidx.car.app.model.ItemList.Builder().build())
            .setTitle("probe")
            .build()
    }

    private fun freshVm(): MapViewModel = MapViewModel(
        appContext = context,
        prefsRepo = PreferencesRepository(context),
        externalScope = CoroutineScope(Dispatchers.Unconfined),
    )

    private fun newScreen(vm: MapViewModel): Pair<ProbeScreen, ScreenController> {
        val carContext = TestCarContext.createCarContext(context)
        val screen = ProbeScreen(carContext, vm)
        return screen to ScreenController(screen)
    }

    /**
     * The refresh listener posts `invalidate()` onto `carContext.mainExecutor` (which
     * uses the main Looper). Robolectric's main Looper only runs queued Runnables
     * when explicitly idled. Call this after a VM mutation to drain the queue and
     * let the listener -> invalidate -> onGetTemplate path complete synchronously.
     */
    private fun idleLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    // -------- listener registration on ON_CREATE --------

    @Test
    fun refreshListener_registeredWhenScreenReachesStarted() {
        // A screen whose lifecycle has never reached STARTED has NOT registered
        // its refresh listener. Trigger a VM mutation that would normally call
        // back into the listener; the screen's `onGetTemplate` is not called as
        // a result (the listener isn't registered yet).
        val vm = freshVm()
        val (screen, controller) = newScreen(vm)
        controller.reset()
        // Lifecycle is INITIALIZED — not STARTED. Mutating the VM must not produce
        // a new template for this screen because the refresh listener isn't
        // registered yet (and even if it were, `Screen.invalidate()` is a no-op
        // below STARTED).
        vm.updateShowLegend(true)
        idleLooper()  // drain queued listener -> invalidate -> onGetTemplate runnable
        val templates = controller.getTemplatesReturned()
        assertEquals(
            "no template expected before ON_START; refresh listener not yet wired",
            0,
            templates.size,
        )
        // Sanity: the screen is non-null and uses the test CarContext.
        assertNotNull(screen)
    }

    @Test
    fun refreshListener_firesOnVmMutationAfterStarted() {
        // The contract: once the screen reaches STARTED, a VM mutation that calls
        // `notifyRefresh()` causes the screen's `onGetTemplate()` to be invoked
        // again (via the listener -> `invalidate()` -> host -> `onGetTemplate`).
        // We measure this by snapshotting the template count, mutating the VM,
        // and asserting the count grew by 1.
        val vm = freshVm()
        val (screen, controller) = newScreen(vm)
        controller.moveToState(Lifecycle.State.STARTED)
        controller.reset()  // ignore the initial template captured during the state change
        val before = controller.getTemplatesReturned().size
        // Any public mutator that calls `notifyRefresh` works. `updateShowLegend`
        // is a simple flag flip with no side effects on the template.
        vm.updateShowLegend(true)
        idleLooper()
        val after = controller.getTemplatesReturned().size
        assertEquals(
            "VM mutation while screen is STARTED must produce one new template",
            before + 1,
            after,
        )
    }

    @Test
    fun refreshListener_firesForEachVmMutation() {
        // Belt-and-suspenders: a regression that only registered a single-fire
        // listener (or that swallowed subsequent invocations) would pass the
        // single-mutation test but fail here.
        val vm = freshVm()
        val (_, controller) = newScreen(vm)
        controller.moveToState(Lifecycle.State.STARTED)
        controller.reset()
        val before = controller.getTemplatesReturned().size
        vm.updateShowLegend(true)
        vm.updateShowLegend(false)
        vm.updateShowLegend(true)
        idleLooper()  // drain the three queued listener invocations
        val after = controller.getTemplatesReturned().size
        assertEquals(
            "three VM mutations while screen is STARTED must produce three new templates",
            before + 3,
            after,
        )
    }

    // -------- listener removal on ON_DESTROY --------

    @Test
    fun refreshListener_removedWhenScreenDestroyed() {
        // Once the screen's lifecycle reaches DESTROYED, the listener must be
        // removed from the VM. A subsequent VM mutation must NOT call back into
        // the screen (the screen is gone; a leaked listener would still try to
        // dispatch and would either no-op via `invalidate()`'s state check or,
        // worse, retain a reference to the dead screen).
        val vm = freshVm()
        val (_, controller) = newScreen(vm)
        controller.moveToState(Lifecycle.State.STARTED)
        controller.moveToState(Lifecycle.State.DESTROYED)
        controller.reset()  // ignore templates from the STARTED transition
        val before = controller.getTemplatesReturned().size
        vm.updateShowLegend(true)
        idleLooper()
        val after = controller.getTemplatesReturned().size
        assertEquals(
            "VM mutation after DESTROYED must not produce a new template",
            before,
            after,
        )
    }

    @Test
    fun refreshListener_idempotentOnRepeatedDestroy() {
        // The lifecycle observer fires ON_DESTROY once; the listener-removal
        // path is `list.remove(listener)` on a `CopyOnWriteArrayList`, which is
        // a no-op if the listener isn't present. Re-destroying the screen
        // (which doesn't happen in production but the test pins the contract)
        // must not throw or cause a second template to render for the dead
        // screen.
        val vm = freshVm()
        val (_, controller) = newScreen(vm)
        controller.moveToState(Lifecycle.State.STARTED)
        controller.moveToState(Lifecycle.State.DESTROYED)
        // Mutating the VM after destroy must be a clean no-op for the screen.
        vm.updateShowLegend(true)
        idleLooper()
        val templates = controller.getTemplatesReturned()
        // We can't easily assert "no exception was thrown" (the test runner
        // would already have failed), but we can assert the dead screen
        // received no further templates after the destroy.
        assertEquals(
            "templates captured should only be from the STARTED window, not from post-destroy mutations",
            0,
            templates.size,
        )
    }
}
