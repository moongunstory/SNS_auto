package com.example.sns_auto

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * Offline audio mixer that combines BGM and SFX into a single PCM stream
 *
 * This mixer:
 * - Loads BGM and decodes it to PCM
 * - Loads SFX files and decodes them to PCM
 * - Mixes SFX into BGM at scheduled timestamps
 * - Outputs mixed PCM suitable for feeding to MediaCodec encoder
 */
class AudioMixer(private val context: Context) {
    companion object {
        private const val TAG = "AudioMixer"
        private const val CODEC_TIMEOUT_US = 10000L
    }

    /**
     * SFX event to be mixed into the audio stream
     */
    data class SfxEvent(
        val resourceName: String,  // e.g., "sfx_hit"
        val startTimeMs: Long      // When to play this SFX (milliseconds)
    )

    /**
     * Mixed audio data ready for encoding
     */
    data class MixedAudio(
        val samples: ShortArray,
        val sampleRate: Int,
        val channelCount: Int
    )

    /**
     * Mix BGM with scheduled SFX events
     *
     * @param bgmPath Path to BGM file
     * @param sfxEvents List of SFX events with timing
     * @param maxDurationUs Maximum duration to extract (microseconds)
     * @return Mixed audio data
     */
    fun mixAudio(
        bgmPath: String,
        sfxEvents: List<SfxEvent>,
        maxDurationUs: Long
    ): MixedAudio {
        Log.d(TAG, "Mixing audio: BGM=$bgmPath, SFX events=${sfxEvents.size}")

        // Extract BGM PCM
        val bgmData = extractPcmFromFile(bgmPath, maxDurationUs)
        Log.d(TAG, "BGM: ${bgmData.samples.size} samples, ${bgmData.sampleRate}Hz, ${bgmData.channelCount}ch")

        // Create output buffer (copy of BGM)
        val mixedSamples = bgmData.samples.copyOf()

        // Load and mix each SFX event
        for (event in sfxEvents) {
            try {
                val sfxData = loadSfxPcm(event.resourceName, bgmData.sampleRate, bgmData.channelCount)
                val startSample = ((event.startTimeMs / 1000.0) * bgmData.sampleRate * bgmData.channelCount).toInt()

                mixSfxIntoBuffer(mixedSamples, sfxData, startSample)
                Log.d(TAG, "Mixed SFX '${event.resourceName}' at ${event.startTimeMs}ms (sample $startSample)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mix SFX '${event.resourceName}': ${e.message}", e)
            }
        }

        return MixedAudio(mixedSamples, bgmData.sampleRate, bgmData.channelCount)
    }

    /**
     * Extract PCM samples from audio file
     */
    private fun extractPcmFromFile(filePath: String, maxDurationUs: Long): MixedAudio {
        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(filePath)

            // Find audio track
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    break
                }
            }

            if (audioTrackIndex < 0) {
                throw IllegalStateException("No audio track found in file: $filePath")
            }

            extractor.selectTrack(audioTrackIndex)
            val inputFormat = extractor.getTrackFormat(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!

            // Create decoder
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val samples = mutableListOf<Short>()
            var outputDone = false
            val bufferInfo = MediaCodec.BufferInfo()

            var actualSampleRate = 44100
            var actualChannelCount = 2

            try {
                while (!outputDone) {
                    // Feed input
                    val inputBufferId = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputBufferId >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferId)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)

                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            decoder.queueInputBuffer(inputBufferId, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }

                    // Get output
                    val outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)

                    when (outputBufferId) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outputFormat = decoder.outputFormat
                            actualSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            actualChannelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            Log.d(TAG, "Decoder output format: $actualSampleRate Hz, $actualChannelCount channels")
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // No output available yet
                        }
                        else -> {
                            if (outputBufferId >= 0) {
                                val outputBuffer = decoder.getOutputBuffer(outputBufferId)!!
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                                // Convert to shorts
                                val shortCount = bufferInfo.size / 2
                                val shortBuffer = outputBuffer.asShortBuffer()
                                val chunk = ShortArray(shortCount)
                                shortBuffer.get(chunk)

                                samples.addAll(chunk.toList())
                                decoder.releaseOutputBuffer(outputBufferId, false)

                                // Stop if we've reached max duration
                                if (bufferInfo.presentationTimeUs >= maxDurationUs) {
                                    outputDone = true
                                }

                                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                    outputDone = true
                                }
                            }
                        }
                    }
                }
            } finally {
                decoder.stop()
                decoder.release()
            }

            return MixedAudio(samples.toShortArray(), actualSampleRate, actualChannelCount)
        } finally {
            extractor.release()
        }
    }

    /**
     * Load SFX from resources and decode to PCM
     */
    private fun loadSfxPcm(resourceName: String, targetSampleRate: Int, targetChannelCount: Int): ShortArray {
        val resourceId = context.resources.getIdentifier(resourceName, "raw", context.packageName)
        if (resourceId == 0) {
            throw IllegalArgumentException("SFX resource not found: $resourceName")
        }

        val extractor = MediaExtractor()

        try {
            // Use resource file descriptor
            val afd = context.resources.openRawResourceFd(resourceId)
            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()

            // Find audio track
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    break
                }
            }

            if (audioTrackIndex < 0) {
                throw IllegalStateException("No audio track in SFX: $resourceName")
            }

            extractor.selectTrack(audioTrackIndex)
            val inputFormat = extractor.getTrackFormat(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!

            // Create decoder
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val samples = mutableListOf<Short>()
            var outputDone = false
            val bufferInfo = MediaCodec.BufferInfo()

            try {
                while (!outputDone) {
                    // Feed input
                    val inputBufferId = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputBufferId >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferId)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)

                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            decoder.queueInputBuffer(inputBufferId, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }

                    // Get output
                    val outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)

                    when (outputBufferId) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            // Format changed
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // No output yet
                        }
                        else -> {
                            if (outputBufferId >= 0) {
                                val outputBuffer = decoder.getOutputBuffer(outputBufferId)!!
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                                // Convert to shorts
                                val shortCount = bufferInfo.size / 2
                                val shortBuffer = outputBuffer.asShortBuffer()
                                val chunk = ShortArray(shortCount)
                                shortBuffer.get(chunk)

                                samples.addAll(chunk.toList())
                                decoder.releaseOutputBuffer(outputBufferId, false)

                                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                    outputDone = true
                                }
                            }
                        }
                    }
                }
            } finally {
                decoder.stop()
                decoder.release()
            }

            return samples.toShortArray()
        } finally {
            extractor.release()
        }
    }

    /**
     * Mix SFX samples into the main buffer at specified position
     * Clamps values to prevent clipping
     */
    private fun mixSfxIntoBuffer(mainBuffer: ShortArray, sfxSamples: ShortArray, startSample: Int) {
        val endSample = min(startSample + sfxSamples.size, mainBuffer.size)
        val sfxLength = endSample - startSample

        for (i in 0 until sfxLength) {
            if (startSample + i < mainBuffer.size) {
                // Mix by adding and clamping to Short range
                val mixed = mainBuffer[startSample + i].toInt() + sfxSamples[i].toInt()
                mainBuffer[startSample + i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
    }
}
