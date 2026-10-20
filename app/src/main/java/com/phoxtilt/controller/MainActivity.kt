package com.phoxtilt.controller

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var angleText: TextView
    private lateinit var statusText: TextView
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.statusBarColor = Color.rgb(15, 18, 25)
        window.navigationBarColor = Color.rgb(15, 18, 25)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.rgb(15, 18, 25))
        }

        val title = TextView(this).apply {
            text = "PHONE TILT CONTROLLER"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        angleText = TextView(this).apply {
            text = "0.0°"
            textSize = 48f
            setTextColor(Color.rgb(0, 230, 140))
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "Sensor ready • USB not connected"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        val startButton = Button(this).apply {
            text = "START SENSOR"
            setOnClickListener {
                running = true
                accelerometer?.let {
                    sensorManager.registerListener(
                        this@MainActivity, it, SensorManager.SENSOR_DELAY_GAME
                    )
                }
                statusText.text = "Tilt sensor running • USB transfer not implemented"
            }
        }

        val stopButton = Button(this).apply {
            text = "STOP"
            setOnClickListener {
                running = false
                sensorManager.unregisterListener(this@MainActivity)
                statusText.text = "Sensor stopped"
            }
        }

        root.addView(title)
        root.addView(angleText)
        root.addView(statusText)
        root.addView(startButton)
        root.addView(stopButton)
        setContentView(root)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!running || event == null) return
        val x = event.values[0]
        val y = event.values[1]
        val angle = Math.toDegrees(kotlin.math.atan2(x.toDouble(), y.toDouble()))
        angleText.text = String.format("%.1f°", angle)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}
