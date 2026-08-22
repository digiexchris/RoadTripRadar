package ca.voiditswarranty.roadtripradar.car

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.text.Layout
import android.widget.TextView
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

/**
 * Overlay widget on the car map that draws the distance labels ("500 m", "1 km", …) at the
 * projected screen position of each range ring's 12-o'clock point. Sits on top of the
 * MapView as a sibling inside the host FrameLayout (see [CarMapContainer.setupMap]) and is
 * driven by [updateLabels] on every camera move.
 *
 * Why plain Android Views and not Compose: the car surface is a virtual-display Presentation
 * (see [android.app.Presentation]), and hosting a ComposeView there would need non-Activity
 * lifecycle/saved-state wiring. The labels are simple positioned TextViews — no layout pass
 * beyond the manual `x`/`y` we set per update.
 *
 * Pool size: [MAX_RING_COUNT] = 4 — matches the maximum number of rings the phone draws per
 * zoom bracket (see [ca.voiditswarranty.roadtripradar.model.ringDistancesForZoom]). Brackets
 * with fewer rings simply leave the surplus TextViews [View.GONE] until the next zoom in.
 *
 * Why system TextViews rather than the phone's `SymbolLayer` glyphs: the phone renders labels
 * via a MapLibre `SymbolLayer` with a glyph PBF fetched over the network — a non-starter for a
 * driver-facing car surface (intermittent car network, tunnels, parking garages with no
 * signal). System fonts ship with the device and require no network.
 *
 * Why [OutlinedTextView] rather than `setShadowLayer`: the phone's text halo is a solid
 * stroked outline of each glyph that occludes the line behind the text. `setShadowLayer` on
 * a `TextView` is a soft Gaussian glow that does not reliably render on many devices and
 * does not produce a hard outline even when it does — the ring line shows through. The
 * outlined subclass paints the text twice: once as a stroked outline in the halo color,
 * then as a filled glyph in the text color. Same visual effect as the phone.
 */
