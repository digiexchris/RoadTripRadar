package ca.voiditswarranty.roadtripradar.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import ca.voiditswarranty.roadtripradar.ui.renderMakiIcon

/**
 * Renders a maki icon (a monochrome SVG from the `maki` asset pack, the same set the map uses)
 * into a circular badge [CarIcon] suitable for car [androidx.car.app.model.GridItem]s.
 *
 * Selected categories render as a filled primary-blue circle with a white glyph; unselected ones
 * render as a white circle with a dark glyph and a thin gray border. The host-side selection state
 * is conveyed separately via a dot [androidx.car.app.model.Badge], so this class only owns the icon
 * chrome. The SVG-decode + circle-chrome + glyph-draw pipeline is shared with the phone's
 * `ui.MapLayers.loadMakiIcon` via [renderMakiIcon]; this class only supplies the car-specific
 * colors and wraps the result as a [CarIcon]. Bitmaps are cached per (iconName, selected) for the
 * life of the process — the POI category set is fixed, so re-decoding on every `onGetTemplate()`
 * would be wasteful.
 */
object MakiIcons {
    private const val SIZE_PX = 128
    private const val SELECTED_FILL = 0xFF3B82F6.toInt()      // blue-500
    private const val SELECTED_GLYPH = 0xFFFFFFFF.toInt()
    private const val UNSELECTED_FILL = 0xFFFFFFFF.toInt()
    private const val UNSELECTED_BORDER = 0xFF9CA3AF.toInt()   // gray-400
    private const val UNSELECTED_GLYPH = 0xFF1F2937.toInt()    // gray-800
    private const val BORDER_WIDTH = 4f
    private const val PADDING_FRACTION = 0.22f
    private const val CIRCLE_INSET = 2f

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
        val fill = if (selected) SELECTED_FILL else UNSELECTED_FILL
        val glyphTint = if (selected) SELECTED_GLYPH else UNSELECTED_GLYPH
        val bmp = renderMakiIcon(
            context = context,
            iconName = iconName,
            sizePx = SIZE_PX,
            fillArgb = fill,
            borderColor = UNSELECTED_BORDER,
            borderWidth = if (selected) 0f else BORDER_WIDTH,
            glyphTintArgb = glyphTint,
            paddingFraction = PADDING_FRACTION,
            circleInset = CIRCLE_INSET,
        )
        // Fall back to a plain circle if the SVG couldn't be decoded (still a valid grid image).
        val out = bmp ?: Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888).also { b ->
            Canvas(b).drawCircle(
                SIZE_PX / 2f,
                SIZE_PX / 2f,
                SIZE_PX / 2f - CIRCLE_INSET,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill; style = Paint.Style.FILL },
            )
        }
        return CarIcon.Builder(IconCompat.createWithBitmap(out)).build()
    }
}