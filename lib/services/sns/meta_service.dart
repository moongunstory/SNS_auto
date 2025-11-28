import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;

import '../../config/app_config.dart';
import '../../utils/result.dart';
import '../auth_service.dart';

/// Service for Meta (Facebook/Instagram) Graph API interactions
///
/// Handles:
/// - Facebook Page photo uploads
/// - Instagram Reels uploads
/// - Instagram token auto-refresh
class MetaService {
  final AuthService _authService;

  MetaService({AuthService? authService})
      : _authService = authService ?? AuthService();

  // ============================================================================
  // Facebook Page Upload
  // ============================================================================

  /// Upload photo(s) to Facebook Page
  ///
  /// For now, this is stubbed but structured for real API integration.
  ///
  /// TODO: Implement actual Facebook Page photo upload via Graph API:
  /// 1. POST to /{page-id}/photos with photo data and caption
  /// 2. Handle response and errors
  ///
  /// Real API endpoint: POST /{page-id}/photos
  /// Required params:
  /// - url or source (photo data)
  /// - message (caption)
  /// - access_token (page access token)
  Future<Result<String, void>> uploadToFacebookPage(
    List<String> imagePaths,
    String caption,
  ) async {
    print('[MetaService] uploadToFacebookPage called');
    print('  - Images: ${imagePaths.length}');
    print('  - Caption: $caption');

    if (AppConfig.mockUploads) {
      // Simulate upload delay
      await Future.delayed(const Duration(seconds: 2));

      // Simulate success
      print('[MetaService] Facebook Page upload SUCCESS (mocked)');
      return const Success(null);

      // Uncomment to test error handling:
      // return Error('Mock error: Facebook Page upload failed');
    }

    // TODO: Real implementation
    try {
      // For multiple images, you would either:
      // 1. Create a multi-photo post (if Facebook Page supports it)
      // 2. Upload images individually

      for (final imagePath in imagePaths) {
        final result = await _uploadPhotoToFacebookPage(imagePath, caption);
        if (result.isError) {
          return Error(result.error);
        }
      }

      return const Success(null);
    } catch (e) {
      return Error('Facebook Page upload failed: $e');
    }
  }

  /// Upload a single photo to Facebook Page
  ///
  /// TODO: Implement actual upload logic
  Future<Result<String, String>> _uploadPhotoToFacebookPage(
    String imagePath,
    String caption,
  ) async {
    // TODO: Implement real API call
    // Example:
    // final uri = Uri.parse('${AppConfig.metaGraphApiBaseUrl}/${AppConfig.facebookPageId}/photos');
    // final request = http.MultipartRequest('POST', uri);
    // request.fields['message'] = caption;
    // request.fields['access_token'] = AppConfig.facebookPageAccessToken;
    // request.files.add(await http.MultipartFile.fromPath('source', imagePath));
    // final response = await request.send();
    // ...

    return Error('Not implemented');
  }

  // ============================================================================
  // Instagram Reels Upload
  // ============================================================================

  /// Upload video as Instagram Reel
  ///
  /// This method:
  /// 1. Ensures the Instagram token is valid (auto-refresh if needed)
  /// 2. Uploads the video as a Reel
  ///
  /// TODO: Implement actual Instagram Reels upload via Graph API:
  /// The Instagram Reels upload is a multi-step process:
  /// 1. POST /{ig-user-id}/media to create a container
  ///    - media_type=REELS
  ///    - video_url=<publicly accessible URL>
  ///    - caption=<caption with hashtags>
  /// 2. Wait for container to be ready (poll status)
  /// 3. POST /{ig-user-id}/media_publish to publish the container
  ///
  /// Note: Instagram API requires the video to be accessible via a public URL.
  /// For personal use, you might need to:
  /// - Upload video to a temporary hosting service
  /// - Or use Instagram's container upload with local file support (if available)
  Future<Result<String, void>> uploadInstagramReel(
    String videoPath,
    String caption, {
    List<String> tags = const [],
  }) async {
    print('[MetaService] uploadInstagramReel called');
    print('  - Video: $videoPath');
    print('  - Caption: $caption');
    print('  - Tags: $tags');

    // Ensure token is valid and refresh if needed
    final tokenResult = await _ensureValidInstagramToken();
    if (tokenResult.isError) {
      return Error(tokenResult.error);
    }

    final token = tokenResult.value;

    if (AppConfig.mockUploads) {
      // Simulate upload delay
      await Future.delayed(const Duration(seconds: 3));

      // Simulate success
      print('[MetaService] Instagram Reel upload SUCCESS (mocked)');
      return const Success(null);

      // Uncomment to test error handling:
      // return Error('Mock error: Instagram upload failed');
    }

    // TODO: Real implementation
    try {
      // Step 1: Create media container
      final containerResult = await _createInstagramMediaContainer(
        token: token,
        videoPath: videoPath,
        caption: _buildCaptionWithTags(caption, tags),
      );

      if (containerResult.isError) {
        return Error(containerResult.error);
      }

      final containerId = containerResult.value;

      // Step 2: Poll until container is ready
      final readyResult = await _waitForContainerReady(token, containerId);
      if (readyResult.isError) {
        return Error(readyResult.error);
      }

      // Step 3: Publish the container
      final publishResult = await _publishInstagramMedia(token, containerId);
      if (publishResult.isError) {
        return Error(publishResult.error);
      }

      return const Success(null);
    } catch (e) {
      return Error('Instagram Reel upload failed: $e');
    }
  }

