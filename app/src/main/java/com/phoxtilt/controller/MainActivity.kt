package com.phoxtilt.controller

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.net.wifi.WifiManager
import java.net.ServerSocket
import java.net.Socket
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.concurrent.Executors
import kotlin.math.atan2

class MainActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private lateinit var angleText: TextView
    private lateinit var statusText: TextView
    private lateinit var ipText: TextView
    private lateinit var ipInput: EditText

    private var serverSocket: ServerSocket? = null
    @Volatile private var connection: Socket? = null
    @Volatile private var writer: PrintWriter? = null

    @Volatile private var running = false
    @Volatile private var isSender = false

    private val network = Executors.newCachedThreadPool()

    private val bg = Color.rgb(12, 16, 25)
    private val green = Color.rgb(0, 235, 150)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        window.statusBarColor = bg
        window.navigationBarColor = bg

        sensorManager =
            getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer =
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 12, 24, 12)
            setBackgroundColor(bg)
        }

        fun label(text: String, size: Float, color: Int): TextView {
            return TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
            }
        }

        val title = label("PHONE TILT", 27f, green).apply {
            typeface = Typeface.DEFAULT_BOLD
        }

        val subtitle = label(
            "WIRELESS CONTROLLER",
            12f,
            Color.LTGRAY
        )

        angleText = label("0.0°", 46f, white).apply {
            typeface = Typeface.DEFAULT_BOLD
        }

        statusText = label("Choose SENDER or RECEIVER", 14f, green)

        ipText = label("Receiver IP: not started", 13f, white)

        ipInput = EditText(this).apply {
            hint = "Enter RECEIVER IP address"
            setHintTextColor(Color.GRAY)
            setTextColor(white)
            textSize = 14f
            singleLine = true
            setPadding(12, 4, 12, 4)
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun makeButton(text: String, action: () -> Unit): Button {
            return Button(this).apply {
                this.text = text
                setOnClickListener { action() }
            }
        }

        val receiverButton = makeButton("RECEIVER") {
            isSender = false
            ipInput.visibility = android.view.View.GONE
            startReceiver()
        }

        val senderButton = makeButton("SENDER") {
            isSender = true
            ipInput.visibility = android.view.View.VISIBLE
            statusText.text = "Enter receiver IP, then CONNECT"
        }

        val connectButton = makeButton("CONNECT") {
            if (isSender) connectToReceiver()
            else statusText.text = "Start RECEIVER first"
        }

        val stopButton = makeButton("STOP") {
            stopEverything()
        }

        row.addView(senderButton)
        row.addView(receiverButton)
        row.addView(connectButton)
        row.addView(stopButton)

        root.addView(title)
        root.addView(subtitle)
        root.addView(angleText)
        root.addView(ipText)
        root.addView(ipInput)
        root.addView(statusText)
        root.addView(row)

        setContentView(root)
    }

    private fun startReceiver() {
        stopEverything()

        isSender = false
        ipInput.visibility = android.view.View.GONE

        val ip = getWifiIp()

        if (ip == null) {
            statusText.text = "Connect both phones to the same Wi-Fi"
            return
        }

        ipText.text = "Receiver IP: $ip"
        statusText.text = "Waiting for sender..."

        network.execute {
            try {
                val server = ServerSocket(45678)
                serverSocket = server

                runOnUiThread {
                    statusText.text = "Ready - waiting for sender"
                }

                val client = server.accept()
                connection = client

                runOnUiThread {
                    statusText.text = "SENDER CONNECTED"
                }

                val reader = BufferedReader(
                    InputStreamReader(client.getInputStream())
                )

                while (!client.isClosed) {
                    val line = reader.readLine() ?: break
                    runOnUiThread {
                        angleText.text = "$line°"
                    }
                }

                runOnUiThread {
                    statusText.text = "Sender disconnected"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (!isFinishing) {
                        statusText.text = "Connection stopped"
                    }
                }
            }
        }
    }

    private fun connectToReceiver() {
        val ip = ipInput.text.toString().trim()

        if (ip.isEmpty()) {
            statusText.text = "Enter receiver IP first"
            return
        }

        val keyboard = getSystemService(
            Context.INPUT_METHOD_SERVICE
        ) as InputMethodManager

        keyboard.hideSoftInputFromWindow(ipInput.windowToken, 0)

        statusText.text = "Connecting..."

        network.execute {
            try {
                val client = Socket(ip, 45678)
                connection = client
                writer = PrintWriter(
                    client.getOutputStream(),
                    true
                )

                running = true
                isSender = true

                runOnUiThread {
                    statusText.text = "Connected - sending tilt"
                    ipText.text = "Connected to $ip"
                }

                val sensor = accelerometer
                if (sensor != null) {
                    sensorManager.registerListener(
                        this,
                        sensor,
                        SensorManager.SENSOR_DELAY_GAME
                    )
                } else {
                    runOnUiThread {
                        statusText.text = "No accelerometer found"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text =
                        "Connection failed - check IP and Wi-Fi"
                }
            }
        }
    }

    private fun getWifiIp(): String? {
        return try {
            val wifi = applicationContext.getSystemService(
                Context.WIFI_SERVICE
            ) as WifiManager

            val address = wifi.connectionInfo.ipAddress

            if (address == 0) {
                null
            } else {
                "${address and 0xff}." +
                    "${(address shr 8) and 0xff}." +
                    "${(address shr 16) and 0xff}." +
                    "${(address shr 24) and 0xff}"
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!running || !isSender || event == null) return

        val x = event.values[0]
        val y = event.values[1]

        val angle = Math.toDegrees(
            atan2(x.toDouble(), y.toDouble())
        )

        val value = String.format("%.1f", angle)

        runOnUiThread {
            angleText.text = "$value°"
        }

        try {
            writer?.println(value)
        } catch (_: Exception) {
            running = false
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {}

    private fun stopEverything() {
        running = false
        sensorManager.unregisterListener(this)

        try {
            writer?.close()
            connection?.close()
            serverSocket?.close()
        } catch (_: Exception) {}

        writer = null
        connection = null
        serverSocket = null

        statusText.text = "Stopped"
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        stopEverything()
        network.shutdownNow()
        super.onDestroy()
    }
}
