package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

class TetrisGameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Tetris ka Canvas Game Engine yahan set kiya gaya hai
        val gameView = TetrisGameView(this)
        setContentView(gameView)
    }
}
