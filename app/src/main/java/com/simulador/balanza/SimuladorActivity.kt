package com.simulador.balanza

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SimuladorActivity : AppCompatActivity() {

    ///private lateinit var binding: ActivitySimuladorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simulador)

        findViewById<Button>(R.id.btnVieja).setOnClickListener {
            startActivity(Intent(this, SerialActivity::class.java))
        }

        findViewById<Button>(R.id.btnMoba).setOnClickListener {
            startActivity(Intent(this, MobaActivity::class.java))
        }
    }
}