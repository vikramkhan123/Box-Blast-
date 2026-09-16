package com.example

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

class SoundManager(val context: Context) {
    private var soundPool: SoundPool
    
    var dropSoundId = 0
    var clearSoundId = 0
    var gameOverSoundId = 0
    var btnClickId = 0
    var countdownTickId = 0
    var victorySoundId = 0
    
    var voiceGoodId = 0
    var voiceExcellentId = 0
    var voiceSuperId = 0
    var voiceMagnificentId = 0
    var voiceUnbelievableId = 0
    var voiceGloriousId = 0
    var voiceMajesticId = 0

    var soundBrokenId = 0

    private var bgmPlayer: MediaPlayer? = null
    private var tickStreamId = 0 
    private val bgmTracks = listOf(R.raw.bgm_relaxing_1, R.raw.bgm_relaxing_2, R.raw.bgm_relaxing_3)

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(16).setAudioAttributes(audioAttributes).build()

        try {
            dropSoundId = soundPool.load(context, R.raw.drop_sound, 1)
            clearSoundId = soundPool.load(context, R.raw.clear_sound, 1)
            gameOverSoundId = soundPool.load(context, R.raw.game_over, 1)
            btnClickId = soundPool.load(context, R.raw.btn_click, 1)
            countdownTickId = soundPool.load(context, R.raw.countdown_tick, 1)
            victorySoundId = soundPool.load(context, R.raw.victory_sound, 1)
            
            voiceGoodId = soundPool.load(context, R.raw.voice_good, 1)
            voiceExcellentId = soundPool.load(context, R.raw.voice_excellent, 1)
            voiceSuperId = soundPool.load(context, R.raw.voice_super, 1)
            voiceMagnificentId = soundPool.load(context, R.raw.voice_magnificent, 1)
            voiceUnbelievableId = soundPool.load(context, R.raw.voice_unbelievable, 1)
            voiceGloriousId = soundPool.load(context, R.raw.voice_glorious, 1)
            voiceMajesticId = soundPool.load(context, R.raw.voice_majestic, 1)

            soundBrokenId = soundPool.load(context, R.raw.sound_broken, 1)
        } catch (e: Exception) { 
            e.printStackTrace() 
        }
    }

    fun playBGM() {
        if (bgmPlayer != null && bgmPlayer!!.isPlaying) return
        try {
            if (bgmPlayer == null) {
                bgmPlayer = MediaPlayer.create(context, bgmTracks.random())
                bgmPlayer?.isLooping = true
                bgmPlayer?.setVolume(0.85f, 0.85f)
            }
            bgmPlayer?.start()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun pauseBGM() { if (bgmPlayer?.isPlaying == true) bgmPlayer?.pause() }
    fun stopBGM() { bgmPlayer?.stop(); bgmPlayer?.release(); bgmPlayer = null }

    // Pick sound completely muted
    fun playPick() {}

    fun playDrop() { if (dropSoundId != 0) soundPool.play(dropSoundId, 1f, 1f, 1, 0, 1f) }
    
    fun playClear() { 
        val id = if (soundBrokenId != 0) soundBrokenId else clearSoundId
        soundPool.play(id, 1f, 1f, 1, 0, 1f) 
    }

    // Fallback blast method for Adventure/Tetris compatibility
    fun playBlastSound(type: String = "") {
        playClear()
    }

    fun playGameOver() { if (gameOverSoundId != 0) soundPool.play(gameOverSoundId, 1f, 1f, 1, 0, 1f) }
    fun playVictory() { if (victorySoundId != 0) soundPool.play(victorySoundId, 1f, 1f, 1, 0, 1f) }
    fun playBtnClick() { if (btnClickId != 0) soundPool.play(btnClickId, 1f, 1f, 1, 0, 1f) }

    fun playCountdownTick() { 
        if (countdownTickId != 0) {
            stopCountdownTick()
            tickStreamId = soundPool.play(countdownTickId, 1f, 1f, 1, 0, 1f) 
        } 
    }
    fun stopCountdownTick() { if (tickStreamId != 0) { soundPool.stop(tickStreamId); tickStreamId = 0 } }

    fun playComboVoice(linesCleared: Int): String {
        val pool1 = listOf(Pair(voiceGoodId, "GOOD!"), Pair(voiceExcellentId, "EXCELLENT!"), Pair(voiceSuperId, "SUPER!"))
        val pool2 = listOf(Pair(voiceMagnificentId, "MAGNIFICENT!"), Pair(voiceUnbelievableId, "UNBELIEVABLE!"), Pair(voiceGloriousId, "GLORIOUS!"), Pair(voiceMajesticId, "MAJESTIC!"))
        val selection = if (linesCleared <= 1) pool1.random() else pool2.random()
        if (selection.first != 0) soundPool.play(selection.first, 1f, 1f, 1, 0, 1f)
        return selection.second
    }

    fun release() { 
        soundPool.release()
        stopBGM() 
    }
}
