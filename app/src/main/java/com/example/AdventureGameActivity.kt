package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

class AdventureGameActivity : ComponentActivity() {
    private lateinit var gameView: AdventureGameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        gameView = AdventureGameView(this)
        setContentView(gameView)
    }

    override fun onResume() {
        super.onResume()
        gameView.soundManager.playBGM()
    }

    override fun onPause() {
        super.onPause()
        gameView.soundManager.pauseBGM()
    }
}
