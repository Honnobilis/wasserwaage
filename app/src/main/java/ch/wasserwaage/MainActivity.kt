package ch.wasserwaage

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max

class MainActivity : Activity(), SensorEventListener {

    private enum class Mode { WALL, SURFACE }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var prefs: SharedPreferences

    private lateinit var wallView: WallLevelView
    private lateinit var surfaceView: SurfaceLevelView
    private lateinit var btnWall: Button
    private lateinit var btnSurface: Button
    private lateinit var btnHold: Button
    private lateinit var btnCalibrate: Button
    private lateinit var btnVibration: Button

    private var mode = Mode.WALL

    /** Anzeige eingefroren? */
    private var held = false

    private val haptics = HapticLogic()

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    // geglätteter Beschleunigungsvektor
    private val g = DoubleArray(3)
    private var hasSample = false

    // letzte Rohwerte (ohne Kalibrierung), für die Kalibrierung
    private var wallSnap: Int? = null
    private var wallRawDeviation = 0.0
    private var surfaceRawLong = 0.0
    private var surfaceRawTrans = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applySystemBarInsets(findViewById(R.id.root))

        prefs = getSharedPreferences("wasserwaage", Context.MODE_PRIVATE)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        wallView = findViewById(R.id.wallView)
        surfaceView = findViewById(R.id.surfaceView)
        btnWall = findViewById(R.id.btnWall)
        btnSurface = findViewById(R.id.btnSurface)
        btnHold = findViewById(R.id.btnHold)
        btnCalibrate = findViewById(R.id.btnCalibrate)

        btnWall.setOnClickListener { setMode(Mode.WALL) }
        btnSurface.setOnClickListener { setMode(Mode.SURFACE) }
        btnCalibrate.setOnClickListener { showCalibrationDialog() }
        btnHold.setOnClickListener { setHeld(!held) }
        btnVibration = findViewById(R.id.btnVibration)
        btnVibration.setOnClickListener { showVibrationDialog() }
        haptics.mode = HapticLogic.Mode.entries.firstOrNull { it.name == prefs.getString(KEY_VIBRATION, null) }
            ?: HapticLogic.Mode.APPROACH
        styleButton(btnVibration, false)
        updateVibrationButton()
        styleButton(btnCalibrate, false)
        setHeld(false)

        setMode(if (prefs.getString(KEY_MODE, null) == Mode.SURFACE.name) Mode.SURFACE else Mode.WALL)

        if (accelerometer == null) {
            Toast.makeText(this, R.string.no_sensor, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        hasSample = false
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    /**
     * Ab Android 15 zeichnet die App unter Status- und Navigationsleiste.
     * Damit nichts verdeckt wird, rücken wir den Inhalt um diese Bereiche ein.
     */
    private fun applySystemBarInsets(root: View) {
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(b.left, b.top, b.right, b.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom,
                )
            }
            insets
        }
        root.requestApplyInsets()
    }

    private fun setHeld(newHeld: Boolean) {
        held = newHeld
        btnHold.setText(if (held) R.string.resume else R.string.hold)
        styleButton(btnHold, held)
        wallView.setHeld(held)
        surfaceView.setHeld(held)
        if (!held) render()
    }

    private fun setMode(newMode: Mode) {
        mode = newMode
        haptics.reset()
        if (held) setHeld(false)
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        wallView.visibility = if (mode == Mode.WALL) View.VISIBLE else View.GONE
        surfaceView.visibility = if (mode == Mode.SURFACE) View.VISIBLE else View.GONE
        styleButton(btnWall, mode == Mode.WALL)
        styleButton(btnSurface, mode == Mode.SURFACE)
        render()
    }

