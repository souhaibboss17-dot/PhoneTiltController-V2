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

    @Volatile private var running = false
    @Volatile private var isSender = false
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var connection: Socket? = null
    @Volatile private var writer: PrintWriter? = null

    private val network = Executors.newCachedThreadPool()

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
            setPadding(24, 12, 24, 12)
            setBackgroundColor(bg)
        }

        fun label(
            text: String,
            size: Float,
            color: Int
        ): TextView {
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
            setSingleLine(true)
            setPadding(12, 4, 12, 4)
            visibility = View.GONE
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun makeButton(
            text: String,
            action: () -> Unit
        ): Button {
            return Button(this).apply {
                this.text = text
                setOnClickListener { action() }
            }
        }

        val senderButton = makeButton("SENDER") {
            stopEverything()
            isSender = true
            ipInput.visibility = View.VISIBLE
            statusText.text = "Enter receiver IP, then CONNECT"
        }

        val receiverButton = makeButton("RECEIVER") {
            stopEverything()
            isSender = false
            ipInput.visibility = View.GONE
            startReceiver()
        }

        val connectButton = makeButton("CONNECT") {
            if (isSender) {
                connectToReceiver()
            } else {
                statusText.text = "Choose SENDER on Redmi"
            }
        }

        val stopButton = makeButton("STOP") {
            stopEverything()
            statusText.text = "Stopped"
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
        statusText.text = "Finding Wi-Fi address..."

        network.execute {
            val ip = getLocalIPv4()

            runOnUiThread {
                if (ip == null) {
                    statusText.text =
                        "No local IP found. Check Wi-Fi."
                    ipText.text = "Receiver IP: unavailable"
                    return@runOnUiThread
                }

                ipText.text = "Receiver IP: $ip"
                statusText.text = "Starting receiver..."
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

                while (!server.isClosed && !client.isClosed) {
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
                        statusText.text =
                            "Receiver stopped or connection failed"
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

    private fun getLocalIPv4(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
                ?: return null

            val addresses = mutableListOf<String>()

            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()

                if (!networkInterface.isUp ||
                    networkInterface.isLoopback
                ) continue

                val entries = networkInterface.inetAddresses

                while (entries.hasMoreElements()) {
                    val address = entries.nextElement()

                    if (address is Inet4Address &&
                        !address.isLoopbackAddress &&
                        !address.isLinkLocalAddress
                    ) {
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

        network.execute {
            try {
                val client = Socket()
                client.connect(
                    InetSocketAddress(ip, 45678),
                    5000
                )

                connection = client
                writer = PrintWriter(
                    client.getOutputStream(),
                    true
                )

                running = true

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
                        "Connection failed. Check IP and Wi-Fi."
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!running || !isSender || event == null) return

        val x = event.values[0]
        val y = event.values[1]

        val angle = Math.toDegrees(
            atan2(x.toDouble(), y.toDouble())
        )

        val value = String.format(Locale.US, "%.1f", angle)

        runOnUiThread {
            angleText.text = "$value°"
        }

        val currentWriter = writer ?: return
        currentWriter.println(value)

        if (currentWriter.checkError()) {
            runOnUiThread {
                statusText.text = "Connection lost"
            }
            stopEverything()
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {
    }

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
        network.shutdownNow()
        super.onDestroy()
    }
}
