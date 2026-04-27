package com.simulador.balanza

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.OutputStream
import java.util.*

/**
 * 🔬 SIMULADOR DE BALANZA BLUETOOTH PARA SARGA DIGITAL
 * 
 * Simula una balanza con las siguientes características:
 * - Nombre del dispositivo: "SERIAL_SIMULADOR_001" 
 * - UUID: 00001101-0000-1000-8000-00805F9B34FB (SPP)
 * - Formato de trama: exacto al esperado por PesadoraBluetooth
 * - Delimitador: ASCII 13 (CR)
 */
class SerialActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BalanzaSimulador"
        private const val DEVICE_NAME = "SERIAL_SIMULADOR_001"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        
        // Formato de trama según PesadoraBluetooth
        private const val DESCRIPCION_LENGTH = 15
        private const val PESO_LENGTH = 8
        private const val DELIMITER = 13.toChar() // ASCII CR
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private var isServerRunning = false
    private var acceptThread: AcceptThread? = null

    // UI Components
    private lateinit var tvStatus: TextView
    private lateinit var tvConnections: TextView
    private lateinit var etPeso: EditText
    private lateinit var etDescripcion: EditText
    private lateinit var btnEnviar: Button
    private lateinit var switchServer: Switch
    private lateinit var btnPermissions: Button

    // Permisos requeridos
    private val requiredPermissions = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            setupBluetooth()
        } else {
            Toast.makeText(this, "Permisos Bluetooth requeridos", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_serial)
        
        initViews()
        setupUI()
        checkPermissions()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvConnections = findViewById(R.id.tvConnections)
        etPeso = findViewById(R.id.etPeso)
        etDescripcion = findViewById(R.id.etDescripcion)
        btnEnviar = findViewById(R.id.btnEnviar)
        switchServer = findViewById(R.id.switchServer)
        btnPermissions = findViewById(R.id.btnPermissions)
    }

    private fun setupUI() {
        // Valores por defecto
        etDescripcion.setText("BOVINO ADULTO")
        etPeso.setText("450.5")
        
        btnEnviar.setOnClickListener { enviarPeso() }
        switchServer.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startServer()
            } else {
                stopServer()
            }
        }
        btnPermissions.setOnClickListener { checkPermissions() }
        
        updateUI()
    }

    private fun checkPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            setupBluetooth()
        }
    }

    private fun setupBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth no soportado", Toast.LENGTH_LONG).show()
            return
        }
        
        if (!bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Habilita Bluetooth para continuar", Toast.LENGTH_LONG).show()
            return
        }
        
        // Hacer el dispositivo discoverable con el nombre correcto
        makeDiscoverable()
        updateUI()
    }

    private fun makeDiscoverable() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter?.name = DEVICE_NAME
            Log.d(TAG, "Nombre del dispositivo cambiado a: $DEVICE_NAME")
        }
    }

    private fun startServer() {
        if (!hasPermissions()) {
            checkPermissions()
            return
        }

        try {
            serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(DEVICE_NAME, SPP_UUID)
            isServerRunning = true
            
            acceptThread = AcceptThread()
            acceptThread?.start()
            
            Log.d(TAG, "Servidor Bluetooth iniciado - Esperando conexiones...")
            updateUI()
            
        } catch (e: IOException) {
            Log.e(TAG, "Error iniciando servidor: ${e.message}")
            Toast.makeText(this, "Error iniciando servidor: ${e.message}", Toast.LENGTH_LONG).show()
            stopServer()
        }
    }

    private fun stopServer() {
        isServerRunning = false
        
        try {
            acceptThread?.interrupt()
            acceptThread = null
            
            clientSocket?.close()
            clientSocket = null
            
            serverSocket?.close()
            serverSocket = null
            
            Log.d(TAG, "Servidor Bluetooth detenido")
            updateUI()
            
        } catch (e: IOException) {
            Log.e(TAG, "Error deteniendo servidor: ${e.message}")
        }
    }

    private fun enviarPeso() {
        if (clientSocket == null || !clientSocket!!.isConnected) {
            Toast.makeText(this, "No hay dispositivo conectado", Toast.LENGTH_SHORT).show()
            return
        }

        val descripcion = etDescripcion.text.toString().take(DESCRIPCION_LENGTH).padEnd(DESCRIPCION_LENGTH)
        val peso = etPeso.text.toString().take(PESO_LENGTH).padStart(PESO_LENGTH)
        
        // Formato exacto esperado por PesadoraBluetooth
        val trama = "$descripcion$peso$DELIMITER"
        
        try {
            val outputStream: OutputStream = clientSocket!!.outputStream
            outputStream.write(trama.toByteArray())
            outputStream.flush()
            
            Log.d(TAG, "Trama enviada: '$trama' (${trama.length} chars)")
            Toast.makeText(this, "Peso enviado: $peso kg", Toast.LENGTH_SHORT).show()
            
        } catch (e: IOException) {
            Log.e(TAG, "Error enviando peso: ${e.message}")
            Toast.makeText(this, "Error enviando peso", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI() {
        runOnUiThread {
            when {
                !hasPermissions() -> {
                    tvStatus.text = "⚠️ Permisos Bluetooth requeridos"
                    switchServer.isEnabled = false
                    btnEnviar.isEnabled = false
                }
                bluetoothAdapter?.isEnabled != true -> {
                    tvStatus.text = "⚠️ Bluetooth deshabilitado"
                    switchServer.isEnabled = false
                    btnEnviar.isEnabled = false
                }
                isServerRunning && clientSocket?.isConnected == true -> {
                    tvStatus.text = "✅ Conectado a Sarga Digital"
                    tvConnections.text = "Cliente: ${clientSocket?.remoteDevice?.name ?: "Desconocido"}"
                    switchServer.isEnabled = true
                    btnEnviar.isEnabled = true
                }
                isServerRunning -> {
                    tvStatus.text = "🔍 Esperando conexión de Sarga Digital..."
                    tvConnections.text = "Nombre del dispositivo: $DEVICE_NAME"
                    switchServer.isEnabled = true
                    btnEnviar.isEnabled = false
                }
                else -> {
                    tvStatus.text = "⭕ Servidor detenido"
                    tvConnections.text = "Activa el servidor para permitir conexiones"
                    switchServer.isEnabled = true
                    btnEnviar.isEnabled = false
                }
            }
        }
    }

    private fun hasPermissions(): Boolean {
        return requiredPermissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private inner class AcceptThread : Thread() {
        override fun run() {
            while (isServerRunning && !isInterrupted) {
                try {
                    Log.d(TAG, "Esperando conexión cliente...")
                    clientSocket = serverSocket?.accept()
                    
                    if (clientSocket != null) {
                        Log.d(TAG, "Cliente conectado: ${clientSocket?.remoteDevice?.name}")
                        updateUI()
                        
                        // Mantener la conexión abierta
                        while (clientSocket?.isConnected == true && isServerRunning) {
                            Thread.sleep(1000)
                        }
                    }
                    
                } catch (e: IOException) {
                    if (isServerRunning) {
                        Log.e(TAG, "Error en AcceptThread: ${e.message}")
                    }
                    break
                } catch (e: InterruptedException) {
                    Log.d(TAG, "AcceptThread interrumpido")
                    break
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
    }
}