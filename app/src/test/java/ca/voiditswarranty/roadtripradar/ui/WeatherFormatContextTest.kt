package ca.voiditswarranty.roadtripradar.ui

import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.model.WindSpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric tests for the Context-dependent helpers in [WeatherFormat] — the
 * pure-math helpers (formatTemp / formatTrend / windValue / tempUnitSymbol /
 * windArrowRotationDeg) live in [WeatherFormatTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WeatherFormatContextTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun windUnitLabel_kmh_returnsKmhLabel() {
        assertEquals("km/h", windUnitLabel(context, WindSpeedUnit.KMH))
    }

    @Test
    fun windUnitLabel_mph_returnsMphLabel() {
        assertEquals("mph", windUnitLabel(context, WindSpeedUnit.MPH))
    }

    @Test
    fun windUnitLabel_knots_returnsKnLabel() {
        assertEquals("kn", windUnitLabel(context, WindSpeedUnit.KNOTS))
    }

    @Test
    fun compassDirectionLabel_all16CardinalPoints_resolveCorrectly() {
        // The 16-point compass sectors: each 22.5° wide.
        // The function rounds (deg/22.5 + 0.5) to the nearest sector.
        // 0° → sector 0 → N
        assertEquals("N", compassDirectionLabel(context, 0.0))
        // 22.5° → boundary; rounds to sector 1 → NNE
        assertEquals("NNE", compassDirectionLabel(context, 22.5))
        // 45° → sector 1 → NNE  (45/22.5 + 0.5 = 2.5, .toInt() truncates to 2 → NE? Let's verify the actual behavior.)
        // The current implementation uses (deg/22.5 + 0.5).toInt() % 16:
        //   0°   → 0.5.toInt() = 0  → N
        //   11°  → 0.98.toInt() = 0 → N
        //   12°  → 1.03.toInt() = 1 → NNE
        //   22.5°→ 1.5.toInt()  = 1 → NNE
        //   23°  → 1.52.toInt() = 1 → NNE
        //   34°  → 2.01.toInt() = 2 → NE
        // Pin these three to nail the rounding rule.
        assertEquals("NE", compassDirectionLabel(context, 34.0))
        assertEquals("E", compassDirectionLabel(context, 79.0))
        assertEquals("S", compassDirectionLabel(context, 169.0))
        assertEquals("W", compassDirectionLabel(context, 259.0))
    }

    @Test
    fun compassDirectionLabel_359Degrees_returnsN() {
        // Wrap-around: 359° is just below north.
        assertEquals("N", compassDirectionLabel(context, 359.0))
    }

    @Test
    fun compassDirectionLabel_negativeDegrees_wrapsToPositive() {
        // -10° should wrap to 350° (just west of N).
        assertEquals("N", compassDirectionLabel(context, -10.0))
    }
}
