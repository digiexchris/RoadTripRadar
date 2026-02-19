# Build Instructions

This document explains how to build the Weather Radar app.

## Quick Start

### Windows (PowerShell)

```powershell
.\build.ps1
```

### Linux/Mac (Bash)

```bash
chmod +x build.sh  # First time only
./build.sh
```

## Build Script Options

### PowerShell (Windows)

```powershell
# Basic build
.\build.ps1

# Build and open Android Studio
.\build.ps1 -OpenAndroidStudio

# Skip npm build, just sync to Android
.\build.ps1 -SkipBuild

# Show help
.\build.ps1 -Help
```

### Bash (Linux/Mac)

```bash
# Basic build
./build.sh

# Build and open Android Studio
./build.sh --open

# Skip npm build, just sync to Android
./build.sh --skip-build

# Show help
./build.sh --help
```

## Manual Build Steps

If you prefer to build manually or need to troubleshoot:

### 1. Build the React App

```bash
npm run build
```

This compiles your TypeScript/React code and creates optimized production files in the `dist` folder.

### 2. Sync with Android

```bash
npx cap sync android
```

This copies the web assets to the Android project and updates Capacitor plugins.

### 3. Open in Android Studio

**Option A: Command Line**
```bash
npx cap open android
```

**Option B: Manual**
1. Open Android Studio
2. Click "Open an Existing Project"
3. Navigate to the `android` folder in this project
4. Select it and click "OK"

### 4. Build APK in Android Studio

Once Android Studio opens:

1. Wait for Gradle sync to complete
2. Click the green "Run" button (▶) to build and run on a connected device/emulator

**OR** build an APK:

**Debug APK:**
```bash
cd android
./gradlew assembleDebug
```
Output: `android/app/build/outputs/apk/debug/app-debug.apk`

**Release APK:**
```bash
cd android
./gradlew assembleRelease
```
Output: `android/app/build/outputs/apk/release/app-release.apk`

## Prerequisites

Make sure you have installed:

- ✅ **Node.js** (v18+) - `node -v`
- ✅ **npm** - `npm -v`
- ✅ **Java JDK 17** - `java -version`
- ✅ **Android Studio** with Android SDK

## Troubleshooting

### "npm not found"
- Restart your terminal after installing Node.js
- Ensure Node.js is in your PATH

### "Gradle error"
- Open Android Studio
- File → Sync Project with Gradle Files
- Wait for sync to complete

### "Permission denied" (Linux/Mac)
```bash
chmod +x build.sh
```

### Build fails
1. Check errors in terminal
2. Delete `node_modules` and run `npm install`
3. Delete `android/build` and sync again
4. Check that all prerequisites are installed

## Development Workflow

For active development:

1. **Web development/testing:**
   ```bash
   npm start
   ```
   Opens in browser for quick testing (GPS won't work in browser)

2. **Build for Android:**
   ```bash
   .\build.ps1  # or ./build.sh
   ```

3. **Test on device:**
   - Open Android Studio
   - Click Run
   - Select your device/emulator

## File Locations

- **Web assets source:** `src/`
- **Built web assets:** `dist/`
- **Android project:** `android/`
- **APK outputs:** `android/app/build/outputs/apk/`

## Environment Variables

To add an OpenWeatherMap API key (optional):

Edit `src/hooks/useWeatherRadar.ts` if you want to switch to OpenWeatherMap instead of RainViewer.

Currently using RainViewer which requires no API key.
