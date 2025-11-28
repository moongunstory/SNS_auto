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

  /// Mock templates for development
  static List<TemplateModel> getMockTemplates() {
    return [
      TemplateModel(
        id: 'template_1',
        name: 'Classic Slideshow',
        description: 'Simple fade transitions with ambient music',
        config: TemplateConfig(
          transitionType: TransitionType.fade,
          imageDurationSeconds: 3.0,
          transitionDurationSeconds: 0.5,
          musicTrackName: 'ambient_music.mp3',
        ),
      ),
      TemplateModel(
        id: 'template_2',
        name: 'Dynamic Zoom',
        description: 'Ken Burns effect with upbeat music',
        config: TemplateConfig(
          transitionType: TransitionType.zoom,
          imageDurationSeconds: 2.5,
          transitionDurationSeconds: 0.8,
          musicTrackName: 'upbeat_music.mp3',
        ),
      ),
      TemplateModel(
        id: 'template_3',
        name: 'Quick Cuts',
        description: 'Fast paced with no transitions',
        config: TemplateConfig(
          transitionType: TransitionType.cut,
          imageDurationSeconds: 1.5,
          transitionDurationSeconds: 0.0,
          musicTrackName: 'energetic_music.mp3',
        ),
      ),
      TemplateModel(
        id: 'template_4',
        name: 'Smooth Slide',
        description: 'Smooth sliding transitions with calm music',
        config: TemplateConfig(
          transitionType: TransitionType.slide,
          imageDurationSeconds: 3.5,
          transitionDurationSeconds: 0.6,
          musicTrackName: 'calm_music.mp3',
        ),
      ),
      TemplateModel(
        id: 'template_5',
        name: 'Dissolve Effect',
        description: 'Cross-dissolve with dramatic music',
        config: TemplateConfig(
          transitionType: TransitionType.dissolve,
          imageDurationSeconds: 4.0,
          transitionDurationSeconds: 1.0,
          musicTrackName: 'dramatic_music.mp3',
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
