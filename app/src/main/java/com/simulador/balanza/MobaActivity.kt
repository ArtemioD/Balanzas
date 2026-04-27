package com.simulador.balanza

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
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
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.OutputStream
import java.util.*

/**
 * 🔬 SIMULADOR DE BALANZA MOBA
 * 
 * Formato de trama:
 * [STX] [POL] [nnnnnnn] [;] [aaaaaaaaaaaaaaaa] [;] [ccc] [CR]
 */
class MobaActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MobaSimulador"
        private const val DEVICE_NAME = "MOBA_SIMULADOR_001"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        
        // Formato de trama MOBA
        private const val STX = 2.toChar()
        private const val CR = 13.toChar()
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

    // Permisos requeridos
    private val requiredPermissions = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private val permissionsByVersion: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            // Android 11 o inferior
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
        etPeso.setText("0000450")
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
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 o superior
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            // Android 11 o inferior
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val missingPermissions = permissions.filter {
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
        
        makeDiscoverable()
        updateUI()
    }

    private fun makeDiscoverable() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {

            // 1. Intentar cambiar el nombre
            bluetoothAdapter?.name = DEVICE_NAME
            Log.d(TAG, "Nombre cambiado a: ${bluetoothAdapter?.name}")

            // 2. LANZAR PETICIÓN DE VISIBILIDAD (Esto hace que el otro teléfono lo encuentre)
            val discoverableIntent = android.content.Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300) // Visible por 5 minutos
            }
            startActivity(discoverableIntent)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
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

        val pesoInput = etPeso.text.toString()
        val pol = if (pesoInput.startsWith("-")) "-" else " "
        val pesoVal = pesoInput.replace("-", "").take(WEIGHT_LENGTH).padStart(WEIGHT_LENGTH, '0')
        val tagVal = etTag.text.toString().take(TAG_LENGTH).padEnd(TAG_LENGTH, ' ')
        val codeVal = etCode.text.toString().take(CODE_LENGTH).padStart(CODE_LENGTH, '0')
        
        // [STX] [POL] [nnnnnnn] [;] [aaaaaaaaaaaaaaaa] [;] [ccc] [CR]
        val trama = "$STX$pol$pesoVal$SEPARATOR$tagVal$SEPARATOR$codeVal$CR"
        
        try {
            val outputStream: OutputStream = clientSocket!!.outputStream
            outputStream.write(trama.toByteArray())
            outputStream.flush()
            
            Log.d(TAG, "Trama MOBA enviada: '$trama'")
            Toast.makeText(this, "Trama MOBA enviada", Toast.LENGTH_SHORT).show()
            
        } catch (e: IOException) {
            Log.e(TAG, "Error enviando trama: ${e.message}")
            Toast.makeText(this, "Error enviando trama", Toast.LENGTH_SHORT).show()
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
                    tvStatus.text = "✅ Conectado"
                    tvConnections.text = "Cliente: ${clientSocket?.remoteDevice?.name ?: "Desconocido"}"
                    switchServer.isEnabled = true
                    btnEnviar.isEnabled = true
                }
                isServerRunning -> {
                    tvStatus.text = "🔍 Esperando conexión..."
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
        return permissionsByVersion.all {
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
                } catch (e: IOException) {
                    break
                } catch (e: InterruptedException) {
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