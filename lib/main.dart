import 'package:flutter/material.dart';
import 'screens/home_screen.dart';
import 'services/auth_service.dart';
import 'config/constants.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize authentication service and Instagram token if needed
  final authService = AuthService();
  await authService.initializeInstagramTokenIfNeeded();

  runApp(const SNSAutoApp());
}

class SNSAutoApp extends StatelessWidget {
  const SNSAutoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: AppConstants.appName,
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: Colors.deepPurple,
          brightness: Brightness.light,
        ),
        useMaterial3: true,
        // Additional theme customization
        appBarTheme: const AppBarTheme(
          centerTitle: true,
          elevation: 0,
        ),
        cardTheme: CardTheme(
          elevation: 2,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(AppConstants.borderRadiusMedium),
          ),
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(AppConstants.borderRadiusMedium),
          ),
        ),
      ),
      home: const HomeScreen(),
    );
  }
}
