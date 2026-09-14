package com.example

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

class SoundManager(val context: Context) {
    private var soundPool: SoundPool
    
    // Core Game Sounds
    var pickSoundId = 0
    var dropSoundId = 0
    var clearSoundId = 0
    var gameOverSoundId = 0
    var btnClickId = 0
    var countdownTickId = 0
    var victorySoundId = 0
    
    // Streak Voice Sounds
    var voiceGoodId = 0
    var voiceExcellentId = 0
    var voiceSuperId = 0
    var voiceMagnificentId = 0
    var voiceUnbelievableId = 0
    var voiceGloriousId = 0
    var voiceMajesticId = 0

    // Theme & Blast Sounds
    var soundBrokenId = 0
    var soundBurnId = 0
    var soundCokeId = 0
    var soundLightningId = 0
    var soundMeltId = 0
    var soundPopId = 0

    private var bgmPlayer: MediaPlayer? = null
    private var tickStreamId = 0 
    private var bgmIndex = 0
    private val bgmTracks = listOf(R.raw.bgm_relaxing_1, R.raw.bgm_relaxing_2, R.raw.bgm_relaxing_3)

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(20).setAudioAttributes(audioAttributes).build()

        try {
            // Core
            pickSoundId = soundPool.load(context, R.raw.pick_sound, 1)
            dropSoundId = soundPool.load(context, R.raw.drop_sound, 1)
            clearSoundId = soundPool.load(context, R.raw.clear_sound, 1)
            gameOverSoundId = soundPool.load(context, R.raw.game_over, 1)
            btnClickId = soundPool.load(context, R.raw.btn_click, 1)
            countdownTickId = soundPool.load(context, R.raw.countdown_tick, 1)
            victorySoundId = soundPool.load(context, R.raw.victory_sound, 1)
            
            // Streak Voices
            voiceGoodId = soundPool.load(context, R.raw.voice_good, 1)
            voiceExcellentId = soundPool.load(context, R.raw.voice_excellent, 1)
            voiceSuperId = soundPool.load(context, R.raw.voice_super, 1)
            voiceMagnificentId = soundPool.load(context, R.raw.voice_magnificent, 1)
            voiceUnbelievableId = soundPool.load(context, R.raw.voice_unbelievable, 1)
            voiceGloriousId = soundPool.load(context, R.raw.voice_glorious, 1)
            voiceMajesticId = soundPool.load(context, R.raw.voice_majestic, 1)

            // Nayi Uploaded Blast Sounds
            soundBrokenId = soundPool.load(context, R.raw.sound_broken, 1)
            soundBurnId = soundPool.load(context, R.raw.sound_burn, 1)
            soundCokeId = soundPool.load(context, R.raw.sound_coke, 1)
            soundLightningId = soundPool.load(context, R.raw.sound_lightning, 1)
            soundMeltId = soundPool.load(context, R.raw.sound_melt, 1)
            soundPopId = soundPool.load(context, R.raw.sound_pop, 1)

        } catch (e: Exception) { 
            e.printStackTrace() 
        }
    }

    fun playBGM() {
        if (bgmPlayer != null && bgmPlayer!!.isPlaying) return
        try {
            if (bgmPlayer == null) {
                bgmPlayer = MediaPlayer.create(context, bgmTracks[bgmIndex])
                bgmPlayer?.isLooping = true
                bgmPlayer?.setVolume(0.9f, 0.9f)
            }
            bgmPlayer?.start()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun pauseBGM() { if (bgmPlayer?.isPlaying == true) bgmPlayer?.pause() }
    fun stopBGM() { bgmPlayer?.stop(); bgmPlayer?.release(); bgmPlayer = null }

    fun playPick() { if (pickSoundId != 0) soundPool.play(pickSoundId, 1f, 1f, 1, 0, 1f) }
    fun playDrop() { if (dropSoundId != 0) soundPool.play(dropSoundId, 1f, 1f, 1, 0, 1f) }
    fun playClear() { if (clearSoundId != 0) soundPool.play(clearSoundId, 1f, 1f, 1, 0, 1f) }
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

    // Blast-specific sound trigger
    fun playBlastSound(type: String) {
        val soundId = when (type.uppercase()) {
            "BURN", "KEROSENE" -> soundBurnId
            "BROKEN", "BRICK", "WOOD" -> soundBrokenId
            "MELT", "CHOCOLATE" -> soundMeltId
            "COKE" -> soundCokeId
            "POP", "BISCUIT" -> soundPopId
            "LIGHTNING" -> soundLightningId
            else -> clearSoundId
        }
        val targetId = if (soundId != 0) soundId else clearSoundId
        soundPool.play(targetId, 1f, 1f, 1, 0, 1f)
    }

    // Streaks (Combo Voices)
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
