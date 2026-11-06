package ch.wasserwaage

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast

class MainActivity : Activity(), SensorEventListener {

    private enum class Mode { WALL, SURFACE }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var prefs: SharedPreferences

    private lateinit var wallView: WallLevelView
    private lateinit var surfaceView: SurfaceLevelView
    private lateinit var btnWall: Button
    private lateinit var btnSurface: Button

    private var mode = Mode.WALL

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

        prefs = getSharedPreferences("wasserwaage", Context.MODE_PRIVATE)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        wallView = findViewById(R.id.wallView)
        surfaceView = findViewById(R.id.surfaceView)
        btnWall = findViewById(R.id.btnWall)
        btnSurface = findViewById(R.id.btnSurface)

        btnWall.setOnClickListener { setMode(Mode.WALL) }
        btnSurface.setOnClickListener { setMode(Mode.SURFACE) }
        findViewById<Button>(R.id.btnCalibrate).setOnClickListener { showCalibrationDialog() }

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

    private fun setMode(newMode: Mode) {
        mode = newMode
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        wallView.visibility = if (mode == Mode.WALL) View.VISIBLE else View.GONE
        surfaceView.visibility = if (mode == Mode.SURFACE) View.VISIBLE else View.GONE
        styleModeButton(btnWall, mode == Mode.WALL)
        styleModeButton(btnSurface, mode == Mode.SURFACE)
        render()
    }

    private fun styleModeButton(b: Button, selected: Boolean) {
        b.setBackgroundColor(if (selected) getColor(R.color.accent) else 0)
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
                wallView.update(r.snapDeg, dev, r.outOfPlaneDeg < MAX_WALL_TILT_DEG)
            }
            Mode.SURFACE -> {
                val s = LevelMath.surface(g[0], g[1], g[2])
                surfaceRawLong = s.longitudinalDeg
                surfaceRawTrans = s.transverseDeg
                surfaceView.update(
                    s.longitudinalDeg - prefs.getFloat(KEY_SURF_LONG, 0f),
                    s.transverseDeg - prefs.getFloat(KEY_SURF_TRANS, 0f),
                    s.tiltDeg < MAX_SURFACE_TILT_DEG,
                )
            }
        }
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
        private const val MAX_WALL_TILT_DEG = 40.0
        private const val MAX_SURFACE_TILT_DEG = 35.0
        private const val KEY_MODE = "mode"
        private const val KEY_WALL_PREFIX = "wall_offset_"
        private const val KEY_SURF_LONG = "surface_offset_long"
        private const val KEY_SURF_TRANS = "surface_offset_trans"
    }
}
