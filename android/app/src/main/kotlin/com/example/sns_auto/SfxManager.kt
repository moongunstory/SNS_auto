package com.example.sns_auto

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

/**
 * SFX Manager for playing short sound effects with pitch/speed control
 *
 * Uses SoundPool for low-latency audio playback, ideal for sound effects.
 * Supports playing the same sound with different pitch/speed parameters.
 */
class SfxManager(private val context: Context) {
    companion object {
        private const val TAG = "SfxManager"
        private const val MAX_STREAMS = 5 // Maximum simultaneous sounds
    }

    private var soundPool: SoundPool? = null
    private var sfxHitSoundId: Int = -1
    private var isLoaded = false

    /**
     * Initialize the SoundPool and load sound effects
     */
    fun initialize() {
        if (soundPool != null) {
            Log.w(TAG, "SfxManager already initialized")
            return
        }

        Log.d(TAG, "Initializing SfxManager...")

        // Create SoundPool with AudioAttributes
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(audioAttributes)
            .build()

        // Set load complete listener
        soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                isLoaded = true
                Log.d(TAG, "Sound loaded successfully: sampleId=$sampleId")
            } else {
                Log.e(TAG, "Failed to load sound: sampleId=$sampleId, status=$status")
            }
        }

        // Load sfx_hit.mp3
        try {
            val resourceId = context.resources.getIdentifier("sfx_hit", "raw", context.packageName)
            if (resourceId != 0) {
                sfxHitSoundId = soundPool?.load(context, resourceId, 1) ?: -1
                Log.d(TAG, "Loading sfx_hit.mp3 (soundId=$sfxHitSoundId)")
            } else {
                Log.e(TAG, "sfx_hit.mp3 not found in res/raw")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading sound effect: ${e.message}", e)
        }
    }

    /**
     * Play the hit SFX with normal pitch and speed
     * Used for tile appearances (4 times)
     *
     * @return Stream ID of the playing sound, or -1 if failed
     */
    fun playHitNormal(): Int {
        if (!isLoaded || sfxHitSoundId < 0) {
            Log.w(TAG, "Sound not loaded yet, cannot play")
            return -1
        }

        val streamId = soundPool?.play(
            sfxHitSoundId,
            1.0f,  // left volume
            1.0f,  // right volume
            1,     // priority
            0,     // loop (0 = no loop)
            1.0f   // rate (normal speed/pitch)
        ) ?: -1

        Log.d(TAG, "Playing hit SFX (normal) - streamId=$streamId")
        return streamId
    }

    /**
     * Play the hit SFX with enhanced pitch and slightly longer duration
     * Used for color transition (1 time)
     *
     * Pitch: 1.2x (20% higher)
     * Speed: 0.95x (slightly slower, making it feel longer)
     *
     * @return Stream ID of the playing sound, or -1 if failed
     */
    fun playHitEnhanced(): Int {
        if (!isLoaded || sfxHitSoundId < 0) {
            Log.w(TAG, "Sound not loaded yet, cannot play")
            return -1
        }

        val streamId = soundPool?.play(
            sfxHitSoundId,
            1.0f,  // left volume
            1.0f,  // right volume
            1,     // priority
            0,     // loop (0 = no loop)
            1.2f   // rate (1.2x = higher pitch + faster)
        ) ?: -1

        Log.d(TAG, "Playing hit SFX (enhanced) - streamId=$streamId, rate=1.2x")
        return streamId
    }

    /**
     * Release all resources
     * Call this when the SfxManager is no longer needed
     */
    fun release() {
        Log.d(TAG, "Releasing SfxManager resources...")
        soundPool?.release()
        soundPool = null
        sfxHitSoundId = -1
        isLoaded = false
    }
}
