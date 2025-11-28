/// Represents the upload configuration for multi-platform uploads
class UploadTarget {
  final bool uploadFacebookPage;
  final bool uploadInstagram;
  final bool uploadYouTube;
  final bool uploadTikTok;
  final String commonCaption;
  final Map<String, List<String>> platformTags;

  const UploadTarget({
    this.uploadFacebookPage = false,
    this.uploadInstagram = false,
    this.uploadYouTube = false,
    this.uploadTikTok = false,
    this.commonCaption = '',
    this.platformTags = const {},
  });

  /// Returns true if at least one platform is selected
  bool hasAnyPlatformSelected() {
    return uploadFacebookPage || uploadInstagram || uploadYouTube || uploadTikTok;
  }

  /// Returns a list of selected platform names
  List<String> getSelectedPlatforms() {
    final platforms = <String>[];
    if (uploadFacebookPage) platforms.add('Facebook Page');
    if (uploadInstagram) platforms.add('Instagram Reels');
    if (uploadYouTube) platforms.add('YouTube Shorts');
    if (uploadTikTok) platforms.add('TikTok');
    return platforms;
  }

  /// Gets tags for a specific platform
  List<String> getTagsForPlatform(String platform) {
    return platformTags[platform] ?? [];
  }

  /// Creates a copy with modified values
  UploadTarget copyWith({
    bool? uploadFacebookPage,
    bool? uploadInstagram,
    bool? uploadYouTube,
    bool? uploadTikTok,
    String? commonCaption,
    Map<String, List<String>>? platformTags,
  }) {
    return UploadTarget(
      uploadFacebookPage: uploadFacebookPage ?? this.uploadFacebookPage,
      uploadInstagram: uploadInstagram ?? this.uploadInstagram,
      uploadYouTube: uploadYouTube ?? this.uploadYouTube,
      uploadTikTok: uploadTikTok ?? this.uploadTikTok,
      commonCaption: commonCaption ?? this.commonCaption,
      platformTags: platformTags ?? this.platformTags,
    );
  }

  @override
  String toString() {
    return 'UploadTarget(platforms: ${getSelectedPlatforms()}, caption: "$commonCaption")';
  }
}

/// Represents the upload status for a single platform
enum UploadStatus {
  pending,
  uploading,
  success,
  failed,
}

/// Tracks the upload state for a platform
class PlatformUploadState {
  final String platformName;
  final UploadStatus status;
  final String? errorMessage;
  final double progress; // 0.0 to 1.0

  const PlatformUploadState({
    required this.platformName,
    this.status = UploadStatus.pending,
    this.errorMessage,
    this.progress = 0.0,
  });

  PlatformUploadState copyWith({
    String? platformName,
    UploadStatus? status,
    String? errorMessage,
    double? progress,
  }) {
    return PlatformUploadState(
      platformName: platformName ?? this.platformName,
      status: status ?? this.status,
      errorMessage: errorMessage ?? this.errorMessage,
      progress: progress ?? this.progress,
    );
  }

  String getStatusText() {
    switch (status) {
      case UploadStatus.pending:
        return 'Pending';
      case UploadStatus.uploading:
        return 'Uploading... ${(progress * 100).toInt()}%';
      case UploadStatus.success:
        return 'Success';
      case UploadStatus.failed:
        return 'Failed${errorMessage != null ? ": $errorMessage" : ""}';
    }
  }
}
