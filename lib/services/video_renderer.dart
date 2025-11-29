import 'dart:io';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as path;

import '../models/render_job.dart';
import '../config/constants.dart';
import '../utils/result.dart';
import '../models/template_model.dart';

/// Service for rendering videos using Android native APIs (MediaCodec/MediaMuxer)
///
/// This service takes a list of images and generates a vertical video (1080x1920)
/// suitable for social media shorts/reels using Android's native video encoding.
class VideoRenderer {
  static const MethodChannel _channel = MethodChannel('sns_auto/video_encoder');

  /// Render a video from images according to the template
  ///
  /// [job] - The render job containing images and template
  /// [onProgress] - Callback for progress updates (0.0 to 1.0)
  ///
  /// Returns the path to the rendered video file, or an error message.
  Future<Result<String, String>> render(
    RenderJob job,
    void Function(double progress) onProgress,
  ) async {
    try {
      print('[VideoRenderer] Starting render: $job');

      // Validate job
      if (!job.isValid()) {
        return const Error('Invalid render job: no images provided');
      }

      // Create output directory
      final outputDir = await _getOutputDirectory();
      final outputFileName = job.getOutputFileName();
      final outputPath = path.join(outputDir.path, outputFileName);

      print('[VideoRenderer] Output path: $outputPath');
      print('[VideoRenderer] Image count: ${job.imagePaths.length}');

      // Get BGM file path (copy from assets if needed)
      String bgmFilePath = '';
      final musicTrackName = job.musicTrackName ?? job.template.config.musicTrackName ?? '';

      if (musicTrackName.isNotEmpty) {
        // Find the track object to get the asset path
        final availableTracks = await MusicTrack.getAvailableBgmTracks();
        final selectedTrack = availableTracks.firstWhere(
          (track) => track.fileName == musicTrackName,
          orElse: () => availableTracks.first,
        );

        if (!selectedTrack.isNoMusic) {
          bgmFilePath = await _copyAssetToTempFile(selectedTrack.assetPath);
          print('[VideoRenderer] BGM copied to: $bgmFilePath');
        }
      }

      // Prepare arguments for native encoder
      final arguments = {
        'imagePaths': job.imagePaths,
        'outputPath': outputPath,
        'templateId': job.template.id,
        // Template configuration (using simple defaults for base template)
        'imageDurationMs': (job.template.config.imageDurationSeconds * 1000).toInt(),
        'transitionDurationMs': (job.template.config.transitionDurationSeconds * 1000).toInt(),
        // Pass absolute file path to BGM, or empty string for no music
        'musicTrackName': bgmFilePath,
      };

      print('[VideoRenderer] Calling native encoder...');

      // Start with initial progress
      onProgress(0.0);

      // Call native method with timeout
      // Note: For real progress updates, we could implement an EventChannel,
      // but for now we'll simulate progress and wait for completion
      _simulateProgress(onProgress, _estimateVideoDuration(job));

      final result = await _channel.invokeMethod<String>(
        'renderSlideshow',
        arguments,
      );

      if (result != null && result.isNotEmpty) {
        // Verify output file exists
        final outputFile = File(result);
        if (await outputFile.exists()) {
          print('[VideoRenderer] Render complete: $result');
          onProgress(1.0);
          return Success(result);
        } else {
          print('[VideoRenderer] Output file not found: $result');
          return const Error('Output file was not created');
        }
      } else {
        print('[VideoRenderer] Native method returned null or empty');
        return const Error('Video encoding failed: no output path returned');
      }
    } on PlatformException catch (e) {
      print('[VideoRenderer] PlatformException: ${e.code} - ${e.message}');
      return Error('Encoding error: ${e.message ?? e.code}');
    } catch (e) {
      print('[VideoRenderer] Exception during render: $e');
      return Error('Rendering error: $e');
    }
  }

  /// Get the output directory for rendered videos
  Future<Directory> _getOutputDirectory() async {
    final appDir = await getApplicationDocumentsDirectory();
    final videoDir = Directory(
      path.join(appDir.path, AppConstants.renderedVideosDirectoryName),
    );

    if (!await videoDir.exists()) {
      await videoDir.create(recursive: true);
    }

    return videoDir;
  }

  /// Simulate progress for rendering
  ///
  /// Since we're waiting for native encoding to complete, we simulate
  /// progress based on estimated duration.
  ///
  /// For more accurate progress, we could implement an EventChannel
  /// that receives periodic updates from the native encoder.
  Future<void> _simulateProgress(
    void Function(double progress) onProgress,
    double estimatedDurationSeconds,
  ) async {
    // Simulate progress over estimated duration
    // The actual encoding happens in native code, so this is just for UX
    final steps = 20;
    final delayMs = (estimatedDurationSeconds * 1000 / steps).round();

    for (int i = 1; i < steps; i++) {
      await Future.delayed(Duration(milliseconds: delayMs));
      // Progress goes from 0.0 to ~0.95, final 1.0 is set after verification
      onProgress((i / steps) * 0.95);
    }
  }

  /// Estimate the total video duration based on images and template
  ///
  /// Formula with crossfade transitions:
  /// - Each image shows for imageDuration seconds
  /// - Crossfade overlaps by transitionDuration seconds
  /// - Total = numImages * imageDuration - (numImages - 1) * transitionDuration
  double _estimateVideoDuration(RenderJob job) {
    final config = job.template.config;
    final numImages = job.imagePaths.length;
    final imageDuration = config.imageDurationSeconds;
    final transitionDuration = config.transitionDurationSeconds;

    // With overlapping transitions
    final totalDuration =
        (numImages * imageDuration) - ((numImages - 1) * transitionDuration);

    return totalDuration;
  }

  /// Copy a Flutter asset to a temporary file
  ///
  /// This is needed because the Android encoder needs an actual file path,
  /// but Flutter assets are embedded in the APK and need to be extracted first.
  ///
  /// [assetPath] - The asset path (e.g., "assets/audio/bgm/bgm_default.mp3")
  ///
  /// Returns the absolute path to the temporary file.
  Future<String> _copyAssetToTempFile(String assetPath) async {
    try {
      // Load asset data
      final ByteData data = await rootBundle.load(assetPath);
      final List<int> bytes = data.buffer.asUint8List();

      // Get temp directory
      final tempDir = await getTemporaryDirectory();

      // Extract filename from asset path
      final fileName = path.basename(assetPath);

      // Create temp file
      final tempFile = File(path.join(tempDir.path, 'bgm_$fileName'));

      // Write asset data to temp file
      await tempFile.writeAsBytes(bytes);

      print('[VideoRenderer] Asset copied: $assetPath -> ${tempFile.path}');

      return tempFile.path;
    } catch (e) {
      print('[VideoRenderer] Error copying asset to temp file: $e');
      rethrow;
    }
  }
}
