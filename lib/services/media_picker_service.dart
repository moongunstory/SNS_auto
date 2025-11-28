import 'package:image_picker/image_picker.dart';
import '../config/constants.dart';
import '../utils/result.dart';

/// Service for selecting images from the device gallery
class MediaPickerService {
  final ImagePicker _picker = ImagePicker();

  /// Pick multiple images from gallery
  ///
  /// Returns a list of file paths for selected images.
  /// Returns an error if:
  /// - User cancels the picker
  /// - No images are selected
  /// - Too many images are selected (exceeds maxPhotosAllowed)
  Future<Result<String, List<String>>> pickImages() async {
    try {
      // Use pickMultiImage for selecting multiple images
      final List<XFile> images = await _picker.pickMultiImage(
        imageQuality: AppConstants.imageQuality,
      );

      // Check if user cancelled or no images selected
      if (images.isEmpty) {
        return Error('No images selected');
      }

      // Check if too many images selected
      if (images.length > AppConstants.maxPhotosAllowed) {
        return Error(
          'Too many images selected. Maximum is ${AppConstants.maxPhotosAllowed}',
        );
      }

      // Extract file paths
      final List<String> imagePaths = images.map((image) => image.path).toList();

      return Success(imagePaths);
    } catch (e) {
      return Error('Failed to pick images: $e');
    }
  }

  /// Pick a single image from gallery
  ///
  /// This is a convenience method for scenarios where only one image is needed.
  Future<Result<String, String>> pickSingleImage() async {
    try {
      final XFile? image = await _picker.pickImage(
        source: ImageSource.gallery,
        imageQuality: AppConstants.imageQuality,
      );

      if (image == null) {
        return Error('No image selected');
      }

      return Success(image.path);
    } catch (e) {
      return Error('Failed to pick image: $e');
    }
  }

  /// Pick images from camera (for future use)
  ///
  /// Note: This would require camera permissions in AndroidManifest.xml
  Future<Result<String, String>> pickImageFromCamera() async {
    try {
      final XFile? image = await _picker.pickImage(
        source: ImageSource.camera,
        imageQuality: AppConstants.imageQuality,
      );

      if (image == null) {
        return Error('No image captured');
      }

      return Success(image.path);
    } catch (e) {
      return Error('Failed to capture image: $e');
    }
  }
}
