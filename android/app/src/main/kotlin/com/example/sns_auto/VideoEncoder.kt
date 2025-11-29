package com.example.sns_auto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.*
import android.opengl.*
import android.util.Log
import android.view.Surface
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGL10
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

        // Audio configuration (defaults, will be overridden by decoder output)
        private const val AUDIO_SAMPLE_RATE = 44100
        private const val AUDIO_BITRATE = 128_000 // 128 kbps
        private const val AUDIO_CHANNELS = 2 // Stereo

        // MIME types
        private const val VIDEO_MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC // H.264
        private const val AUDIO_MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC

        // Timeouts
        private const val CODEC_TIMEOUT_US = 10000L // 10ms

        // Before & After template constants
        const val BEFORE_HOLD_MS = 2500L
        const val AFTER_HOLD_MS = 2500L
        const val TEXT_FLIP_MS = 200L
        const val AFTER_ZOOM_SCALE = 1.05f

        // Simple default overlay text style
        const val TEXT_SIZE_PX = 72f
        const val TEXT_SIZE_BEFORE_AFTER_PX = TEXT_SIZE_PX * 2f  // 144f - doubled for Before & After template
        const val TEXT_STROKE_WIDTH = 4f
    }

    /**
     * Render a slideshow video from images
     *
     * @param imagePaths List of absolute paths to image files
     * @param outputPath Absolute path where MP4 should be written
     * @param templateId Template ID to determine rendering mode
     * @param imageDurationMs Duration each image is displayed (milliseconds)
     * @param transitionDurationMs Duration of crossfade transition (milliseconds)
     * @param musicTrackName Name of the music file to use (e.g., "bgm_default.mp3")
     * @return Path to the created video file
     */
    fun renderSlideshow(
        imagePaths: List<String>,
        outputPath: String,
        templateId: String = "classic_slideshow",
        imageDurationMs: Int = 1500,
        transitionDurationMs: Int = 500,
        musicTrackName: String = "bgm_default.mp3"
    ): String {
        Log.d(TAG, "Starting slideshow render:")
        Log.d(TAG, "  Template: $templateId")
        Log.d(TAG, "  Images: ${imagePaths.size}")
        Log.d(TAG, "  Output: $outputPath")
        Log.d(TAG, "  Image duration: ${imageDurationMs}ms")
        Log.d(TAG, "  Transition duration: ${transitionDurationMs}ms")

        // Route to appropriate rendering method based on template
        return when (templateId) {
            "four_tile_intro" -> renderFourTileIntro(imagePaths, outputPath, musicTrackName)
            "before_after" -> renderBeforeAfter(imagePaths, outputPath)
            else -> renderClassicSlideshow(imagePaths, outputPath, imageDurationMs, transitionDurationMs, musicTrackName)
        }
    }

    /**
     * Render classic slideshow with crossfade transitions
     */
    private fun renderClassicSlideshow(
        imagePaths: List<String>,
        outputPath: String,
        imageDurationMs: Int,
        transitionDurationMs: Int,
        musicTrackName: String
    ): String {
        Log.d(TAG, "Rendering classic slideshow")

        // Calculate video parameters
        val framesPerImage = (imageDurationMs * VIDEO_FPS) / 1000
        val crossfadeFrames = (transitionDurationMs * VIDEO_FPS) / 1000
        val totalFrames = calculateTotalFrames(imagePaths.size, framesPerImage, crossfadeFrames)
        val videoDurationUs = (totalFrames * 1_000_000L) / VIDEO_FPS

        Log.d(TAG, "Video will be $totalFrames frames (${videoDurationUs / 1_000_000.0}s)")

        // Check for BGM file
        val bgmPath = getBgmFilePath(musicTrackName)
        val hasBgm = bgmPath != null && File(bgmPath).exists()
        if (hasBgm) {
            Log.d(TAG, "BGM file found: $bgmPath")
        } else {
            Log.d(TAG, "No BGM file found, rendering video without audio")
        }

        // Load and prepare images
        val bitmaps = loadAndPrepareImages(imagePaths)

        try {
            // Setup MediaMuxer
            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Encode video and audio tracks
            val trackIndices = encodeVideoAndAudio(
                muxer = muxer,
                bitmaps = bitmaps,
                totalFrames = totalFrames,
                framesPerImage = framesPerImage,
                crossfadeFrames = crossfadeFrames,
                videoDurationUs = videoDurationUs,
                bgmPath = bgmPath
            )

            // Finalize muxer (only if it was started)
            if (trackIndices.muxerStarted) {
                muxer.stop()
            }
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
     * Get the BGM file path from external storage
     * Path: /Android/data/com.example.sns_auto/files/bgm/{musicTrackName}
     *
     * If the file doesn't exist but exists in res/raw, it will be copied automatically.
     *
     * @param musicTrackName Name of the music file (e.g., "bgm_default.mp3")
     */
    private fun getBgmFilePath(musicTrackName: String): String? {
        return try {
            val externalFilesDir = context.getExternalFilesDir(null)
            if (externalFilesDir != null) {
                val bgmDir = File(externalFilesDir, "bgm")
                // Create directory if it doesn't exist
                if (!bgmDir.exists()) {
                    bgmDir.mkdirs()
                    Log.d(TAG, "Created BGM directory: ${bgmDir.absolutePath}")
                }

                val bgmFile = File(bgmDir, musicTrackName)

                // If BGM doesn't exist in external storage, try to copy from res/raw
                if (!bgmFile.exists()) {
                    Log.d(TAG, "BGM not found in external storage, checking res/raw...")
                    copyBgmFromResources(bgmFile, musicTrackName)
                }

                bgmFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting BGM file path: ${e.message}")
            null
        }
    }

    /**
     * Copy BGM from app resources (res/raw) to external storage
     *
     * @param destinationFile Destination file to copy to
     * @param musicTrackName Name of the music file (e.g., "bgm_default.mp3")
     */
    private fun copyBgmFromResources(destinationFile: File, musicTrackName: String) {
        try {
            // Extract resource name from musicTrackName (remove .mp3 extension)
            val resourceName = musicTrackName.removeSuffix(".mp3")
            val resourceId = context.resources.getIdentifier(resourceName, "raw", context.packageName)

            if (resourceId != 0) {
                Log.d(TAG, "Found BGM '$resourceName' in res/raw, copying to external storage...")

                context.resources.openRawResource(resourceId).use { inputStream ->
                    FileOutputStream(destinationFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                Log.d(TAG, "BGM copied successfully: ${destinationFile.absolutePath}")
            } else {
                Log.d(TAG, "No BGM found in res/raw (R.raw.$resourceName not found)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy BGM from resources: ${e.message}", e)
        }
    }

    /**
     * Track indices returned from encoding
     */
    private data class TrackIndices(
        val videoTrackIndex: Int,
        val audioTrackIndex: Int,
        val muxerStarted: Boolean
    )

    /**
     * Audio samples with format information
     */
    private data class AudioData(
        val samples: ShortArray,
        val sampleRate: Int,
        val channelCount: Int
    )

    /**
     * Video segment definition for timeline-based rendering
     */
    private data class VideoSegment(
        val imageIndex: Int,
        val startMs: Long,
        val endMs: Long,
        val zoomFrom: Float = 1.0f,
        val zoomTo: Float = 1.0f
    ) {
        val durationMs: Long get() = endMs - startMs

        fun validate(totalDurationMs: Long): Boolean {
            return startMs >= 0 &&
                   endMs > startMs &&
                   endMs <= totalDurationMs &&
                   durationMs > 0 &&
                   zoomFrom > 0 &&
                   zoomTo > 0
        }
    }

    /**
     * Text overlay definition for timeline-based rendering
     */
    private data class TextOverlay(
        val text: String,
        val startMs: Long,
        val endMs: Long,
        val fadeInMs: Long = 0
    ) {
        val durationMs: Long get() = endMs - startMs

        fun validate(totalDurationMs: Long): Boolean {
            return startMs >= 0 &&
                   endMs > startMs &&
                   endMs <= totalDurationMs &&
                   durationMs > 0 &&
                   fadeInMs >= 0
        }
    }

    /**
     * Complete timeline for a template including all video/audio/text elements
     */
    private data class TemplateTimeline(
        val segments: List<VideoSegment>,
        val audioEvents: List<AudioEvent>,
        val textOverlays: List<TextOverlay>,
        val totalDurationMs: Long,
        val totalFrames: Int
    ) {
        fun validate(): Boolean {
            return segments.all { it.validate(totalDurationMs) } &&
                   audioEvents.all { it.validate(totalDurationMs) } &&
                   textOverlays.all { it.validate(totalDurationMs) } &&
                   totalDurationMs > 0 &&
                   totalFrames > 0
        }
    }

    /**
     * Audio event definition for timeline-based audio mixing
     */
    private data class AudioEvent(
        val assetName: String,
        val filePath: String,
        val startMs: Long,
        val maxDurationMs: Long = Long.MAX_VALUE,
        val speed: Float = 1.0f,  // Playback speed multiplier (1.0 = normal, 1.3 = 30% faster)
        val pitchCorrection: Boolean = false  // If true, preserve original pitch when speed != 1.0
    ) {
        fun validate(totalDurationMs: Long): Boolean {
            return startMs >= 0 && startMs < totalDurationMs
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
     * Encode video and audio tracks together
     * This method properly handles MediaCodec INFO_OUTPUT_FORMAT_CHANGED event
     */
    private fun encodeVideoAndAudio(
        muxer: MediaMuxer,
        bitmaps: List<Bitmap>,
        totalFrames: Int,
        framesPerImage: Int,
        crossfadeFrames: Int,
        videoDurationUs: Long,
        bgmPath: String?
    ): TrackIndices {
        Log.d(TAG, "Encoding video and audio tracks...")

        // Configure video format
        val videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_I_FRAME_INTERVAL)
        }

        // Create and configure video encoder
        val videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE)
        videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = videoEncoder.createInputSurface()
        videoEncoder.start()

        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var muxerStarted = false

        // Extract audio samples if BGM is available
        val audioData = if (bgmPath != null && File(bgmPath).exists()) {
            try {
                Log.d(TAG, "Extracting audio from BGM: $bgmPath")
                extractAudioSamplesFromFile(bgmPath, videoDurationUs)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract audio: ${e.message}", e)
                null
            }
        } else {
            null
        }

        // Create audio encoder if we have samples
        val audioEncoder = if (audioData != null && audioData.samples.isNotEmpty()) {
            Log.d(TAG, "Creating audio encoder with ${audioData.samples.size} samples at ${audioData.sampleRate}Hz, ${audioData.channelCount} channels")
            val audioFormat = MediaFormat.createAudioFormat(
                AUDIO_MIME_TYPE,
                audioData.sampleRate,
                audioData.channelCount
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
                setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            }
            MediaCodec.createEncoderByType(AUDIO_MIME_TYPE).apply {
                configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
        } else {
            Log.d(TAG, "No audio encoder (no BGM)")
            null
        }

        // Setup EGL for rendering to the input surface
        val eglHelper = EglHelper()
        eglHelper.setup(inputSurface)

        try {
            var frameIndex = 0
            var videoInputDone = false
            var audioInputDone = audioEncoder == null // If no audio, mark as done
            var audioInputOffset = 0
            val audioInputBufferSize = 2048 // samples per buffer

            // Main encoding loop
            while (!videoInputDone || !audioInputDone ||
                   videoTrackIndex < 0 || (audioEncoder != null && audioTrackIndex < 0)) {

                // Feed video frames
                if (!videoInputDone && frameIndex < totalFrames) {
                    // Generate frame bitmap
                    val frameBitmap = generateFrame(bitmaps, frameIndex, framesPerImage, crossfadeFrames)

                    // Calculate presentation time for this frame
                    val presentationTimeNs = (frameIndex * 1_000_000_000L) / VIDEO_FPS

                    // Render to surface with EGL
                    eglHelper.drawFrame(frameBitmap, presentationTimeNs)
                    frameBitmap.recycle()

                    frameIndex++

                    if (frameIndex >= totalFrames) {
                        videoEncoder.signalEndOfInputStream()
                        videoInputDone = true
                        Log.d(TAG, "All video frames submitted ($frameIndex frames)")
                    }

                    if (frameIndex % 30 == 0) {
                        Log.d(TAG, "Submitted video frame $frameIndex/$totalFrames")
                    }
                }

                // Feed audio samples
                if (audioEncoder != null && audioData != null && !audioInputDone) {
                    val inputBufferId = audioEncoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputBufferId >= 0) {
                        val inputBuffer = audioEncoder.getInputBuffer(inputBufferId)!!
                        inputBuffer.clear()

                        // Calculate max samples that can fit in buffer (each sample is 2 bytes for 16-bit PCM)
                        val bufferCapacitySamples = inputBuffer.capacity() / 2
                        val remainingSamples = audioData.samples.size - audioInputOffset
                        val samplesToWrite = min(min(bufferCapacitySamples, audioInputBufferSize), remainingSamples)

                        // Safety check and logging
                        if (bufferCapacitySamples < audioInputBufferSize) {
                            Log.w(TAG, "Audio input buffer capacity ($bufferCapacitySamples samples, ${inputBuffer.capacity()} bytes) " +
                                    "is smaller than requested ($audioInputBufferSize samples). Using buffer capacity.")
                        }

                        if (samplesToWrite > 0) {
                            // Verify we won't overflow (defensive check)
                            val bytesNeeded = samplesToWrite * 2
                            if (bytesNeeded > inputBuffer.capacity()) {
                                val errorMsg = "Audio buffer overflow prevented: need $bytesNeeded bytes but capacity is ${inputBuffer.capacity()} bytes"
                                Log.e(TAG, errorMsg)
                                throw RuntimeException(errorMsg)
                            }

                            // Write PCM samples to buffer
                            for (i in 0 until samplesToWrite) {
                                inputBuffer.putShort(audioData.samples[audioInputOffset + i])
                            }
                            inputBuffer.flip()

                            // Calculate presentation time based on sample position
                            val presentationTimeUs = (audioInputOffset * 1_000_000L) / (audioData.sampleRate * audioData.channelCount)
                            audioEncoder.queueInputBuffer(inputBufferId, 0, samplesToWrite * 2, presentationTimeUs, 0)

                            audioInputOffset += samplesToWrite
                        } else {
                            audioEncoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            audioInputDone = true
                            Log.d(TAG, "All audio samples submitted")
                        }
                    }
                }

                // Process video encoder output
                val videoBufferInfo = MediaCodec.BufferInfo()
                var videoOutputIndex = videoEncoder.dequeueOutputBuffer(videoBufferInfo, CODEC_TIMEOUT_US)

                when (videoOutputIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (videoTrackIndex >= 0) {
                            throw RuntimeException("Video format changed twice")
                        }
                        val outputFormat = videoEncoder.outputFormat
                        videoTrackIndex = muxer.addTrack(outputFormat)
                        Log.d(TAG, "Video track added to muxer (index: $videoTrackIndex)")

                        // Start muxer if all tracks are ready
                        if ((audioEncoder == null || audioTrackIndex >= 0) && !muxerStarted) {
                            muxer.start()
                            muxerStarted = true
                            Log.d(TAG, "Muxer started (video only)")
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // No output available yet
                    }
                    else -> {
                        if (videoOutputIndex >= 0) {
                            val encodedData = videoEncoder.getOutputBuffer(videoOutputIndex)
                                ?: throw RuntimeException("Video encoder output buffer was null")

                            // Skip codec config data
                            if (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                videoBufferInfo.size = 0
                            }

                            if (videoBufferInfo.size != 0 && muxerStarted) {
                                encodedData.position(videoBufferInfo.offset)
                                encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size)
                                muxer.writeSampleData(videoTrackIndex, encodedData, videoBufferInfo)
                            }

                            videoEncoder.releaseOutputBuffer(videoOutputIndex, false)

                            if (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                Log.d(TAG, "Video encoding complete")
                                if (audioEncoder == null) {
                                    break // Exit if no audio
                                }
                            }
                        }
                    }
                }

                // Process audio encoder output
                if (audioEncoder != null) {
                    val audioBufferInfo = MediaCodec.BufferInfo()
                    var audioOutputIndex = audioEncoder.dequeueOutputBuffer(audioBufferInfo, CODEC_TIMEOUT_US)

                    when (audioOutputIndex) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (audioTrackIndex >= 0) {
                                throw RuntimeException("Audio format changed twice")
                            }
                            val outputFormat = audioEncoder.outputFormat
                            audioTrackIndex = muxer.addTrack(outputFormat)
                            Log.d(TAG, "Audio track added to muxer (index: $audioTrackIndex)")

                            // Start muxer if all tracks are ready
                            if (videoTrackIndex >= 0 && !muxerStarted) {
                                muxer.start()
                                muxerStarted = true
                                Log.d(TAG, "Muxer started (video + audio)")
                            }
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // No output available yet
                        }
                        else -> {
                            if (audioOutputIndex >= 0) {
                                val encodedData = audioEncoder.getOutputBuffer(audioOutputIndex)
                                    ?: throw RuntimeException("Audio encoder output buffer was null")

                                // Skip codec config data
                                if (audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                    audioBufferInfo.size = 0
                                }

                                if (audioBufferInfo.size != 0 && muxerStarted) {
                                    encodedData.position(audioBufferInfo.offset)
                                    encodedData.limit(audioBufferInfo.offset + audioBufferInfo.size)
                                    muxer.writeSampleData(audioTrackIndex, encodedData, audioBufferInfo)
                                }

                                audioEncoder.releaseOutputBuffer(audioOutputIndex, false)

                                if (audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                    Log.d(TAG, "Audio encoding complete")
                                    if (videoInputDone && (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0)) {
                                        break // Exit if both are done
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "Encoding finished successfully")
            return TrackIndices(videoTrackIndex, audioTrackIndex, muxerStarted)
        } finally {
            eglHelper.release()
            videoEncoder.stop()
            videoEncoder.release()
            inputSurface.release()
            audioEncoder?.stop()
            audioEncoder?.release()
        }
    }

    /**
     * Generate a single frame with crossfade effect
     *
     * @param bitmaps List of prepared image bitmaps
     * @param frameIndex Current frame number (0-based)
     * @param framesPerImage How many frames each image is shown (e.g., 45 for 2.5s at 30fps)
     * @param crossfadeFrames How many frames the crossfade lasts (e.g., 15 for 0.5s at 30fps)
     *
     * Timeline example with 3 images (framesPerImage=45, crossfadeFrames=15):
     * - Frames 0-29:   Image 0 only
     * - Frames 30-44:  Image 0 → Image 1 crossfade
     * - Frames 45-74:  Image 1 only
     * - Frames 75-89:  Image 1 → Image 2 crossfade
     * - Frames 90-119: Image 2 only
     *
     * Each image displays for 45 frames (2.5s), with 15-frame (0.5s) overlap
     */
    private fun generateFrame(
        bitmaps: List<Bitmap>,
        frameIndex: Int,
        framesPerImage: Int,
        crossfadeFrames: Int
    ): Bitmap {
        // Calculate the offset between image start times (accounting for overlap)
        val frameOffset = framesPerImage - crossfadeFrames

        // Find which image started most recently (but not after this frame)
        var currentImageIndex = frameIndex / frameOffset

        // Handle last image edge case
        if (currentImageIndex >= bitmaps.size) {
            currentImageIndex = bitmaps.size - 1
        }

        // Calculate position within this image's display time
        val frameInCurrentImage = frameIndex - (currentImageIndex * frameOffset)

        // Determine if we're in a crossfade period
        val crossfadeStart = framesPerImage - crossfadeFrames

        // Check if we're in crossfade with the PREVIOUS image
        val isInCrossfadeWithPrevious = frameInCurrentImage < crossfadeFrames &&
                                        currentImageIndex > 0

        // Check if we're in crossfade with the NEXT image
        val isInCrossfadeWithNext = frameInCurrentImage >= crossfadeStart &&
                                   currentImageIndex < bitmaps.size - 1

        return when {
            isInCrossfadeWithPrevious -> {
                // Crossfade from previous to current image
                val prevImageIndex = currentImageIndex - 1
                val crossfadeProgress = frameInCurrentImage.toFloat() / crossfadeFrames
                Log.v(TAG, "Frame $frameIndex: Crossfade ${prevImageIndex}→${currentImageIndex} (${(crossfadeProgress * 100).toInt()}%)")
                crossfadeBitmaps(bitmaps[prevImageIndex], bitmaps[currentImageIndex], crossfadeProgress)
            }
            isInCrossfadeWithNext -> {
                // Crossfade from current to next image
                val nextImageIndex = currentImageIndex + 1
                val crossfadeFrameIndex = frameInCurrentImage - crossfadeStart
                val crossfadeProgress = crossfadeFrameIndex.toFloat() / crossfadeFrames
                Log.v(TAG, "Frame $frameIndex: Crossfade ${currentImageIndex}→${nextImageIndex} (${(crossfadeProgress * 100).toInt()}%)")
                crossfadeBitmaps(bitmaps[currentImageIndex], bitmaps[nextImageIndex], crossfadeProgress)
            }
            else -> {
                // Show single image
                Log.v(TAG, "Frame $frameIndex: Image $currentImageIndex")
                bitmaps[currentImageIndex].copy(Bitmap.Config.ARGB_8888, false)
            }
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
     * Extract audio samples from file path
     *
     * Extracts PCM audio samples from the BGM file, trimming to video duration
     * and applying 1-second fade-out at the end
     */
    private fun extractAudioSamplesFromFile(filePath: String, maxDurationUs: Long): AudioData {
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
                    Log.d(TAG, "Found audio track: $mime")
                    break
                }
            }

            if (audioTrackIndex < 0) {
                throw IllegalStateException("No audio track found in BGM file")
            }

            extractor.selectTrack(audioTrackIndex)
            val inputFormat = extractor.getTrackFormat(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!

            Log.d(TAG, "Input audio format: $inputFormat")

            // Create decoder
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val samples = mutableListOf<Short>()
            var outputDone = false
            val bufferInfo = MediaCodec.BufferInfo()
            val fadeOutDurationUs = 1_000_000L // 1 second fade-out

            // Read actual output format from decoder
            var actualSampleRate = AUDIO_SAMPLE_RATE
            var actualChannelCount = AUDIO_CHANNELS

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
                            // Read actual audio format from decoder output
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

                                // CRITICAL: Set position and limit based on bufferInfo
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                                // Convert ByteBuffer to ShortArray (16-bit PCM samples)
                                val shortCount = bufferInfo.size / 2
                                val shortBuffer = outputBuffer.asShortBuffer()
                                val chunk = ShortArray(shortCount)
                                shortBuffer.get(chunk)

                                // Log first few samples for debugging (only once)
                                if (samples.isEmpty() && chunk.isNotEmpty()) {
                                    val samplePreview = chunk.take(8).joinToString(", ")
                                    Log.d(TAG, "First PCM samples: [$samplePreview]")
                                }

                                // Apply fade-out if near end of video duration
                                val currentTimeUs = bufferInfo.presentationTimeUs
                                if (currentTimeUs + fadeOutDurationUs >= maxDurationUs) {
                                    applyFadeOut(chunk, currentTimeUs, maxDurationUs, fadeOutDurationUs, actualSampleRate, actualChannelCount)
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
                            }
                        }
                    }
                }
            } finally {
                decoder.stop()
                decoder.release()
            }

            val durationSec = samples.size.toFloat() / (actualSampleRate * actualChannelCount)
            Log.d(TAG, "Extracted ${samples.size} audio samples (${durationSec}s at ${actualSampleRate}Hz, ${actualChannelCount}ch)")

            return AudioData(samples.toShortArray(), actualSampleRate, actualChannelCount)
        } finally {
            extractor.release()
        }
    }

    /**
     * Apply fade-out envelope to audio samples
     */
    private fun applyFadeOut(
        samples: ShortArray,
        currentTimeUs: Long,
        maxDurationUs: Long,
        fadeOutDurationUs: Long,
        sampleRate: Int,
        channelCount: Int
    ) {
        val fadeStartUs = maxDurationUs - fadeOutDurationUs

        for (i in samples.indices) {
            // Calculate sample timestamp
            val sampleTimeUs = currentTimeUs + (i * 1_000_000L) / (sampleRate * channelCount)

            if (sampleTimeUs >= fadeStartUs) {
                // Calculate fade multiplier (1.0 at start, 0.0 at end)
                val fadeProgress = (sampleTimeUs - fadeStartUs).toFloat() / fadeOutDurationUs
                val multiplier = (1.0f - fadeProgress).coerceIn(0f, 1f)
                samples[i] = (samples[i] * multiplier).toInt().toShort()
            }
        }
    }

    /**
     * EGL helper for rendering bitmaps to MediaCodec input surface
     */
    private inner class EglHelper {
        private var eglDisplay: EGLDisplay? = null
        private var eglContext: EGLContext? = null
        private var eglSurface: EGLSurface? = null

        private var program = 0
        private var textureId = 0
        private var positionHandle = 0
        private var texCoordHandle = 0
        private var samplerHandle = 0

        private val vertexBuffer: FloatBuffer
        private val texCoordBuffer: FloatBuffer

        // Vertex shader - simple passthrough
        private val vertexShaderCode = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """.trimIndent()

        // Fragment shader - texture sampling
        private val fragmentShaderCode = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """.trimIndent()

        // Vertices for a full-screen quad
        private val vertices = floatArrayOf(
            -1.0f, -1.0f,  // bottom-left
             1.0f, -1.0f,  // bottom-right
            -1.0f,  1.0f,  // top-left
             1.0f,  1.0f   // top-right
        )

        // Texture coordinates (flipped vertically for Android)
        private val texCoords = floatArrayOf(
            0.0f, 1.0f,  // bottom-left
            1.0f, 1.0f,  // bottom-right
            0.0f, 0.0f,  // top-left
            1.0f, 0.0f   // top-right
        )

        init {
            vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertices)
            vertexBuffer.position(0)

            texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(texCoords)
            texCoordBuffer.position(0)
        }

        fun setup(surface: Surface) {
            // Get EGL display
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                throw RuntimeException("Unable to get EGL14 display")
            }

            // Initialize EGL
            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                throw RuntimeException("Unable to initialize EGL14")
            }

            // Configure EGL
            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)) {
                throw RuntimeException("Unable to find RGB888+recordable ES2 EGL config")
            }

            // Create EGL context
            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(
                eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0
            )
            checkEglError("eglCreateContext")

            // Create window surface
            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay, configs[0], surface, surfaceAttribs, 0
            )
            checkEglError("eglCreateWindowSurface")

            // Make current
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                throw RuntimeException("eglMakeCurrent failed")
            }

            // Setup OpenGL
            setupOpenGL()
        }

        private fun setupOpenGL() {
            // Create shader program
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)

            // Check link status
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                val error = GLES20.glGetProgramInfoLog(program)
                GLES20.glDeleteProgram(program)
                throw RuntimeException("Could not link program: $error")
            }

            // Get attribute/uniform locations
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
            samplerHandle = GLES20.glGetUniformLocation(program, "sTexture")

            // Generate texture
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

            // Set texture parameters
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        private fun loadShader(type: Int, shaderCode: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)

            // Check compile status
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] != GLES20.GL_TRUE) {
                val error = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                throw RuntimeException("Could not compile shader $type: $error")
            }

            return shader
        }

        fun drawFrame(bitmap: Bitmap, presentationTimeNs: Long) {
            // Make current
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            // Clear
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            // Use program
            GLES20.glUseProgram(program)

            // Upload bitmap to texture
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

            // Set vertex attributes
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

            // Set sampler uniform
            GLES20.glUniform1i(samplerHandle, 0)

            // Draw
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            // Disable attributes
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(texCoordHandle)

            // Set presentation time
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeNs)

            // Swap buffers
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        fun release() {
            if (eglDisplay != null && eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    eglDisplay,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT
                )

                if (eglSurface != null && eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }

                if (eglContext != null && eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }

                EGL14.eglTerminate(eglDisplay)
            }

            eglDisplay = null
            eglContext = null
            eglSurface = null

            // Delete OpenGL resources
            if (textureId != 0) {
                val textures = intArrayOf(textureId)
                GLES20.glDeleteTextures(1, textures, 0)
                textureId = 0
            }

            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }
        }

        private fun checkEglError(msg: String) {
            val error = EGL14.eglGetError()
            if (error != EGL14.EGL_SUCCESS) {
                throw RuntimeException("$msg: EGL error: 0x${Integer.toHexString(error)}")
            }
        }
    }

    /**
     * Encode 4-tile video with SFX timing
     * Similar to encodeVideoAndAudio but with 4-tile frame generation and offline audio mixing
     */
    private fun encodeFourTileVideo(
        muxer: MediaMuxer,
        bitmaps: List<Bitmap>,
        totalFrames: Int,
        framesPerImage: Int,
        grayscaleEndFrame: Int,
        tilesEndFrame: Int,
        colorEndFrame: Int,
        videoDurationUs: Long,
        bgmPath: String?,
        sfxEvents: List<AudioMixer.SfxEvent>
    ): TrackIndices {
        Log.d(TAG, "Encoding 4-tile video...")

        // Configure video format
        val videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_I_FRAME_INTERVAL)
        }

        // Create and configure video encoder
        val videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE)
        videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = videoEncoder.createInputSurface()
        videoEncoder.start()

        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var muxerStarted = false

        // Mix BGM with SFX offline using AudioMixer
        val audioData = if (bgmPath != null && File(bgmPath).exists()) {
            try {
                Log.d(TAG, "Mixing audio: BGM=$bgmPath with ${sfxEvents.size} SFX events")
                val mixer = AudioMixer(context)
                val mixed = mixer.mixAudio(bgmPath, sfxEvents, videoDurationUs)
                AudioData(mixed.samples, mixed.sampleRate, mixed.channelCount)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mix audio: ${e.message}", e)
                null
            }
        } else {
            null
        }

        // Create audio encoder if we have samples
        val audioEncoder = if (audioData != null && audioData.samples.isNotEmpty()) {
            Log.d(TAG, "Creating audio encoder")
            val audioFormat = MediaFormat.createAudioFormat(
                AUDIO_MIME_TYPE,
                audioData.sampleRate,
                audioData.channelCount
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
                setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            }
            MediaCodec.createEncoderByType(AUDIO_MIME_TYPE).apply {
                configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
        } else {
            Log.d(TAG, "No audio encoder (no BGM)")
            null
        }

        // Setup EGL for rendering
        val eglHelper = EglHelper()
        eglHelper.setup(inputSurface)

        try {
            var frameIndex = 0
            var videoInputDone = false
            var audioInputDone = audioEncoder == null
            var audioInputOffset = 0
            val audioInputBufferSize = 2048

            // Main encoding loop
            while (!videoInputDone || !audioInputDone ||
                videoTrackIndex < 0 || (audioEncoder != null && audioTrackIndex < 0)) {

                // Feed video frames
                if (!videoInputDone && frameIndex < totalFrames) {
                    // Calculate which image and local frame within that image
                    val imageIndex = frameIndex / framesPerImage
                    val localFrame = frameIndex % framesPerImage

                    // Generate 4-tile frame (no SFX playback - audio is mixed offline)
                    val frameBitmap = generateFourTileFrame(
                        bitmap = bitmaps[imageIndex],
                        localFrame = localFrame,
                        grayscaleEndFrame = grayscaleEndFrame,
                        tilesEndFrame = tilesEndFrame,
                        colorEndFrame = colorEndFrame,
                        framesPerImage = framesPerImage
                    )

                    // Calculate presentation time
                    val presentationTimeNs = (frameIndex * 1_000_000_000L) / VIDEO_FPS

                    // Render to surface
                    eglHelper.drawFrame(frameBitmap, presentationTimeNs)
                    frameBitmap.recycle()

                    frameIndex++

                    if (frameIndex >= totalFrames) {
                        videoEncoder.signalEndOfInputStream()
                        videoInputDone = true
                        Log.d(TAG, "All video frames submitted ($frameIndex frames)")
                    }

                    if (frameIndex % 30 == 0) {
                        Log.d(TAG, "Submitted video frame $frameIndex/$totalFrames")
                    }
                }

                // Feed audio samples (same as classic slideshow)
                if (audioEncoder != null && audioData != null && !audioInputDone) {
                    val inputBufferId = audioEncoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputBufferId >= 0) {
                        val inputBuffer = audioEncoder.getInputBuffer(inputBufferId)!!
                        inputBuffer.clear()

                        // Calculate max samples that can fit in buffer (each sample is 2 bytes for 16-bit PCM)
                        val bufferCapacitySamples = inputBuffer.capacity() / 2
                        val remainingSamples = audioData.samples.size - audioInputOffset
                        val samplesToWrite = min(min(bufferCapacitySamples, audioInputBufferSize), remainingSamples)

                        // Safety check and logging
                        if (bufferCapacitySamples < audioInputBufferSize) {
                            Log.w(TAG, "Audio input buffer capacity ($bufferCapacitySamples samples, ${inputBuffer.capacity()} bytes) " +
                                    "is smaller than requested ($audioInputBufferSize samples). Using buffer capacity.")
                        }

                        if (samplesToWrite > 0) {
                            // Verify we won't overflow (defensive check)
                            val bytesNeeded = samplesToWrite * 2
                            if (bytesNeeded > inputBuffer.capacity()) {
                                val errorMsg = "Audio buffer overflow prevented: need $bytesNeeded bytes but capacity is ${inputBuffer.capacity()} bytes"
                                Log.e(TAG, errorMsg)
                                throw RuntimeException(errorMsg)
                            }

                            for (i in 0 until samplesToWrite) {
                                inputBuffer.putShort(audioData.samples[audioInputOffset + i])
                            }
                            inputBuffer.flip()

                            val presentationTimeUs = (audioInputOffset * 1_000_000L) / (audioData.sampleRate * audioData.channelCount)
                            audioEncoder.queueInputBuffer(inputBufferId, 0, samplesToWrite * 2, presentationTimeUs, 0)

                            audioInputOffset += samplesToWrite
                        } else {
                            audioEncoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            audioInputDone = true
                            Log.d(TAG, "All audio samples submitted")
                        }
                    }
                }

                // Process video encoder output
                val videoBufferInfo = MediaCodec.BufferInfo()
                var videoOutputIndex = videoEncoder.dequeueOutputBuffer(videoBufferInfo, CODEC_TIMEOUT_US)

                when (videoOutputIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (videoTrackIndex >= 0) {
                            throw RuntimeException("Video format changed twice")
                        }
                        val outputFormat = videoEncoder.outputFormat
                        videoTrackIndex = muxer.addTrack(outputFormat)
                        Log.d(TAG, "Video track added (index: $videoTrackIndex)")

                        if ((audioEncoder == null || audioTrackIndex >= 0) && !muxerStarted) {
                            muxer.start()
                            muxerStarted = true
                            Log.d(TAG, "Muxer started")
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // No output available yet
                    }
                    else -> {
                        if (videoOutputIndex >= 0) {
                            val encodedData = videoEncoder.getOutputBuffer(videoOutputIndex)
                                ?: throw RuntimeException("Video encoder output buffer was null")

                            if (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                videoBufferInfo.size = 0
                            }

                            if (videoBufferInfo.size != 0 && muxerStarted) {
                                encodedData.position(videoBufferInfo.offset)
                                encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size)
                                muxer.writeSampleData(videoTrackIndex, encodedData, videoBufferInfo)
                            }

                            videoEncoder.releaseOutputBuffer(videoOutputIndex, false)

                            if (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                Log.d(TAG, "Video encoding complete")
                                if (audioEncoder == null) {
                                    break
                                }
                            }
                        }
                    }
                }

                // Process audio encoder output (same as classic slideshow)
                if (audioEncoder != null) {
                    val audioBufferInfo = MediaCodec.BufferInfo()
                    var audioOutputIndex = audioEncoder.dequeueOutputBuffer(audioBufferInfo, CODEC_TIMEOUT_US)

                    when (audioOutputIndex) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (audioTrackIndex >= 0) {
                                throw RuntimeException("Audio format changed twice")
                            }
                            val outputFormat = audioEncoder.outputFormat
                            audioTrackIndex = muxer.addTrack(outputFormat)
                            Log.d(TAG, "Audio track added (index: $audioTrackIndex)")

                            if (videoTrackIndex >= 0 && !muxerStarted) {
                                muxer.start()
                                muxerStarted = true
                                Log.d(TAG, "Muxer started")
                            }
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // No output available yet
                        }
                        else -> {
                            if (audioOutputIndex >= 0) {
                                val encodedData = audioEncoder.getOutputBuffer(audioOutputIndex)
                                    ?: throw RuntimeException("Audio encoder output buffer was null")

                                if (audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                    audioBufferInfo.size = 0
                                }

                                if (audioBufferInfo.size != 0 && muxerStarted) {
                                    encodedData.position(audioBufferInfo.offset)
                                    encodedData.limit(audioBufferInfo.offset + audioBufferInfo.size)
                                    muxer.writeSampleData(audioTrackIndex, encodedData, audioBufferInfo)
                                }

                                audioEncoder.releaseOutputBuffer(audioOutputIndex, false)

                                if (audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                    Log.d(TAG, "Audio encoding complete")
                                    if (videoInputDone && (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0)) {
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "4-tile encoding finished successfully")
            return TrackIndices(videoTrackIndex, audioTrackIndex, muxerStarted)
        } finally {
            eglHelper.release()
            videoEncoder.stop()
            videoEncoder.release()
            inputSurface.release()
            audioEncoder?.stop()
            audioEncoder?.release()
        }
    }

    /**
     * Calculate SFX event timings for 4-tile template
     * - Tiles: 4 times during tile appearance phase (sfx_hit)
     * - Color transition: 1 time when color transition starts (sfx_hit)
     */
    private fun calculateSfxEvents(
        imagePaths: List<String>,
        framesPerImage: Int,
        grayscaleEndFrame: Int,
        tilesEndFrame: Int
    ): List<AudioMixer.SfxEvent> {
        val events = mutableListOf<AudioMixer.SfxEvent>()
        val tilesPhaseFrames = tilesEndFrame - grayscaleEndFrame
        val framesPerTile = tilesPhaseFrames / 4

        for (imageIndex in imagePaths.indices) {
            val imageStartTimeMs = (imageIndex * framesPerImage * 1000L) / VIDEO_FPS

            // Tile 1-4 SFX timings
            for (tileIndex in 0..3) {
                val tileFrame = grayscaleEndFrame + (tileIndex * framesPerTile)
                val tileTimeMs = imageStartTimeMs + (tileFrame * 1000L) / VIDEO_FPS
                events.add(AudioMixer.SfxEvent("sfx_hit", tileTimeMs))
                Log.d(TAG, "SFX scheduled: Tile ${tileIndex + 1} at ${tileTimeMs}ms")
            }

            // Color transition SFX (5th SFX)
            val colorTransitionTimeMs = imageStartTimeMs + (tilesEndFrame * 1000L) / VIDEO_FPS
            events.add(AudioMixer.SfxEvent("sfx_hit", colorTransitionTimeMs))
            Log.d(TAG, "SFX scheduled: Color transition at ${colorTransitionTimeMs}ms")
        }

        return events
    }

    /**
     * Generate a single frame for 4-tile template
     *
     * @param bitmap Source bitmap (already scaled to VIDEO_WIDTH x VIDEO_HEIGHT)
     * @param localFrame Frame number within this image (0-based)
     * @param grayscaleEndFrame End of grayscale phase
     * @param tilesEndFrame End of tiles phase (INSTANT color transition + start of punch zoom)
     * @param colorEndFrame End of color transition (unused, kept for compatibility)
     * @param framesPerImage Total frames for this image
     * @return Rendered frame bitmap
     */
    private fun generateFourTileFrame(
        bitmap: Bitmap,
        localFrame: Int,
        grayscaleEndFrame: Int,
        tilesEndFrame: Int,
        colorEndFrame: Int,
        framesPerImage: Int
    ): Bitmap {
        // Determine timeline phase
        val isGrayscalePhase = localFrame < grayscaleEndFrame
        val isTilesPhase = localFrame >= grayscaleEndFrame && localFrame < tilesEndFrame
        val isPunchZoomPhase = localFrame >= tilesEndFrame

        // Calculate parameters based on phase
        val grayscale: Float
        val tilesVisible: Int  // 0-4
        val zoomScale: Float

        when {
            isGrayscalePhase -> {
                // Full grayscale, no tiles, no zoom
                grayscale = 1.0f
                tilesVisible = 0
                zoomScale = 1.0f
            }
            isTilesPhase -> {
                // Full grayscale, tiles appearing sequentially
                val tilesPhaseFrames = tilesEndFrame - grayscaleEndFrame
                val framesPerTile = tilesPhaseFrames / 4
                val progress = localFrame - grayscaleEndFrame
                tilesVisible = min((progress / framesPerTile) + 1, 4)
                grayscale = 1.0f
                zoomScale = 1.0f
            }
            isPunchZoomPhase -> {
                // INSTANT color change + PUNCH zoom
                // Color: step change at tilesEndFrame (no interpolation)
                grayscale = 0.0f  // Full color instantly
                tilesVisible = 4

                // Punch zoom with overshoot and settle
                // localT = time in seconds since the hit moment (tilesEndFrame)
                val localT = (localFrame - tilesEndFrame).toFloat() / VIDEO_FPS

                // Option B: Overshoot and settle
                // 0.0 - 0.08s: 1.0 → 1.35 (overshoot, very fast)
                // 0.08 - 0.20s: 1.35 → 1.30 (settle, slow)
                // 0.20s+: stay at 1.30
                zoomScale = when {
                    localT < 0.08f -> {
                        // Fast overshoot phase
                        val p = localT / 0.08f
                        1.0f + 0.35f * p  // 1.0 → 1.35
                    }
                    localT < 0.20f -> {
                        // Slow settle phase
                        val p = (localT - 0.08f) / 0.12f
                        1.35f - 0.05f * p  // 1.35 → 1.30
                    }
                    else -> {
                        1.30f  // Final scale
                    }
                }
            }
            else -> {
                grayscale = 0.0f
                tilesVisible = 4
                zoomScale = 1.0f
            }
        }

        // Create output bitmap
        val output = Bitmap.createBitmap(VIDEO_WIDTH, VIDEO_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)

        // Apply zoom transform if needed
        if (zoomScale > 1.0f) {
            canvas.save()
            canvas.scale(zoomScale, zoomScale, VIDEO_WIDTH / 2f, VIDEO_HEIGHT / 2f)
        }

        // Render based on tiles visible
        if (tilesVisible == 0) {
            // Just full grayscale image
            val grayscaleBitmap = applyGrayscale(bitmap, grayscale)
            canvas.drawBitmap(grayscaleBitmap, 0f, 0f, null)
            grayscaleBitmap.recycle()
        } else {
            // Render 2x2 grid with tiles
            val tileWidth = VIDEO_WIDTH / 2
            val tileHeight = VIDEO_HEIGHT / 2

            // MODIFIED: Tile reveal order changed to top-right, bottom-left, top-left, bottom-right
            // Each entry: (screen_x, screen_y, src_x_index, src_y_index)
            val tileConfigs = arrayOf(
                TileConfig(tileWidth, 0, 1, 0),            // Tile 1: top-right
                TileConfig(0, tileHeight, 0, 1),           // Tile 2: bottom-left
                TileConfig(0, 0, 0, 0),                    // Tile 3: top-left
                TileConfig(tileWidth, tileHeight, 1, 1)    // Tile 4: bottom-right
            )

            for (i in 0 until tilesVisible) {
                val config = tileConfigs[i]

                // Create tile bitmap (crop and scale)
                val srcX = config.srcXIndex * (bitmap.width / 2)
                val srcY = config.srcYIndex * (bitmap.height / 2)
                val srcWidth = bitmap.width / 2
                val srcHeight = bitmap.height / 2

                val tileBitmap = Bitmap.createBitmap(bitmap, srcX, srcY, srcWidth, srcHeight)
                val scaledTile = Bitmap.createScaledBitmap(tileBitmap, tileWidth, tileHeight, true)
                tileBitmap.recycle()

                // Apply grayscale if needed
                val finalTile = if (grayscale > 0) {
                    val gray = applyGrayscale(scaledTile, grayscale)
                    scaledTile.recycle()
                    gray
                } else {
                    scaledTile
                }

                canvas.drawBitmap(finalTile, config.x.toFloat(), config.y.toFloat(), null)
                finalTile.recycle()
            }
        }

        if (zoomScale > 1.0f) {
            canvas.restore()
        }

        return output
    }

    /**
     * Apply grayscale effect to bitmap
     *
     * @param source Source bitmap
     * @param amount Amount of grayscale (0.0 = color, 1.0 = full grayscale)
     * @return New bitmap with grayscale applied
     */
    private fun applyGrayscale(source: Bitmap, amount: Float): Bitmap {
        if (amount <= 0) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }

        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint()
        if (amount >= 1.0f) {
            // Full grayscale
            val colorMatrix = android.graphics.ColorMatrix().apply {
                setSaturation(0f)
            }
            paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        } else {
            // Partial grayscale (interpolate)
            val colorMatrix = android.graphics.ColorMatrix().apply {
                setSaturation(1.0f - amount)
            }
            paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        }

        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /**
     * Render 4-tile intro template with full timeline
     *
     * Timeline per image (SCENE_DURATION = 2.5s):
     * - 0.0 ~ 0.2 * SCENE_DURATION (0.0~0.5s): Grayscale fullscreen
     * - 0.2 ~ 0.6 * SCENE_DURATION (0.5~1.5s): 4 tiles appear sequentially
     *   - Each tile triggers SFX (4 times total)
     * - 0.6 ~ 0.8 * SCENE_DURATION (1.5~2.0s): Grayscale→Color transition + SFX (5th)
     * - 0.8 ~ 1.0 * SCENE_DURATION (2.0~2.5s): Color zoom in
     */
    private fun renderFourTileIntro(
        imagePaths: List<String>,
        outputPath: String,
        musicTrackName: String
    ): String {
        Log.d(TAG, "Rendering 4-tile intro template (FULL IMPLEMENTATION)")

        // Timeline constants
        val sceneDurationSec = 2.5f  // Total time per image
        val framesPerImage = (sceneDurationSec * VIDEO_FPS).toInt()  // 75 frames
        val totalFrames = imagePaths.size * framesPerImage
        val videoDurationUs = (totalFrames * 1_000_000L) / VIDEO_FPS

        Log.d(TAG, "4-tile video: $totalFrames frames (${videoDurationUs / 1_000_000.0}s), ${framesPerImage} frames/image")

        // Timeline boundaries (as frame indices within each image)
        // MODIFIED: Start directly in 4-split mode (no fullscreen grayscale phase)
        val grayscaleEndFrame = 0  // Start with tiles immediately
        val tilesEndFrame = (framesPerImage * 0.4f).toInt()      // ~30 frames (1.5x faster: 45/1.5=30)
        val colorEndFrame = (framesPerImage * 0.8f).toInt()      // ~60 frames (unchanged)
        // Zoom continues until framesPerImage (~75 frames)

        Log.d(TAG, "Timeline: grayscale=0-$grayscaleEndFrame, tiles=$grayscaleEndFrame-$tilesEndFrame (1.5x faster), " +
                "color=$tilesEndFrame-$colorEndFrame, zoom=$colorEndFrame-$framesPerImage")

        // Check for BGM file
        val bgmPath = getBgmFilePath(musicTrackName)
        val hasBgm = bgmPath != null && File(bgmPath).exists()
        if (hasBgm) {
            Log.d(TAG, "BGM file found: $bgmPath")
        } else {
            Log.d(TAG, "No BGM file found, rendering without audio")
        }

        // Calculate SFX event timings for offline mixing
        val sfxEvents = calculateSfxEvents(imagePaths, framesPerImage, grayscaleEndFrame, tilesEndFrame)
        Log.d(TAG, "Calculated ${sfxEvents.size} SFX events for offline mixing")

        // Load and prepare images
        val bitmaps = loadAndPrepareImages(imagePaths)

        try {
            // Setup MediaMuxer
            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Encode with 4-tile specific logic
            val trackIndices = encodeFourTileVideo(
                muxer = muxer,
                bitmaps = bitmaps,
                totalFrames = totalFrames,
                framesPerImage = framesPerImage,
                grayscaleEndFrame = grayscaleEndFrame,
                tilesEndFrame = tilesEndFrame,
                colorEndFrame = colorEndFrame,
                videoDurationUs = videoDurationUs,
                bgmPath = bgmPath,
                sfxEvents = sfxEvents
            )

            // Finalize muxer
            if (trackIndices.muxerStarted) {
                muxer.stop()
            }
            muxer.release()

            Log.d(TAG, "4-tile video encoding complete: $outputPath")

            // Cleanup
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
     * Configuration for a single tile in the 4-tile layout
     * @param x Screen X position (in pixels)
     * @param y Screen Y position (in pixels)
     * @param srcXIndex Source image X index (0 = left half, 1 = right half)
     * @param srcYIndex Source image Y index (0 = top half, 1 = bottom half)
     */
    private data class TileConfig(
        val x: Int,
        val y: Int,
        val srcXIndex: Int,
        val srcYIndex: Int
    )

    // ============================================================================
    // BEFORE & AFTER TEMPLATE
    // ============================================================================

    /**
     * Render Before & After template
     *
     * This template shows exactly 2 images:
     * - Image 0: "Before" (interior before remodeling)
     * - Image 1: "After" (interior after remodeling)
     *
     * Audio timeline:
     * - sfx_before.mp3 plays from t=0 at 1.3x tempo with pitch correction (sounds faster but same voice)
     * - sfx_after.mp3 plays from t=BEFORE_HOLD_MS at normal speed
     *
     * Visual timeline:
     * - 0.0 ~ BEFORE_HOLD_MS: Show Before image + "Before" text (static)
     * - BEFORE_HOLD_MS (instant cut): Show After image + "After" text with flip animation
     * - BEFORE_HOLD_MS ~ end: After image with subtle zoom-in
     *
     * @param imagePaths List of exactly 2 image paths [before, after]
     * @param outputPath Output video path
     */
    private fun renderBeforeAfter(
        imagePaths: List<String>,
        outputPath: String
    ): String {
        Log.d(TAG, "Rendering Before & After template")

        // Validate exactly 2 images
        if (imagePaths.size != 2) {
            throw IllegalArgumentException("Before & After template requires exactly 2 images, got ${imagePaths.size}")
        }

        // Calculate video parameters
        val totalDurationMs = BEFORE_HOLD_MS + AFTER_HOLD_MS
        val totalFrames = ((totalDurationMs * VIDEO_FPS) / 1000).toInt()
        val videoDurationUs = totalDurationMs * 1000L

        Log.d(TAG, "Video duration: ${totalDurationMs}ms ($totalFrames frames)")

        // ============================================================================
        // 1. VIDEO SEGMENT TIMELINE
        // ============================================================================
        val segments = listOf(
            VideoSegment(
                imageIndex = 0,
                startMs = 0,
                endMs = BEFORE_HOLD_MS,
                zoomFrom = 1.0f,
                zoomTo = 1.0f  // No zoom on before image
            ),
            VideoSegment(
                imageIndex = 1,
                startMs = BEFORE_HOLD_MS,
                endMs = BEFORE_HOLD_MS + AFTER_HOLD_MS,
                zoomFrom = 1.0f,
                zoomTo = AFTER_ZOOM_SCALE  // Gradual zoom on after image
            )
        )

        // Validate segments
        segments.forEachIndexed { index, segment ->
            if (!segment.validate(totalDurationMs)) {
                throw IllegalStateException("Invalid segment $index: startMs=${segment.startMs}, endMs=${segment.endMs}, totalDurationMs=$totalDurationMs")
            }
        }

        // ============================================================================
        // 2. AUDIO TIMELINE
        // ============================================================================
        val beforeMp3Path = getAudioFilePath("sfx_before.mp3")
        val afterMp3Path = getAudioFilePath("sfx_after.mp3")

        // Validate audio files
        if (beforeMp3Path == null || !File(beforeMp3Path).exists()) {
            throw IllegalStateException("sfx_before.mp3 not found in res/raw or external storage")
        }
        if (afterMp3Path == null || !File(afterMp3Path).exists()) {
            throw IllegalStateException("sfx_after.mp3 not found in res/raw or external storage")
        }

        val audioEvents = listOf(
            AudioEvent(
                assetName = "sfx_before.mp3",
                filePath = beforeMp3Path,
                startMs = 0,
                maxDurationMs = BEFORE_HOLD_MS,
                speed = 1.3f,  // Play before at 1.3x tempo (30% faster)
                pitchCorrection = true  // Preserve original pitch despite speed change
            ),
            AudioEvent(
                assetName = "sfx_after.mp3",
                filePath = afterMp3Path,
                startMs = BEFORE_HOLD_MS,
                maxDurationMs = AFTER_HOLD_MS
            )
        )

        // Validate audio events
        audioEvents.forEach { event ->
            if (!event.validate(totalDurationMs)) {
                throw IllegalStateException("Invalid audio event ${event.assetName}: startMs=${event.startMs}, totalDurationMs=$totalDurationMs")
            }
        }

        val textFlipFrames = ((TEXT_FLIP_MS * VIDEO_FPS) / 1000).toInt()

        // Load and prepare images
        val bitmaps = loadAndPrepareImages(imagePaths)

        try {
            // Setup MediaMuxer
            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Encode with Before & After specific logic
            val trackIndices = encodeBeforeAfterVideo(
                muxer = muxer,
                bitmaps = bitmaps,
                segments = segments,
                totalFrames = totalFrames,
                textFlipFrames = textFlipFrames,
                videoDurationUs = videoDurationUs,
                audioEvents = audioEvents,
                totalDurationMs = totalDurationMs
            )

            // Finalize muxer
            if (trackIndices.muxerStarted) {
                muxer.stop()
            }
            muxer.release()

            Log.d(TAG, "Before & After video encoding complete")

            // Cleanup
            bitmaps.forEach { it.recycle() }

            return outputPath
        } catch (e: Exception) {
            Log.e(TAG, "Before & After encoding failed: ${e.message}", e)
            // Cleanup on error
            bitmaps.forEach { it.recycle() }
            File(outputPath).delete()
            throw RuntimeException("Video encoding failed: ${e.message}", e)
        }
    }

    /**
     * Get audio file path from external storage (or copy from res/raw if needed)
     * Similar to getBgmFilePath but for any audio file
     */
    private fun getAudioFilePath(audioFileName: String): String? {
        return try {
            val externalFilesDir = context.getExternalFilesDir(null)
            if (externalFilesDir != null) {
                val bgmDir = File(externalFilesDir, "bgm")
                if (!bgmDir.exists()) {
                    bgmDir.mkdirs()
                }

                val audioFile = File(bgmDir, audioFileName)

                // If audio doesn't exist in external storage, try to copy from res/raw
                if (!audioFile.exists()) {
                    Log.d(TAG, "Audio not found in external storage, checking res/raw...")
                    copyBgmFromResources(audioFile, audioFileName)
                }

                audioFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio file path: ${e.message}")
            null
        }
    }

    /**
     * Encode Before & After video with audio mixing using segment-based timeline
     */
    private fun encodeBeforeAfterVideo(
        muxer: MediaMuxer,
        bitmaps: List<Bitmap>,
        segments: List<VideoSegment>,
        totalFrames: Int,
        textFlipFrames: Int,
        videoDurationUs: Long,
        audioEvents: List<AudioEvent>,
        totalDurationMs: Long
    ): TrackIndices {
        Log.d(TAG, "Encoding Before & After video with ${totalFrames} frames...")

        // Configure video format
        val videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_I_FRAME_INTERVAL)
        }

        // Create and configure video encoder
        val videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE)
        videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = videoEncoder.createInputSurface()
        videoEncoder.start()

        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var muxerStarted = false

        // Mix audio offline using the audio events timeline
        val audioData = try {
            Log.d(TAG, "Mixing Before & After audio timeline...")
            mixBeforeAfterAudioFromEvents(
                audioEvents = audioEvents,
                totalDurationMs = totalDurationMs,
                videoDurationUs = videoDurationUs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mix audio: ${e.message}", e)
            e.printStackTrace()
            null
        }

        // Create audio encoder if we have samples
        val audioEncoder = if (audioData != null && audioData.samples.isNotEmpty()) {
            Log.d(TAG, "Creating audio encoder")
            val audioFormat = MediaFormat.createAudioFormat(
                AUDIO_MIME_TYPE,
                audioData.sampleRate,
                audioData.channelCount
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
                setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            }
            MediaCodec.createEncoderByType(AUDIO_MIME_TYPE).apply {
                configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
        } else {
            Log.d(TAG, "No audio encoder (no audio)")
            null
        }

        // Setup EGL for rendering
        val eglHelper = EglHelper()
        eglHelper.setup(inputSurface)

        try {
            var frameIndex = 0
            var videoInputDone = false
            var audioInputDone = audioEncoder == null
            var audioInputOffset = 0
            val audioInputBufferSize = 2048

            // Main encoding loop
            while (!videoInputDone || !audioInputDone ||
                videoTrackIndex < 0 || (audioEncoder != null && audioTrackIndex < 0)) {

                // Feed video frames
                if (!videoInputDone && frameIndex < totalFrames) {
                    // Generate frame using segment-based approach
                    val frameBitmap = generateBeforeAfterFrameFromSegments(
                        bitmaps = bitmaps,
                        segments = segments,
                        frameIndex = frameIndex,
                        totalFrames = totalFrames,
                        textFlipFrames = textFlipFrames,
                        totalDurationMs = totalDurationMs
                    )

                    // Calculate presentation time using consistent formula
                    // This ensures strictly increasing timestamps
                    val presentationTimeNs = toPresentationTimeNs(frameIndex.toLong())

                    // Render to surface
                    eglHelper.drawFrame(frameBitmap, presentationTimeNs)
                    frameBitmap.recycle()

                    frameIndex++

                    if (frameIndex >= totalFrames) {
                        videoEncoder.signalEndOfInputStream()
                        videoInputDone = true
                        Log.d(TAG, "All video frames submitted ($frameIndex frames)")
                    }

                    if (frameIndex % 30 == 0) {
                        Log.d(TAG, "Submitted video frame $frameIndex/$totalFrames")
                    }
                }

                // Feed audio samples
                if (audioEncoder != null && audioData != null && !audioInputDone) {
                    val inputBufferId = audioEncoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputBufferId >= 0) {
                        val inputBuffer = audioEncoder.getInputBuffer(inputBufferId)!!
                        inputBuffer.clear()

                        // Calculate max samples that can fit in buffer (each sample is 2 bytes for 16-bit PCM)
                        val bufferCapacitySamples = inputBuffer.capacity() / 2
                        val remainingSamples = audioData.samples.size - audioInputOffset
                        val samplesToWrite = min(min(bufferCapacitySamples, audioInputBufferSize), remainingSamples)

                        // Safety check and logging
                        if (bufferCapacitySamples < audioInputBufferSize) {
                            Log.w(TAG, "Audio input buffer capacity ($bufferCapacitySamples samples, ${inputBuffer.capacity()} bytes) " +
                                    "is smaller than requested ($audioInputBufferSize samples). Using buffer capacity.")
                        }

                        if (samplesToWrite > 0) {
                            // Verify we won't overflow (defensive check)
                            val bytesNeeded = samplesToWrite * 2
                            if (bytesNeeded > inputBuffer.capacity()) {
                                val errorMsg = "Audio buffer overflow prevented: need $bytesNeeded bytes but capacity is ${inputBuffer.capacity()} bytes"
                                Log.e(TAG, errorMsg)
                                throw RuntimeException(errorMsg)
                            }

                            for (i in 0 until samplesToWrite) {
                                inputBuffer.putShort(audioData.samples[audioInputOffset + i])
                            }
                            inputBuffer.flip()

                            val presentationTimeUs = (audioInputOffset * 1_000_000L) / (audioData.sampleRate * audioData.channelCount)
                            audioEncoder.queueInputBuffer(inputBufferId, 0, samplesToWrite * 2, presentationTimeUs, 0)

                            audioInputOffset += samplesToWrite
                        } else {
                            audioEncoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            audioInputDone = true
                            Log.d(TAG, "All audio samples submitted")
                        }
                    }
                }

                // Process video encoder output
                val videoBufferInfo = MediaCodec.BufferInfo()
                var videoOutputIndex = videoEncoder.dequeueOutputBuffer(videoBufferInfo, CODEC_TIMEOUT_US)

                when (videoOutputIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (videoTrackIndex >= 0) {
                            throw RuntimeException("Video format changed twice")
                        }
                        val outputFormat = videoEncoder.outputFormat
                        videoTrackIndex = muxer.addTrack(outputFormat)
                        Log.d(TAG, "Video track added (index: $videoTrackIndex)")

                        if ((audioEncoder == null || audioTrackIndex >= 0) && !muxerStarted) {
                            muxer.start()
                            muxerStarted = true
                            Log.d(TAG, "Muxer started")
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // No output available yet
                    }
                    else -> {
                        if (videoOutputIndex >= 0) {
                            val encodedData = videoEncoder.getOutputBuffer(videoOutputIndex)
                                ?: throw RuntimeException("Video encoder output buffer was null")

                            if (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                videoBufferInfo.size = 0
                            }

                            if (videoBufferInfo.size != 0 && muxerStarted) {
                                encodedData.position(videoBufferInfo.offset)
                                encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size)
                                muxer.writeSampleData(videoTrackIndex, encodedData, videoBufferInfo)
                            }

                            videoEncoder.releaseOutputBuffer(videoOutputIndex, false)

                            if (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                Log.d(TAG, "Video encoding complete")
                                if (audioEncoder == null) {
                                    break
                                }
                            }
                        }
                    }
                }

                // Process audio encoder output
                if (audioEncoder != null) {
                    val audioBufferInfo = MediaCodec.BufferInfo()
                    var audioOutputIndex = audioEncoder.dequeueOutputBuffer(audioBufferInfo, CODEC_TIMEOUT_US)

                    when (audioOutputIndex) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (audioTrackIndex >= 0) {
                                throw RuntimeException("Audio format changed twice")
                            }
                            val outputFormat = audioEncoder.outputFormat
                            audioTrackIndex = muxer.addTrack(outputFormat)
                            Log.d(TAG, "Audio track added (index: $audioTrackIndex)")

                            if (videoTrackIndex >= 0 && !muxerStarted) {
                                muxer.start()
                                muxerStarted = true
                                Log.d(TAG, "Muxer started")
                            }
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // No output available yet
                        }
                        else -> {
                            if (audioOutputIndex >= 0) {
                                val encodedData = audioEncoder.getOutputBuffer(audioOutputIndex)
                                    ?: throw RuntimeException("Audio encoder output buffer was null")

                                if (audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                    audioBufferInfo.size = 0
                                }

                                if (audioBufferInfo.size != 0 && muxerStarted) {
                                    encodedData.position(audioBufferInfo.offset)
                                    encodedData.limit(audioBufferInfo.offset + audioBufferInfo.size)
                                    muxer.writeSampleData(audioTrackIndex, encodedData, audioBufferInfo)
                                }

                                audioEncoder.releaseOutputBuffer(audioOutputIndex, false)

                                if (audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                    Log.d(TAG, "Audio encoding complete")
                                    if (videoInputDone && (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0)) {
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "Before & After encoding finished successfully")
            return TrackIndices(videoTrackIndex, audioTrackIndex, muxerStarted)
        } finally {
            eglHelper.release()
            videoEncoder.stop()
            videoEncoder.release()
            inputSurface.release()
            audioEncoder?.stop()
            audioEncoder?.release()
        }
    }

    /**
     * Convert frame index to presentation timestamp in nanoseconds
     * Ensures strictly increasing timestamps
     */
    private fun toPresentationTimeNs(frameIndex: Long): Long {
        return (frameIndex * 1_000_000_000L) / VIDEO_FPS
    }

    /**
     * Mix audio for Before & After template using audio events timeline
     */
    private fun mixBeforeAfterAudioFromEvents(
        audioEvents: List<AudioEvent>,
        totalDurationMs: Long,
        videoDurationUs: Long
    ): AudioData? {
        if (audioEvents.isEmpty()) {
            Log.w(TAG, "No audio events to mix")
            return null
        }

        // Extract the first audio file to get the sample rate and channel count
        val firstEvent = audioEvents[0]
        val baseAudioData = extractAudioSamplesFromFile(firstEvent.filePath, videoDurationUs)

        // Create output buffer sized for the full video duration
        val targetSampleCount = ((videoDurationUs / 1_000_000.0) * baseAudioData.sampleRate * baseAudioData.channelCount).toInt()
        val mixedSamples = ShortArray(targetSampleCount)

        Log.d(TAG, "Audio mixing: targetSampleCount=$targetSampleCount, sampleRate=${baseAudioData.sampleRate}, channels=${baseAudioData.channelCount}")

        // Mix each audio event into the output buffer
        audioEvents.forEach { event ->
            try {
                var audioData = extractAudioSamplesFromFile(event.filePath, videoDurationUs)

                // Apply speed change if needed
                if (event.speed != 1.0f) {
                    audioData = if (event.pitchCorrection) {
                        // Use pitch-preserving time-stretch (WSOLA algorithm)
                        timeStretchWithPitchCorrection(audioData, event.speed)
                    } else {
                        // Use simple resampling (changes both tempo and pitch)
                        resampleAudioForSpeed(audioData, event.speed)
                    }
                    Log.d(TAG, "Resampled ${event.assetName} at ${event.speed}x speed (pitchCorrection=${event.pitchCorrection}): ${audioData.samples.size} samples")
                }

                // Calculate start sample position
                val startSample = ((event.startMs / 1000.0) * audioData.sampleRate * audioData.channelCount).toInt()

                // Calculate max samples to mix (clamped by maxDurationMs and remaining buffer)
                val maxDurationSamples = if (event.maxDurationMs == Long.MAX_VALUE) {
                    audioData.samples.size
                } else {
                    ((event.maxDurationMs / 1000.0) * audioData.sampleRate * audioData.channelCount).toInt()
                }

                val availableSamples = minOf(
                    audioData.samples.size,
                    maxDurationSamples,
                    mixedSamples.size - startSample
                ).coerceAtLeast(0)

                if (availableSamples > 0 && startSample < mixedSamples.size) {
                    mixSfxIntoBuffer(mixedSamples, audioData.samples, startSample, availableSamples)
                    Log.d(TAG, "Mixed ${event.assetName}: startMs=${event.startMs}, startSample=$startSample, samples=$availableSamples")
                } else {
                    Log.w(TAG, "Skipped ${event.assetName}: startSample=$startSample >= bufferSize=${mixedSamples.size}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mix ${event.assetName}: ${e.message}", e)
            }
        }

        return AudioData(mixedSamples, baseAudioData.sampleRate, baseAudioData.channelCount)
    }

    /**
     * Mix SFX samples into the main buffer at specified position
     * Clamps values to prevent clipping
     */
    private fun mixSfxIntoBuffer(
        mainBuffer: ShortArray,
        sfxSamples: ShortArray,
        startSample: Int,
        maxSamples: Int = Int.MAX_VALUE
    ) {
        val endSample = minOf(startSample + sfxSamples.size, mainBuffer.size, startSample + maxSamples)
        val sfxLength = (endSample - startSample).coerceAtLeast(0)

        for (i in 0 until sfxLength) {
            if (startSample + i < mainBuffer.size && i < sfxSamples.size) {
                // Mix by adding and clamping to Short range
                val mixed = mainBuffer[startSample + i].toInt() + sfxSamples[i].toInt()
                mainBuffer[startSample + i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
    }

    /**
     * Resample audio to achieve speed change
     *
     * @param audioData Original audio data
     * @param speed Speed multiplier (1.0 = normal, 1.3 = 30% faster, 0.8 = 20% slower)
     * @return Resampled audio data with adjusted length
     */
    private fun resampleAudioForSpeed(audioData: AudioData, speed: Float): AudioData {
        if (speed == 1.0f) return audioData

        val originalSamples = audioData.samples
        val channelCount = audioData.channelCount

        // Calculate new sample count (compressed for speed > 1.0, extended for speed < 1.0)
        val newSampleCount = (originalSamples.size / speed).toInt()
        val newSamples = ShortArray(newSampleCount)

        // Resample using linear interpolation
        for (i in 0 until newSampleCount step channelCount) {
            // Calculate source position (floating point)
            val srcPos = i * speed
            val srcIndex = srcPos.toInt()

            // Handle each channel separately
            for (ch in 0 until channelCount) {
                val targetIdx = i + ch
                if (targetIdx >= newSampleCount) break

                val srcIdx = srcIndex + ch
                if (srcIdx >= originalSamples.size - channelCount) {
                    // At the end, just copy the last sample
                    newSamples[targetIdx] = if (srcIdx < originalSamples.size) {
                        originalSamples[srcIdx]
                    } else {
                        originalSamples[originalSamples.size - channelCount + ch]
                    }
                } else {
                    // Linear interpolation between current and next sample
                    val frac = srcPos - srcIndex
                    val sample1 = originalSamples[srcIdx].toInt()
                    val sample2 = originalSamples[srcIdx + channelCount].toInt()
                    val interpolated = (sample1 + (sample2 - sample1) * frac).toInt()
                    newSamples[targetIdx] = interpolated.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
        }

        return AudioData(newSamples, audioData.sampleRate, audioData.channelCount)
    }

    /**
     * Time-stretch audio while preserving pitch using WSOLA algorithm
     * (Waveform Similarity Overlap-Add)
     *
     * @param audioData Original audio data
     * @param speed Speed multiplier (1.0 = normal, 1.3 = 30% faster, 0.8 = 20% slower)
     * @return Time-stretched audio with preserved pitch
     */
    private fun timeStretchWithPitchCorrection(audioData: AudioData, speed: Float): AudioData {
        if (speed == 1.0f) return audioData

        val originalSamples = audioData.samples
        val channelCount = audioData.channelCount
        val sampleRate = audioData.sampleRate

        // WSOLA parameters (tuned for speech at ~44.1kHz)
        val frameSize = (0.02 * sampleRate * channelCount).toInt()  // 20ms frames
        val hopSizeInput = (frameSize / speed).toInt()  // Input hop size
        val hopSizeOutput = frameSize  // Output hop size
        val searchRange = (0.01 * sampleRate * channelCount).toInt()  // 10ms search range

        // Calculate output size
        val newSampleCount = (originalSamples.size / speed).toInt()
        val newSamples = ShortArray(newSampleCount)

        var inputPos = 0
        var outputPos = 0

        // Hann window for smooth overlapping
        val window = FloatArray(frameSize) { i ->
            0.5f * (1.0f - kotlin.math.cos(2.0 * kotlin.math.PI * i / (frameSize - 1))).toFloat()
        }

        while (outputPos + frameSize < newSampleCount && inputPos + frameSize < originalSamples.size) {
            // Calculate natural input position
            val naturalInputPos = (outputPos * speed).toInt()

            // Search for best match around natural position
            var bestPos = naturalInputPos
            var bestCorrelation = Float.NEGATIVE_INFINITY

            val searchStart = maxOf(0, naturalInputPos - searchRange)
            val searchEnd = minOf(originalSamples.size - frameSize, naturalInputPos + searchRange)

            for (testPos in searchStart until searchEnd step channelCount) {
                // Calculate cross-correlation between frames
                var correlation = 0.0
                val overlapSize = minOf(frameSize, outputPos)  // Overlap with previous frame

                if (overlapSize > 0 && outputPos >= overlapSize) {
                    for (i in 0 until overlapSize) {
                        if (testPos + i < originalSamples.size && outputPos - overlapSize + i < newSamples.size) {
                            val s1 = originalSamples[testPos + i].toDouble()
                            val s2 = newSamples[outputPos - overlapSize + i].toDouble()
                            correlation += s1 * s2
                        }
                    }
                }

                if (correlation > bestCorrelation) {
                    bestCorrelation = correlation.toFloat()
                    bestPos = testPos
                }
            }

            // Copy frame with overlap-add
            for (i in 0 until frameSize) {
                if (bestPos + i < originalSamples.size && outputPos + i < newSamples.size) {
                    val sample = (originalSamples[bestPos + i] * window[i]).toInt()

                    // Overlap-add with existing samples
                    val mixed = newSamples[outputPos + i].toInt() + sample
                    newSamples[outputPos + i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }

            inputPos = bestPos + hopSizeInput
            outputPos += hopSizeOutput
        }

        return AudioData(newSamples, audioData.sampleRate, audioData.channelCount)
    }

    /**
     * Generate a single frame for Before & After template using segment-based timeline
     *
     * This function uses the segment timeline to determine which image to show
     * and applies text overlays based on the current segment.
     */
    private fun generateBeforeAfterFrameFromSegments(
        bitmaps: List<Bitmap>,
        segments: List<VideoSegment>,
        frameIndex: Int,
        totalFrames: Int,
        textFlipFrames: Int,
        totalDurationMs: Long
    ): Bitmap {
        // Calculate current time in milliseconds
        val currentTimeMs = (frameIndex * 1000L * totalDurationMs) / (totalFrames * 1000L)

        // Find which segment we're in
        var currentSegment: VideoSegment? = null
        for (segment in segments) {
            if (currentTimeMs >= segment.startMs && currentTimeMs < segment.endMs) {
                currentSegment = segment
                break
            }
        }

        // If we're past the last segment, use the last segment
        if (currentSegment == null && segments.isNotEmpty()) {
            currentSegment = segments.last()
        }

        if (currentSegment == null) {
            throw IllegalStateException("No segment found for frame $frameIndex at time ${currentTimeMs}ms")
        }

        // Create output bitmap
        val output = Bitmap.createBitmap(VIDEO_WIDTH, VIDEO_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)

        // Get the image for this segment
        val segmentBitmap = bitmaps[currentSegment.imageIndex]

        // Calculate zoom based on segment progress
        val segmentProgress = (currentTimeMs - currentSegment.startMs).toFloat() / currentSegment.durationMs.toFloat()
        val currentScale = currentSegment.zoomFrom + (currentSegment.zoomTo - currentSegment.zoomFrom) * segmentProgress

        // Apply zoom if needed
        if (currentScale != 1.0f) {
            canvas.save()
            canvas.scale(currentScale, currentScale, VIDEO_WIDTH / 2f, VIDEO_HEIGHT / 2f)
            canvas.drawBitmap(segmentBitmap, 0f, 0f, null)
            canvas.restore()
        } else {
            canvas.drawBitmap(segmentBitmap, 0f, 0f, null)
        }

        // Draw text overlay based on segment
        val isAfterSegment = currentSegment.imageIndex == 1
        if (isAfterSegment) {
            // Draw "After" text with flip animation at the start of segment
            val framesIntoSegment = frameIndex - ((currentSegment.startMs * VIDEO_FPS) / 1000).toInt()
            if (framesIntoSegment < textFlipFrames) {
                val flipProgress = framesIntoSegment.toFloat() / textFlipFrames.toFloat()
                drawText(canvas, "AFTER", flipProgress)
            } else {
                drawText(canvas, "AFTER", 1.0f)
            }
        } else {
            // Draw "Before" text
            drawText(canvas, "BEFORE", 1.0f)
        }

        return output
    }

    /**
     * Draw text overlay at top-center
     *
     * @param canvas Canvas to draw on
     * @param text Text to draw ("BEFORE" or "AFTER")
     * @param alpha Alpha for fade-in effect (0.0 = invisible, 1.0 = fully visible)
     */
    private fun drawText(canvas: Canvas, text: String, alpha: Float) {
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = TEXT_SIZE_BEFORE_AFTER_PX  // Use doubled text size for Before & After template
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        }

        // Draw text with stroke for readability
        val strokePaint = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = TEXT_STROKE_WIDTH
            color = Color.BLACK
        }

        val x = VIDEO_WIDTH / 2f
        val y = 200f // Top position with some padding

        // Draw stroke first, then fill
        canvas.drawText(text, x, y, strokePaint)
        canvas.drawText(text, x, y, paint)
    }
}
