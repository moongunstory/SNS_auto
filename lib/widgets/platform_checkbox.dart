import 'package:flutter/material.dart';
import '../config/constants.dart';

/// A reusable checkbox widget for platform selection
///
/// Displays a checkbox with a platform label and optional icon.
class PlatformCheckbox extends StatelessWidget {
  final String label;
  final bool value;
  final ValueChanged<bool?> onChanged;
  final IconData? icon;

  const PlatformCheckbox({
    super.key,
    required this.label,
    required this.value,
    required this.onChanged,
    this.icon,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: () => onChanged(!value),
      child: Padding(
        padding: const EdgeInsets.symmetric(
          vertical: AppConstants.paddingSmall,
          horizontal: AppConstants.paddingMedium,
        ),
        child: Row(
          children: [
            // Checkbox
            Checkbox(
              value: value,
              onChanged: onChanged,
            ),

            const SizedBox(width: AppConstants.paddingSmall),

            // Icon (if provided)
            if (icon != null) ...[
              Icon(
                icon,
                size: AppConstants.iconSizeMedium,
                color: Theme.of(context).colorScheme.primary,
              ),
              const SizedBox(width: AppConstants.paddingSmall),
            ],

            // Label
            Expanded(
              child: Text(
                label,
                style: Theme.of(context).textTheme.bodyLarge,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
