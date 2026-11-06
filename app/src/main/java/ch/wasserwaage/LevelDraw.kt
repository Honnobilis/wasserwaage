package ch.wasserwaage

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max

/** Gemeinsame Farben und Zeichenfunktionen für beide Anzeigen. */
class LevelDraw(context: Context) {
    val density = context.resources.displayMetrics.density

    val colorText = context.getColor(R.color.text)
    val colorDim = context.getColor(R.color.text_dim)
    val colorAccent = context.getColor(R.color.accent)
    private val colorOk = context.getColor(R.color.level_ok)
    private val colorNear = context.getColor(R.color.level_near)
    private val colorOff = context.getColor(R.color.level_off)
    private val colorVial = context.getColor(R.color.vial)
    private val colorLiquid = context.getColor(R.color.vial_liquid)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val rect = RectF()

    fun levelColor(angleDeg: Double): Int {
        val a = abs(angleDeg)
        return when {
            a < OK_DEG -> colorOk
            a < NEAR_DEG -> colorNear
            else -> colorOff
        }
    }

    /** Text horizontal zentriert, vertikal mittig um [cy]. */
    fun drawText(canvas: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int, bold: Boolean = false) {
        text.textSize = size
        text.color = color
        text.isFakeBoldText = bold
        val fm = text.fontMetrics
        canvas.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, text)
    }

    /**
     * Röhrenlibelle. Die Blase wandert zur höheren Seite.
     * @param valueDeg positiv = Blase nach rechts (horizontal) bzw. nach oben (vertikal).
     */
    fun drawVial(
        canvas: Canvas, cx: Float, cy: Float, length: Float, thickness: Float,
        horizontal: Boolean, valueDeg: Double, rangeDeg: Double = VIAL_RANGE_DEG,
    ) {
        val halfL = length / 2f
        val halfT = thickness / 2f
        if (horizontal) rect.set(cx - halfL, cy - halfT, cx + halfL, cy + halfT)
        else rect.set(cx - halfT, cy - halfL, cx + halfT, cy + halfL)

        fill.color = colorLiquid
        canvas.drawRoundRect(rect, halfT, halfT, fill)
        stroke.color = colorVial
        stroke.strokeWidth = 3f * density
        canvas.drawRoundRect(rect, halfT, halfT, stroke)

        val bubbleHalf = thickness * 0.8f
        val bubbleHalfT = halfT * 0.72f
        val travel = halfL - bubbleHalf - halfT * 0.3f
        val pos = (valueDeg / rangeDeg).coerceIn(-1.0, 1.0).toFloat() * travel

        // Markierungen für die Mitte
        stroke.color = colorText
        stroke.strokeWidth = 2f * density
        val gap = bubbleHalf + 3f * density
        if (horizontal) {
            canvas.drawLine(cx - gap, cy - halfT, cx - gap, cy + halfT, stroke)
            canvas.drawLine(cx + gap, cy - halfT, cx + gap, cy + halfT, stroke)
            rect.set(cx + pos - bubbleHalf, cy - bubbleHalfT, cx + pos + bubbleHalf, cy + bubbleHalfT)
        } else {
            canvas.drawLine(cx - halfT, cy - gap, cx + halfT, cy - gap, stroke)
            canvas.drawLine(cx - halfT, cy + gap, cx + halfT, cy + gap, stroke)
            rect.set(cx - bubbleHalfT, cy - pos - bubbleHalf, cx + bubbleHalfT, cy - pos + bubbleHalf)
        }
        fill.color = levelColor(valueDeg)
        canvas.drawOval(rect, fill)
        // kleiner Glanzpunkt
        fill.color = 0x55FFFFFF
        val r = max(2f * density, bubbleHalfT * 0.3f)
        canvas.drawCircle(rect.left + rect.width() * 0.35f, rect.top + rect.height() * 0.35f, r, fill)
    }

    companion object {
        const val OK_DEG = 0.2
        const val NEAR_DEG = 1.0
        const val VIAL_RANGE_DEG = 5.0

        fun formatAngle(deg: Double): String = String.format("%.1f°", abs(deg) + 0.0)
        fun formatMm(deg: Double): String = String.format("%.1f mm/m", abs(LevelMath.mmPerMeter(deg)))
    }
}
