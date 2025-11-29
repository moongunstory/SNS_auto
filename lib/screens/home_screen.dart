import 'dart:io';
import 'package:flutter/material.dart';

import '../models/template_model.dart';
import '../models/render_job.dart';
import '../services/media_picker_service.dart';
import '../widgets/template_card.dart';
import '../widgets/primary_button.dart';
import '../config/constants.dart';
import 'render_screen.dart';

/// Home screen for photo selection and template selection
///
/// User flow:
/// 1. Select photos from gallery
/// 2. Choose a template
/// 3. Tap "Generate Video" to proceed to RenderScreen
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final MediaPickerService _mediaPickerService = MediaPickerService();

  // State
  List<String> _selectedImagePaths = [];
  TemplateModel? _selectedTemplate;
  final List<TemplateModel> _templates = TemplateModel.getMockTemplates();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text(AppConstants.appName),
        backgroundColor: Theme.of(context).colorScheme.primaryContainer,
      ),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(AppConstants.paddingMedium),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Template selection section (먼저 표시)
                    _buildTemplateSelectionSection(),

                    const SizedBox(height: AppConstants.paddingLarge),

                    // Photo selection section (템플릿 선택 후에만 표시)
                    if (_selectedTemplate != null) _buildPhotoSelectionSection(),
                  ],
                ),
              ),
            ),

            // Bottom button
            _buildBottomButton(),
          ],
        ),
      ),
    );
  }

  Widget _buildPhotoSelectionSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          AppConstants.sectionSelectPhotos,
          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.bold,
              ),
        ),

        const SizedBox(height: AppConstants.paddingMedium),

        // Select photos button
        OutlinedButton.icon(
          onPressed: _pickImages,
          icon: const Icon(Icons.photo_library),
          label: const Text(AppConstants.labelSelectPhotos),
        ),

        const SizedBox(height: AppConstants.paddingMedium),

        // Selected photos display
        if (_selectedImagePaths.isEmpty)
          Text(
            AppConstants.hintNoPhotos,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: Theme.of(context).colorScheme.onSurface.withOpacity(0.6),
                ),
          )
        else
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '${_selectedImagePaths.length}${AppConstants.hintPhotoCount}',
                style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
              ),

              const SizedBox(height: AppConstants.paddingSmall),

              // Thumbnail grid
              SizedBox(
                height: 100,
                child: ListView.builder(
                  scrollDirection: Axis.horizontal,
                  itemCount: _selectedImagePaths.length,
                  itemBuilder: (context, index) {
                    return Container(
                      width: 80,
                      height: 80,
                      margin: const EdgeInsets.only(right: AppConstants.paddingSmall),
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(AppConstants.borderRadiusSmall),
                        image: DecorationImage(
                          image: FileImage(File(_selectedImagePaths[index])),
                          fit: BoxFit.cover,
                        ),
                      ),
                    );
                  },
                ),
              ),
            ],
          ),
      ],
    );
  }

  Widget _buildTemplateSelectionSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          AppConstants.sectionChooseTemplate,
          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.bold,
              ),
        ),

        const SizedBox(height: AppConstants.paddingMedium),

        // Template cards in horizontal scroll
        SizedBox(
          height: AppConstants.templateCardHeight + AppConstants.paddingMedium,
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            itemCount: _templates.length,
            itemBuilder: (context, index) {
              final template = _templates[index];
              return TemplateCard(
                template: template,
                isSelected: _selectedTemplate?.id == template.id,
                onTap: () => _selectTemplate(template),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildBottomButton() {
    final bool canGenerate = _selectedImagePaths.isNotEmpty && _selectedTemplate != null;

    return Container(
      padding: const EdgeInsets.all(AppConstants.paddingMedium),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.1),
            blurRadius: 4,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: PrimaryButton(
        text: AppConstants.labelGenerateVideo,
        icon: Icons.play_circle_outline,
        onPressed: canGenerate ? _generateVideo : null,
      ),
    );
  }

  // ============================================================================
  // Actions
  // ============================================================================

  Future<void> _pickImages() async {
    // 템플릿이 선택되지 않은 경우는 UI에서 이미 제어되므로 여기서는 확인 불필요
    final config = _selectedTemplate!.config;

    // 템플릿의 최대 이미지 수를 전달하여 피커에서 제한
    final result = await _mediaPickerService.pickImages(
      maxImages: config.maxImages,
    );

    if (!mounted) return;

    result.fold(
      onSuccess: (imagePaths) {
        // 추가 검증: 최소/최대 이미지 수 확인
        if (!config.isValidImageCount(imagePaths.length)) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('이 템플릿은 ${config.getImageCountRange()}만 가능합니다'),
              backgroundColor: Theme.of(context).colorScheme.error,
            ),
          );
          return;
        }

        setState(() {
          _selectedImagePaths = imagePaths;
        });
      },
      onError: (error) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(error),
            backgroundColor: Theme.of(context).colorScheme.error,
          ),
        );
      },
    );
  }

  void _selectTemplate(TemplateModel template) {
    setState(() {
      _selectedTemplate = template;

      // Clear images if they don't match the new template's requirements
      if (_selectedImagePaths.isNotEmpty &&
          !template.config.isValidImageCount(_selectedImagePaths.length)) {
        _selectedImagePaths = [];

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('템플릿이 변경되어 선택된 사진이 초기화되었습니다 (${template.config.getImageCountRange()} 필요)'),
            duration: const Duration(seconds: 3),
          ),
        );
      }
    });
  }

  void _generateVideo() {
    if (_selectedImagePaths.isEmpty || _selectedTemplate == null) {
      return;
    }

    // Create render job
    final renderJob = RenderJob(
      imagePaths: _selectedImagePaths,
      template: _selectedTemplate!,
    );

    // Navigate to RenderScreen
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => RenderScreen(renderJob: renderJob),
      ),
    );
  }
}
