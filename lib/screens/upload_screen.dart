import 'dart:io';
import 'package:flutter/material.dart';

import '../models/upload_target.dart';
import '../services/sns/meta_service.dart';
import '../services/sns/youtube_service.dart';
import '../services/sns/tiktok_service.dart';
import '../widgets/platform_checkbox.dart';
import '../widgets/primary_button.dart';
import '../config/constants.dart';

/// Screen for configuring and uploading to multiple SNS platforms
///
/// User flow:
/// 1. View video preview
/// 2. Select target platforms (checkboxes)
/// 3. Enter caption and tags
/// 4. Tap "Upload"
/// 5. See per-platform upload progress and status
class UploadScreen extends StatefulWidget {
  final String videoPath;
  final List<String> imagePaths;

  const UploadScreen({
    super.key,
    required this.videoPath,
    required this.imagePaths,
  });

  @override
  State<UploadScreen> createState() => _UploadScreenState();
}

class _UploadScreenState extends State<UploadScreen> {
  final MetaService _metaService = MetaService();
  final YouTubeService _youtubeService = YouTubeService();
  final TikTokService _tiktokService = TikTokService();

  // Form controllers
  final TextEditingController _captionController = TextEditingController();
  final TextEditingController _instagramTagsController = TextEditingController();
  final TextEditingController _youtubeTagsController = TextEditingController();
  final TextEditingController _tiktokTagsController = TextEditingController();

  // Platform selection state
  bool _uploadFacebookPage = true;
  bool _uploadInstagram = true;
  bool _uploadYouTube = true;
  bool _uploadTikTok = true;

  // Upload state
  bool _isUploading = false;
  final Map<String, PlatformUploadState> _uploadStates = {};

  @override
  void dispose() {
    _captionController.dispose();
    _instagramTagsController.dispose();
    _youtubeTagsController.dispose();
    _tiktokTagsController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text(AppConstants.titleUpload),
        backgroundColor: Theme.of(context).colorScheme.primaryContainer,
      ),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(AppConstants.paddingMedium),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Video preview thumbnail
                    _buildVideoPreview(),

                    const SizedBox(height: AppConstants.paddingLarge),

                    // Platform selection
                    _buildPlatformSelection(),

                    const SizedBox(height: AppConstants.paddingLarge),

                    // Caption input
                    _buildCaptionInput(),

                    const SizedBox(height: AppConstants.paddingLarge),

                    // Platform-specific tags
                    _buildPlatformTags(),

                    const SizedBox(height: AppConstants.paddingLarge),

