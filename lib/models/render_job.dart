import 'template_model.dart';

/// Represents a video rendering job with all necessary data
class RenderJob {
  final List<String> imagePaths;
  final TemplateModel template;
  final String? outputFileName;

  const RenderJob({
    required this.imagePaths,
    required this.template,
    this.outputFileName,
  });

  /// Validates that the render job has valid data
  bool isValid() {
    return imagePaths.isNotEmpty;
  }

  /// Returns the expected output file name
  String getOutputFileName() {
    if (outputFileName != null && outputFileName!.isNotEmpty) {
      return outputFileName!;
    }

    // Generate a timestamped filename
    final timestamp = DateTime.now().millisecondsSinceEpoch;
    return 'video_${timestamp}.mp4';
  }

  @override
  String toString() {
    return 'RenderJob(images: ${imagePaths.length}, template: ${template.name}, output: ${getOutputFileName()})';
  }
}
