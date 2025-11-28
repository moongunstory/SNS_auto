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
  // Messages & Labels (Korean)
  // ============================================================================

  static const String appName = 'SNS Auto';

  // Error messages
  static const String errorNoPhotosSelected = '사진을 한 장 이상 선택해주세요';
  static const String errorNoTemplateSelected = '템플릿을 선택해주세요';
  static const String errorRenderingFailed = '영상 렌더링에 실패했습니다';
  static const String errorUploadFailed = '업로드에 실패했습니다';
  static const String errorNoPlatformSelected = '업로드할 플랫폼을 한 곳 이상 선택해주세요';
  static const String errorPickingImages = '사진 선택 중 오류가 발생했습니다';

  // Button labels
  static const String labelSelectPhotos = '사진 선택';
  static const String labelGenerateVideo = '영상 생성';
  static const String labelNext = '다음';
  static const String labelUpload = '업로드';
  static const String labelRetry = '다시 시도';
  static const String labelBack = '뒤로';

  // Status messages
  static const String messageRenderingInProgress = '영상을 렌더링하는 중...';
  static const String messageRenderingComplete = '영상이 완성되었습니다!';
  static const String messageUploadInProgress = '업로드 중...';
  static const String messageUploadComplete = '업로드 완료!';
  static const String messageNoBgm = '배경음악 파일이 없어 무음으로 렌더링했습니다';

  // Screen titles
  static const String titleHome = '홈';
  static const String titleRendering = '영상 렌더링';
  static const String titleUpload = 'SNS 업로드';

  // Section titles
  static const String sectionSelectPhotos = '사진 선택';
  static const String sectionChooseTemplate = '템플릿 선택';
  static const String sectionVideoPreview = '영상 미리보기';
  static const String sectionUploadTo = '업로드할 플랫폼';
  static const String sectionCaption = '캡션';
  static const String sectionPlatformTags = '플랫폼별 태그';
  static const String sectionUploadStatus = '업로드 상태';

  // Hints and descriptions
  static const String hintNoPhotos = '선택된 사진 없음';
  static const String hintPhotoCount = '장의 사진 선택됨';
  static const String hintCaptionInput = '캡션을 입력하세요...';
  static const String hintTapToPlayPause = '탭하여 재생/일시정지';
  static const String hintUsingTemplate = '템플릿 사용 중';

  // Platform names
  static const String platformFacebookPage = 'Facebook 페이지';
  static const String platformInstagramReels = 'Instagram 릴스';
  static const String platformYouTubeShorts = 'YouTube Shorts';
  static const String platformTikTok = 'TikTok';

  // Tag input labels
  static const String labelInstagramHashtags = 'Instagram 해시태그';
  static const String labelYouTubeTags = 'YouTube 태그';
  static const String labelTikTokHashtags = 'TikTok 해시태그';

  // Tag input hints
  static const String hintInstagramTags = '#태그1 #태그2 #태그3';
  static const String hintYouTubeTags = '태그1, 태그2, 태그3';
  static const String hintTikTokTags = '#태그1 #태그2 #태그3';

  // BGM related
  static const String bgmFilePath = '/Android/data/com.example.sns_auto/files/bgm/bgm_default.mp3';
  static const String bgmHelpMessage = 'mp3 파일을 위 경로에 bgm_default.mp3 이름으로 저장하면 배경음악이 적용됩니다';
}
