package com.example

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class SoundManager(context: Context) {
    private var soundPool: SoundPool
    
    var pickSoundId = 0
    var dropSoundId = 0
    var clearSoundId = 0
    var gameOverSoundId = 0

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5) 
            .setAudioAttributes(audioAttributes)
            .build()

        // TODO: Jab aap res/raw/ folder me sounds daal dein, tab in 4 lines ke aage se '//' hata dein
        // pickSoundId = soundPool.load(context, R.raw.pick_sound, 1)
        // dropSoundId = soundPool.load(context, R.raw.drop_sound, 1)
        // clearSoundId = soundPool.load(context, R.raw.clear_sound, 1)
        // gameOverSoundId = soundPool.load(context, R.raw.game_over, 1)
    }

    fun playPick() {
        if (pickSoundId != 0) soundPool.play(pickSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playDrop() {
        if (dropSoundId != 0) soundPool.play(dropSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playClear() {
        if (clearSoundId != 0) soundPool.play(clearSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun playGameOver() {
        if (gameOverSoundId != 0) soundPool.play(gameOverSoundId, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }
}