class CarRangeRingLabels(
    context: Context,
    @Suppress("unused") private val vm: MapViewModel?,
    isDark: Boolean = false,
) : FrameLayout(context) {

    /** Cached so [setDark] can short-circuit when the value hasn't changed. */
    private var isDark: Boolean = isDark

    /** One per ring in the pool. Built once, mutated in place by [updateLabels]. */
    private val labels: List<OutlinedTextView> = (0 until MAX_RING_COUNT).map {
        OutlinedTextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LABEL_TEXT_SIZE_SP)
            setTextColor(carLabelTextColor(isDark))
            // Halo: matches the phone's textHaloWidth = 3.dp (= 3px at the car virtual-display
            // density of 1.0). OutlinedTextView paints this as a solid stroke around the
            // glyphs, not a soft glow.
            setHalo(haloColor = carLabelHaloColor(isDark), haloWidth = LABEL_HALO_WIDTH)
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
            // Wrap content — we position via x/y in the parent FrameLayout, so layout gravity
            // is irrelevant. WRAP_CONTENT keeps the centering math (label.width / 2f) correct.
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            addView(this)
        }
    }

    /**
     * Re-apply the label text + halo colors for the current theme. Called from
     * [CarMapContainer.reloadStyleIfNeeded] when the resolved map style flips dark↔light, so
     * the labels stay readable against the new base map. No-op when the value hasn't changed
     * (avoids a per-textview `setTextColor` round-trip on every refresh).
     */
    fun setDark(isDark: Boolean) {
        if (this.isDark == isDark) return
        this.isDark = isDark
        val textColor = carLabelTextColor(isDark)
        val haloColor = carLabelHaloColor(isDark)
        for (label in labels) {
            label.setTextColor(textColor)
            label.setHalo(haloColor = haloColor, haloWidth = LABEL_HALO_WIDTH)
        }
    }

    /**
     * Update text + screen position for the first [items]`.size` rings, hide the rest.
     *
     * [items] is the list of `(screenPoint, labelText)` pairs computed by the caller from
     * each ring's 12-o'clock position projected to screen pixels via
     * `MapLibreMap.projection.toScreenLocation`. The widget does not touch the camera or the
     * MapView — it just mirrors the positions into its child TextViews.
     */
    fun updateLabels(items: List<LabelItem>) {
        for (i in 0 until MAX_RING_COUNT) {
            val label = labels[i]
            if (i < items.size) {
                val item = items[i]
                label.text = item.text
                // Measure with the new text so measuredWidth/Height reflect the actual rendered
                // size, then re-anchor. measure() is cheap for a single TextView and avoids the
                // alternative of waiting for a full layout pass.
                label.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                )
                label.x = item.screenPoint.x - label.measuredWidth / 2f
                label.y = item.screenPoint.y - label.measuredHeight / 2f
                label.visibility = View.VISIBLE
            } else {
                label.visibility = View.GONE
            }
        }
    }

    /**
     * Detach the widget from its parent. Safe to call multiple times. Does not destroy the
     * underlying TextViews — the same instance can be re-attached if needed.
     */
    fun dispose() {
        (parent as? ViewGroup)?.removeView(this)
    }

    /**
     * One ring's label, expressed in the host FrameLayout's pixel coordinate system. The
     * [screenPoint] is the projection of the ring's 12-o'clock GeoJSON point under the current
     * camera (see [org.maplibre.android.maps.Projection.toScreenLocation]).
     */
    data class LabelItem(val screenPoint: PointF, val text: String)

    /**
     * [TextView] subclass that paints its text twice in [onDraw]: first a stroked outline
     * in the halo color (occluding whatever is behind the text — the map ring line in our
     * case), then a filled glyph in the text color via the default TextView path. Mirrors
     * MapLibre's `textHaloColor` + `textHaloWidth` behavior, which the phone's `SymbolLayer`
     * uses for its ring labels.
     *
     * **Why a subclass and not `setShadowLayer`:** `setShadowLayer` draws a soft Gaussian
     * glow around the glyphs and is unreliable on many Android devices (often disabled for
     * performance). The stroke+fill approach uses standard 2D paint passes that render
     * consistently on any device that can draw text. The visual matches the phone exactly:
     * the ring line stops visually at the halo's outer edge and resumes on the other side.
     *
     * **Why the halo pass uses `Canvas.drawText` directly with a fresh `Paint`:** the
     * Layout's `mPaint` and the TextView's `mTextPaint` are the same object in practice
     * (the Layout is constructed using the TextView's paint). Mutating it for the halo
     * pass and restoring it works on most devices, but on some devices `TextView.onDraw`
     * resets `mTextPaint.color` from `mCurTextColor` before drawing, so any mutations we
     * make are silently overwritten and the visual becomes "larger text" instead of an
     * outline. Drawing the halo via `Canvas.drawText` with a fresh `Paint` sidesteps any
     * internal TextView state interference entirely.
     */
    class OutlinedTextView(context: Context) : TextView(context) {
        var haloColor: Int = Color.TRANSPARENT
            private set
        var haloWidth: Float = 0f
            private set

        /**
         * Update the halo color and width. Triggers a redraw but no re-layout — the halo's
         * size is purely a paint property.
         */
        fun setHalo(haloColor: Int, haloWidth: Float) {
            if (this.haloColor == haloColor && this.haloWidth == haloWidth) return
            this.haloColor = haloColor
            this.haloWidth = haloWidth
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            // If the layout isn't ready (e.g. the view was never measured), fall back to
            // the default TextView draw — nothing visible to outline yet.
            val layout = layout
            if (layout == null || haloWidth <= 0f) {
                super.onDraw(canvas)
                return
            }
            // First pass: stroke the glyph outlines in the halo color. We bypass the
            // TextView's mTextPaint and Layout's mPaint (they're the same object in
            // practice — the Layout was constructed with the TextView's paint) and
            // draw the text directly with canvas.drawText using a fresh Paint we
            // control. Earlier versions of this code mutated the TextView's mTextPaint
            // (or the Layout's mPaint, which is the same object) between two
            // super.onDraw calls; that approach broke on devices where TextView.onDraw
            // resets mTextPaint.color back to mCurTextColor before drawing, leaving the
            // halo pass with the text color and producing a "larger text" visual
            // instead of an outline. Drawing the halo via canvas.drawText with a fresh
            // Paint sidesteps any internal TextView state interference.
            drawHaloPass(
                canvas = canvas,
                layout = layout,
                basePaint = paint,
                haloColor = haloColor,
                haloWidth = haloWidth,
                paddingLeft = paddingLeft,
                paddingTop = paddingTop,
            )
            // Second pass: TextView draws normally in text color on top of the halo. The
            // fill occludes the interior of the halo so only the outline is visible.
            super.onDraw(canvas)
        }

        companion object {
            /**
             * Stroke the [layout]'s text in the [haloColor] on [canvas], drawing each
             * line of the layout directly with [Canvas.drawText] so we have full control
             * over the paint (no reliance on mutating the TextView's mTextPaint or the
             * Layout's mPaint — those are the same object in practice, and the TextView
             * may reset mTextPaint.color from mCurTextColor before drawing, which would
             * erase our halo color and produce a "larger text" visual instead of an
             * outline). [basePaint] supplies the typography (font, size, flags) for the
             * halo pass so the outline glyphs match the fill glyphs; [paddingLeft] and
             * [paddingTop] are the TextView's padding offsets that shift the layout's
             * coordinate space to the canvas.
             *
             * Static + takes a `Layout` so tests can substitute a spy Layout and verify
             * the draw calls.
             */
            fun drawHaloPass(
                canvas: Canvas,
                layout: Layout,
                basePaint: Paint,
                haloColor: Int,
                haloWidth: Float,
                paddingLeft: Int = 0,
                paddingTop: Int = 0,
            ) {
                // Fresh Paint object: we don't want to share state with the TextView's
                // mTextPaint or the Layout's mPaint (which are the same object in practice
                // and can be reset by TextView.onDraw before drawing). Copy from a
                // caller-supplied base paint so the font/size match the fill pass's
                // typography, but isolate style/strokeWidth/color for the halo pass.
                val paint = Paint(basePaint)
                paint.isAntiAlias = true
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = haloWidth
                paint.color = haloColor
                val text: String = layout.text.toString()
                for (line in 0 until layout.lineCount) {
                    val start: Int = layout.getLineStart(line)
                    val end: Int = layout.getLineEnd(line)
                    val baseline: Float = layout.getLineBaseline(line).toFloat() + paddingTop
                    val xStart: Float = layout.getLineLeft(line) + paddingLeft
                    if (end > start) {
                        canvas.drawText(text, start, end, xStart, baseline, paint)
                    }
                }
            }
        }
    }

    companion object {
        /** Mirrors [ca.voiditswarranty.roadtripradar.model.ringDistancesForZoom] max count = 4. */
        const val MAX_RING_COUNT = 4

        // Text size 14sp — matches the existing CarRouteWidget / CarWeatherWidget, and
        // approximates the phone's "1.2f.em" on the 12sp base (~14.4sp).
        private const val LABEL_TEXT_SIZE_SP = 14f

        // Halo stroke width in pixels. The phone uses textHaloWidth = 3.dp. The car virtual
        // display runs at density 1.0 (1dp = 1px), so 3px is the equivalent. 3px is wide
        // enough to occlude the 3.5px ring line behind the text on a 1:1 pixel scale.
        private const val LABEL_HALO_WIDTH = 3f
    }
}
