package ch.wasserwaage

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Anzeige für den Flächen-Modus: Handy liegt mit dem Display nach oben.
 * Dosenlibelle (beide Richtungen) plus je eine Röhrenlibelle für Längs- und Querrichtung.
 */
class SurfaceLevelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val d = LevelDraw(context)
    private var longitudinalDeg = 0.0
    private var transverseDeg = 0.0
    private var valid = false
    private var hasData = false

    fun update(longitudinalDeg: Double, transverseDeg: Double, valid: Boolean) {
        this.longitudinalDeg = longitudinalDeg
        this.transverseDeg = transverseDeg
        this.valid = valid
        hasData = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val dens = d.density

        if (!hasData || !valid) {
            d.drawText(canvas, context.getString(R.string.hint_surface), w / 2f, h / 2f, 18f * dens, d.colorDim)
            return
        }

        val pad = 16f * dens
        val textBlock = 120f * dens
        val t = min(w, h) * 0.11f
        // Radius so wählen, dass Kreis + senkrechte Libelle in die Breite und
        // Kreis + waagrechte Libelle + Texte in die Höhe passen
        val r = min((w - 3 * pad - t) / 2f, (h - 3 * pad - t - textBlock) / 2f)
        val totalW = 2 * r + pad + t
        val ox = (w - totalW) / 2f
        val ccx = ox + r
        val ccy = pad + r

        val total = sqrt(longitudinalDeg * longitudinalDeg + transverseDeg * transverseDeg)
        val range = LevelDraw.VIAL_RANGE_DEG

        // Dosenlibelle
        d.fill.color = context.getColor(R.color.vial_liquid)
        canvas.drawCircle(ccx, ccy, r, d.fill)
        d.stroke.color = context.getColor(R.color.vial)
        d.stroke.strokeWidth = 3f * dens
        canvas.drawCircle(ccx, ccy, r, d.stroke)
        d.stroke.strokeWidth = 1f * dens
        d.stroke.color = d.colorDim
        for (deg in 1..4) canvas.drawCircle(ccx, ccy, r * deg / range.toFloat(), d.stroke)
        canvas.drawLine(ccx - r, ccy, ccx + r, ccy, d.stroke)
        canvas.drawLine(ccx, ccy - r, ccx, ccy + r, d.stroke)

        val bubbleR = r * 0.13f
        var bx = (transverseDeg / range).toFloat() * r
        var by = (-longitudinalDeg / range).toFloat() * r
        val dist = sqrt(bx * bx + by * by)
        val maxDist = r - bubbleR
        if (dist > maxDist) {
            bx *= maxDist / dist
            by *= maxDist / dist
        }
        // Zielkreis
        d.stroke.color = d.colorText
        d.stroke.strokeWidth = 2f * dens
        canvas.drawCircle(ccx, ccy, bubbleR + 3f * dens, d.stroke)
        d.fill.color = d.levelColor(total)
        canvas.drawCircle(ccx + bx, ccy + by, bubbleR, d.fill)
        d.fill.color = 0x55FFFFFF
        canvas.drawCircle(ccx + bx - bubbleR * 0.35f, ccy + by - bubbleR * 0.35f, bubbleR * 0.25f, d.fill)

        // Senkrechte Röhrenlibelle rechts: Längsrichtung
        d.drawVial(canvas, ox + 2 * r + pad + t / 2f, ccy, 2 * r, t, horizontal = false, valueDeg = longitudinalDeg)
        // Waagrechte Röhrenlibelle unten: Querrichtung
        val hvY = ccy + r + pad + t / 2f
        d.drawVial(canvas, ccx, hvY, 2 * r, t, horizontal = true, valueDeg = transverseDeg)

        // Zahlenwerte
        val rowSize = 22f * dens
        val y1 = hvY + t / 2f + pad + rowSize
        drawRow(canvas, w, y1, context.getString(R.string.longitudinal), longitudinalDeg,
            if (longitudinalDeg > 0) "oben höher" else "unten höher", rowSize)
        drawRow(canvas, w, y1 + rowSize * 1.9f, context.getString(R.string.transverse), transverseDeg,
            if (transverseDeg > 0) "rechts höher" else "links höher", rowSize)
    }

    private fun drawRow(canvas: Canvas, w: Float, y: Float, label: String, deg: Double, side: String, size: Float) {
        val sideText = if (abs(deg) < 0.05) "eben" else side
        d.drawText(canvas, "$label  ${LevelDraw.formatAngle(deg)}", w / 2f, y, size, d.levelColor(deg), bold = true)
        d.drawText(canvas, "${LevelDraw.formatMm(deg)}  ·  $sideText", w / 2f, y + size * 0.85f, size * 0.6f, d.colorDim)
    }
}
