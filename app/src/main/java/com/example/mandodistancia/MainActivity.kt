package com.example.mandodistancia

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var btnConectar: Button
    private lateinit var btnAdelante: Button
    private lateinit var btnAtras: Button
    private lateinit var btnIzquierda: Button
    private lateinit var btnDerecha: Button
    private lateinit var btnDetener: Button
    private lateinit var txtEstado: TextView

    private val DEVICE_NAME = "Barco_RC_ESP32"
    private val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val CHARACTERISTIC_UUID_RX: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var characteristicRx: BluetoothGattCharacteristic? = null

    private val REQUEST_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnConectar = findViewById(R.id.btnConectar)
        btnAdelante = findViewById(R.id.btnAdelante)
        btnAtras = findViewById(R.id.btnAtras)
        btnIzquierda = findViewById(R.id.btnIzquierda)
        btnDerecha = findViewById(R.id.btnDerecha)
        btnDetener = findViewById(R.id.btnDetener)
        txtEstado = findViewById(R.id.txtEstado)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        verificarPermisos()

        btnConectar.setOnClickListener {
            if (bluetoothGatt == null) {
                conectarDispositivo()
            } else {
                desconectarDispositivo()
            }
        }

        // Configuración de botones con feedback visual (cambio de color al presionar)
        configurarBotonControl(btnAdelante, "F", "S")
        configurarBotonControl(btnAtras, "B", "S")
        configurarBotonControl(btnIzquierda, "L", "C")
        configurarBotonControl(btnDerecha, "R", "C")

        btnDetener.setOnClickListener {
            enviarComando("S")
            enviarComando("C")
        }
    }

    private fun configurarBotonControl(boton: Button, comandoPresionar: String, comandoSoltar: String) {
        boton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Feedback visual: Cambia el botón a verde oscuro al presionarlo
                    v.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
                    enviarComando(comandoPresionar)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Restaura el color original del botón al soltarlo
                    v.backgroundTintList = null
                    enviarComando(comandoSoltar)
                    true
                }
                else -> false
            }
        }
    }

    private fun verificarPermisos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permisos = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            if (permisos.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
                ActivityCompat.requestPermissions(this, permisos, REQUEST_PERMISSION_CODE)
            }
        }
    }

    private fun conectarDispositivo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Faltan permisos de escaneo", Toast.LENGTH_SHORT).show()
            return
        }

        characteristicRx = null
        txtEstado.text = "Estado: Escaneando..."
        val scanner = bluetoothAdapter?.bluetoothLeScanner

        scanner?.startScan(object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val dispositivo = result?.device
                val nombreDispositivo = result?.scanRecord?.deviceName ?: dispositivo?.name

                if (nombreDispositivo == DEVICE_NAME) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                        return
                    }
                    scanner.stopScan(this)

                    runOnUiThread { txtEstado.text = "Estado: Conectando..." }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return
                    }

                    bluetoothGatt = dispositivo?.connectGatt(applicationContext, false, gattCallback)
                }
            }
        })
    }

    // Método para limpiar la caché interna de Android y evitar fallos de servicios fantasma
    private fun refreshGattCache(gatt: BluetoothGatt): Boolean {
        try {
            val localMethod = gatt.javaClass.getMethod("refresh")
            return localMethod.invoke(gatt) as Boolean
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                runOnUiThread {
                    txtEstado.text = "Estado: Sincronizando..."
                    btnConectar.text = "Desconectar"
                }

                android.os.Handler(mainLooper).postDelayed({
                    if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        // Limpiamos caché y descubrimos servicios de manera limpia
                        if (gatt != null) {
                            refreshGattCache(gatt)
                        }
                        gatt?.discoverServices()
                    }
                }, 600)

            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                runOnUiThread {
                    txtEstado.text = "Estado: Desconectado"
                    txtEstado.setTextColor(ContextCompat.getColor(applicationContext, android.R.color.holo_red_dark))
                    btnConectar.text = "Conectar Bluetooth"
                    Toast.makeText(applicationContext, "Desconectado del Barco", Toast.LENGTH_SHORT).show()
                }
                try {
                    gatt?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                bluetoothGatt = null
                characteristicRx = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(SERVICE_UUID)
                if (service != null) {
                    characteristicRx = service.getCharacteristic(CHARACTERISTIC_UUID_RX)
                }

                runOnUiThread {
                    if (characteristicRx != null) {
                        txtEstado.text = "Estado: ¡Listo para Controlar!"
                        txtEstado.setTextColor(ContextCompat.getColor(applicationContext, android.R.color.holo_green_dark))
                        Toast.makeText(applicationContext, "¡Canales BLE vinculados con éxito!", Toast.LENGTH_SHORT).show()
                    } else {
                        txtEstado.text = "Estado: Error de Característica"
                        Toast.makeText(applicationContext, "Error: Característica RX no hallada en el ESP32", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Fallo al descubrir servicios: $status", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun enviarComando(comando: String) {
        if (bluetoothGatt == null || characteristicRx == null) {
            return
        }

        val data = comando.toByteArray(Charsets.UTF_8)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        characteristicRx?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

        val envioExitoso: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val resultado = bluetoothGatt?.writeCharacteristic(
                characteristicRx!!,
                data,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            )
            resultado == BluetoothGatt.GATT_SUCCESS
        } else {
            characteristicRx?.value = data
            bluetoothGatt?.writeCharacteristic(characteristicRx) ?: false
        }

        if (!envioExitoso) {
            runOnUiThread {
                Toast.makeText(this, "Error de salida al enviar comando", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun desconectarDispositivo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        bluetoothGatt?.disconnect()
        try {
            bluetoothGatt?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        bluetoothGatt = null
        characteristicRx = null

        txtEstado.text = "Estado: Desconectado"
        txtEstado.setTextColor(ContextCompat.getColor(applicationContext, android.R.color.holo_red_dark))
        btnConectar.text = "Conectar Bluetooth"
    }
}