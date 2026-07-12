package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.OnDoneCallback
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder
import ca.voiditswarranty.roadtripradar.model.WeatherMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [CarNavMapScreen] — the car surface that hosts a root
 * [NavigationTemplate] (used by the diagnostic split-screen experiment). The
 * screen builds a [NavigationTemplate] with two action strips:
 *
 * - The top action strip has three custom-icon actions: Menu (pushes
 *   [HomeScreen]), Weather (a 3-state cycle OFF → PLAYING → ON(paused) → OFF via
 *   [MapViewModel.cycleWeatherMode]; the icon shows the *next* action —
 *   [R.drawable.ic_car_play] / [R.drawable.ic_car_pause] /
 *   [R.drawable.ic_car_weather_off]), and Recenter.
 * - The map action strip has exactly one action: [Action.PAN] (required for
 *   the host to forward pan / pinch gestures to the [CarMapRenderer]).
 *
 * The test pins both action strips and the play/pause icon swap. The
 * [CarMapRenderer] constructor instantiates a virtual display and a
 * `Presentation`; under Robolectric those no-op (the underlying
 * `VirtualDisplay` create call is a stub), so the screen builds but never
 * renders a real surface. We don't need the real map to assert the template
 * structure — `buildTemplate()` is purely template-level.
 *
 * Uses Robolectric + `TestCarContext`. Pinned to SDK 33 because the real
 * [MapViewModel] registers a default network callback in `init` that
 * Robolectric 4.16.1's `ConnectivityManager` shadow only implements on
 * SDK 33.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CarNavMapScreenTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun vm() = CarViewModelHolder.ensureInitialized(context)

    private fun buildScreen(): NavigationTemplate {
        val carContext = TestCarContext.createCarContext(context)
        val screen = CarNavMapScreen(carContext)
        return screen.onGetTemplate() as NavigationTemplate
    }

    private fun mapActions(template: NavigationTemplate): List<Action> =
        template.mapActionStrip!!.actions

    /**
     * Returns the resource id backing an [Action]'s [CarIcon]. The chain is
     * `Action.icon` (a [CarIcon]) → `CarIcon.icon` (an `IconCompat`) →
     * `IconCompat.resId` (a `@DrawableRes` int). The constructor sets an icon
     * on every custom action, so the chain is non-null.
     */
    private fun actionIconRes(action: Action): Int = action.icon!!.icon!!.resId

    @Before
    fun resetRadar() {
        // weatherMode is a public-mutable var with public setter; reset to the
        // app's default (OFF — the radar pauses on the phone too, by default).
        vm().updateWeatherMode(WeatherMode.OFF)
    }

    // -------- top action strip --------

    @Test
    fun carNavMapScreen_actionStrip_hasThreeActions() {
        // The top action strip carries Menu / Play-Pause / Recenter — three
        // actions. NavigationTemplate requires a non-null action strip with
        // at least one action; we pin the exact count so a future addition /
        // removal is surfaced as a test diff.
        val template = buildScreen()
        assertEquals(
            "top action strip must have exactly 3 actions",
            3,
            template.actionStrip!!.actions.size,
        )
    }

    @Test
    fun carNavMapScreen_actionStrip_firstActionIsMenu() {
        // The first top-strip action is the menu icon. The screen uses the
        // ic_car_menu drawable for it; pin the resource so a future swap is
        // intentional.
        val template = buildScreen()
        val firstIcon = actionIconRes(template.actionStrip!!.actions[0])
        assertEquals(
            "first top action must use the menu icon",
            R.drawable.ic_car_menu,
            firstIcon,
        )
    }

    @Test
    fun carNavMapScreen_actionStrip_thirdActionIsRecenter() {
        // The third top-strip action is the recenter icon.
        val template = buildScreen()
        val thirdIcon = actionIconRes(template.actionStrip!!.actions[2])
        assertEquals(
            "third top action must use the recenter icon",
            R.drawable.ic_car_recenter,
            thirdIcon,
        )
    }

    @Test
    fun carNavMapScreen_radarIconIsPauseWhenPlaying() {
        val v = vm()
        v.updateWeatherMode(WeatherMode.PLAYING)
        val template = buildScreen()
        val secondIcon = actionIconRes(template.actionStrip!!.actions[1])
        assertEquals(
            "second action must use the pause icon when radar is playing",
            R.drawable.ic_car_pause,
            secondIcon,
        )
    }

    @Test
    fun carNavMapScreen_radarIconIsPlayWhenPaused() {
        val v = vm()
        v.updateWeatherMode(WeatherMode.OFF)
        val template = buildScreen()
        val secondIcon = actionIconRes(template.actionStrip!!.actions[1])
        assertEquals(
            "second action must use the play icon when radar is not playing",
            R.drawable.ic_car_play,
            secondIcon,
        )
    }

    @Test
    fun carNavMapScreen_radarIconIsOffWhenPaused() {
        // ON (paused, radar visible) — the next tap turns the radar fully off,
        // so the icon shows the off affordance (ic_car_weather_off).
        val v = vm()
        v.updateWeatherMode(WeatherMode.ON)
        val template = buildScreen()
        val secondIcon = actionIconRes(template.actionStrip!!.actions[1])
        assertEquals(
            "second action must use the off icon when weatherMode is ON (paused)",
            R.drawable.ic_car_weather_off,
            secondIcon,
        )
    }

    @Test
    fun carNavMapScreen_weatherAction_clickCyclesOffToPlayingToOnToOff() {
        // The weather action is a 3-state cycle: OFF -> PLAYING -> ON -> OFF.
        // Driving three clicks from OFF must land back on OFF. (Today the
        // listener is toggleWeatherPlayPause, whose cycle is OFF -> PLAYING ->
        // ON -> PLAYING, so the third click yields PLAYING, not OFF — this test
        // fails until the listener switches to cycleWeatherMode.)
        val v = vm()
        v.updateWeatherMode(WeatherMode.OFF)
        val template = buildScreen()
        val weatherAction = template.actionStrip!!.actions[1]
        weatherAction.onClickDelegate!!.sendClick(object : OnDoneCallback {})
        assertEquals("OFF -> PLAYING on first tap", WeatherMode.PLAYING, v.weatherMode)
        weatherAction.onClickDelegate!!.sendClick(object : OnDoneCallback {})
        assertEquals("PLAYING -> ON on second tap", WeatherMode.ON, v.weatherMode)
        weatherAction.onClickDelegate!!.sendClick(object : OnDoneCallback {})
        assertEquals("ON -> OFF on third tap", WeatherMode.OFF, v.weatherMode)
    }

    // -------- map action strip --------

    @Test
    fun carNavMapScreen_mapActionStrip_hasExactlyPan() {
        // The map action strip must contain exactly Action.PAN. NavigationTemplate
        // requires a non-null map action strip (and without PAN the host
        // doesn't forward pan / pinch gestures to the SurfaceCallback, so the
        // map is unusable). Pin the exact action so a future addition of, say,
        // a zoom action is intentional.
        val template = buildScreen()
        val actions = mapActions(template)
        assertEquals(
            "map action strip must have exactly 1 action",
            1,
            actions.size,
        )
        assertEquals(
            "map action strip's sole action must be PAN",
            Action.PAN,
            actions[0],
        )
    }
}
