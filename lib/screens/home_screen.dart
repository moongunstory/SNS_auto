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
                    // Photo selection section
                    _buildPhotoSelectionSection(),

                    const SizedBox(height: AppConstants.paddingLarge),

                    // Template selection section
                    _buildTemplateSelectionSection(),
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
          'Select Photos',
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
            'No photos selected',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: Theme.of(context).colorScheme.onSurface.withOpacity(0.6),
                ),
          )
        else
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '${_selectedImagePaths.length} photo(s) selected',
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
          'Choose Template',
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
    final result = await _mediaPickerService.pickImages();

    if (!mounted) return;

    result.fold(
      onSuccess: (imagePaths) {
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
