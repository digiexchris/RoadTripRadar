# Weather Radar App

An Android app built with Ionic React that displays weather radar similar to aircraft weather radar systems. The map rotates to keep your heading at the top of the screen.

## Features

- ✅ Real-time GPS tracking with heading calculation
- ✅ Rotating map display (heading always up)
- ✅ Speed and heading indicators
- ✅ Zoom controls
- ✅ OpenStreetMap integration
- ✅ Weather radar overlay (OpenWeatherMap)
- ✅ Radar animation (5 frames over 30 minutes)
- ✅ Manual refresh control

## Prerequisites

- Node.js (v18+)
- Java JDK 17 or higher
- Android Studio
- Android SDK (API 29+)

## Running the App

### Quick Build (Recommended)

**Windows (PowerShell):**
```powershell
.\build.ps1
```

**Linux/Mac (Bash):**
```bash
chmod +x build.sh  # First time only
./build.sh
```

For detailed build options and troubleshooting, see [BUILD.md](BUILD.md).

### Development (Web Preview)
```bash
npm start
```
Note: GPS features won't work in browser preview.

### Manual Build for Android
```bash
npm run build
npx cap sync android
npx cap open android
```

Then use Android Studio to:
1. Build the APK
2. Run on an emulator or physical device

## Project Structure

```
src/
├── components/
│   ├── RadarMap.tsx          # Main map component with rotation
│   ├── RadarMap.css
│   ├── RadarControls.tsx     # UI controls and info display
│   └── RadarControls.css
├── hooks/
│   ├── useGPS.ts             # GPS tracking and heading calculation
│   └── useWeatherRadar.ts    # Weather radar data fetching
├── pages/
│   └── Home.tsx              # Main page integrating all components
└── App.tsx                    # App root
```

## How It Works

### GPS & Heading
- Uses Capacitor Geolocation API for position tracking
- Calculates heading from movement between GPS points
- Only updates heading when moving (> 0.5 m/s)
- Persists last heading during current session
- Defaults to North (0°) on app start

### Map Rotation
- Map rotates using CSS transforms
- Smooth transitions between heading changes
- Handles wrap-around (359° to 1°)
- 50km x 50km view area

### Weather Radar
- Polls OpenWeatherMap API every 5 minutes
- Displays 5 frames covering last 30 minutes
- Animates frames at 1 second intervals
- Overlay opacity: 60%

## Known Limitations & Future Improvements

### Current Limitations
1. **Radar Data**: OpenWeatherMap's free tier has limited radar imagery. Consider using:
   - RainViewer API (free, better radar data)
   - Weather.gov API (US only, free)
   - Local weather service APIs

2. **Heading Accuracy**: Heading is calculated from GPS movement, not compass
   - Best accuracy when moving
   - May be inaccurate at low speeds

3. **Battery Usage**: Continuous GPS tracking can drain battery
   - Consider adding power-saving modes
   - Option to reduce GPS update frequency

### Suggested Improvements

1. **Better Radar Source**
   ```typescript
   // Consider switching to RainViewer API
   // https://www.rainviewer.com/api.html
   ```

2. **Compass Integration**
   - Add device compass for heading when stationary
   - Combine GPS heading (when moving) + compass (when stopped)

3. **Offline Support**
   - Cache map tiles
   - Cache recent radar frames
   - Work without internet for basic map view

4. **Additional Features**
   - Multiple layer support (temperature, wind, clouds)
   - Time scrubber to view past/future radar
   - Alerts for severe weather
   - Route planning with weather overlay

5. **Performance**
   - Optimize map rendering
   - Reduce bundle size
   - Add service worker for caching

## Building for Production

### Debug APK
```bash
cd android
./gradlew assembleDebug
```
Output: `android/app/build/outputs/apk/debug/app-debug.apk`

### Release APK
1. Configure signing in `android/app/build.gradle`
2. Build:
```bash
cd android
./gradlew assembleRelease
```

## Troubleshooting

### GPS Not Working
- Check location permissions in Android settings
- Ensure location services are enabled
- Try on a physical device (emulator GPS can be unreliable)

### Map Not Displaying
- Check internet connection
- Verify OpenStreetMap tiles are accessible
- Check browser console for errors

### Radar Not Showing
- Verify OpenWeatherMap API is accessible
- Check API key if using premium tier
- Monitor console for API errors

## Permissions

The app requires:
- `ACCESS_FINE_LOCATION` - High-accuracy GPS
- `ACCESS_COARSE_LOCATION` - Network-based location
- `INTERNET` - Map tiles and radar data

## License

This project is for educational/demonstration purposes.

## Credits

- Ionic Framework
- React
- Leaflet & OpenStreetMap
- Capacitor
- OpenWeatherMap
