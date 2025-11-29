import 'package:flutter/material.dart';
import '../models/template_model.dart';
import '../config/constants.dart';

/// A card widget for displaying a video template option
///
/// Shows:
/// - Template name
/// - Template description
/// - Selected/unselected state
class TemplateCard extends StatelessWidget {
  final TemplateModel template;
  final bool isSelected;
  final VoidCallback onTap;

  const TemplateCard({
    super.key,
    required this.template,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: AppConstants.templateCardWidth,
        height: AppConstants.templateCardHeight,
        margin: const EdgeInsets.symmetric(
          horizontal: AppConstants.paddingSmall,
          vertical: AppConstants.paddingSmall,
        ),
        decoration: BoxDecoration(
          color: isSelected
              ? Theme.of(context).colorScheme.primaryContainer
              : Theme.of(context).colorScheme.surfaceContainerHighest,
          borderRadius: BorderRadius.circular(AppConstants.borderRadiusMedium),
          border: Border.all(
            color: isSelected
                ? Theme.of(context).colorScheme.primary
                : Colors.transparent,
            width: 2,
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.1),
              blurRadius: 4,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Padding(
          padding: const EdgeInsets.all(AppConstants.paddingMedium),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.start,
            children: [
              // Template icon (if selected)
              if (isSelected)
                Icon(
                  Icons.check_circle,
                  color: Theme.of(context).colorScheme.primary,
                  size: AppConstants.iconSizeMedium,
                ),

              if (isSelected) const SizedBox(height: AppConstants.paddingSmall),

              // Template name
              Text(
                template.name,
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: isSelected
                          ? Theme.of(context).colorScheme.onPrimaryContainer
                          : Theme.of(context).colorScheme.onSurface,
                    ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),

              const SizedBox(height: AppConstants.paddingSmall),

              // Template description
              Text(
                template.description,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: isSelected
                          ? Theme.of(context).colorScheme.onPrimaryContainer.withOpacity(0.8)
                          : Theme.of(context).colorScheme.onSurface.withOpacity(0.7),
                    ),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),

              const SizedBox(height: AppConstants.paddingSmall),

              // Image count limit
              Row(
                children: [
                  Icon(
                    Icons.photo_library,
                    size: 14,
                    color: isSelected
                        ? Theme.of(context).colorScheme.onPrimaryContainer.withOpacity(0.6)
                        : Theme.of(context).colorScheme.onSurface.withOpacity(0.5),
                  ),
                  const SizedBox(width: 4),
                  Text(
                    template.config.getImageCountRange(),
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          fontSize: 11,
                          color: isSelected
                              ? Theme.of(context).colorScheme.onPrimaryContainer.withOpacity(0.6)
                              : Theme.of(context).colorScheme.onSurface.withOpacity(0.5),
                        ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
