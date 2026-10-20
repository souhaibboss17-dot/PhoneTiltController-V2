package com.phoxtilt.controller

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.atan2

class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var angleText: TextView
    private lateinit var statusText: TextView
    private lateinit var bar: TextView
    private var running = false

    private val bg = Color.rgb(12, 16, 25)
    private val green = Color.rgb(0, 235, 150)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.statusBarColor = bg
        window.navigationBarColor = bg

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 12, 28, 12)
            setBackgroundColor(bg)
        }

        val title = TextView(this).apply {
            text = "PHONE TILT"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "CONTROLLER  •  LANDSCAPE MODE"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        angleText = TextView(this).apply {
            text = "0.0°"
            textSize = 48f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            gravity = Gravity.CENTER
        }

        bar = TextView(this).apply {
            text = "◀────────●────────▶"
            textSize = 24f
            setTextColor(green)
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "SENSOR STOPPED"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val start = Button(this).apply {
            text = "START"
            setTextColor(Color.BLACK)
            setBackgroundColor(green)
            setOnClickListener {
                running = true
                val sensor = accelerometer
                if (sensor != null) {
                    sensorManager.registerListener(
                        this@MainActivity,
                        sensor,
                        SensorManager.SENSOR_DELAY_GAME
                    )
                    statusText.text = "SENSOR ACTIVE"
                } else {
                    statusText.text = "NO ACCELEROMETER FOUND"
                }
            }
        }

        val stop = Button(this).apply {
            text = "STOP"
            setOnClickListener {
                running = false
                sensorManager.unregisterListener(this@MainActivity)
                statusText.text = "SENSOR STOPPED"
            }
        }

        buttons.addView(start)
        buttons.addView(stop)

        root.addView(title)
        root.addView(subtitle)
        root.addView(angleText)
        root.addView(bar)
        root.addView(statusText)
        root.addView(buttons)
        setContentView(root)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!running || event == null) return
        val x = event.values[0]
        val y = event.values[1]
        val angle = Math.toDegrees(atan2(x.toDouble(), y.toDouble()))
        angleText.text = String.format("%.1f°", angle)

        val indicator = when {
            angle < -25 -> "◀────●──────────▶"
            angle > 25 -> "◀──────────●────▶"
            else -> "◀────────●──────▶"
        }
        bar.text = indicator
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}
