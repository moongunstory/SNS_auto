/// Central configuration for the SNS Auto app
///
/// IMPORTANT SECURITY NOTE:
/// This is a PERSONAL-USE-ONLY app. In a production app, sensitive credentials
/// like API keys and tokens should NEVER be hardcoded or stored client-side.
/// For personal use, we store long-lived tokens securely using flutter_secure_storage.
class AppConfig {
  // ============================================================================
  // API Base URLs
  // ============================================================================

  /// Meta (Facebook/Instagram) Graph API base URL
  static const String metaGraphApiBaseUrl = 'https://graph.facebook.com/v18.0';

  /// Instagram Graph API base URL
  static const String instagramGraphApiBaseUrl = 'https://graph.instagram.com';

  /// YouTube Data API v3 base URL
  static const String youtubeApiBaseUrl = 'https://www.googleapis.com/youtube/v3';

  /// YouTube upload API base URL
  static const String youtubeUploadApiBaseUrl = 'https://www.googleapis.com/upload/youtube/v3';

  /// TikTok API base URL (v2)
  static const String tiktokApiBaseUrl = 'https://open.tiktokapis.com/v2';

  // ============================================================================
  // Facebook Page Configuration
  // ============================================================================

  /// Your Facebook Page ID
  /// TODO: Replace with your actual Facebook Page ID
  /// You can find this in your Facebook Page settings or using the Graph API Explorer
  static const String facebookPageId = 'YOUR_FACEBOOK_PAGE_ID';

  /// Facebook Page Access Token
  /// IMPORTANT: For personal use only. In production, use server-side token management.
  /// This should be a long-lived Page Access Token.
  /// TODO: Replace with your actual Page Access Token
  static const String facebookPageAccessToken = 'YOUR_FACEBOOK_PAGE_ACCESS_TOKEN';

  // ============================================================================
  // Instagram Configuration
  // ============================================================================

  /// Instagram Business Account ID
  /// TODO: Replace with your Instagram Business Account ID
  static const String instagramBusinessAccountId = 'YOUR_INSTAGRAM_BUSINESS_ACCOUNT_ID';

  /// Initial Instagram Long-Lived Access Token
  /// IMPORTANT: This is only used as a fallback for initial setup.
  /// The app will auto-refresh this token and store it securely.
  /// Long-lived tokens are valid for ~60 days and can be refreshed indefinitely.
  ///
  /// To get an initial long-lived token:
  /// 1. Get a short-lived token from Facebook Login
  /// 2. Exchange it for a long-lived token using:
  ///    GET https://graph.facebook.com/v18.0/oauth/access_token
  ///    ?grant_type=fb_exchange_token
  ///    &client_id={app-id}
  ///    &client_secret={app-secret}
  ///    &fb_exchange_token={short-lived-token}
  ///
  /// TODO: Replace with your actual initial Instagram long-lived token
  static const String instagramInitialLongLivedToken = 'YOUR_INSTAGRAM_LONG_LIVED_TOKEN';

  /// Threshold in days before token expiry to trigger auto-refresh
  /// If token expires in less than this many days, it will be refreshed
  static const int instagramTokenRefreshThresholdDays = 7;

  // ============================================================================
  // YouTube Configuration
  // ============================================================================

  /// YouTube API Key (for read operations)
  /// TODO: Replace with your YouTube Data API key
  static const String youtubeApiKey = 'YOUR_YOUTUBE_API_KEY';

  /// YouTube OAuth 2.0 Client ID
  /// TODO: Replace with your OAuth 2.0 client ID for Android
  static const String youtubeClientId = 'YOUR_YOUTUBE_CLIENT_ID';

  // ============================================================================
  // TikTok Configuration
  // ============================================================================

  /// TikTok Client Key (from TikTok Developer Portal)
  /// TODO: Replace with your TikTok app's client key
  static const String tiktokClientKey = 'YOUR_TIKTOK_CLIENT_KEY';

  // ============================================================================
  // App Features & Limits
  // ============================================================================

  /// Enable debug mode (verbose logging, etc.)
  static const bool debugMode = true;

  /// Enable mock/stub uploads (for testing without real API calls)
  static const bool mockUploads = true;

  /// Maximum number of photos allowed per video
  static const int maxPhotosPerVideo = 10;

  /// Minimum number of photos required per video
  static const int minPhotosPerVideo = 1;

  // ============================================================================
  // Video Rendering Configuration
  // ============================================================================

  /// Output video width (vertical video)
  static const int videoWidth = 1080;

  /// Output video height (vertical video, 9:16 aspect ratio for shorts/reels)
  static const int videoHeight = 1920;

  /// Video frame rate
  static const int videoFrameRate = 30;

  /// Video codec
  static const String videoCodec = 'libx264';

  /// Video quality/bitrate (adjust for quality vs file size)
  static const String videoBitrate = '8M';

  // ============================================================================
  // Storage Keys (for flutter_secure_storage)
  // ============================================================================

  static const String storageKeyInstagramToken = 'instagram_access_token';
  static const String storageKeyInstagramTokenExpiry = 'instagram_token_expiry';
  static const String storageKeyYouTubeToken = 'youtube_access_token';
  static const String storageKeyYouTubeTokenExpiry = 'youtube_token_expiry';
  static const String storageKeyTikTokToken = 'tiktok_access_token';
  static const String storageKeyTikTokTokenExpiry = 'tiktok_token_expiry';
}
