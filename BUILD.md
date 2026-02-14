# MiniJarvis Build Guide

## How to Build the APK

### Prerequisites
- Android Studio installed
- Android SDK 34
- Java 8+
- Gradle 8.0+

### Quick Build
```bash
# Make gradlew executable
chmod +x gradlew

# Build debug APK
./gradlew assembleDebug

# Build release APK  
./gradlew assembleRelease
```

### Build Output
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk` (~5-10MB)
- **Release APK**: `app/build/outputs/apk/release/app-release.apk` (~3-8MB)

### Install on Device
```bash
# Install via ADB
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Model Download (NEW)

The app now **dynamically downloads** the AI model after installation to keep APK size small:

### ✅ What's Changed
- **No model bundled** in APK (keeps size small ~10MB)
- **Automatic download** after app first launch
- **Model stored** in app's internal storage
- **Resume capability** if download interrupted

### 📱 User Experience
1. Install MiniJarvis APK (~10MB)
2. Open app - shows "Model not downloaded"
3. Tap "Download Model (2GB)" button
4. Background download with progress notification
5. After download completes - AI is ready!

### 🔧 Technical Implementation
- **ModelDownloadService**: Foreground service for downloading
- **Progress notifications**: Shows download progress
- **Resumable**: Continues if interrupted
- **Storage**: Downloads to `/data/data/com.minijarvis.app/files/`
- **URL**: Downloads from HuggingFace mirror

### 📊 Size Comparison
- **Old (Bundled model)**: ~2GB+ APK
- **New (Dynamic download)**: ~10MB APK

## Build Variants

### Debug Build
```bash
./gradlew assembleDebug
```
- Includes debug symbols
- APK size: ~10-15MB
- Ready for testing

### Release Build
```bash
./gradlew assembleRelease
```
- Optimized and minified
- APK size: ~5-10MB
- Ready for production

## Requirements Met

✅ **Small APK size** - Model not bundled  
✅ **Dynamic download** - Downloads 2GB model separately  
✅ **User control** - Button to trigger download  
✅ **Progress tracking** - Shows download progress  
✅ **Background operation** - Foreground service  
✅ **Resume capability** - Handles interruptions  

The APK will now be much smaller and download the AI model only when needed!