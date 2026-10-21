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
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.net.ServerSocket
import java.net.Socket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.InetSocketAddress
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.atan2

class MainActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var angleText: TextView
    private lateinit var statusText: TextView
    private lateinit var ipText: TextView
    private lateinit var ipInput: EditText

    private val executor = Executors.newCachedThreadPool()

    @Volatile private var running = false
    @Volatile private var isSender = false
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var connection: Socket? = null
    @Volatile private var writer: PrintWriter? = null

    private val bg = Color.rgb(12, 16, 25)
    private val green = Color.rgb(0, 235, 150)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.statusBarColor = bg
        window.navigationBarColor = bg

        sensorManager =
            getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer =
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20, 8, 20, 8)
            setBackgroundColor(bg)
        }

        fun label(text: String, size: Float, color: Int) =
            TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
            }

        val title = label("PHONE TILT", 27f, green).apply {
            typeface = Typeface.DEFAULT_BOLD
        }

        val subtitle = label("WIRELESS CONTROLLER", 12f, Color.LTGRAY)
        angleText = label("0.0°", 46f, white).apply {
            typeface = Typeface.DEFAULT_BOLD
        }
        statusText = label("Choose SENDER or RECEIVER", 14f, green)
        ipText = label("Receiver IP: not started", 13f, white)

        ipInput = EditText(this).apply {
            hint = "Enter RECEIVER IP address"
            setHintTextColor(Color.GRAY)
            setTextColor(white)
            setSingleLine(true)
            textSize = 14f
            visibility = View.GONE
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun button(text: String, action: () -> Unit) =
            Button(this).apply {
                this.text = text
                setOnClickListener { action() }
            }

        row.addView(button("SENDER") {
            stopEverything()
            isSender = true
            ipInput.visibility = View.VISIBLE
            statusText.text = "Enter receiver IP, then CONNECT"
        })

        row.addView(button("RECEIVER") {
            stopEverything()
            isSender = false
            ipInput.visibility = View.GONE
            startReceiver()
        })

        row.addView(button("CONNECT") {
            if (isSender) connectToReceiver()
            else statusText.text = "Choose SENDER on Redmi"
        })

        row.addView(button("STOP") {
            stopEverything()
            statusText.text = "Stopped"
        })

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
        statusText.text = "Finding network address..."

        executor.execute {
            val ip = findLocalIPv4()

            runOnUiThread {
                if (ip == null) {
                    ipText.text = "Receiver IP: unavailable"
                    statusText.text = "No local IP found. Check Wi-Fi."
                } else {
                    ipText.text = "Receiver IP: $ip"
                    statusText.text = "Starting receiver..."
                }
            }

            if (ip == null) return@execute

            var server: ServerSocket? = null

            try {
                server = ServerSocket(45678)
                serverSocket = server

                runOnUiThread {
                    statusText.text = "Waiting for sender..."
                }

                val client = server.accept()
                connection = client

                runOnUiThread {
                    statusText.text = "SENDER CONNECTED"
                }

                val reader = BufferedReader(
                    InputStreamReader(client.getInputStream())
                )

                while (!client.isClosed && !server.isClosed) {
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
                        statusText.text = "Receiver stopped or connection error"
                    }
                }
            } finally {
                try {
                    connection?.close()
                    server?.close()
                } catch (_: Exception) {
                }
                connection = null
                serverSocket = null
            }
        }
    }

    private fun findLocalIPv4(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
                ?: return null
            val addresses = mutableListOf<String>()

            while (interfaces.hasMoreElements()) {
                val net = interfaces.nextElement()
                if (!net.isUp || net.isLoopback) continue

                val entries = net.inetAddresses
                while (entries.hasMoreElements()) {
                    val address = entries.nextElement()
                    if (address !is Inet4Address ||
                        address.isLoopbackAddress ||
                        address.isLinkLocalAddress
                    ) continue

                    val ip = address.hostAddress ?: continue
                    if (ip.startsWith("192.168.") ||
                        ip.startsWith("10.") ||
                        ip.matches(
                            Regex("""172\.(1[6-9]|2[0-9]|3[01])\..*""")
                        )
                    ) {
                        addresses.add(ip)
                    }
                }
            }
            addresses.firstOrNull()
        } catch (_: Exception) {
            null
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

        executor.execute {
            var client: Socket? = null

            try {
                client = Socket()
                client.connect(InetSocketAddress(ip, 45678), 5000)

                val output = PrintWriter(
                    client.getOutputStream(),
                    true
                )

                connection = client
                writer = output

                runOnUiThread {
                    if (accelerometer == null) {
                        statusText.text = "No accelerometer found"
                        try {
                            client?.close()
                        } catch (_: Exception) {
                        }
                        return@runOnUiThread
                    }

                    running = true
                    isSender = true

                    sensorManager.registerListener(
                        this@MainActivity,
                        accelerometer,
                        SensorManager.SENSOR_DELAY_GAME
                    )

                    statusText.text = "Connected - sending tilt"
                    ipText.text = "Connected to $ip"
                }
            } catch (e: Exception) {
                try {
                    client?.close()
                } catch (_: Exception) {
                }

                running = false
                runOnUiThread {
                    statusText.text =
                        "Connection failed. Check IP and Wi-Fi."
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!running || !isSender || event == null) return

        val angle = Math.toDegrees(
            atan2(
                event.values[0].toDouble(),
                event.values[1].toDouble()
            )
        )

        val value = String.format(Locale.US, "%.1f", angle)

        runOnUiThread {
            angleText.text = "$value°"
        }

        val output = writer ?: return
        output.println(value)

        if (output.checkError()) {
            runOnUiThread {
                statusText.text = "Connection lost"
            }
            stopEverything()
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
        } catch (_: Exception) {
        }

        writer = null
        connection = null
        serverSocket = null
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        stopEverything()
        executor.shutdownNow()
        super.onDestroy()
    }
}