                    // Upload status
                    if (_uploadStates.isNotEmpty) _buildUploadStatus(),
                  ],
                ),
              ),
            ),

            // Bottom button
            _buildBottomButton(),
          ],
        ),
      ),
    );
  }

  Widget _buildVideoPreview() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          AppConstants.sectionVideoPreview,
          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.bold,
              ),
        ),

        const SizedBox(height: AppConstants.paddingMedium),

        Container(
          height: 200,
          width: double.infinity,
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(AppConstants.borderRadiusMedium),
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(AppConstants.borderRadiusMedium),
            child: Stack(
              alignment: Alignment.center,
              children: [
                // Placeholder for video thumbnail
                Icon(
                  Icons.video_library,
                  size: 64,
                  color: Theme.of(context).colorScheme.onSurface.withOpacity(0.3),
                ),
                // Play icon overlay
                Icon(
                  Icons.play_circle_outline,
                  size: 80,
                  color: Theme.of(context).colorScheme.primary,
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildPlatformSelection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          AppConstants.sectionUploadTo,
          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.bold,
              ),
        ),

        const SizedBox(height: AppConstants.paddingSmall),

        PlatformCheckbox(
          label: AppConstants.platformFacebookPage,
          icon: Icons.facebook,
          value: _uploadFacebookPage,
          onChanged: (value) => setState(() => _uploadFacebookPage = value!),
        ),

        PlatformCheckbox(
          label: AppConstants.platformInstagramReels,
          icon: Icons.photo_camera,
          value: _uploadInstagram,
          onChanged: (value) => setState(() => _uploadInstagram = value!),
        ),

        PlatformCheckbox(
          label: AppConstants.platformYouTubeShorts,
          icon: Icons.video_library,
          value: _uploadYouTube,
          onChanged: (value) => setState(() => _uploadYouTube = value!),
        ),

        PlatformCheckbox(
          label: AppConstants.platformTikTok,
          icon: Icons.music_note,
          value: _uploadTikTok,
          onChanged: (value) => setState(() => _uploadTikTok = value!),
        ),
      ],
    );
  }

  Widget _buildCaptionInput() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          AppConstants.sectionCaption,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
        ),

        const SizedBox(height: AppConstants.paddingSmall),

        TextField(
          controller: _captionController,
          maxLines: 4,
          maxLength: AppConstants.instagramCaptionMaxLength,
          decoration: InputDecoration(
            hintText: AppConstants.hintCaptionInput,
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(AppConstants.borderRadiusMedium),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildPlatformTags() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          AppConstants.sectionPlatformTags,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
        ),

        const SizedBox(height: AppConstants.paddingMedium),

        // Instagram tags
        if (_uploadInstagram) ...[
          TextField(
            controller: _instagramTagsController,
            decoration: InputDecoration(
              labelText: AppConstants.labelInstagramHashtags,
              hintText: AppConstants.hintInstagramTags,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(AppConstants.borderRadiusSmall),
              ),
            ),
          ),
          const SizedBox(height: AppConstants.paddingSmall),
        ],

        // YouTube tags
        if (_uploadYouTube) ...[
          TextField(
            controller: _youtubeTagsController,
            decoration: InputDecoration(
              labelText: AppConstants.labelYouTubeTags,
              hintText: AppConstants.hintYouTubeTags,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(AppConstants.borderRadiusSmall),
              ),
            ),
          ),
          const SizedBox(height: AppConstants.paddingSmall),
        ],

        // TikTok tags
        if (_uploadTikTok) ...[
          TextField(
            controller: _tiktokTagsController,
            decoration: InputDecoration(
              labelText: AppConstants.labelTikTokHashtags,
              hintText: AppConstants.hintTikTokTags,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(AppConstants.borderRadiusSmall),
              ),
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildUploadStatus() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          AppConstants.sectionUploadStatus,
          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.bold,
              ),
        ),

        const SizedBox(height: AppConstants.paddingMedium),

        ..._uploadStates.entries.map((entry) {
          final state = entry.value;
          return _buildPlatformStatusRow(state);
        }),
      ],
    );
  }

  Widget _buildPlatformStatusRow(PlatformUploadState state) {
    Color statusColor;
    IconData statusIcon;

    switch (state.status) {
      case UploadStatus.pending:
        statusColor = Colors.grey;
        statusIcon = Icons.pending;
        break;
      case UploadStatus.uploading:
        statusColor = Colors.blue;
        statusIcon = Icons.cloud_upload;
        break;
      case UploadStatus.success:
        statusColor = Colors.green;
        statusIcon = Icons.check_circle;
        break;
      case UploadStatus.failed:
        statusColor = Colors.red;
        statusIcon = Icons.error;
        break;
    }

    return Card(
      margin: const EdgeInsets.only(bottom: AppConstants.paddingSmall),
      child: ListTile(
        leading: Icon(statusIcon, color: statusColor),
        title: Text(state.platformName),
        subtitle: Text(state.getStatusText()),
        trailing: state.status == UploadStatus.uploading
            ? const SizedBox(
                width: 24,
                height: 24,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : null,
      ),
    );
  }

  Widget _buildBottomButton() {
    final bool canUpload = _hasAnyPlatformSelected() && !_isUploading;

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
        text: AppConstants.labelUpload,
        icon: Icons.cloud_upload,
        onPressed: canUpload ? _startUpload : null,
        isLoading: _isUploading,
      ),
    );
  }

  // ============================================================================
  // Actions
  // ============================================================================

  bool _hasAnyPlatformSelected() {
    return _uploadFacebookPage || _uploadInstagram || _uploadYouTube || _uploadTikTok;
  }

  Future<void> _startUpload() async {
    if (!_hasAnyPlatformSelected()) {
      _showError(AppConstants.errorNoPlatformSelected);
      return;
    }

    setState(() {
      _isUploading = true;
      _uploadStates.clear();
    });

    final caption = _captionController.text.trim();

    // Parse tags
    final instagramTags = _parseHashtags(_instagramTagsController.text);
    final youtubeTags = _parseTags(_youtubeTagsController.text);
    final tiktokTags = _parseHashtags(_tiktokTagsController.text);

    // Initialize upload states
    if (_uploadFacebookPage) {
      _uploadStates['Facebook Page'] = const PlatformUploadState(
        platformName: 'Facebook Page',
        status: UploadStatus.pending,
      );
    }
    if (_uploadInstagram) {
      _uploadStates['Instagram Reels'] = const PlatformUploadState(
        platformName: 'Instagram Reels',
        status: UploadStatus.pending,
      );
    }
    if (_uploadYouTube) {
      _uploadStates['YouTube Shorts'] = const PlatformUploadState(
        platformName: 'YouTube Shorts',
        status: UploadStatus.pending,
      );
    }
    if (_uploadTikTok) {
      _uploadStates['TikTok'] = const PlatformUploadState(
        platformName: 'TikTok',
        status: UploadStatus.pending,
      );
    }

    setState(() {});

    // Upload to each platform
    // Note: For now we upload sequentially, but these could be done in parallel
    // using Future.wait()

    if (_uploadFacebookPage) {
      await _uploadToFacebookPage(caption);
    }

    if (_uploadInstagram) {
      await _uploadToInstagram(caption, instagramTags);
    }

    if (_uploadYouTube) {
      await _uploadToYouTube(caption, youtubeTags);
    }

    if (_uploadTikTok) {
      await _uploadToTikTok(caption, tiktokTags);
    }

    setState(() {
      _isUploading = false;
    });

    // Show completion message
    _showSuccess(AppConstants.messageUploadComplete);
  }

  Future<void> _uploadToFacebookPage(String caption) async {
    _updateUploadState('Facebook Page', UploadStatus.uploading);

    final result = await _metaService.uploadToFacebookPage(
      widget.imagePaths,
      caption,
    );

    result.fold(
      onSuccess: (_) {
        _updateUploadState('Facebook Page', UploadStatus.success);
      },
      onError: (error) {
        _updateUploadState('Facebook Page', UploadStatus.failed, error);
      },
    );
  }

  Future<void> _uploadToInstagram(String caption, List<String> tags) async {
    _updateUploadState('Instagram Reels', UploadStatus.uploading);

    final result = await _metaService.uploadInstagramReel(
      widget.videoPath,
      caption,
      tags: tags,
    );

    result.fold(
      onSuccess: (_) {
        _updateUploadState('Instagram Reels', UploadStatus.success);
      },
      onError: (error) {
        _updateUploadState('Instagram Reels', UploadStatus.failed, error);
      },
    );
  }

  Future<void> _uploadToYouTube(String caption, List<String> tags) async {
    _updateUploadState('YouTube Shorts', UploadStatus.uploading);

    final result = await _youtubeService.uploadShort(
      widget.videoPath,
      title: caption.length > AppConstants.youtubeTitleMaxLength
          ? caption.substring(0, AppConstants.youtubeTitleMaxLength)
          : caption,
      description: caption,
      tags: tags,
    );

    result.fold(
      onSuccess: (_) {
        _updateUploadState('YouTube Shorts', UploadStatus.success);
      },
      onError: (error) {
        _updateUploadState('YouTube Shorts', UploadStatus.failed, error);
      },
    );
  }

  Future<void> _uploadToTikTok(String caption, List<String> tags) async {
    _updateUploadState('TikTok', UploadStatus.uploading);

    final result = await _tiktokService.uploadShort(
      widget.videoPath,
      caption: caption,
      tags: tags,
    );

    result.fold(
      onSuccess: (_) {
        _updateUploadState('TikTok', UploadStatus.success);
      },
      onError: (error) {
        _updateUploadState('TikTok', UploadStatus.failed, error);
      },
    );
  }

  void _updateUploadState(
    String platformName,
    UploadStatus status, [
    String? errorMessage,
  ]) {
    setState(() {
      _uploadStates[platformName] = PlatformUploadState(
        platformName: platformName,
        status: status,
        errorMessage: errorMessage,
      );
    });
  }

  List<String> _parseHashtags(String text) {
    final trimmed = text.trim();
    if (trimmed.isEmpty) return [];

    // Split by spaces and filter out empty strings
    return trimmed
        .split(RegExp(r'\s+'))
        .where((tag) => tag.isNotEmpty)
        .map((tag) => tag.startsWith('#') ? tag.substring(1) : tag)
        .toList();
  }

  List<String> _parseTags(String text) {
    final trimmed = text.trim();
    if (trimmed.isEmpty) return [];

    // Split by comma or space
    return trimmed
        .split(RegExp(r'[,\s]+'))
        .where((tag) => tag.isNotEmpty)
        .toList();
  }

  void _showError(String message) {
    if (!mounted) return;

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Theme.of(context).colorScheme.error,
      ),
    );
  }

  void _showSuccess(String message) {
    if (!mounted) return;

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Colors.green,
      ),
    );
  }
}
