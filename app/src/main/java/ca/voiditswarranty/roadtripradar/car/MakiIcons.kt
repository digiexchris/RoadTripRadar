package ca.voiditswarranty.roadtripradar.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import com.caverock.androidsvg.SVG

/**
 * Renders a maki icon (a monochrome SVG from the `maki` asset pack, the same set the map
 * uses) into a circular badge [CarIcon] suitable for car [androidx.car.app.model.GridItem]s.
 *
 * Selected categories render as a filled primary-blue circle with a white glyph;
 * unselected ones render as a white circle with a dark glyph and a thin gray border. The
 * host-side selection state is conveyed separately via a dot [androidx.car.app.model.Badge],
 * so this class only owns the icon chrome. Bitmaps are cached per (iconName, selected) for
 * the life of the process — the POI category set is fixed, so re-decoding on every
 * `onGetTemplate()` would be wasteful.
 */
object MakiIcons {
    private const val SIZE_PX = 128
    private const val SELECTED_FILL = 0xFF3B82F6.toInt()      // blue-500
    private const val SELECTED_GLYPH = 0xFFFFFFFF.toInt()
    private const val UNSELECTED_FILL = 0xFFFFFFFF.toInt()
    private const val UNSELECTED_BORDER = 0xFF9CA3AF.toInt()   // gray-400
    private const val UNSELECTED_GLYPH = 0xFF1F2937.toInt()    // gray-800

    private val cache: MutableMap<String, CarIcon> =
        java.util.Collections.synchronizedMap(mutableMapOf())

    /** Returns a [CarIcon] for [iconName]; falls back to a plain circle if the SVG can't load. */
    fun forName(context: Context, iconName: String, selected: Boolean): CarIcon {
        val key = "$iconName:$selected"
        cache[key]?.let { return it }
        val icon = build(context, iconName, selected)
        cache[key] = icon
        return icon
    }

    private fun build(context: Context, iconName: String, selected: Boolean): CarIcon {
        val out = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val center = SIZE_PX / 2f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (selected) SELECTED_FILL else UNSELECTED_FILL
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center - 2f, fillPaint)
        if (!selected) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = UNSELECTED_BORDER
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawCircle(center, center, center - 2f, borderPaint)
        }
        // Render the maki glyph on top, tinted to contrast with the circle fill. If the
        // SVG can't be decoded we leave the plain circle (still a valid grid image).
        try {
            val svg = context.assets.open("maki/$iconName.svg").use { SVG.getFromInputStream(it) }
            val glyph = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
            val gCanvas = Canvas(glyph)
            val pad = SIZE_PX * 0.22f
            val g = SIZE_PX - 2 * pad
            svg.documentWidth = g
            svg.documentHeight = g
            gCanvas.save()
            gCanvas.translate(pad, pad)
            svg.renderToCanvas(gCanvas)
            gCanvas.restore()
            val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = PorterDuffColorFilter(
                    if (selected) SELECTED_GLYPH else UNSELECTED_GLYPH,
                    PorterDuff.Mode.SRC_IN,
                )
            }
            canvas.drawBitmap(glyph, 0f, 0f, glyphPaint)
            glyph.recycle()
        } catch (_: Exception) {
            // Fall back to the plain circle already drawn.
        }
        return CarIcon.Builder(IconCompat.createWithBitmap(out)).build()
    }
}