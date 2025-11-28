import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../config/app_config.dart';

/// Represents an Instagram access token with expiry
class InstagramToken {
  final String accessToken;
  final DateTime expiresAt;

  InstagramToken({
    required this.accessToken,
    required this.expiresAt,
  });

  /// Check if the token is expired
  bool get isExpired => DateTime.now().isAfter(expiresAt);

  /// Check if the token will expire soon (within threshold days)
  bool willExpireSoon({int thresholdDays = AppConfig.instagramTokenRefreshThresholdDays}) {
    final thresholdDate = DateTime.now().add(Duration(days: thresholdDays));
    return expiresAt.isBefore(thresholdDate);
  }

  /// Days until expiry
  int get daysUntilExpiry => expiresAt.difference(DateTime.now()).inDays;

  /// Convert to JSON-like map for storage
  Map<String, String> toMap() {
    return {
      'access_token': accessToken,
      'expires_at': expiresAt.toIso8601String(),
    };
  }

  /// Create from JSON-like map
  factory InstagramToken.fromMap(Map<String, String> map) {
    return InstagramToken(
      accessToken: map['access_token']!,
      expiresAt: DateTime.parse(map['expires_at']!),
    );
  }

  @override
  String toString() {
    return 'InstagramToken(expiresAt: $expiresAt, daysUntilExpiry: $daysUntilExpiry)';
  }
}

/// Service for managing authentication tokens securely
///
/// Uses flutter_secure_storage to store sensitive tokens.
/// This is appropriate for a personal-use app where tokens are managed client-side.
///
/// SECURITY NOTE: In a production multi-user app, tokens should be managed server-side.
class AuthService {
  // Use flutter_secure_storage for encrypted storage
  final FlutterSecureStorage _storage = const FlutterSecureStorage();

  // ============================================================================
  // Instagram Token Management
  // ============================================================================

  /// Save Instagram token securely
  Future<void> saveInstagramToken(InstagramToken token) async {
    await _storage.write(
      key: AppConfig.storageKeyInstagramToken,
      value: token.accessToken,
    );
    await _storage.write(
      key: AppConfig.storageKeyInstagramTokenExpiry,
      value: token.expiresAt.toIso8601String(),
    );
    print('[AuthService] Instagram token saved, expires at: ${token.expiresAt}');
  }

  /// Load Instagram token from secure storage
  Future<InstagramToken?> loadInstagramToken() async {
    final accessToken = await _storage.read(key: AppConfig.storageKeyInstagramToken);
    final expiryString = await _storage.read(key: AppConfig.storageKeyInstagramTokenExpiry);

    if (accessToken == null || expiryString == null) {
      print('[AuthService] No Instagram token found in storage');
      return null;
    }

    try {
      final expiresAt = DateTime.parse(expiryString);
      final token = InstagramToken(
        accessToken: accessToken,
        expiresAt: expiresAt,
      );
      print('[AuthService] Instagram token loaded: $token');
      return token;
    } catch (e) {
      print('[AuthService] Error parsing Instagram token expiry: $e');
      return null;
    }
  }

  /// Clear Instagram token from storage
  Future<void> clearInstagramToken() async {
    await _storage.delete(key: AppConfig.storageKeyInstagramToken);
    await _storage.delete(key: AppConfig.storageKeyInstagramTokenExpiry);
    print('[AuthService] Instagram token cleared');
  }

  /// Initialize Instagram token from app config if not already stored
  ///
  /// This is used for initial setup in a personal-use app.
  /// Call this once when the app starts to populate the initial token.
  Future<void> initializeInstagramTokenIfNeeded() async {
    final existingToken = await loadInstagramToken();

    if (existingToken != null && !existingToken.isExpired) {
      print('[AuthService] Instagram token already exists and is valid');
      return;
    }

    // If no valid token exists, use the initial token from config
    if (AppConfig.instagramInitialLongLivedToken.isNotEmpty &&
        AppConfig.instagramInitialLongLivedToken != 'YOUR_INSTAGRAM_LONG_LIVED_TOKEN') {
      // Assume initial token is valid for 60 days (standard long-lived token)
      final initialToken = InstagramToken(
        accessToken: AppConfig.instagramInitialLongLivedToken,
        expiresAt: DateTime.now().add(const Duration(days: 60)),
      );
      await saveInstagramToken(initialToken);
      print('[AuthService] Initialized Instagram token from config');
    } else {
      print('[AuthService] WARNING: No initial Instagram token configured in AppConfig');
    }
  }

  // ============================================================================
  // YouTube Token Management (for future implementation)
  // ============================================================================

  Future<void> saveYouTubeToken(String token, DateTime expiresAt) async {
    await _storage.write(key: AppConfig.storageKeyYouTubeToken, value: token);
    await _storage.write(
      key: AppConfig.storageKeyYouTubeTokenExpiry,
      value: expiresAt.toIso8601String(),
    );
  }

  Future<String?> loadYouTubeToken() async {
    return await _storage.read(key: AppConfig.storageKeyYouTubeToken);
  }

  Future<void> clearYouTubeToken() async {
    await _storage.delete(key: AppConfig.storageKeyYouTubeToken);
    await _storage.delete(key: AppConfig.storageKeyYouTubeTokenExpiry);
  }

  // ============================================================================
  // TikTok Token Management (for future implementation)
  // ============================================================================

  Future<void> saveTikTokToken(String token, DateTime expiresAt) async {
    await _storage.write(key: AppConfig.storageKeyTikTokToken, value: token);
    await _storage.write(
      key: AppConfig.storageKeyTikTokTokenExpiry,
      value: expiresAt.toIso8601String(),
    );
  }

  Future<String?> loadTikTokToken() async {
    return await _storage.read(key: AppConfig.storageKeyTikTokToken);
  }

  Future<void> clearTikTokToken() async {
    await _storage.delete(key: AppConfig.storageKeyTikTokToken);
    await _storage.delete(key: AppConfig.storageKeyTikTokTokenExpiry);
  }

  // ============================================================================
  // Utility Methods
  // ============================================================================

  /// Clear all stored tokens (useful for logout or reset)
  Future<void> clearAllTokens() async {
    await clearInstagramToken();
    await clearYouTubeToken();
    await clearTikTokToken();
    print('[AuthService] All tokens cleared');
  }
}
