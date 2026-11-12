package com.example.mandodistancia

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {

    private lateinit var btnConectar: Button
    private lateinit var seekBarMotor: SeekBar
    private lateinit var seekBarTimon: SeekBar
    private lateinit var txtEstado: TextView

    private val TAG = "BLE_BARCO"
    private val DEVICE_NAME = "Barco_RC_ESP32"
    private val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val CHARACTERISTIC_UUID_RX: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var characteristicRx: BluetoothGattCharacteristic? = null

    private var escaneando = false
    private var ultimoEnvio = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    private val REQUEST_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnConectar = findViewById(R.id.btnConectar)
        seekBarMotor = findViewById(R.id.seekBarMotor)
        seekBarTimon = findViewById(R.id.seekBarTimon)
        txtEstado = findViewById(R.id.txtEstado)

        // Rango del timon: 0..40  ->  70..110 grados (centro = 18 -> 88)
        seekBarTimon.max = 40
        seekBarTimon.progress = 18

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        verificarPermisos()

        btnConectar.setOnClickListener {
            if (bluetoothGatt == null && !escaneando) {
                conectarDispositivo()
            } else {
                desconectarDispositivo()
            }
        }

        seekBarMotor.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) enviarConLimite("V$progress")
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                enviarComando("V${seekBar?.progress ?: 0}")
            }
        })

        seekBarTimon.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) enviarConLimite("T${progress + 70}")
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Siempre enviar el valor final al soltar
                enviarComando("T${(seekBar?.progress ?: 18) + 70}")
            }
        })
    }

    private fun verificarPermisos() {
        val permisos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        if (permisos.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permisos, REQUEST_PERMISSION_CODE)
        }
    }

    private fun tienePermisoScan(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

    private fun tienePermisoConnect(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (!escaneando) return // evita conectar varias veces
            val dispositivo = result?.device ?: return
            val nombre = result.scanRecord?.deviceName ?: (if (tienePermisoConnect()) dispositivo.name else null)

            if (nombre == DEVICE_NAME) {
                escaneando = false
                if (tienePermisoScan()) {
                    bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)
                }
                runOnUiThread { txtEstado.text = "Estado: Conectando..." }

                if (!tienePermisoConnect()) return
                bluetoothGatt = dispositivo.connectGatt(
                    applicationContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            escaneando = false
            Log.e(TAG, "Fallo el escaneo: $errorCode")
            runOnUiThread {
                txtEstado.text = "Estado: Error de escaneo ($errorCode)"
                btnConectar.text = "Conectar Bluetooth"
            }
        }
    }

    private fun conectarDispositivo() {
        if (!tienePermisoScan()) {
            verificarPermisos()
            Toast.makeText(this, "Acepta los permisos y vuelve a pulsar Conectar", Toast.LENGTH_SHORT).show()
            return
        }
        characteristicRx = null
        escaneando = true
        txtEstado.text = "Estado: Escaneando..."
        btnConectar.text = "Cancelar"
        bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)

        // Timeout de 10 s
        mainHandler.postDelayed({
            if (escaneando) {
                escaneando = false
                if (tienePermisoScan()) bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
                txtEstado.text = "Estado: No se encontro el barco"
                btnConectar.text = "Conectar Bluetooth"
            }
        }, 10000)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                runOnUiThread {
                    txtEstado.text = "Estado: Sincronizando..."
                    btnConectar.text = "Desconectar"
                }
                mainHandler.postDelayed({
                    if (tienePermisoConnect()) gatt?.discoverServices()
                }, 600)

            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                runOnUiThread {
                    txtEstado.text = "Estado: Desconectado"
                    txtEstado.setTextColor(ContextCompat.getColor(applicationContext, android.R.color.holo_red_dark))
                    btnConectar.text = "Conectar Bluetooth"
                    Toast.makeText(applicationContext, "Desconectado del Barco", Toast.LENGTH_SHORT).show()
                }
                try { if (tienePermisoConnect()) gatt?.close() } catch (e: Exception) { e.printStackTrace() }
                bluetoothGatt = null
                characteristicRx = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            Log.d(TAG, "onServicesDiscovered status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt?.services?.forEach { Log.d(TAG, "Servicio: ${it.uuid}") }

                val service = gatt?.getService(SERVICE_UUID)
                characteristicRx = service?.getCharacteristic(CHARACTERISTIC_UUID_RX)
                Log.d(TAG, "characteristicRx = $characteristicRx")

                runOnUiThread {
                    if (characteristicRx != null) {
                        txtEstado.text = "Estado: ¡Listo para Controlar!"
                        txtEstado.setTextColor(ContextCompat.getColor(applicationContext, android.R.color.holo_green_dark))
                        Toast.makeText(applicationContext, "¡Canales BLE listos!", Toast.LENGTH_SHORT).show()
                    } else {
                        txtEstado.text = "Estado: Error de Servicios"
                    }
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            Log.d(TAG, "onCharacteristicWrite status=$status")
        }
    }

    // Limita el envio a 1 comando cada 60 ms mientras se arrastra el slider
    private fun enviarConLimite(comando: String) {
        val ahora = System.currentTimeMillis()
        if (ahora - ultimoEnvio >= 60) {
            ultimoEnvio = ahora
            enviarComando(comando)
        }
    }

    private fun enviarComando(comando: String) {
        val gatt = bluetoothGatt
        val caracteristica = characteristicRx
        if (gatt == null || caracteristica == null) {
            Log.e(TAG, "No listo: gatt=$gatt rx=$caracteristica")
            return
        }
        if (!tienePermisoConnect()) return

        val data = comando.toByteArray(Charsets.UTF_8)

        val resultado: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                caracteristica, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            caracteristica.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            caracteristica.value = data
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(caracteristica)
        }
        Log.d(TAG, "Enviado '$comando' -> ok=$resultado")
    }

    private fun desconectarDispositivo() {
        escaneando = false
        if (tienePermisoScan()) bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (tienePermisoConnect()) {
            bluetoothGatt?.disconnect()
            try { bluetoothGatt?.close() } catch (e: Exception) { e.printStackTrace() }
        }
        bluetoothGatt = null
        characteristicRx = null

        txtEstado.text = "Estado: Desconectado"
        txtEstado.setTextColor(ContextCompat.getColor(applicationContext, android.R.color.holo_red_dark))
        btnConectar.text = "Conectar Bluetooth"
    }
}