  /// Build caption with hashtags
  String _buildCaptionWithTags(String caption, List<String> tags) {
    if (tags.isEmpty) return caption;

    final hashtags = tags.map((tag) => tag.startsWith('#') ? tag : '#$tag').join(' ');
    return '$caption\n\n$hashtags';
  }

  /// Create Instagram media container
  ///
  /// TODO: Implement actual API call
  Future<Result<String, String>> _createInstagramMediaContainer({
    required InstagramToken token,
    required String videoPath,
    required String caption,
  }) async {
    // TODO: Real implementation
    // POST /{ig-user-id}/media
    // Params:
    // - media_type: REELS
    // - video_url: <public URL> (challenge: need to host video publicly)
    // - caption: caption text
    // - access_token: token
    //
    // Returns: { "id": "<container-id>" }

    return Error('Not implemented');
  }

  /// Wait for Instagram container to be ready
  ///
  /// TODO: Implement polling logic
  Future<Result<String, void>> _waitForContainerReady(
    InstagramToken token,
    String containerId,
  ) async {
    // TODO: Poll GET /{container-id}?fields=status_code
    // Wait until status_code is FINISHED
    return Error('Not implemented');
  }

  /// Publish Instagram media container
  ///
  /// TODO: Implement actual API call
  Future<Result<String, String>> _publishInstagramMedia(
    InstagramToken token,
    String containerId,
  ) async {
    // TODO: POST /{ig-user-id}/media_publish
    // Params:
    // - creation_id: container-id
    // - access_token: token
    //
    // Returns: { "id": "<media-id>" }

    return Error('Not implemented');
  }

  // ============================================================================
  // Instagram Token Management
  // ============================================================================

  /// Ensure Instagram token is valid, refreshing if necessary
  ///
  /// This method implements client-side token refresh for Instagram long-lived tokens.
  /// Long-lived tokens can be refreshed without the app secret using the refresh_access_token endpoint.
  Future<Result<String, InstagramToken>> _ensureValidInstagramToken() async {
    // Load token from storage
    InstagramToken? token = await _authService.loadInstagramToken();

    // If no token, try to initialize from config
    if (token == null) {
      await _authService.initializeInstagramTokenIfNeeded();
      token = await _authService.loadInstagramToken();
    }

    // If still no token, return error
    if (token == null) {
      return Error(
        'Instagram token not configured. Please set up your token in AppConfig.',
      );
    }

    // Check if token is expired
    if (token.isExpired) {
      return Error('Instagram token is expired. Please refresh manually.');
    }

    // Check if token will expire soon and needs refresh
    if (token.willExpireSoon()) {
      print('[MetaService] Instagram token expires soon (${token.daysUntilExpiry} days), refreshing...');

      final refreshResult = await _refreshInstagramToken(token);

      if (refreshResult.isError) {
        // If refresh fails, we can still try to use the existing token if not expired
        print('[MetaService] Token refresh failed: ${refreshResult.error}');
        if (!token.isExpired) {
          print('[MetaService] Using existing token despite refresh failure');
          return Success(token);
        }
        return Error('Token refresh failed: ${refreshResult.error}');
      }

      token = refreshResult.value;
      print('[MetaService] Token refreshed successfully');
    }

    return Success(token);
  }

  /// Refresh Instagram long-lived access token
  ///
  /// Instagram allows refreshing long-lived tokens using only the current token
  /// (no app secret required on the client side).
  ///
  /// Endpoint: GET https://graph.instagram.com/refresh_access_token
  /// Params:
  /// - grant_type: ig_refresh_token
  /// - access_token: <current-long-lived-token>
  ///
  /// Response:
  /// {
  ///   "access_token": "<new-token>",
  ///   "token_type": "bearer",
  ///   "expires_in": 5183944  // seconds (~60 days)
  /// }
  Future<Result<String, InstagramToken>> _refreshInstagramToken(
    InstagramToken currentToken,
  ) async {
    try {
      final uri = Uri.parse('${AppConfig.instagramGraphApiBaseUrl}/refresh_access_token').replace(
        queryParameters: {
          'grant_type': 'ig_refresh_token',
          'access_token': currentToken.accessToken,
        },
      );

      print('[MetaService] Refreshing Instagram token...');

      final response = await http.get(uri);

      if (response.statusCode == 200) {
        final data = json.decode(response.body);
        final newAccessToken = data['access_token'] as String;
        final expiresInSeconds = data['expires_in'] as int;

        final newToken = InstagramToken(
          accessToken: newAccessToken,
          expiresAt: DateTime.now().add(Duration(seconds: expiresInSeconds)),
        );

        // Save the new token
        await _authService.saveInstagramToken(newToken);

        print('[MetaService] Token refreshed, new expiry: ${newToken.expiresAt}');

        return Success(newToken);
      } else {
        final errorBody = response.body;
        print('[MetaService] Token refresh failed: ${response.statusCode} - $errorBody');
        return Error('Token refresh failed: ${response.statusCode} - $errorBody');
      }
    } catch (e) {
      print('[MetaService] Exception during token refresh: $e');
      return Error('Token refresh error: $e');
    }
  }
}
