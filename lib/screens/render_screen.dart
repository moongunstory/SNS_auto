import 'dart:io';
import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';

import '../models/render_job.dart';
import '../services/video_renderer.dart';
import '../widgets/primary_button.dart';
import '../config/constants.dart';
import 'upload_screen.dart';

/// Screen for rendering video with progress tracking
///
/// User flow:
/// 1. Automatically starts rendering when screen loads
/// 2. Shows progress indicator during rendering
/// 3. On success: shows video preview and "Next" button
/// 4. On error: shows error message and "Retry" button
class RenderScreen extends StatefulWidget {
  final RenderJob renderJob;

  const RenderScreen({
    super.key,
    required this.renderJob,
  });

  @override
  State<RenderScreen> createState() => _RenderScreenState();
}

class _RenderScreenState extends State<RenderScreen> {
  final VideoRenderer _videoRenderer = VideoRenderer();

  // State
  RenderState _state = RenderState.rendering;
  double _progress = 0.0;
  String? _videoPath;
  String? _errorMessage;
  VideoPlayerController? _videoController;

  @override
  void initState() {
    super.initState();
    _startRendering();
  }

  @override
  void dispose() {
    _videoController?.setVolume(0.0);
    _videoController?.pause();
    _videoController?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text(AppConstants.titleRendering),
        backgroundColor: Theme.of(context).colorScheme.primaryContainer,
      ),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: _buildContent(),
            ),
            if (_state == RenderState.success) _buildBottomButton(),
          ],
        ),
      ),
    );
  }

  Widget _buildContent() {
    switch (_state) {
      case RenderState.rendering:
        return _buildRenderingView();
      case RenderState.success:
        return _buildSuccessView();
      case RenderState.error:
        return _buildErrorView();
    }
  }

  Widget _buildRenderingView() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppConstants.paddingLarge),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Progress indicator
            SizedBox(
              width: 120,
              height: 120,
              child: Stack(
                alignment: Alignment.center,
                children: [
                  SizedBox(
                    width: 120,
                    height: 120,
                    child: CircularProgressIndicator(
                      value: _progress,
                      strokeWidth: 8,
                    ),
                  ),
                  Text(
                    '${(_progress * 100).toInt()}%',
                    style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: AppConstants.paddingLarge),

            Text(
              AppConstants.messageRenderingInProgress,
              style: Theme.of(context).textTheme.titleLarge,
            ),

            const SizedBox(height: AppConstants.paddingSmall),

            Text(
              '${widget.renderJob.template.name} ${AppConstants.hintUsingTemplate}',
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Theme.of(context).colorScheme.onSurface.withOpacity(0.7),
                  ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSuccessView() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(AppConstants.paddingMedium),
      child: Column(
        children: [
          // Success message
          Card(
            color: Theme.of(context).colorScheme.primaryContainer,
            child: Padding(
              padding: const EdgeInsets.all(AppConstants.paddingMedium),
              child: Row(
                children: [
                  Icon(
                    Icons.check_circle,
                    color: Theme.of(context).colorScheme.primary,
                    size: AppConstants.iconSizeLarge,
                  ),
                  const SizedBox(width: AppConstants.paddingMedium),
                  Expanded(
                    child: Text(
                      AppConstants.messageRenderingComplete,
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                    ),
                  ),
                ],
              ),
            ),
          ),

          const SizedBox(height: AppConstants.paddingLarge),

          // Video preview
          if (_videoController != null && _videoController!.value.isInitialized)
            AspectRatio(
              aspectRatio: _videoController!.value.aspectRatio,
              child: Container(
                constraints: const BoxConstraints(
                  maxHeight: AppConstants.videoPreviewMaxHeight,
                ),
                child: Stack(
                  alignment: Alignment.center,
                  children: [
                    VideoPlayer(_videoController!),
                    // Play/pause overlay
                    GestureDetector(
                      onTap: _togglePlayPause,
                      child: Container(
                        color: Colors.transparent,
                        child: Center(
                          child: Icon(
                            _videoController!.value.isPlaying
                                ? Icons.pause_circle_outline
                                : Icons.play_circle_outline,
                            size: 64,
                            color: Colors.white.withOpacity(0.8),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            )
          else
            Container(
              height: 300,
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(AppConstants.borderRadiusMedium),
              ),
              child: const Center(
                child: CircularProgressIndicator(),
              ),
            ),

          const SizedBox(height: AppConstants.paddingMedium),

          Text(
            AppConstants.hintTapToPlayPause,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: Theme.of(context).colorScheme.onSurface.withOpacity(0.6),
                ),
          ),
        ],
      ),
    );
  }

  Widget _buildErrorView() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppConstants.paddingLarge),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.error_outline,
              size: 80,
              color: Theme.of(context).colorScheme.error,
            ),

            const SizedBox(height: AppConstants.paddingLarge),

            Text(
              AppConstants.errorRenderingFailed,
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    color: Theme.of(context).colorScheme.error,
                  ),
            ),

            const SizedBox(height: AppConstants.paddingMedium),

            Text(
              _errorMessage ?? 'Unknown error',
              style: Theme.of(context).textTheme.bodyMedium,
              textAlign: TextAlign.center,
            ),

            const SizedBox(height: AppConstants.paddingLarge),

            PrimaryButton(
              text: AppConstants.labelRetry,
              icon: Icons.refresh,
              onPressed: _startRendering,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildBottomButton() {
    return Container(
      padding: const EdgeInsets.all(AppConstants.paddingMedium),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.1),
            blurRadius: 4,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: PrimaryButton(
        text: AppConstants.labelNext,
        icon: Icons.arrow_forward,
        onPressed: _navigateToUploadScreen,
      ),
    );
  }

  // ============================================================================
  // Actions
  // ============================================================================

  Future<void> _startRendering() async {
    setState(() {
      _state = RenderState.rendering;
      _progress = 0.0;
      _errorMessage = null;
    });

    final result = await _videoRenderer.render(
      widget.renderJob,
      (progress) {
        if (mounted) {
          setState(() {
            _progress = progress;
          });
        }
      },
    );

    if (!mounted) return;

    result.fold(
      onSuccess: (videoPath) async {
        setState(() {
          _videoPath = videoPath;
          _state = RenderState.success;
        });

        // Initialize video player
        await _initializeVideoPlayer(videoPath);
      },
      onError: (error) {
        setState(() {
          _errorMessage = error;
          _state = RenderState.error;
        });
      },
    );
  }

  Future<void> _initializeVideoPlayer(String videoPath) async {
    try {
      _videoController = VideoPlayerController.file(File(videoPath));
      await _videoController!.initialize();
      _videoController!.setLooping(true);

      if (mounted) {
        setState(() {});
      }
    } catch (e) {
      print('[RenderScreen] Error initializing video player: $e');
    }
  }

  void _togglePlayPause() {
    if (_videoController == null) return;

    setState(() {
      if (_videoController!.value.isPlaying) {
        _videoController!.pause();
      } else {
        _videoController!.play();
      }
    });
  }

  void _navigateToUploadScreen() async {
    if (_videoPath == null) return;

    // Critical: Mute and pause video before navigating to prevent audio bleeding
    _videoController?.setVolume(0.0);
    _videoController?.pause();

    // Navigate to upload screen and wait for return
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => UploadScreen(
          videoPath: _videoPath!,
          imagePaths: widget.renderJob.imagePaths,
        ),
      ),
    );

    // Restore volume when coming back from upload screen
    if (mounted && _videoController != null) {
      _videoController!.setVolume(1.0);
      print('[RenderScreen] Volume restored after returning from upload screen');
    }
  }
}

/// Rendering state
enum RenderState {
  rendering,
  success,
  error,
}
