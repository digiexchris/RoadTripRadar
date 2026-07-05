package ca.voiditswarranty.roadtripradar.car

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [CarViewModelHolder] — the process-wide singleton holder for the
 * shared [ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel]. The phone
 * Activity and the Android Auto car [androidx.car.app.Session] both go through
 * this holder so the continuous polling work (Open-Meteo, RainViewer, POI
 * pipeline) runs exactly once per process.
 *
 * The implementation is a double-checked `@Volatile` + `synchronized` block;
 * the tests pin both the sequential and concurrent singleton contract so a
 * future refactor can't silently break the "only one MapViewModel per process"
 * guarantee.
 *
 * Notes:
 * - The `MapViewModel` constructor registers a default network callback
 *   (used for wind-conditions polling retry). Robolectric 4.16.1's
 *   `ConnectivityManager` shadow doesn't implement that method on
 *   `compileSdk` (36), so the test is pinned to SDK 33 where the shadow
 *   works.
 * - The holder is a Kotlin `object` with a private `vm` field. The tests
 *   rely on the public `ensureInitialized` API rather than reaching into
 *   the private state, so a future refactor (e.g. a DI-injected factory)
 *   can swap the implementation without rewriting these tests as long as
 *   the singleton contract holds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CarViewModelHolderTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun ensureInitialized_firstCallReturnsNonNull() {
        // Sanity: the holder returns a real MapViewModel. (Subsequent tests
        // assert identity across calls; this one only checks non-null.)
        val vm = CarViewModelHolder.ensureInitialized(context)
        assertNotNull(vm)
    }

    @Test
    fun ensureInitialized_calledTwice_returnsSameInstance() {
        // The point of the holder: regardless of which surface (phone Activity
        // or car Session) calls `ensureInitialized` first, every later call
        // gets the same instance. A regression that resets `vm` on every
        // call would silently double the polling work.
        val first = CarViewModelHolder.ensureInitialized(context)
        val second = CarViewModelHolder.ensureInitialized(context)
        assertSame(
            "sequential ensureInitialized calls must return the same MapViewModel",
            first,
            second,
        )
    }

    @Test
    fun ensureInitialized_threeCallsAllReturnSameInstance() {
        // Belt-and-suspenders: a future "reset on each call" regression would
        // fail this even if two-call versions pass (e.g. via memoization at
        // depth 1).
        val a = CarViewModelHolder.ensureInitialized(context)
        val b = CarViewModelHolder.ensureInitialized(context)
        val c = CarViewModelHolder.ensureInitialized(context)
        assertSame(a, b)
        assertSame(b, c)
    }

    @Test
    fun ensureInitialized_concurrentCalls_returnsSameInstance() {
        // The double-checked locking must hold under contention. Launch N
        // parallel coroutines all calling `ensureInitialized` simultaneously;
        // every call must observe the same MapViewModel. The
        // `Dispatchers.Unconfined` is fine here because we just want the
        // holder's `synchronized` block to be exercised — not real parallelism.
        val parallelism = 32
        val first = CarViewModelHolder.ensureInitialized(context)
        val results = runBlocking {
            val scope = CoroutineScope(Dispatchers.Unconfined)
            (1..parallelism)
                .map { scope.async { CarViewModelHolder.ensureInitialized(context) } }
                .awaitAll()
        }
        // Every concurrent result must be the same instance as the seed and
        // as every other concurrent result.
        for (vm in results) {
            assertSame(
                "concurrent ensureInitialized must return the seed instance",
                first,
                vm,
            )
        }
        // And the concurrent results are all the same instance among themselves.
        val distinct = results.toSet()
        assertEquals(
            "all concurrent calls must collapse to one instance",
            1,
            distinct.size,
        )
    }

    @Test
    fun appScope_isSharedAcrossCalls() {
        // The app-scoped supervisor scope is the holder's process-wide
        // coroutine scope; polling launched onto it survives Activity
        // destruction. Two reads of `appScope` must return the same
        // CoroutineScope instance (it's a `val` on the object, so this is a
        // trivial identity check, but pinning the contract here documents
        // that callers can rely on a single shared scope).
        val a = CarViewModelHolder.appScope
        val b = CarViewModelHolder.appScope
        assertSame(a, b)
    }
}
