package com.example.sns_auto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.media.*
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.min

/**
 * VideoEncoder using Android native MediaCodec and MediaMuxer APIs
 *
 * Creates slideshow videos with:
 * - 1080x1920 vertical resolution (portrait)
 * - 30 FPS frame rate
 * - H.264 video codec
 * - Crossfade transitions between images
 * - Background music with fade-out
 */
class VideoEncoder(private val context: Context) {
    companion object {
        private const val TAG = "VideoEncoder"

        // Video configuration (matching Flutter AppConfig)
        private const val VIDEO_WIDTH = 1080
        private const val VIDEO_HEIGHT = 1920
        private const val VIDEO_FPS = 30
        private const val VIDEO_BITRATE = 8_000_000 // 8 Mbps
        private const val VIDEO_I_FRAME_INTERVAL = 1

        // Audio configuration
        private const val AUDIO_SAMPLE_RATE = 44100
        private const val AUDIO_BITRATE = 128_000 // 128 kbps
        private const val AUDIO_CHANNELS = 2 // Stereo

        // MIME types
        private const val VIDEO_MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC // H.264
        private const val AUDIO_MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC

        // Timeouts
        private const val CODEC_TIMEOUT_US = 10000L // 10ms
    }

    /**
     * Render a slideshow video from images
     *
     * @param imagePaths List of absolute paths to image files
     * @param outputPath Absolute path where MP4 should be written
     * @param imageDurationMs Duration each image is displayed (milliseconds)
     * @param transitionDurationMs Duration of crossfade transition (milliseconds)
     * @return Path to the created video file
     */
    fun renderSlideshow(
        imagePaths: List<String>,
        outputPath: String,
        imageDurationMs: Int = 1500,
        transitionDurationMs: Int = 500
    ): String {
        Log.d(TAG, "Starting slideshow render:")
        Log.d(TAG, "  Images: ${imagePaths.size}")
        Log.d(TAG, "  Output: $outputPath")
        Log.d(TAG, "  Image duration: ${imageDurationMs}ms")
        Log.d(TAG, "  Transition duration: ${transitionDurationMs}ms")

        // Calculate video parameters
        val framesPerImage = (imageDurationMs * VIDEO_FPS) / 1000
        val crossfadeFrames = (transitionDurationMs * VIDEO_FPS) / 1000
        val totalFrames = calculateTotalFrames(imagePaths.size, framesPerImage, crossfadeFrames)
        val videoDurationUs = (totalFrames * 1_000_000L) / VIDEO_FPS

        Log.d(TAG, "Video will be $totalFrames frames (${videoDurationUs / 1_000_000.0}s)")

        // Load and prepare images
        val bitmaps = loadAndPrepareImages(imagePaths)

        try {
            // For simplicity, we'll encode video-only first
            // (Adding audio requires more complex track synchronization)

            // Setup MediaMuxer
            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Encode video track
            encodeVideoTrack(
                muxer = muxer,
                bitmaps = bitmaps,
                totalFrames = totalFrames,
                framesPerImage = framesPerImage,
                crossfadeFrames = crossfadeFrames
            )

            // Finalize muxer
            muxer.stop()
            muxer.release()

            Log.d(TAG, "Video encoding complete: $outputPath")

            // Cleanup bitmaps
            bitmaps.forEach { it.recycle() }

            return outputPath
        } catch (e: Exception) {
            // Cleanup on error
            bitmaps.forEach { it.recycle() }
            File(outputPath).delete()
            throw e
        }
    }

    /**
     * Calculate total number of frames for the video
     *
     * With crossfade:
     * - Each image shows for framesPerImage
     * - Images overlap by crossfadeFrames
     * - Total = numImages * framesPerImage - (numImages - 1) * crossfadeFrames
     */
    private fun calculateTotalFrames(
        numImages: Int,
        framesPerImage: Int,
        crossfadeFrames: Int
    ): Int {
        return numImages * framesPerImage - (numImages - 1) * crossfadeFrames
    }

