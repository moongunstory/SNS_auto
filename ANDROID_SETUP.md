# Android Configuration Instructions

This document contains the required Android configuration changes for the SNS Auto app.

**IMPORTANT:** Follow these instructions carefully to enable all app features.

---

## 1. Update AndroidManifest.xml

**File:** `android/app/src/main/AndroidManifest.xml`

Add the following permissions **BEFORE** the `<application>` tag:

```xml
<!-- Permissions for SNS Auto app -->

<!-- Internet access for API calls to SNS platforms -->
<uses-permission android:name="android.permission.INTERNET"/>

<!-- Photo gallery access for image selection -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES"/>
<!-- For Android 12 and below -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32"/>

<!-- Storage access for saving rendered videos -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="32"/>

<!-- Optional: Camera access if you want to support taking photos directly -->
<!-- <uses-permission android:name="android.permission.CAMERA"/> -->
```

### Complete AndroidManifest.xml should look like:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- ADD PERMISSIONS HERE -->
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES"/>
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32"/>
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="32"/>

    <application
        android:label="sns_auto"
        android:name="${applicationName}"
        android:icon="@mipmap/ic_launcher">
        <!-- ... rest of the file remains unchanged ... -->
    </application>

    <!-- ... rest of the file remains unchanged ... -->
</manifest>
```

---

## 2. Update build.gradle (App Level)

**File:** `android/app/build.gradle`

### 2.1 Set minimum SDK version

FFmpeg requires **Android API 24 or higher**.

**Change this line:**

```gradle
minSdk = flutter.minSdkVersion
```

**To:**

```gradle
minSdk = 24  // Required for ffmpeg_kit_flutter
```

### Location in the file:

Find the `defaultConfig` block (around line 36-44) and update it:

```gradle
defaultConfig {
    applicationId = "com.example.sns_auto"
    minSdk = 24  // CHANGED: Required for ffmpeg_kit_flutter
    targetSdk = flutter.targetSdkVersion
    versionCode = flutterVersionCode.toInteger()
    versionName = flutterVersionName
}
```

### 2.2 (Optional) Configure ProGuard for Release Builds

If you plan to create a release build, you may need ProGuard rules for certain dependencies.

**Add to the `buildTypes` release block:**

```gradle
buildTypes {
    release {
        // Signing config
        signingConfig = signingConfigs.debug

        // ProGuard configuration (optional but recommended)
        minifyEnabled true
        shrinkResources true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

**Create:** `android/app/proguard-rules.pro` (if using ProGuard):

```proguard
# Flutter wrapper
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.**  { *; }
-keep class io.flutter.util.**  { *; }
-keep class io.flutter.view.**  { *; }
-keep class io.flutter.**  { *; }
-keep class io.flutter.plugins.**  { *; }

# FFmpeg Kit
-keep class com.arthenica.ffmpegkit.** { *; }

# Image Picker
-keep class io.flutter.plugins.imagepicker.** { *; }

# Secure Storage
-keep class com.it_nomads.fluttersecurestorage.** { *; }
```

---

## 3. (Optional) Update Kotlin Version

If you encounter Kotlin compatibility issues, ensure you're using a recent Kotlin version.

**File:** `android/build.gradle` (Project level, NOT app level)

Check the Kotlin version in the `buildscript` block:

```gradle
buildscript {
    ext.kotlin_version = '1.9.0'  // Use 1.9.0 or higher
    // ...
}
```

---

## 4. Install Dependencies

After making the above changes, run:

```bash
cd ~/Projects/sns_auto
flutter pub get
```

---

## 5. Verify Configuration

To verify everything is set up correctly:

```bash
# Clean build cache
flutter clean

# Get dependencies
flutter pub get

# Run on Android device/emulator
flutter run
```

---

## 6. Troubleshooting

### Issue: "Minimum supported Gradle version is X.X"

**Solution:** Update Gradle wrapper in `android/gradle/wrapper/gradle-wrapper.properties`:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.0-all.zip
```

### Issue: "Execution failed for task ':app:checkDebugAarMetadata'"

**Solution:** This usually means minSdk is too low. Ensure minSdk is set to 24 or higher.

### Issue: "Permission denied" when selecting photos

**Solution:**
1. Ensure all permissions are added to AndroidManifest.xml
2. On Android 13+, the app will request runtime permissions automatically
3. You can manually grant permissions in device Settings > Apps > SNS Auto > Permissions

### Issue: FFmpeg execution fails

**Solutions:**
1. Verify minSdk is 24 or higher
2. Check device architecture (ffmpeg_kit supports arm64-v8a, armeabi-v7a, x86, x86_64)
3. Try a different FFmpeg package version if needed

### Issue: "flutter.minSdkVersion" error

**Solution:** Replace `minSdk = flutter.minSdkVersion` with `minSdk = 24` as shown in section 2.1

---

## 7. Additional Notes

### App Size Optimization

FFmpeg library increases APK size significantly (~40-80MB depending on configuration).

To reduce size, you can:

1. Use `ffmpeg_kit_flutter_min` instead of `ffmpeg_kit_flutter` (fewer codecs)
2. Split APKs per architecture in build.gradle:

```gradle
android {
    ...
    splits {
        abi {
            enable true
            reset()
            include 'arm64-v8a', 'armeabi-v7a'
            universalApk false
        }
    }
}
```

### Network Security Configuration (Optional)

If you need to connect to non-HTTPS endpoints during development:

**Create:** `android/app/src/main/res/xml/network_security_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

**Reference in AndroidManifest.xml** (inside `<application>` tag):

```xml
<application
    ...
    android:networkSecurityConfig="@xml/network_security_config">
```

**⚠️ WARNING:** Only use cleartext traffic in development. Remove for production.

---

## Summary of Changes

✅ **AndroidManifest.xml:**
- Add INTERNET permission
- Add READ_MEDIA_IMAGES permission
- Add READ_EXTERNAL_STORAGE permission (API ≤ 32)
- Add WRITE_EXTERNAL_STORAGE permission (API ≤ 32)

✅ **build.gradle (app):**
- Set minSdk = 24
- (Optional) Configure ProGuard for release builds

✅ **Run:**
- `flutter pub get`
- `flutter run`

---

**You're all set! 🎉**

The app should now:
- Request photo gallery access ✓
- Render videos using FFmpeg ✓
- Upload to SNS platforms (when API integrations are completed) ✓
