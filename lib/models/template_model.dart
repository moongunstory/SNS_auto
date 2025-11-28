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
  /// Currently only one template is available, but the structure
  /// is designed to be easily extended in the future
  static List<TemplateModel> getMockTemplates() {
    return [
      TemplateModel(
        id: 'classic_slideshow',
        name: '기본 슬라이드쇼 (클래식)',
        description: '부드러운 크로스페이드 전환 효과',
        config: TemplateConfig(
          transitionType: TransitionType.fade,
          imageDurationSeconds: 1.5,
          transitionDurationSeconds: 0.5,
          musicTrackName: 'bgm_default.mp3',
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

  const TemplateConfig({
    required this.transitionType,
    required this.imageDurationSeconds,
    required this.transitionDurationSeconds,
    this.musicTrackName,
    this.addTextOverlay = false,
    this.textOverlayContent,
  });
}

/// Types of transitions between images
enum TransitionType {
  fade,
  dissolve,
  slide,
  zoom,
  cut,
}