    /**
     * Load images and prepare them (scale, letterbox to 1080x1920)
     */
    private fun loadAndPrepareImages(imagePaths: List<String>): List<Bitmap> {
        return imagePaths.map { path ->
            Log.d(TAG, "Loading image: $path")

            // Load bitmap
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)

            // Calculate sample size for efficient loading
            options.inSampleSize = calculateInSampleSize(options, VIDEO_WIDTH, VIDEO_HEIGHT)
            options.inJustDecodeBounds = false

            val originalBitmap = BitmapFactory.decodeFile(path, options)
                ?: throw IllegalArgumentException("Failed to load image: $path")

            // Scale and letterbox to target resolution
            scaleAndLetterbox(originalBitmap, VIDEO_WIDTH, VIDEO_HEIGHT).also {
                originalBitmap.recycle()
            }
        }
    }

    /**
     * Calculate appropriate sample size for efficient bitmap loading
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Scale image to fit within bounds and add letterboxing (black bars)
     */
    private fun scaleAndLetterbox(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val sourceWidth = source.width
        val sourceHeight = source.height

        // Calculate scale to fit within target dimensions
        val scale = min(
            targetWidth.toFloat() / sourceWidth,
            targetHeight.toFloat() / sourceHeight
        )

        val scaledWidth = (sourceWidth * scale).toInt()
        val scaledHeight = (sourceHeight * scale).toInt()

        // Create output bitmap with black background
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)

        // Calculate position to center the scaled image
        val left = (targetWidth - scaledWidth) / 2f
        val top = (targetHeight - scaledHeight) / 2f

        // Scale and draw the image
        val scaledBitmap = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
        canvas.drawBitmap(scaledBitmap, left, top, null)
        scaledBitmap.recycle()

        return output
    }

    /**
     * Encode the video track with crossfade transitions
     */
    private fun encodeVideoTrack(
        muxer: MediaMuxer,
        bitmaps: List<Bitmap>,
        totalFrames: Int,
        framesPerImage: Int,
        crossfadeFrames: Int
    ): Int {
        Log.d(TAG, "Encoding video track...")

        // Configure video format
        val format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_I_FRAME_INTERVAL)
        }

        // Create and configure encoder
        val encoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        var videoTrackIndex = -1
        var frameIndex = 0
        val frameIntervalUs = 1_000_000L / VIDEO_FPS
        var muxerStarted = false

        try {
            // Encode all frames
            while (frameIndex < totalFrames) {
                // Generate and draw frame
                val canvas = inputSurface.lockCanvas(null)
                val frame = generateFrame(bitmaps, frameIndex, framesPerImage, crossfadeFrames)
                canvas.drawBitmap(frame, 0f, 0f, null)
                frame.recycle()
                inputSurface.unlockCanvasAndPost(canvas)

                // Feed frame timestamp to encoder
                val presentationTimeUs = frameIndex * frameIntervalUs
                frameIndex++

                // Retrieve encoded data
                val bufferInfo = MediaCodec.BufferInfo()
                var outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)

                while (outputBufferIndex >= 0) {
                    val encodedData = encoder.getOutputBuffer(outputBufferIndex)
                        ?: throw RuntimeException("Encoder output buffer was null")

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        // Codec config info - don't write to muxer
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0) {
                        if (!muxerStarted) {
                            throw RuntimeException("Muxer not started before writing video data")
                        }

                        // Write encoded data to muxer
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                    outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                }

                // Check for format change (track index)
                if (videoTrackIndex < 0) {
                    val outputFormat = encoder.outputFormat
                    videoTrackIndex = muxer.addTrack(outputFormat)
                    muxer.start()
                    muxerStarted = true
                    Log.d(TAG, "Video track added, muxer started")
                }

                if (frameIndex % 30 == 0) {
                    Log.d(TAG, "Encoded video frame $frameIndex/$totalFrames")
                }
            }

            // Signal end of stream
            encoder.signalEndOfInputStream()

            // Drain remaining output
            drainEncoder(encoder, muxer, videoTrackIndex)

            return videoTrackIndex
        } finally {
            encoder.stop()
            encoder.release()
            inputSurface.release()
        }
    }

    /**
     * Generate a single frame with crossfade effect
     *
     * @param bitmaps List of prepared image bitmaps
     * @param frameIndex Current frame number (0-based)
     * @param framesPerImage How many frames each image is shown
     * @param crossfadeFrames How many frames the crossfade lasts
     */
    private fun generateFrame(
        bitmaps: List<Bitmap>,
        frameIndex: Int,
        framesPerImage: Int,
        crossfadeFrames: Int
    ): Bitmap {
        // Calculate which image(s) to show
        val frameOffset = framesPerImage - crossfadeFrames

        // Determine current image index
        val currentImageIndex = (frameIndex / frameOffset).coerceAtMost(bitmaps.size - 1)

        // Check if we're in a crossfade region
        val frameInCurrentImage = frameIndex - (currentImageIndex * frameOffset)
        val isInCrossfade = frameInCurrentImage >= framesPerImage - crossfadeFrames &&
                currentImageIndex < bitmaps.size - 1

        return if (isInCrossfade) {
            // Crossfade between current and next image
            val nextImageIndex = currentImageIndex + 1
            val crossfadeProgress = (frameInCurrentImage - (framesPerImage - crossfadeFrames)).toFloat() / crossfadeFrames
            crossfadeBitmaps(bitmaps[currentImageIndex], bitmaps[nextImageIndex], crossfadeProgress)
        } else {
            // Show single image
            bitmaps[currentImageIndex].copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    /**
     * Blend two bitmaps with crossfade effect
     *
     * @param from Source bitmap (fading out)
     * @param to Destination bitmap (fading in)
     * @param progress Crossfade progress (0.0 = all from, 1.0 = all to)
     */
    private fun crossfadeBitmaps(from: Bitmap, to: Bitmap, progress: Float): Bitmap {
        val output = Bitmap.createBitmap(VIDEO_WIDTH, VIDEO_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Draw "from" image with decreasing alpha
        val fromPaint = Paint().apply {
            alpha = ((1 - progress) * 255).toInt().coerceIn(0, 255)
        }
        canvas.drawBitmap(from, 0f, 0f, fromPaint)

        // Draw "to" image with increasing alpha
        val toPaint = Paint().apply {
            alpha = (progress * 255).toInt().coerceIn(0, 255)
        }
        canvas.drawBitmap(to, 0f, 0f, toPaint)

        return output
    }

    /**
     * Encode audio track from background music resource
     *
     * Loads BGM from res/raw/bgm_default.mp3 and encodes it to match video duration
     * with a 1-second fade-out at the end
     */
    private fun encodeAudioTrack(muxer: MediaMuxer, videoDurationUs: Long): Int {
        Log.d(TAG, "Encoding audio track...")

        // Try to load BGM from resources
        val bgmResId = context.resources.getIdentifier("bgm_default", "raw", context.packageName)
        if (bgmResId == 0) {
            Log.w(TAG, "No BGM resource found (res/raw/bgm_default.mp3), skipping audio track")
            return -1 // No audio track
        }

        try {
            // Extract and decode BGM
            val bgmSamples = extractAudioSamples(bgmResId, videoDurationUs)

            // Configure audio format
            val format = MediaFormat.createAudioFormat(
                AUDIO_MIME_TYPE,
                AUDIO_SAMPLE_RATE,
                AUDIO_CHANNELS
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
            }

            // Create and configure encoder
            val encoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            var audioTrackIndex = -1

            try {
                // Encode audio samples
                audioTrackIndex = feedAudioEncoder(encoder, muxer, bgmSamples, videoDurationUs)
                return audioTrackIndex
            } finally {
                encoder.stop()
                encoder.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode audio track: ${e.message}", e)
            return -1 // Continue without audio
        }
    }

    /**
     * Extract audio samples from resource file
     */
    private fun extractAudioSamples(resId: Int, maxDurationUs: Long): ShortArray {
        val extractor = MediaExtractor()
        val afd = context.resources.openRawResourceFd(resId)

        try {
            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)

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
                throw IllegalStateException("No audio track found in BGM file")
            }

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)

            // Create decoder
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val samples = mutableListOf<Short>()
            var outputDone = false
            val bufferInfo = MediaCodec.BufferInfo()
            val fadeOutDurationUs = 1_000_000L // 1 second fade-out

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
                    if (outputBufferId >= 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputBufferId)!!

                        // Convert ByteBuffer to ShortArray (16-bit PCM samples)
                        val shortBuffer = outputBuffer.asShortBuffer()
                        val chunk = ShortArray(shortBuffer.remaining())
                        shortBuffer.get(chunk)

                        // Apply fade-out if near end of video duration
                        val currentTimeUs = bufferInfo.presentationTimeUs
                        if (currentTimeUs + fadeOutDurationUs >= maxDurationUs) {
                            applyFadeOut(chunk, currentTimeUs, maxDurationUs, fadeOutDurationUs)
                        }

                        samples.addAll(chunk.toList())

                        decoder.releaseOutputBuffer(outputBufferId, false)

                        // Stop if we've reached video duration
                        if (currentTimeUs >= maxDurationUs) {
                            outputDone = true
                        }

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // Audio format changed (expected on first output)
                    }
                }
            } finally {
                decoder.stop()
                decoder.release()
            }

            return samples.toShortArray()
        } finally {
            extractor.release()
            afd.close()
        }
    }

    /**
     * Apply fade-out envelope to audio samples
     */
    private fun applyFadeOut(
        samples: ShortArray,
        currentTimeUs: Long,
        maxDurationUs: Long,
        fadeOutDurationUs: Long
    ) {
        val fadeStartUs = maxDurationUs - fadeOutDurationUs

        for (i in samples.indices) {
            // Calculate sample timestamp
            val sampleTimeUs = currentTimeUs + (i * 1_000_000L) / (AUDIO_SAMPLE_RATE * AUDIO_CHANNELS)

            if (sampleTimeUs >= fadeStartUs) {
                // Calculate fade multiplier (1.0 at start, 0.0 at end)
                val fadeProgress = (sampleTimeUs - fadeStartUs).toFloat() / fadeOutDurationUs
                val multiplier = (1.0f - fadeProgress).coerceIn(0f, 1f)
                samples[i] = (samples[i] * multiplier).toInt().toShort()
            }
        }
    }

    /**
     * Feed audio samples to encoder and write to muxer
     */
    private fun feedAudioEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        samples: ShortArray,
        videoDurationUs: Long
    ): Int {
        var audioTrackIndex = -1
        var inputOffset = 0
        val inputBufferSize = 2048 // samples per chunk
        var inputDone = false
        val bufferInfo = MediaCodec.BufferInfo()

        while (!inputDone || inputOffset < samples.size) {
            // Feed input
            if (!inputDone) {
                val inputBufferId = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                if (inputBufferId >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputBufferId)!!
                    inputBuffer.clear()

                    val samplesToWrite = min(inputBufferSize, samples.size - inputOffset)
                    if (samplesToWrite > 0) {
                        // Convert short samples to bytes
                        val byteBuffer = ByteBuffer.allocate(samplesToWrite * 2)
                        for (i in 0 until samplesToWrite) {
                            byteBuffer.putShort(samples[inputOffset + i])
                        }
                        inputBuffer.put(byteBuffer.array())

                        val presentationTimeUs = (inputOffset * 1_000_000L) / (AUDIO_SAMPLE_RATE * AUDIO_CHANNELS)
                        encoder.queueInputBuffer(inputBufferId, 0, samplesToWrite * 2, presentationTimeUs, 0)

                        inputOffset += samplesToWrite
                    } else {
                        encoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    }
                }
            }

            // Get output
            val outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
            if (outputBufferId >= 0) {
                val encodedData = encoder.getOutputBuffer(outputBufferId)!!

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size != 0) {
                    if (audioTrackIndex < 0) {
                        throw RuntimeException("Audio track not added to muxer")
                    }

                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(audioTrackIndex, encodedData, bufferInfo)
                }

                encoder.releaseOutputBuffer(outputBufferId, false)

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (audioTrackIndex >= 0) {
                    throw RuntimeException("Audio format changed twice")
                }
                audioTrackIndex = muxer.addTrack(encoder.outputFormat)
                Log.d(TAG, "Audio track added to muxer")
            }
        }

        return audioTrackIndex
    }

    /**
     * Drain remaining output from encoder
     */
    private fun drainEncoder(encoder: MediaCodec, muxer: MediaMuxer, trackIndex: Int) {
        val bufferInfo = MediaCodec.BufferInfo()
        var outputDone = false

        while (!outputDone) {
            val outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)

            if (outputBufferId >= 0) {
                val encodedData = encoder.getOutputBuffer(outputBufferId)!!

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && bufferInfo.size != 0) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                }

                encoder.releaseOutputBuffer(outputBufferId, false)

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
            } else if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // No output available yet
            }
        }
    }
}
