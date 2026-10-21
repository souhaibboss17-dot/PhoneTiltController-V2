
package com.phoxtilt.controller

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.app.AlertDialog
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.atan2

class MainActivity : Activity(), SensorEventListener {

    companion object {
        private val APP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val PERMISSION_REQUEST = 100
        private const val ENABLE_BT_REQUEST = 101
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var angleText: TextView
    private lateinit var statusText: TextView
    private lateinit var deviceText: TextView

    private val executor = Executors.newCachedThreadPool()
    private var bluetoothAdapter: BluetoothAdapter? = null

    @Volatile private var running = false
    @Volatile private var isSender = false
    @Volatile private var receiverMode = false
    @Volatile private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var connection: BluetoothSocket? = null
    @Volatile private var writer: PrintWriter? = null

    private val bg = Color.rgb(12, 16, 25)
    private val green = Color.rgb(0, 235, 150)

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
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 8, 16, 8)
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

        val subtitle = label("BLUETOOTH CONTROLLER", 12f, Color.LTGRAY)
        angleText = label("0.0°", 46f, Color.WHITE).apply {
            typeface = Typeface.DEFAULT_BOLD
        }
        deviceText = label("Bluetooth: not connected", 13f, Color.WHITE)
        statusText = label("Choose SENDER or RECEIVER", 14f, green)

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
            receiverMode = false
            statusText.text = "Press CONNECT to choose paired phone"
        })

        row.addView(button("RECEIVER") {
            if (!ensureBluetoothReady()) return@button
            stopEverything()
            isSender = false
            receiverMode = true
            startReceiver()
        })

        row.addView(button("CONNECT") {
            if (!isSender) {
                statusText.text = "Choose SENDER first"
            } else if (ensureBluetoothReady()) {
                choosePairedDevice()
            }
        })

        row.addView(button("STOP") {
            stopEverything()
            statusText.text = "Stopped"
            deviceText.text = "Bluetooth: disconnected"
        })

        root.addView(title)
        root.addView(subtitle)
        root.addView(angleText)
        root.addView(deviceText)
        root.addView(statusText)
        root.addView(row)

        setContentView(root)
    }

    private fun ensureBluetoothReady(): Boolean {
        val adapter = bluetoothAdapter

        if (adapter == null) {
            statusText.text = "This phone has no Bluetooth"
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                PERMISSION_REQUEST
            )
            statusText.text = "Allow Bluetooth permission, then try again"
            return false
        }

        return try {
            if (!adapter.isEnabled) {
                startActivityForResult(
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                    ENABLE_BT_REQUEST
                )
                statusText.text = "Enable Bluetooth, then try again"
                false
            } else {
                true
            }
        } catch (_: SecurityException) {
            statusText.text = "Bluetooth permission required"
            false
        }
    }

    private fun choosePairedDevice() {
        try {
            val devices = bluetoothAdapter?.bondedDevices?.toList()
                ?: emptyList()

            if (devices.isEmpty()) {
                statusText.text = "Pair the phones in Bluetooth settings first"
                return
            }

            val labels = devices.map {
                "${it.name ?: "Bluetooth device"}  (${it.address})"
            }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("Choose Samsung receiver")
                .setItems(labels) { _, index ->
                    connectToDevice(devices[index])
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (_: SecurityException) {
            statusText.text = "Bluetooth permission required"
        }
    }

    private fun startReceiver() {
        statusText.text = "Starting Bluetooth receiver..."
        deviceText.text = "Waiting for sender"

        executor.execute {
            var server: BluetoothServerSocket? = null

            try {
                server = bluetoothAdapter!!.listenUsingRfcommWithServiceRecord(
                    "PhoneTiltController",
                    APP_UUID
                )
                serverSocket = server

                runOnUiThread {
                    statusText.text = "Waiting for Redmi sender..."
                }

                while (receiverMode && !server.isClosed) {
                    val client = server.accept()
                    if (!receiverMode) {
                        client.close()
                        break
                    }

                    connection = client

                    runOnUiThread {
                        deviceText.text = "Bluetooth: connected"
                        statusText.text = "SENDER CONNECTED"
                    }

                    try {
                        val reader = BufferedReader(
                            InputStreamReader(client.inputStream)
                        )

                        while (receiverMode && !client.isClosed) {
                            val line = reader.readLine() ?: break
                            runOnUiThread {
                                angleText.text = "$line°"
                            }
                        }
                    } finally {
                        try {
                            client.close()
                        } catch (_: Exception) {
                        }

                        if (connection === client) connection = null

                        runOnUiThread {
                            if (receiverMode) {
                                deviceText.text = "Bluetooth: disconnected"
                                statusText.text = "Waiting for sender again..."
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (receiverMode && !isFinishing) {
                        statusText.text = "Receiver stopped or Bluetooth error"
                    }
                }
            } finally {
                try {
                    server?.close()
                } catch (_: Exception) {
                }
                if (serverSocket === server) serverSocket = null
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        statusText.text = "Connecting by Bluetooth..."
        deviceText.text = "Connecting..."

        executor.execute {
            var socket: BluetoothSocket? = null

            try {
                socket = device.createRfcommSocketToServiceRecord(APP_UUID)
                socket.connect()

                val output = PrintWriter(socket.outputStream, true)

                connection = socket
                writer = output

                runOnUiThread {
                    if (accelerometer == null) {
                        statusText.text = "No accelerometer found"
                        stopEverything()
                        return@runOnUiThread
                    }

                    running = true
                    deviceText.text = "Connected to ${device.name ?: device.address}"
                    statusText.text = "Connected - sending tilt"

                    sensorManager.registerListener(
                        this@MainActivity,
                        accelerometer,
                        SensorManager.SENSOR_DELAY_GAME
                    )
                }
            } catch (_: Exception) {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }

                running = false
                runOnUiThread {
                    deviceText.text = "Bluetooth: disconnected"
                    statusText.text =
                        "Connection failed. Start RECEIVER on Samsung first."
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
            running = false
            sensorManager.unregisterListener(this)
            try {
                connection?.close()
            } catch (_: Exception) {
            }
            writer = null
            connection = null

            runOnUiThread {
                deviceText.text = "Bluetooth: disconnected"
                statusText.text = "Connection lost. Press CONNECT to retry."
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun stopEverything() {
        running = false
        receiverMode = false
        sensorManager.unregisterListener(this)

        try {
            writer?.close()
        } catch (_: Exception) {
        }

        try {
            connection?.close()
        } catch (_: Exception) {
        }

        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }

        writer = null
        connection = null
        serverSocket = null
    }

    override fun onDestroy() {
        stopEverything()
        executor.shutdownNow()
        super.onDestroy()
    }
}
