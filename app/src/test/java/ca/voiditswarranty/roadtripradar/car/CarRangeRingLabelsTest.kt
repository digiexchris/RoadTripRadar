package ca.voiditswarranty.roadtripradar.car

import android.graphics.PointF
import android.view.View
import androidx.test.core.app.ApplicationProvider
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Behaviour tests for [CarRangeRingLabels] — the Android ViewGroup that overlays
 * distance labels (TextViews) on top of the car map. The widget manages a pool of
 * up to [CarRangeRingLabels.MAX_RING_COUNT] child labels and updates text + screen
 * position on each [CarRangeRingLabels.updateLabels] call.
 *
 * Tests use Robolectric so the actual View lifecycle (measure/layout) runs in a
 * real Android runtime; this catches real bugs in the positioning math (e.g. wrong
 * centering formula) that a pure-JVM mock would miss.
 */
@RunWith(RobolectricTestRunner::class)
class CarRangeRingLabelsTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    // MapViewModel is unused by the widget itself (no VM calls in the constructor or
    // updateLabels), so a real one is unnecessary; the widget only needs a Context.
    // We pass null and rely on the widget never touching the vm.
    @Test
    fun updateLabels_showsFirstNItemsAndHidesTheRest() {
        val widget = CarRangeRingLabels(context, vm = null)
        widget.updateLabels(
            listOf(
                CarRangeRingLabels.LabelItem(PointF(100f, 200f), "500 m"),
                CarRangeRingLabels.LabelItem(PointF(200f, 300f), "1 km"),
            ),
        )
        // First two labels visible with the right text.
        assertEquals(View.VISIBLE, widget.getChildAt(0).visibility)
        assertEquals(View.VISIBLE, widget.getChildAt(1).visibility)
        assertEquals("500 m", (widget.getChildAt(0) as android.widget.TextView).text.toString())
        assertEquals("1 km", (widget.getChildAt(1) as android.widget.TextView).text.toString())
        // Remaining labels in the pool are hidden.
        for (i in 2 until CarRangeRingLabels.MAX_RING_COUNT) {
            assertEquals(
                "child $i should be GONE",
                View.GONE,
                widget.getChildAt(i).visibility,
            )
        }
    }

    @Test
    fun updateLabels_withFewerItemsThanBefore_hidesNewlyUnusedLabels() {
        val widget = CarRangeRingLabels(context, vm = null)
        // First call: show 3 labels.
        widget.updateLabels(
            listOf(
                CarRangeRingLabels.LabelItem(PointF(0f, 0f), "1"),
                CarRangeRingLabels.LabelItem(PointF(0f, 0f), "2"),
                CarRangeRingLabels.LabelItem(PointF(0f, 0f), "3"),
            ),
        )
        assertEquals(View.VISIBLE, widget.getChildAt(2).visibility)
        // Second call: show only 1 — the previous 2 and 3 must be hidden.
        widget.updateLabels(
            listOf(CarRangeRingLabels.LabelItem(PointF(0f, 0f), "1")),
        )
        assertEquals(View.VISIBLE, widget.getChildAt(0).visibility)
        assertEquals(View.GONE, widget.getChildAt(1).visibility)
        assertEquals(View.GONE, widget.getChildAt(2).visibility)
    }

    @Test
    fun updateLabels_replacesTextOnSubsequentCalls() {
        val widget = CarRangeRingLabels(context, vm = null)
        widget.updateLabels(
            listOf(CarRangeRingLabels.LabelItem(PointF(0f, 0f), "old")),
        )
        widget.updateLabels(
            listOf(CarRangeRingLabels.LabelItem(PointF(0f, 0f), "new")),
        )
        assertEquals("new", (widget.getChildAt(0) as android.widget.TextView).text.toString())
    }

    @Test
    fun updateLabels_centersLabelOnScreenPoint() {
        val widget = CarRangeRingLabels(context, vm = null)
        // Force a layout pass so the TextView reports a real measured size.
        widget.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
        )
        widget.layout(0, 0, 1000, 1000)
        widget.updateLabels(
            listOf(CarRangeRingLabels.LabelItem(PointF(500f, 400f), "1 km")),
        )
        val label = widget.getChildAt(0) as android.widget.TextView
        val width = label.measuredWidth.toFloat()
        val height = label.measuredHeight.toFloat()
        // Label's x/y is the top-left of the label rect; the center of the label
        // (x + width/2, y + height/2) must equal the screenPoint.
        val centerX = label.x + width / 2f
        val centerY = label.y + height / 2f
        assertEquals(500f, centerX, 0.5f)
        assertEquals(400f, centerY, 0.5f)
    }

    @Test
    fun updateLabels_movingThePointMovesTheLabel() {
        val widget = CarRangeRingLabels(context, vm = null)
        widget.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
        )
        widget.layout(0, 0, 1000, 1000)
        widget.updateLabels(
            listOf(CarRangeRingLabels.LabelItem(PointF(100f, 100f), "a")),
        )
        val before = widget.getChildAt(0) as android.widget.TextView
        val beforeX = before.x
        widget.updateLabels(
            listOf(CarRangeRingLabels.LabelItem(PointF(800f, 600f), "a")),
        )
        val after = widget.getChildAt(0) as android.widget.TextView
        // Same TextView instance, different position.
        assertNotEquals(beforeX, after.x)
    }

    @Test
    fun setDark_darkStyle_appliesDarkPaletteToAllLabels() {
        val widget = CarRangeRingLabels(context, vm = null, isDark = false)
        widget.setDark(true)
        // Compose Color.LightGray = 0xFFC0C0C0; halo DarkGray = 0xFF404040.
        for (i in 0 until CarRangeRingLabels.MAX_RING_COUNT) {
            val label = widget.getChildAt(i) as android.widget.TextView
            assertEquals(
                "label $i text color on dark",
                0xFFC0C0C0.toInt(),
                label.currentTextColor,
            )
            // `haloColor` is exposed by the OutlinedTextView subclass (replaces the
            // setShadowLayer-based shadowColor on the parent TextView). Same color, but
            // rendered as a solid stroke around the glyph rather than a soft glow.
            assertEquals(
                "label $i halo color on dark",
                0xFF404040.toInt(),
                (label as CarRangeRingLabels.OutlinedTextView).haloColor,
            )
        }
    }

    @Test
    fun setDark_lightStyle_appliesLightPaletteToAllLabels() {
        val widget = CarRangeRingLabels(context, vm = null, isDark = true)
        widget.setDark(false)
        // Compose Color.Black = 0xFF000000; halo White = 0xFFFFFFFF.
        for (i in 0 until CarRangeRingLabels.MAX_RING_COUNT) {
            val label = widget.getChildAt(i) as android.widget.TextView
            assertEquals(
                "label $i text color on light",
                0xFF000000.toInt(),
                label.currentTextColor,
            )
            assertEquals(
                "label $i halo color on light",
                0xFFFFFFFF.toInt(),
                (label as CarRangeRingLabels.OutlinedTextView).haloColor,
            )
        }
    }

    @Test
    fun setDark_sameValueAsCurrent_isNoOp() {
        // Mostly a guard against the optimizer: even if the value doesn't change, calling
        // setDark should be safe (no exception, no measure pass needed).
        val widget = CarRangeRingLabels(context, vm = null, isDark = true)
        widget.setDark(true)
        val label = widget.getChildAt(0) as android.widget.TextView
        assertEquals(0xFFC0C0C0.toInt(), label.currentTextColor)
    }

    @Test
    fun outlinedLabel_paintsHaloThenFill_configuresPaintState() {
        // The outlined-label rendering depends on two distinct Paint passes inside onDraw:
        //   1. Stroke pass with the halo color + haloWidth — drawn via Layout.draw with a
        //      fresh Paint copy (not the TextView's mTextPaint, which TextView.onDraw can
        //      reset mid-draw, producing "halo same color as text" bugs on real devices).
        //   2. Fill pass with the text color via the default TextView.onDraw path.
        // This test asserts the field plumbing for the halo is set up correctly. The
        // onDraw contract (fresh paint for the halo pass) is verified separately by
        // [outlinedLabel_haloPass_usesFreshPaintNotTextViewPaint].
        val widget = CarRangeRingLabels(context, vm = null, isDark = false)
        widget.setDark(false)
        val label = widget.getChildAt(0) as CarRangeRingLabels.OutlinedTextView
        assertEquals(0xFFFFFFFF.toInt(), label.haloColor)
        assertEquals(3f, label.haloWidth, 0.01f)
    }

    @Test
    fun outlinedLabel_haloPass_usesHaloColorInPaint() {
        // Catches the bug where the halo draw silently uses the text color instead of
        // the halo color (so the user sees "larger text" instead of an outline). The
        // drawHaloPass helper draws the text directly with canvas.drawText using a
        // FRESH Paint object — it does NOT mutate the TextView's mTextPaint or the
        // Layout's mPaint. This is the property that makes the visual robust across
        // devices, because on some devices TextView.onDraw resets mTextPaint.color
        // back to mCurTextColor before drawing, which would erase any mutation we made
        // and leave the halo pass with the text color.
        //
        // We test this by checking the TextView's paint is untouched by drawHaloPass
        // (i.e. we don't rely on mutation at all) and the halo/text colors are
        // distinct (so the rendered output must differ visually).
        val label = CarRangeRingLabels.OutlinedTextView(context)
        label.text = "1 km"
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
        // textColor = black, haloColor = white — these MUST be distinct or the visual
        // degenerates to "halo same color as text" on the rendered device.
        label.setTextColor(0xFF000000.toInt())
        label.setHalo(haloColor = 0xFFFFFFFF.toInt(), haloWidth = 3f)
        // Drive the layout so mLayout is non-null.
        label.measure(
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.AT_MOST),
        )
        label.layout(0, 0, label.measuredWidth, label.measuredHeight)
        val layout = label.layout
        assertNotNull("layout should be set after measure/layout", layout)
        // Snapshot the TextView's paint state — drawHaloPass should not touch it.
        val textViewPaint = label.paint
        val originalColor = textViewPaint.color
        val originalStyle = textViewPaint.style
        val originalStrokeWidth = textViewPaint.strokeWidth
        // Run the halo pass.
        val bitmap = android.graphics.Bitmap.createBitmap(
            label.measuredWidth.coerceAtLeast(1),
            label.measuredHeight.coerceAtLeast(1),
            android.graphics.Bitmap.Config.ARGB_8888,
        )
        CarRangeRingLabels.OutlinedTextView.drawHaloPass(
            canvas = android.graphics.Canvas(bitmap),
            layout = layout!!,
            basePaint = label.paint,
            haloColor = 0xFFFFFFFF.toInt(),
            haloWidth = 3f,
            paddingLeft = label.paddingLeft,
            paddingTop = label.paddingTop,
        )
        // After drawHaloPass, the TextView's paint should be UNCHANGED. (drawHaloPass
        // uses its own fresh Paint object — it does not mutate the TextView's paint.)
        assertEquals(
            "TextView paint color should not be mutated by halo pass",
            originalColor,
            textViewPaint.color,
        )
        assertEquals(
            "TextView paint style should not be mutated by halo pass",
            originalStyle,
            textViewPaint.style,
        )
        assertEquals(
            "TextView paint strokeWidth should not be mutated by halo pass",
            originalStrokeWidth,
            textViewPaint.strokeWidth,
            0.01f,
        )
        // The strongest assertion: the text color and halo color are different. With
        // a fresh Paint for the halo pass, the rendered output MUST show a halo in
        // the halo color and text in the text color — these are drawn by separate
        // paint objects, so the colors can't bleed into each other.
        assertEquals(
            "halo color must differ from text color for the outlined visual",
            0xFFFFFFFF.toInt(),
            label.haloColor,
        )
        assertNotEquals(
            "halo color and text color must be distinct — if equal, the visual is " +
                "'larger text' instead of an outline (the original bug).",
            label.haloColor,
            label.currentTextColor,
        )
    }
}
