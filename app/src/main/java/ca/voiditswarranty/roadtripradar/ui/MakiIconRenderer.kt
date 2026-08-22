package ca.voiditswarranty.roadtripradar.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import com.caverock.androidsvg.SVG

/**
 * Renders a maki icon (a monochrome SVG from the `maki` asset pack) into a circular-badge
 * [Bitmap], shared by the phone (`ui.MapLayers.loadMakiIcon`) and the car (`car.MakiIcons`).
 *
 * The phone wants a plain white circle with a gray border and the glyph drawn as-is; the car wants
 * a filled (selected) or white (unselected) circle with the glyph recolored to contrast. Both are
 * the same "decode SVG → draw circle chrome → draw glyph inset" pipeline, parameterized here.
 *
 * @param fillArgb      fill color of the circular background.
 * @param borderColor   stroke color of the circular border; drawn only when [borderWidth] > 0.
 * @param borderWidth   border stroke width in pixels; 0 means no border.
 * @param glyphTintArgb if non-null, the glyph is rendered to a temp bitmap then drawn through a
 *                      `SRC_IN` color filter to recolor it (the car path). If null, the glyph is
 *                      rendered directly onto the canvas in its own colors (the phone path).
 * @param paddingFraction  fraction of [sizePx] to inset the glyph by (0.2 on the phone, 0.22 car).
 * @param circleInset   pixels to inset the circle radius from the edge (1 phone, 2 car).
 * @return the bitmap, or `null` if the SVG could not be decoded.
 */
fun renderMakiIcon(
    context: Context,
    iconName: String,
    sizePx: Int,
    fillArgb: Int,
    borderColor: Int = 0,
    borderWidth: Float = 0f,
    glyphTintArgb: Int? = null,
    paddingFraction: Float = 0.2f,
    circleInset: Float = 1f,
): Bitmap? {
    return try {
        val svg = context.assets.open("maki/$iconName.svg").use { SVG.getFromInputStream(it) }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = sizePx / 2f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillArgb
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center - circleInset, fillPaint)
        if (borderWidth > 0f) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = borderColor
                style = Paint.Style.STROKE
                strokeWidth = borderWidth
            }
            canvas.drawCircle(center, center, center - circleInset, borderPaint)
        }
        val pad = sizePx * paddingFraction
        val g = sizePx - 2 * pad
        svg.documentWidth = g
        svg.documentHeight = g
        if (glyphTintArgb != null) {
            // Render the glyph to a temp bitmap, then draw it recolored so the maki SVG's own
            // fill color is replaced with [glyphTintArgb].
            val glyph = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val gCanvas = Canvas(glyph)
            gCanvas.save()
            gCanvas.translate(pad, pad)
            svg.renderToCanvas(gCanvas)
            gCanvas.restore()
            val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = PorterDuffColorFilter(glyphTintArgb, PorterDuff.Mode.SRC_IN)
            }
            canvas.drawBitmap(glyph, 0f, 0f, glyphPaint)
            glyph.recycle()
        } else {
            canvas.save()
            canvas.translate(pad, pad)
            svg.renderToCanvas(canvas)
            canvas.restore()
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}