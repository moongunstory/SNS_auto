package com.example.sns_auto

import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FlutterActivity() {
    private val CHANNEL = "sns_auto/video_encoder"
    private var methodChannel: MethodChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Register MethodChannel for video encoding
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        methodChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "renderSlideshow" -> {
                    try {
                        // Extract arguments
                        val imagePaths = call.argument<List<String>>("imagePaths")
                        val outputPath = call.argument<String>("outputPath")
                        val imageDurationMs = call.argument<Int>("imageDurationMs") ?: 1500
                        val transitionDurationMs = call.argument<Int>("transitionDurationMs") ?: 500

                        if (imagePaths == null || imagePaths.isEmpty()) {
                            result.error("INVALID_ARGS", "Image paths list is empty or null", null)
                            return@setMethodCallHandler
                        }

                        if (outputPath == null || outputPath.isEmpty()) {
                            result.error("INVALID_ARGS", "Output path is empty or null", null)
                            return@setMethodCallHandler
                        }

                        // Launch encoding on background thread
                        CoroutineScope(Dispatchers.Default).launch {
                            try {
                                val encoder = VideoEncoder(context)
                                val finalOutputPath = encoder.renderSlideshow(
                                    imagePaths = imagePaths,
                                    outputPath = outputPath,
                                    imageDurationMs = imageDurationMs,
                                    transitionDurationMs = transitionDurationMs
                                )

                                // Return success on main thread
                                withContext(Dispatchers.Main) {
                                    result.success(finalOutputPath)
                                }
                            } catch (e: Exception) {
                                // Return error on main thread
                                withContext(Dispatchers.Main) {
                                    result.error(
                                        "ENCODING_ERROR",
                                        "Video encoding failed: ${e.message}",
                                        e.stackTraceToString()
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        result.error("SETUP_ERROR", "Failed to start encoding: ${e.message}", null)
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    override fun onDestroy() {
        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
        super.onDestroy()
    }
}
