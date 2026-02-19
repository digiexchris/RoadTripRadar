# RoadTripRadar - Build Script (PowerShell)
# This script builds the web app and syncs it with the Android project.
# Debug vs release is controlled by Android Studio's build variant selector.

param(
    [switch]$OpenAndroidStudio,
    [switch]$SkipBuild,
    [switch]$Help
)

function Show-Help {
    Write-Host @"
RoadTripRadar - Build Script

Usage: .\build.ps1 [options]

Options:
    -OpenAndroidStudio    Open Android Studio after syncing
    -SkipBuild           Skip the npm build step (useful if already built)
    -Help                Show this help message

Debug vs Release:
    Use Android Studio's Build Variants panel to switch between
    debug and release. WebView debugging is automatically enabled
    in debug builds and disabled in release builds.

Examples:
    .\build.ps1                           # Build and sync
    .\build.ps1 -OpenAndroidStudio        # Build, sync, and open Android Studio
    .\build.ps1 -SkipBuild                # Skip build, just sync to Android

"@
    exit 0
}

if ($Help) {
    Show-Help
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  RoadTripRadar - Build Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if we're in the correct directory
if (-not (Test-Path "package.json")) {
    Write-Host "Error: package.json not found!" -ForegroundColor Red
    Write-Host "Please run this script from the weather-radar directory" -ForegroundColor Yellow
    exit 1
}

# Step 1: Build the React app
if (-not $SkipBuild) {
    Write-Host "[1/3] Building React app..." -ForegroundColor Green
    npm run build
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Build failed!" -ForegroundColor Red
        exit 1
    }
    Write-Host "Build completed successfully!" -ForegroundColor Green
    Write-Host ""
}
else {
    Write-Host "[1/3] Skipping build step..." -ForegroundColor Yellow
    Write-Host ""
}

# Step 2: Sync with Capacitor
Write-Host "[2/3] Syncing with Android platform..." -ForegroundColor Green
npx cap sync android
if ($LASTEXITCODE -ne 0) {
    Write-Host "Sync failed!" -ForegroundColor Red
    exit 1
}
Write-Host "Sync completed successfully!" -ForegroundColor Green
Write-Host ""

# Step 3: Optionally open Android Studio
if ($OpenAndroidStudio) {
    Write-Host "[3/3] Opening Android Studio..." -ForegroundColor Green
    npx cap open android
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Warning: Could not open Android Studio automatically" -ForegroundColor Yellow
        Write-Host "You can manually open: android/" -ForegroundColor Yellow
    }
}
else {
    Write-Host "[3/3] Done!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Cyan
    Write-Host "  1. Open Android Studio" -ForegroundColor White
    Write-Host "  2. Open the 'android' folder in this project" -ForegroundColor White
    Write-Host "  3. Click the Run button to build and deploy" -ForegroundColor White
    Write-Host ""
    Write-Host "Or run: .\build.ps1 -OpenAndroidStudio" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Build process completed!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
