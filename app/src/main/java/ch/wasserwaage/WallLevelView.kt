package ch.wasserwaage

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.min

/**
 * Anzeige für den Wand-Modus: Handy liegt mit dem Rücken an der Wand.
 * Die Anzeige dreht sich in 90°-Schritten mit, damit sie immer lesbar ist.
 * Die farbige Linie bleibt immer waagrecht (wie ein Horizont), die Libelle zeigt die Abweichung.
 */
class WallLevelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val d = LevelDraw(context)
    private var snapDeg = 0
    private var deviationDeg = 0.0
    private var valid = false
    private var hasData = false
    private var held = false

    fun update(snapDeg: Int, deviationDeg: Double, valid: Boolean) {
        held = false
        this.snapDeg = snapDeg
        this.deviationDeg = deviationDeg
        this.valid = valid
        hasData = true
        invalidate()
    }

    /** Friert die Anzeige ein (true) oder gibt sie wieder frei (false). */
    fun setHeld(held: Boolean) {
        this.held = held
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val dens = d.density

        if (!hasData || !valid) {
            d.drawText(canvas, context.getString(R.string.hint_wall), cx, cy, 18f * dens, d.colorDim)
            return
        }

        canvas.save()
        canvas.rotate(snapDeg.toFloat(), cx, cy)
        val landscape = snapDeg % 180 != 0
        val fw = (if (landscape) height else width).toFloat()
        val fh = (if (landscape) width else height).toFloat()
        val m = min(fw, fh)
        val color = d.levelColor(deviationDeg)

        // Zahlenwert
        val bigSize = m * 0.17f
        val numY = cy - fh * 0.27f
        d.drawText(canvas, LevelDraw.formatAngle(deviationDeg), cx, numY, bigSize, color, bold = true)
        val side = when {
            abs(deviationDeg) < 0.05 -> "im Lot"
            deviationDeg > 0 -> "rechts höher"
            else -> "links höher"
        }
        d.drawText(
            canvas, "${LevelDraw.formatMm(deviationDeg)}  ·  $side",
            cx, numY + bigSize * 0.75f, m * 0.05f, d.colorDim,
        )

        // Bezugslinie entlang der Gerätekante (gestrichelt wirkend durch zwei Stücke)
        val half = fw * 0.42f
        d.stroke.color = d.colorDim
        d.stroke.strokeWidth = 2f * dens
        canvas.drawLine(cx - half, cy, cx - half * 0.15f, cy, d.stroke)
        canvas.drawLine(cx + half * 0.15f, cy, cx + half, cy, d.stroke)

        // Horizont: echte Waagrechte
        canvas.save()
        canvas.rotate(deviationDeg.toFloat(), cx, cy)
        d.stroke.color = color
        d.stroke.strokeWidth = 5f * dens
        canvas.drawLine(cx - half, cy, cx + half, cy, d.stroke)
        d.fill.color = color
        canvas.drawCircle(cx, cy, 7f * dens, d.fill)
        canvas.restore()

        // Röhrenlibelle
        d.drawVial(canvas, cx, cy + fh * 0.22f, fw * 0.8f, m * 0.11f, horizontal = true, valueDeg = deviationDeg)

        // Ausrichtung
        val label = if (landscape) R.string.long_edge_horizontal else R.string.long_edge_vertical
        d.drawText(canvas, context.getString(label), cx, cy + fh * 0.38f, m * 0.045f, d.colorDim)

        if (held) {
            d.drawText(canvas, context.getString(R.string.held), cx, cy - fh * 0.43f, m * 0.05f, d.colorAccent, bold = true)
        }

        canvas.restore()
    }
}
