/// Application-wide constants
class AppConstants {
  // ============================================================================
  // UI Constants
  // ============================================================================

  /// Standard padding
  static const double paddingSmall = 8.0;
  static const double paddingMedium = 16.0;
  static const double paddingLarge = 24.0;

  /// Border radius
  static const double borderRadiusSmall = 4.0;
  static const double borderRadiusMedium = 8.0;
  static const double borderRadiusLarge = 16.0;

  /// Icon sizes
  static const double iconSizeSmall = 20.0;
  static const double iconSizeMedium = 24.0;
  static const double iconSizeLarge = 32.0;

  /// Template card dimensions
  static const double templateCardHeight = 120.0;
  static const double templateCardWidth = 200.0;

  /// Video preview dimensions
  static const double videoPreviewMaxHeight = 400.0;

  // ============================================================================
  // Photo/Media Constants
  // ============================================================================

  /// Maximum number of photos that can be selected
  static const int maxPhotosAllowed = 10;

  /// Minimum number of photos required
  static const int minPhotosRequired = 1;

  /// Image quality for compression (0-100)
  static const int imageQuality = 85;

  // ============================================================================
  // Video Rendering Constants
  // ============================================================================

  /// Default duration for each image in seconds
  static const double defaultImageDurationSeconds = 3.0;

  /// Default transition duration in seconds
  static const double defaultTransitionDurationSeconds = 0.5;

  /// Video output format
  static const String videoOutputFormat = 'mp4';

  /// Video container format
  static const String videoContainer = 'mp4';

  /// Audio codec
  static const String audioCodec = 'aac';

  /// Audio bitrate
  static const String audioBitrate = '128k';

  // ============================================================================
  // Upload Constants
  // ============================================================================

  /// Upload timeout in seconds
  static const int uploadTimeoutSeconds = 300; // 5 minutes

  /// Retry attempts for failed uploads
  static const int maxUploadRetries = 3;

  /// Delay between retry attempts in milliseconds
  static const int retryDelayMilliseconds = 2000;

  // ============================================================================
  // Platform-Specific Limits
  // ============================================================================

  /// Instagram caption max length
  static const int instagramCaptionMaxLength = 2200;

  /// YouTube title max length
  static const int youtubeTitleMaxLength = 100;

  /// YouTube description max length
  static const int youtubeDescriptionMaxLength = 5000;

  /// TikTok caption max length
  static const int tiktokCaptionMaxLength = 2200;

  /// Facebook caption max length
  static const int facebookCaptionMaxLength = 63206;

  /// Max hashtags for Instagram
  static const int instagramMaxHashtags = 30;

  // ============================================================================
  // File/Directory Names
  // ============================================================================

  /// Directory name for rendered videos
  static const String renderedVideosDirectoryName = 'sns_auto_videos';

  /// Prefix for video files
  static const String videoFilePrefix = 'video_';

  // ============================================================================
  // Messages & Labels
  // ============================================================================

  static const String appName = 'SNS Auto';

  static const String errorNoPhotosSelected = 'Please select at least one photo';
  static const String errorNoTemplateSelected = 'Please select a template';
  static const String errorRenderingFailed = 'Video rendering failed';
  static const String errorUploadFailed = 'Upload failed';
  static const String errorNoPlatformSelected = 'Please select at least one platform';

  static const String labelSelectPhotos = 'Select Photos';
  static const String labelGenerateVideo = 'Generate Video';
  static const String labelNext = 'Next';
  static const String labelUpload = 'Upload';
  static const String labelRetry = 'Retry';
  static const String labelBack = 'Back';

  static const String messageRenderingInProgress = 'Rendering video...';
  static const String messageRenderingComplete = 'Video ready!';
  static const String messageUploadInProgress = 'Uploading...';
  static const String messageUploadComplete = 'Upload complete!';
}
