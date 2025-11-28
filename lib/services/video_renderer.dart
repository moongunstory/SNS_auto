import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as path;
import 'package:flutter_ffmpeg_lts_custom/flutter_ffmpeg.dart';

import '../models/render_job.dart';
import '../models/template_model.dart';
import '../config/app_config.dart';
import '../config/constants.dart';
import '../utils/result.dart';

/// Service for rendering videos using FFmpeg
///
/// This service takes a list of images and a template configuration,
/// and generates a vertical video (1080x1920) suitable for social media shorts/reels.
class VideoRenderer {
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
        return Error('Invalid render job: no images provided');
      }

      // Create output directory
      final outputDir = await _getOutputDirectory();
      final outputFileName = job.getOutputFileName();
      final outputPath = path.join(outputDir.path, outputFileName);

      print('[VideoRenderer] Output path: $outputPath');

      // Build FFmpeg command based on template
      final command = await _buildFFmpegCommand(
        imagePaths: job.imagePaths,
        template: job.template,
        outputPath: outputPath,
      );

      print('[VideoRenderer] FFmpeg command: $command');

      // Execute FFmpeg command with progress tracking
      final result = await _executeFFmpegCommand(
        command: command,
        onProgress: onProgress,
        estimatedDurationSeconds: _estimateVideoDuration(job),
      );

      if (result.isSuccess) {
        print('[VideoRenderer] Render complete: ${result.value}');
        return Success(result.value);
      } else {
        print('[VideoRenderer] Render failed: ${result.error}');
        return Error(result.error);
      }
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

  /// Build the FFmpeg command based on template and images
  ///
  /// TODO: Expand this to support:
  /// - Different transition types (fade, slide, zoom, etc.)
  /// - Background music
  /// - Text overlays
  /// - Ken Burns effect (zoom/pan)
  /// - Custom filters
  Future<String> _buildFFmpegCommand({
    required List<String> imagePaths,
    required TemplateModel template,
    required String outputPath,
  }) async {
    final config = template.config;

    // For now, we'll create a simple slideshow with fade transitions
    // Each image is displayed for config.imageDurationSeconds
    // Transitions last for config.transitionDurationSeconds

    // Calculate durations
    final imageDuration = config.imageDurationSeconds;
    final transitionDuration = config.transitionDurationSeconds;

    // Build input file list for FFmpeg concat demuxer
    final concatListFile = await _createConcatListFile(imagePaths);

    // TODO: Add more sophisticated rendering based on template.transitionType:
    // - TransitionType.fade: use xfade filter
    // - TransitionType.slide: use xfade with slide transitions
    // - TransitionType.zoom: use zoompan filter
    // - TransitionType.dissolve: use xfade with dissolve
    // - TransitionType.cut: simple concat without transitions

    // For now, we'll use a simple approach:
    // 1. Scale each image to target resolution (1080x1920)
    // 2. Apply fade transitions if configured
    // 3. Concat all images into a video

    // Simple command for slideshow (without transitions for MVP)
    // Each image is shown for imageDuration seconds
    final command = '-f concat -safe 0 -i "$concatListFile" '
        '-vf "scale=${AppConfig.videoWidth}:${AppConfig.videoHeight}:force_original_aspect_ratio=decrease,pad=${AppConfig.videoWidth}:${AppConfig.videoHeight}:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=${AppConfig.videoFrameRate}" '
        '-c:v ${AppConfig.videoCodec} '
        '-b:v ${AppConfig.videoBitrate} '
        '-pix_fmt yuv420p '
        '-y "$outputPath"';

    // TODO: Add background music:
    // If config.musicTrackName is provided:
    // -i "path/to/music.mp3" -c:a ${AppConstants.audioCodec} -b:a ${AppConstants.audioBitrate} -shortest

    return command;
  }

  /// Create a concat list file for FFmpeg
  ///
  /// This creates a text file listing all input images with durations.
  /// Format: file 'path' \n duration X
  Future<String> _createConcatListFile(List<String> imagePaths) async {
    final tempDir = await getTemporaryDirectory();
    final listFile = File(path.join(tempDir.path, 'concat_list_${DateTime.now().millisecondsSinceEpoch}.txt'));

    final buffer = StringBuffer();
    for (int i = 0; i < imagePaths.length; i++) {
      final imagePath = imagePaths[i];
      buffer.writeln("file '$imagePath'");
      // Set duration for all images except the last one
      if (i < imagePaths.length - 1) {
        buffer.writeln('duration ${AppConstants.defaultImageDurationSeconds}');
      }
    }
    // Add the last image again to apply duration
    if (imagePaths.isNotEmpty) {
      buffer.writeln("file '${imagePaths.last}'");
    }

    await listFile.writeAsString(buffer.toString());
    print('[VideoRenderer] Created concat list file: ${listFile.path}');

    return listFile.path;
  }

  // Execute FFmpeg command with progress tracking
  Future<Result<String, String>> _executeFFmpegCommand({
    required String command,
    required void Function(double progress) onProgress,
    required double estimatedDurationSeconds,
  }) async {
    try {
      final FlutterFFmpeg _ffmpeg = FlutterFFmpeg();

      // Simulate progress while rendering
      _simulateProgress(onProgress, estimatedDurationSeconds);

      // Execute FFmpeg
      final int rc = await _ffmpeg.execute(command);

      if (rc == 0) {
        // Extract output path from command
        final outputMatch = RegExp(r'"([^"]+\.mp4)"').allMatches(command).last;
        final outputPath = outputMatch.group(1)!;

        final outputFile = File(outputPath);
        if (await outputFile.exists()) {
          onProgress(1.0);
          return Success(outputPath);
        } else {
          return Error('Output file not created');
        }
      } else {
        return Error('FFmpeg failed with return code: $rc');
      }
    } catch (e) {
      return Error('FFmpeg error: $e');
    }
  }

  /// Simulate progress for rendering
  ///
  /// TODO: Replace with actual FFmpeg statistics callback
  /// FFmpegKit provides statistics callbacks that give frame number and time,
  /// which can be used to calculate actual progress.
  Future<void> _simulateProgress(
    void Function(double progress) onProgress,
    double estimatedDurationSeconds,
  ) async {
    // Simulate progress over estimated duration
    final steps = 20;
    final delayMs = (estimatedDurationSeconds * 1000 / steps).round();

    for (int i = 1; i <= steps; i++) {
      await Future.delayed(Duration(milliseconds: delayMs));
      onProgress(i / steps);
    }
  }

  /// Estimate the total video duration based on images and template
  double _estimateVideoDuration(RenderJob job) {
    final config = job.template.config;
    final numImages = job.imagePaths.length;
    final imageDuration = config.imageDurationSeconds;
    final transitionDuration = config.transitionDurationSeconds;

    // Total duration = (numImages * imageDuration) + ((numImages - 1) * transitionDuration)
    final totalDuration = (numImages * imageDuration) + ((numImages - 1) * transitionDuration);

    return totalDuration;
  }
}
