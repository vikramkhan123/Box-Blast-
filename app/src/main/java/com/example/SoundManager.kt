package com.example

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

class SoundManager(context: Context) {
    
    private var toneGen: ToneGenerator? = null

    init {
        try {
            // 100 means max volume
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playPick() {
        // Ek choti aur sharp 'Tick/Beep' sound
        toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
    }

    fun playDrop() {
        // Halki si 'Boop' sound jab block fit ho
        toneGen?.startTone(ToneGenerator.TONE_DTMF_8, 50)
    }

    fun playClear() {
        // Success ki double-beep sound jab line clear ho
        toneGen?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
    }

    fun playGameOver() {
        // Game Over ke liye ek long error buzzer
        toneGen?.startTone(ToneGenerator.TONE_SUP_ERROR, 600)
    }

    fun release() {
        toneGen?.release()
        toneGen = null
    }
}
