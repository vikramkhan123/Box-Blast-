package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

class ClassicGameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Hamara banaya hua premium GameView seedha yahan set hoga
        val gameView = GameView(this)
        setContentView(gameView)
    }
}
