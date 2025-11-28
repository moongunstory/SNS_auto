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

        // Check for BGM file
        val bgmPath = getBgmFilePath()
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
     * Path: /Android/data/com.example.sns_auto/files/bgm/bgm_default.mp3
     *
     * If the file doesn't exist but exists in res/raw, it will be copied automatically.
     */
    private fun getBgmFilePath(): String? {
        return try {
            val externalFilesDir = context.getExternalFilesDir(null)
            if (externalFilesDir != null) {
                val bgmDir = File(externalFilesDir, "bgm")
                // Create directory if it doesn't exist
                if (!bgmDir.exists()) {
                    bgmDir.mkdirs()
                    Log.d(TAG, "Created BGM directory: ${bgmDir.absolutePath}")
                }

                val bgmFile = File(bgmDir, "bgm_default.mp3")

                // If BGM doesn't exist in external storage, try to copy from res/raw
                if (!bgmFile.exists()) {
                    Log.d(TAG, "BGM not found in external storage, checking res/raw...")
                    copyBgmFromResources(bgmFile)
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
     * Resource ID: R.raw.bgm_default
     */
    private fun copyBgmFromResources(destinationFile: File) {
        try {
            // Check if bgm_default exists in res/raw
            val resourceId = context.resources.getIdentifier("bgm_default", "raw", context.packageName)

            if (resourceId != 0) {
                Log.d(TAG, "Found BGM in res/raw, copying to external storage...")

                context.resources.openRawResource(resourceId).use { inputStream ->
                    FileOutputStream(destinationFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                Log.d(TAG, "BGM copied successfully: ${destinationFile.absolutePath}")
            } else {
                Log.d(TAG, "No BGM found in res/raw (R.raw.bgm_default not found)")
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
        val audioSamples = if (bgmPath != null && File(bgmPath).exists()) {
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
        val audioEncoder = if (audioSamples != null && audioSamples.isNotEmpty()) {
            Log.d(TAG, "Creating audio encoder with ${audioSamples.size} samples")
            val audioFormat = MediaFormat.createAudioFormat(
                AUDIO_MIME_TYPE,
                AUDIO_SAMPLE_RATE,
                AUDIO_CHANNELS
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
            }
            MediaCodec.createEncoderByType(AUDIO_MIME_TYPE).apply {
                configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
        } else {
            Log.d(TAG, "No audio encoder (no BGM)")
            null
        }

        try {
            var frameIndex = 0
            val frameIntervalUs = 1_000_000L / VIDEO_FPS
            var videoInputDone = false
            var audioInputDone = audioEncoder == null // If no audio, mark as done
            var audioInputOffset = 0
            val audioInputBufferSize = 2048

            // Main encoding loop
            while (!videoInputDone || !audioInputDone ||
                   videoTrackIndex < 0 || (audioEncoder != null && audioTrackIndex < 0)) {

                // Feed video frames
                if (!videoInputDone && frameIndex < totalFrames) {
                    val canvas = inputSurface.lockCanvas(null)
                    val frame = generateFrame(bitmaps, frameIndex, framesPerImage, crossfadeFrames)
                    canvas.drawBitmap(frame, 0f, 0f, null)
                    frame.recycle()
                    inputSurface.unlockCanvasAndPost(canvas)

                    frameIndex++

                    if (frameIndex >= totalFrames) {
                        videoEncoder.signalEndOfInputStream()
                        videoInputDone = true
                        Log.d(TAG, "All video frames submitted")
                    }

                    if (frameIndex % 30 == 0) {
                        Log.d(TAG, "Submitted video frame $frameIndex/$totalFrames")
                    }
                }

                // Feed audio samples
                if (audioEncoder != null && audioSamples != null && !audioInputDone) {
                    val inputBufferId = audioEncoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputBufferId >= 0) {
                        val inputBuffer = audioEncoder.getInputBuffer(inputBufferId)!!
                        inputBuffer.clear()

                        val samplesToWrite = min(audioInputBufferSize, audioSamples.size - audioInputOffset)
                        if (samplesToWrite > 0) {
                            val byteBuffer = ByteBuffer.allocate(samplesToWrite * 2)
                            for (i in 0 until samplesToWrite) {
                                byteBuffer.putShort(audioSamples[audioInputOffset + i])
                            }
                            inputBuffer.put(byteBuffer.array())

                            val presentationTimeUs = (audioInputOffset * 1_000_000L) / (AUDIO_SAMPLE_RATE * AUDIO_CHANNELS)
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
     * @param framesPerImage How many frames each image is shown (e.g., 45 for 1.5s at 30fps)
     * @param crossfadeFrames How many frames the crossfade lasts (e.g., 15 for 0.5s at 30fps)
     *
     * Timeline example with 3 images (framesPerImage=45, crossfadeFrames=15):
     * - Frames 0-29:   Image 0 only
     * - Frames 30-44:  Image 0 → Image 1 crossfade
     * - Frames 45-74:  Image 1 only
     * - Frames 75-89:  Image 1 → Image 2 crossfade
     * - Frames 90-119: Image 2 only
     *
     * Each image displays for 45 frames (1.5s), with 15-frame (0.5s) overlap
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
    private fun extractAudioSamplesFromFile(filePath: String, maxDurationUs: Long): ShortArray {
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

                        // Set position and limit based on bufferInfo
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

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

            Log.d(TAG, "Extracted ${samples.size} audio samples (${samples.size / (AUDIO_SAMPLE_RATE * AUDIO_CHANNELS)}s)")
            return samples.toShortArray()
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

}
