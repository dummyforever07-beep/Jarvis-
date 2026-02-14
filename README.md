# MiniJarvis Android App

Fully offline AI assistant for Android automation using local LLM and Accessibility Service.

## Features

- **Offline AI**: Uses Google Gemma 2B model (4-bit quantized GGUF)
- **Dynamic Download**: Model downloads after installation (small APK)
- **Accessibility Automation**: Read UI elements and perform actions
- **Floating Interface**: Minimal floating button for activation
- **Lightweight**: Optimized for 4GB RAM devices
- **Performance**: No continuous background inference

## Quick Start

1. **Build APK**: `./gradlew assembleDebug`
2. **Install**: `adb install app-debug.apk`
3. **Open App**: Grant permissions
4. **Download Model**: Tap "Download Model" button
5. **Ready**: Start using AI automation!

## Architecture

### 1. UI Extraction Layer
- Uses Android Accessibility API
- Extracts visible clickable elements, text fields, focused elements
- Converts to structured JSON

### 2. LLM Prompt Engine
- System prompt optimized for Android automation
- Strict JSON output format
- Temperature 0.2, 120 max tokens, 1024 context

### 3. Action Execution Layer
- Validates actions against current UI
- Executes using Accessibility Service
- 500ms delays between actions
- Throttle prevention

### 4. Trigger System
- Floating button activation
- No continuous inference loop

## Setup Instructions

### Prerequisites
- Android Studio Arctic Fox or newer
- Android SDK 24+ (minimum), 34 (target)
- Java 8+
- NDK and CMake (for native code)

### 1. Download Gemma 2B Model
```bash
# Download Google Gemma 2B quantized model
wget https://huggingface.co/leliuga/ggml-gemma-2b-v1-q4_0/resolve/main/gemma-2b-v1-q4_0.gguf

# Copy to app/src/main/assets/
cp gemma-2b-v1-q4_0.gguf app/src/main/assets/
```

### 2. Build Native Libraries
```bash
# Install NDK (if not using Android Studio)
# Build llama.cpp for Android

# Place compiled libraries in app/src/main/jniLibs/
# arm64-v8a/libllama.so
# armeabi-v7a/libllama.so
```

### 3. Build with Gradle
```bash
# Make gradlew executable
chmod +x gradlew

# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

### 4. Install on Device
```bash
# Install via ADB
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Permissions Required

1. **Accessibility Service** (mandatory)
   - Grant in Settings > Accessibility
   - Allows reading UI elements and performing actions

2. **Overlay Permission** (for floating button)
   - Grant in Settings > Apps > MiniJarvis > Draw over other apps

3. **Foreground Service** (for background operation)
   - Automatically requested by the app

4. **Battery Optimization** (optional)
   - Disable for reliable background operation

## Usage

1. **Grant Permissions**
   - Open app and follow permission prompts
   - Enable accessibility service
   - Enable overlay permission

2. **Start Service**
   - Tap "Start Service" button
   - Floating button appears in top-right corner

3. **Issue Commands**
   - Tap floating button to activate
   - Speak or type command (e.g., "click Search", "type Hello")
   - Emergency stop button available

4. **Debug Mode**
   - Main activity shows UI JSON and model output
   - Monitor actions and troubleshooting

## Commands

### Click Actions
- "click [element]"
- "tap [element]"
- "press [element]"

### Text Input
- "type [text]"
- "send [message]"
- "write [text] in [field]"

### Navigation
- "scroll down"
- "scroll up"
- "go back"
- "open [app_name]"

## Configuration

### Model Settings (in LLMEngine.java)
```java
private static final String MODEL_FILE = "gemma-2b-q4_0.gguf";
private static final int CONTEXT_SIZE = 1024;
private static final float TEMPERATURE = 0.2f;
private static final int MAX_TOKENS = 120;
```

### Performance Tuning
- Adjust context size for memory/performance tradeoff
- Modify temperature for response creativity
- Configure action delays in ActionExecutor

## Development Notes

### Mock Mode
The app includes `MockLLMEngine` for testing without the actual model:
- Provides rule-based responses
- Simulates LLM behavior
- Useful for development and debugging

### Native Integration
For production, replace `MockLLMEngine` with `LLMEngine`:
1. Build llama.cpp with Android NDK
2. Include .so files in jniLibs
3. Enable `LLMEngine` initialization

### Troubleshooting

**Model not loading:**
- Check model file exists in assets/
- Verify file format (GGUF)
- Check device storage space

**Accessibility not working:**
- Verify permission granted
- Check service enabled in accessibility settings
- Try restarting accessibility service

**Floating button not appearing:**
- Grant overlay permission
- Check overlay settings in Android settings
- Restart the service

## File Structure

```
app/
├── src/main/
│   ├── java/com/minijarvis/app/
│   │   ├── MiniJarvisApplication.java
│   │   ├── ui/MainActivity.java
│   │   ├── service/FloatingButtonService.java
│   │   ├── accessibility/MiniJarvisAccessibilityService.java
│   │   ├── llm/LLMEngine.java
│   │   ├── llm/MockLLMEngine.java
│   │   ├── util/ActionExecutor.java
│   │   └── model/ (ActionModel, UIStructure)
│   ├── res/ (layouts, drawables, strings)
│   ├── assets/ (model files)
│   └── jni/ (native code)
└── build.gradle
```

## Performance Considerations

- **Memory**: Model loaded once, reused
- **CPU**: Inference only on user request
- **Battery**: Foreground service, no continuous processing
- **Storage**: Minimal app size, model file separate
- **Network**: Fully offline, no cloud dependencies

## Security & Privacy

- **Local Processing**: All inference on-device
- **No Data Collection**: No cloud uploads
- **Minimal Permissions**: Only required permissions
- **Secure**: No network communications

## Contributing

1. Fork the repository
2. Create feature branch
3. Test on target devices
4. Submit pull request

## License

See LICENSE file for details.