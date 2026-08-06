package com.example

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper

class SoundManager(val context: Context) {
    private var soundPool: SoundPool
    
    // Core Gameplay SFX
    var pickSoundId = 0
    var dropSoundId = 0
    var clearSoundId = 0
    var gameOverSoundId = 0
    
    // UI & System SFX
    var btnClickId = 0
    var countdownTickId = 0
    
    // Combo Voiceovers
    var voiceGoodId = 0
    var voiceExcellentId = 0
    var voiceSuperId = 0
    var voiceMagnificentId = 0

    // --- Background Music Variables ---
    private val bgmPlaylist = listOf(
        R.raw.bgm_relaxing_1,
        R.raw.bgm_relaxing_2,
        R.raw.bgm_relaxing_3
    )
    private var currentBgmIndex = 0
    private var bgmPlayer: MediaPlayer? = null
    
    // Timer 5 second ka gap dene ke liye
    private val handler = Handler(Looper.getMainLooper())
    private var isBgmActive = false // Track karega ki app background me to nahi hai

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(10) 
            .setAudioAttributes(audioAttributes)
            .build()

        try {
            pickSoundId = soundPool.load(context, R.raw.pick_sound, 1)
            dropSoundId = soundPool.load(context, R.raw.drop_sound, 1)
            clearSoundId = soundPool.load(context, R.raw.clear_sound, 1)
            gameOverSoundId = soundPool.load(context, R.raw.game_over, 1)
            
            btnClickId = soundPool.load(context, R.raw.btn_click, 1)
            countdownTickId = soundPool.load(context, R.raw.countdown_tick, 1)
            
            voiceGoodId = soundPool.load(context, R.raw.voice_good, 1)
            voiceExcellentId = soundPool.load(context, R.raw.voice_excellent, 1)
            voiceSuperId = soundPool.load(context, R.raw.voice_super, 1)
            voiceMagnificentId = soundPool.load(context, R.raw.voice_magnificent, 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Background Music Playlist Logic ---
    fun playBGM() {
        isBgmActive = true
        
        if (bgmPlayer == null) {
            // Agar player khali hai (ya gaana khatam ho chuka hai), naya track chalao
            startNextTrack()
        } else if (bgmPlayer?.isPlaying == false) {
            // Agar gaana pause kiya tha (app minimize karne pe), toh wapas resume karo
            bgmPlayer?.start()
        }
    }

    private fun startNextTrack() {
        if (!isBgmActive) return // Agar user ne app minimize kar di hai toh mat chalao

        try {
            bgmPlayer?.release() // Purane player ko clear karo
            
            bgmPlayer = MediaPlayer.create(context, bgmPlaylist[currentBgmIndex])
            bgmPlayer?.setVolume(0.4f, 0.4f) // Volume 40%
            
            // Jab gaana khatam ho jaye, tab ye block chalega
            bgmPlayer?.setOnCompletionListener {
                bgmPlayer?.release()
                bgmPlayer = null
                
                // Next gaane par jao (3 ke baad wapas 1 par aane ke liye modulo % use kiya)
                currentBgmIndex = (currentBgmIndex + 1) % bgmPlaylist.size
                
                // 5000 milliseconds (5 seconds) ka gap
                if (isBgmActive) {
                    handler.postDelayed({
                        startNextTrack()
                    }, 5000)
                }
            }
            
            bgmPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pauseBGM() {
        isBgmActive = false
        handler.removeCallbacksAndMessages(null) // Agar 5 sec ka timer chal raha tha toh use rok do
        if (bgmPlayer?.isPlaying == true) {
            bgmPlayer?.pause()
        }
    }

    fun stopBGM() {
        isBgmActive = false
        handler.removeCallbacksAndMessages(null)
        bgmPlayer?.stop()
        bgmPlayer?.release()
        bgmPlayer = null
    }

    // --- Core SFX Methods ---
    fun playPick() { if (pickSoundId != 0) soundPool.play(pickSoundId, 1f, 1f, 1, 0, 1f) }
    fun playDrop() { if (dropSoundId != 0) soundPool.play(dropSoundId, 1f, 1f, 1, 0, 1f) }
    fun playClear() { if (clearSoundId != 0) soundPool.play(clearSoundId, 1f, 1f, 1, 0, 1f) }
    fun playGameOver() { if (gameOverSoundId != 0) soundPool.play(gameOverSoundId, 1f, 1f, 1, 0, 1f) }
    
    // --- UI & System Methods ---
    fun playBtnClick() { if (btnClickId != 0) soundPool.play(btnClickId, 1f, 1f, 1, 0, 1f) }
    fun playCountdownTick() { if (countdownTickId != 0) soundPool.play(countdownTickId, 1f, 1f, 1, 0, 1f) }

    // --- Voiceover Methods ---
    fun playVoiceGood() { if (voiceGoodId != 0) soundPool.play(voiceGoodId, 1f, 1f, 1, 0, 1f) }
    fun playVoiceExcellent() { if (voiceExcellentId != 0) soundPool.play(voiceExcellentId, 1f, 1f, 1, 0, 1f) }
    fun playVoiceSuper() { if (voiceSuperId != 0) soundPool.play(voiceSuperId, 1f, 1f, 1, 0, 1f) }
    fun playVoiceMagnificent() { if (voiceMagnificentId != 0) soundPool.play(voiceMagnificentId, 1f, 1f, 1, 0, 1f) }

    fun playComboVoice(linesCleared: Int) {
        when (linesCleared) {
            1 -> playVoiceGood()
            2 -> playVoiceExcellent()
            3 -> playVoiceSuper()
            4 -> playVoiceMagnificent()
            else -> if(linesCleared > 4) playVoiceMagnificent()
        }
    }

    fun release() {
        soundPool.release()
        stopBGM()
    }
}
