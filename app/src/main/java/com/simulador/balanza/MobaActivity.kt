package com.simulador.balanza

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
 * 🔬 SIMULADOR DE BALANZA MOBA
 * 
 * Simula una balanza con el formato de trama MOBA:
 * [STX][POL][nnnnnnn];[aaaaaaaaaaaaaaaa];[ccc][CR]
 */
class MobaActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MobaSimulador"
        private const val DEVICE_NAME = "MOBA_SIMULADOR_001"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // Formato MOBA
        private const val STX = 2.toChar() // Start of Text
        private const val CR = 13.toChar() // Carriage Return
        private const val SEPARATOR = ";"
        private const val WEIGHT_LENGTH = 7
        private const val TAG_LENGTH = 16
        private const val CODE_LENGTH = 3
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
    private lateinit var etTag: EditText
    private lateinit var etCode: EditText
    private lateinit var btnEnviar: Button
    private lateinit var switchServer: Switch
    private lateinit var btnPermissions: Button

    // Permisos requeridos según la versión de Android
    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

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
        setContentView(R.layout.activity_moba)
        
        initViews()
        setupUI()
        checkPermissions()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvConnections = findViewById(R.id.tvConnections)
        etPeso = findViewById(R.id.etPeso)
        etTag = findViewById(R.id.etTag)
        etCode = findViewById(R.id.etCode)
        btnEnviar = findViewById(R.id.btnEnviar)
        switchServer = findViewById(R.id.switchServer)
        btnPermissions = findViewById(R.id.btnPermissions)
    }

    private fun setupUI() {
        // Valores por defecto
        etTag.setText("1234567890123456")
        etPeso.setText("450")
        etCode.setText("001")
        
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
            Toast.makeText(this, "Habilitando Bluetooth...", Toast.LENGTH_SHORT).show()
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    startActivity(enableBtIntent)
                }
            } else {
                startActivity(enableBtIntent)
            }
            return
        }
        
        updateUI()
    }

    private fun makeDiscoverable() {
        val hasConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true

        if (hasConnectPermission) {
            bluetoothAdapter?.name = DEVICE_NAME
            Log.d(TAG, "Nombre del dispositivo cambiado a: $DEVICE_NAME")
            
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            }
            startActivity(discoverableIntent)
        }
    }

    private fun startServer() {
        if (!hasPermissions()) {
            checkPermissions()
            return
        }

        // Asegurar que el nombre sea correcto y sea visible antes de iniciar
        makeDiscoverable()

        try {
            serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(DEVICE_NAME, SPP_UUID)
            isServerRunning = true
            
            acceptThread = AcceptThread()
            acceptThread?.start()
            
            Log.d(TAG, "Servidor Bluetooth MOBA iniciado")
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
            
            Log.d(TAG, "Servidor Bluetooth MOBA detenido")
            updateUI()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo servidor: ${e.message}")
        }
    }

    private fun enviarPeso() {
        if (clientSocket == null || !clientSocket!!.isConnected) {
            Toast.makeText(this, "No hay dispositivo conectado", Toast.LENGTH_SHORT).show()
            return
        }

        val pesoInput = etPeso.text.toString()
        val pol = if (pesoInput.startsWith("-")) "-" else " "
        
        // El valor numérico limpio, relleno con ceros a la izquierda (7 dígitos)
        val pesoVal = pesoInput.replace("-", "")
            .padStart(WEIGHT_LENGTH, '0')
            .takeLast(WEIGHT_LENGTH)
            
        val tagVal = etTag.text.toString()
            .padEnd(TAG_LENGTH, ' ')
            .take(TAG_LENGTH)
            
        val codeVal = etCode.text.toString()
            .padStart(CODE_LENGTH, '0')
            .takeLast(CODE_LENGTH)

        // Formato: [STX][POL][nnnnnnn];[aaaaaaaaaaaaaaaa];[ccc][CR]
        val trama = "$STX$pol$pesoVal$SEPARATOR$tagVal$SEPARATOR$codeVal$CR"
        
        try {
            val outputStream: OutputStream = clientSocket!!.outputStream
            outputStream.write(trama.toByteArray())
            outputStream.flush()
            
            Log.d(TAG, "Trama MOBA enviada: $trama")
            Toast.makeText(this, "Trama MOBA enviada", Toast.LENGTH_SHORT).show()
            
        } catch (e: IOException) {
            Log.e(TAG, "Error enviando: ${e.message}")
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
                    tvStatus.text = "✅ CONECTADO"
                    tvConnections.text = "Cliente: ${clientSocket?.remoteDevice?.name ?: "Desconocido"}"
                    switchServer.isEnabled = true
                    btnEnviar.isEnabled = true
                }
                isServerRunning -> {
                    tvStatus.text = "🔍 ESPERANDO CONEXIÓN..."
                    tvConnections.text = "Nombre: $DEVICE_NAME"
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
                    clientSocket = serverSocket?.accept()
                    
                    if (clientSocket != null) {
                        updateUI()
                        while (clientSocket?.isConnected == true && isServerRunning) {
                            Thread.sleep(1000)
                        }
                    }
                } catch (e: Exception) {
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