    private fun styleButton(b: Button, selected: Boolean) {
        b.setBackgroundColor(getColor(if (selected) R.color.accent else R.color.surface))
        b.setTextColor(getColor(if (selected) R.color.background else R.color.text))
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val v = event.values
        if (!hasSample) {
            for (i in 0..2) g[i] = v[i].toDouble()
            hasSample = true
        } else {
            // Tiefpass: glättet Zittern, reagiert bei großen Bewegungen schneller
            val delta = Math.abs(v[0] - g[0]) + Math.abs(v[1] - g[1]) + Math.abs(v[2] - g[2])
            val alpha = if (delta > 3.0) 0.4 else SMOOTHING
            for (i in 0..2) g[i] += alpha * (v[i] - g[i])
        }
        render()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun render() {
        if (!hasSample) return
        when (mode) {
            Mode.WALL -> {
                val r = LevelMath.wall(g[0], g[1], g[2], wallSnap)
                wallSnap = r.snapDeg
                wallRawDeviation = r.deviationDeg
                val dev = LevelMath.signedDiff(r.deviationDeg, wallOffset(r.snapDeg))
                val valid = r.outOfPlaneDeg < MAX_WALL_TILT_DEG
                if (held) return
                wallView.update(r.snapDeg, dev, valid)
                updateLevelFeedback(valid, abs(dev))
            }
            Mode.SURFACE -> {
                val s = LevelMath.surface(g[0], g[1], g[2])
                surfaceRawLong = s.longitudinalDeg
                surfaceRawTrans = s.transverseDeg
                val long = s.longitudinalDeg - prefs.getFloat(KEY_SURF_LONG, 0f)
                val trans = s.transverseDeg - prefs.getFloat(KEY_SURF_TRANS, 0f)
                val valid = s.tiltDeg < MAX_SURFACE_TILT_DEG
                if (held) return
                surfaceView.update(long, trans, valid)
                updateLevelFeedback(valid, max(abs(long), abs(trans)))
            }
        }
    }

    private fun updateLevelFeedback(valid: Boolean, deviationDeg: Double) {
        when (haptics.update(valid, deviationDeg, SystemClock.elapsedRealtime())) {
            HapticLogic.Event.TICK -> vibrateTick()
            HapticLogic.Event.LEVEL -> vibrateLevel()
            HapticLogic.Event.NONE -> Unit
        }
    }

    /** Ganz kurzes Klopfen beim Annähern – stört die Messung kaum. */
    private fun vibrateTick() {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amp = if (v.hasAmplitudeControl()) TICK_AMPLITUDE else VibrationEffect.DEFAULT_AMPLITUDE
            v.vibrate(VibrationEffect.createOneShot(TICK_MS, amp))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(TICK_MS)
        }
    }

    /** Kräftiger Doppelpuls im Lot. */
    private fun vibrateLevel() {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (v.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(LEVEL_PATTERN, intArrayOf(0, 255, 0, 255), -1)
            } else {
                VibrationEffect.createWaveform(LEVEL_PATTERN, -1)
            }
            v.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(LEVEL_PATTERN, -1)
        }
    }

    private fun showVibrationDialog() {
        val modes = HapticLogic.Mode.entries
        val labels = arrayOf(
            getString(R.string.vibration_off),
            getString(R.string.vibration_level_only),
            getString(R.string.vibration_approach),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.vibration)
            .setSingleChoiceItems(labels, modes.indexOf(haptics.mode)) { dialog, which ->
                haptics.mode = modes[which]
                haptics.reset()
                prefs.edit().putString(KEY_VIBRATION, haptics.mode.name).apply()
                updateVibrationButton()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateVibrationButton() {
        btnVibration.setTextColor(getColor(if (haptics.mode == HapticLogic.Mode.OFF) R.color.text_dim else R.color.text))
    }

    private fun wallOffset(snap: Int): Double = prefs.getFloat(KEY_WALL_PREFIX + snap, 0f).toDouble()

    private fun showCalibrationDialog() {
        val msg = if (mode == Mode.WALL) R.string.calibrate_wall_msg else R.string.calibrate_surface_msg
        AlertDialog.Builder(this)
            .setTitle(R.string.calibrate_title)
            .setMessage(msg)
            .setPositiveButton(R.string.calibrate_set) { _, _ -> calibrate() }
            .setNeutralButton(R.string.calibrate_reset) { _, _ -> resetCalibration() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun calibrate() {
        if (!hasSample) return
        val e = prefs.edit()
        when (mode) {
            Mode.WALL -> wallSnap?.let { e.putFloat(KEY_WALL_PREFIX + it, wallRawDeviation.toFloat()) }
            Mode.SURFACE -> {
                e.putFloat(KEY_SURF_LONG, surfaceRawLong.toFloat())
                e.putFloat(KEY_SURF_TRANS, surfaceRawTrans.toFloat())
            }
        }
        e.apply()
        Toast.makeText(this, R.string.calibrated, Toast.LENGTH_SHORT).show()
        render()
    }

    private fun resetCalibration() {
        val e = prefs.edit()
        when (mode) {
            Mode.WALL -> listOf(0, 90, 180, 270).forEach { e.remove(KEY_WALL_PREFIX + it) }
            Mode.SURFACE -> e.remove(KEY_SURF_LONG).remove(KEY_SURF_TRANS)
        }
        e.apply()
        Toast.makeText(this, R.string.calibration_reset, Toast.LENGTH_SHORT).show()
        render()
    }

    companion object {
        private const val SMOOTHING = 0.08
        private const val TICK_MS = 18L
        private const val TICK_AMPLITUDE = 160
        private val LEVEL_PATTERN = longArrayOf(0, 120, 80, 120)
        private const val KEY_VIBRATION = "vibration"
        private const val MAX_WALL_TILT_DEG = 40.0
        private const val MAX_SURFACE_TILT_DEG = 35.0
        private const val KEY_MODE = "mode"
        private const val KEY_WALL_PREFIX = "wall_offset_"
        private const val KEY_SURF_LONG = "surface_offset_long"
        private const val KEY_SURF_TRANS = "surface_offset_trans"
    }
}
