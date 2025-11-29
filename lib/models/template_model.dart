/// Represents a video template with its configuration
class TemplateModel {
  final String id;
  final String name;
  final String description;
  final TemplateConfig config;

  const TemplateModel({
    required this.id,
    required this.name,
    required this.description,
    required this.config,
  });

  /// Get available templates
  static List<TemplateModel> getMockTemplates() {
    return [
      TemplateModel(
        id: 'classic_slideshow',
        name: '기본 슬라이드쇼 (클래식)',
        description: '부드러운 크로스페이드 전환 효과',
        config: TemplateConfig(
          transitionType: TransitionType.fade,
          imageDurationSeconds: 2.5,
          transitionDurationSeconds: 0.5,
          musicTrackName: 'bgm_default.mp3',
          minImages: 1,
          maxImages: 10,
        ),
      ),
      TemplateModel(
        id: 'four_tile_intro',
        name: '4-타일 인트로',
        description: '타일 분할 + 리듬감 있는 전환',
        config: TemplateConfig(
          transitionType: TransitionType.cut,
          imageDurationSeconds: 2.0,
          transitionDurationSeconds: 0.0,
          musicTrackName: 'bgm_default.mp3',
          minImages: 3,
          maxImages: 5,
        ),
      ),
    ];
  }
}

/// Configuration for a video template
class TemplateConfig {
  final TransitionType transitionType;
  final double imageDurationSeconds;
  final double transitionDurationSeconds;
  final String? musicTrackName;
  final bool addTextOverlay;
  final String? textOverlayContent;
  final int? minImages;
  final int? maxImages;

  const TemplateConfig({
    required this.transitionType,
    required this.imageDurationSeconds,
    required this.transitionDurationSeconds,
    this.musicTrackName,
    this.addTextOverlay = false,
    this.textOverlayContent,
    this.minImages,
    this.maxImages,
  });

  /// Get the allowed image count range as a string
  String getImageCountRange() {
    if (minImages != null && maxImages != null) {
      if (minImages == maxImages) {
        return '$minImages장';
      }
      return '$minImages~$maxImages장';
    } else if (minImages != null) {
      return '최소 $minImages장';
    } else if (maxImages != null) {
      return '최대 $maxImages장';
    }
    return '제한 없음';
  }

  /// Check if the given number of images is valid for this template
  bool isValidImageCount(int count) {
    if (minImages != null && count < minImages!) return false;
    if (maxImages != null && count > maxImages!) return false;
    return true;
  }
}

/// Types of transitions between images
enum TransitionType {
  fade,
  dissolve,
  slide,
  zoom,
  cut,
}
