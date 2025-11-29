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
  String? _selectedMusicTrack;  // User's selected music track for current template

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

                    // Music selection section (템플릿 선택 후에만 표시)
                    if (_selectedTemplate != null) _buildMusicSelectionSection(),

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

        // Template cards in horizontal scroll - dynamic height
        ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.of(context).size.height * 0.25, // 화면 높이의 25% 이하
            minHeight: 120,
          ),
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            itemCount: _templates.length,
            shrinkWrap: true,
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

  Widget _buildMusicSelectionSection() {
    if (_selectedTemplate == null) return const SizedBox.shrink();

    final allowMusicSelection = _selectedTemplate!.config.allowMusicSelection;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '배경음악',
          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.bold,
              ),
        ),

        const SizedBox(height: AppConstants.paddingMedium),

        // Music selection card with FutureBuilder
        FutureBuilder<List<MusicTrack>>(
          future: MusicTrack.getAvailableBgmTracks(),
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const Card(
                child: ListTile(
                  leading: CircularProgressIndicator(),
                  title: Text('음악 목록 로딩 중...'),
                ),
              );
            }

            final tracks = snapshot.data ?? [];
            final selectedTrack = tracks.firstWhere(
              (track) => track.fileName == _selectedMusicTrack,
              orElse: () => tracks.first,
            );

            return Card(
              child: ListTile(
                leading: Icon(
                  allowMusicSelection ? Icons.music_note : Icons.music_off,
                  color: allowMusicSelection
                      ? Theme.of(context).colorScheme.primary
                      : Theme.of(context).colorScheme.onSurface.withOpacity(0.4),
                ),
                title: Text(selectedTrack.displayName),
                subtitle: Text(
                  allowMusicSelection
                      ? '탭하여 변경'
                      : '이 템플릿은 배경음악을 사용하지 않습니다',
                  style: TextStyle(
                    fontSize: 12,
                    color: Theme.of(context).colorScheme.onSurface.withOpacity(0.6),
                  ),
                ),
                trailing: allowMusicSelection ? const Icon(Icons.chevron_right) : null,
                enabled: allowMusicSelection,
                onTap: allowMusicSelection ? _showMusicSelectionDialog : null,
              ),
            );
          },
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

      // Initialize music selection based on template's default
      _selectedMusicTrack = template.config.musicTrackName;

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

  Future<void> _showMusicSelectionDialog() async {
    final availableTracks = await MusicTrack.getAvailableBgmTracks();

    await showDialog<String>(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('배경음악 선택'),
          contentPadding: const EdgeInsets.symmetric(vertical: 12),
          content: SizedBox(
            width: double.maxFinite,
            child: ListView.builder(
              shrinkWrap: true,
              itemCount: availableTracks.length,
              itemBuilder: (context, index) {
                final track = availableTracks[index];
                final isSelected = track.fileName == _selectedMusicTrack;

                return ListTile(
                  leading: Icon(
                    track.isNoMusic ? Icons.music_off : Icons.music_note,
                    color: isSelected
                        ? Theme.of(context).colorScheme.primary
                        : Theme.of(context).colorScheme.onSurface.withOpacity(0.6),
                  ),
                  title: Text(
                    track.displayName,
                    style: TextStyle(
                      fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                      color: isSelected
                          ? Theme.of(context).colorScheme.primary
                          : null,
                    ),
                  ),
                  trailing: isSelected
                      ? Icon(Icons.check, color: Theme.of(context).colorScheme.primary)
                      : null,
                  onTap: () {
                    setState(() {
                      _selectedMusicTrack = track.fileName;
                    });
                    Navigator.of(context).pop();
                  },
                );
              },
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('닫기'),
            ),
          ],
        );
      },
    );
  }

  void _generateVideo() {
    if (_selectedImagePaths.isEmpty || _selectedTemplate == null) {
      return;
    }

    // Create render job with user's selected music (or template default)
    final renderJob = RenderJob(
      imagePaths: _selectedImagePaths,
      template: _selectedTemplate!,
      musicTrackName: _selectedMusicTrack,  // User's music selection
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
