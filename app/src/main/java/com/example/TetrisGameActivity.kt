package com.example

import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

class TetrisGameActivity : ComponentActivity() {
    private lateinit var gameView: TetrisGameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL

        gameView = TetrisGameView(this)
        val gameParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        layout.addView(gameView, gameParams)

        // Banner Ad
        val adView = AdView(this)
        adView.setAdSize(AdSize.BANNER)
        adView.adUnitId = "ca-app-pub-4346513942475662/6000762095" // Original Banner ID
        adView.loadAd(AdRequest.Builder().build())
        layout.addView(adView)

        setContentView(layout)
    }

    override fun onResume() { super.onResume(); gameView.soundManager.playBGM() }
    override fun onPause() { super.onPause(); gameView.soundManager.pauseBGM() }
}
