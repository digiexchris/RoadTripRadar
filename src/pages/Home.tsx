import { useState, useEffect, useRef } from 'react';
import { IonContent, IonPage, IonText } from '@ionic/react';
import { ScreenOrientation } from '@capacitor/screen-orientation';
import { RadarMap } from '../components/RadarMap';
import { RadarControls } from '../components/RadarControls';
import { InfoBar } from '../components/InfoBar';
import { RadarTimeline } from '../components/RadarTimeline';
import { useGPS } from '../hooks/useGPS';
import { useCompass } from '../hooks/useCompass';
import { useWeatherRadar } from '../hooks/useWeatherRadar';
import { useSettings } from '../contexts/SettingsContext';
import './Home.css';

const Home: React.FC = () => {
  const { settings } = useSettings();
  const { heading: compassHeading } = useCompass();
  const { gpsData, error: gpsError, isTracking } = useGPS({
    compassHeading: settings.useCompassRotation ? compassHeading : null,
    movementThreshold: settings.movementThreshold,
  });
  const [zoom, setZoom] = useState(settings.defaultZoom);
  const [isTrackingMode, setIsTrackingMode] = useState(true);
  const [showBaseMap, setShowBaseMap] = useState(true);
  const [showRadar, setShowRadar] = useState(true);
  const [showRangeRings, setShowRangeRings] = useState(true);
  const [radarOpacity, setRadarOpacity] = useState(0.6);
  const [isLandscape, setIsLandscape] = useState(settings.defaultOrientation === 'landscape');
  const [radarMode, setRadarMode] = useState<'history' | 'now'>(settings.defaultRadarMode);
  const wakeLockRef = useRef<WakeLockSentinel | null>(null);

  // Lock screen orientation on mount and when toggled
  useEffect(() => {
    const lockOrientation = async () => {
      try {
        await ScreenOrientation.lock({
          orientation: isLandscape ? 'landscape' : 'portrait',
        });
      } catch (err) {
        console.warn('Screen orientation lock failed:', err);
      }
    };
    lockOrientation();
  }, [isLandscape]);

  // Set default orientation from settings on mount
  useEffect(() => {
    setIsLandscape(settings.defaultOrientation === 'landscape');
  }, [settings.defaultOrientation]);

  // Keep screen on based on setting
  useEffect(() => {
    const requestWakeLock = async () => {
      if (settings.keepScreenOn && 'wakeLock' in navigator) {
        try {
          wakeLockRef.current = await navigator.wakeLock.request('screen');
        } catch (err) {
          console.warn('Wake Lock request failed:', err);
        }
      } else if (!settings.keepScreenOn && wakeLockRef.current) {
        await wakeLockRef.current.release();
        wakeLockRef.current = null;
      }
    };

    requestWakeLock();

    // Re-acquire wake lock when page becomes visible again
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible' && settings.keepScreenOn) {
        requestWakeLock();
      }
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      if (wakeLockRef.current) {
        wakeLockRef.current.release();
        wakeLockRef.current = null;
      }
    };
  }, [settings.keepScreenOn]);

  // Update zoom when default changes in settings
  useEffect(() => {
    if (isTrackingMode) {
      setZoom(settings.defaultZoom);
    }
  }, [settings.defaultZoom, isTrackingMode]);

  const { radarData, currentFrameIndex, error: radarError, refresh } = useWeatherRadar(
    gpsData?.latitude || null,
    gpsData?.longitude || null,
    settings.frameCount,
    settings.playbackSpeed,
    radarMode === 'now'
  );

  const handleZoomIn = () => {
    setZoom((prev) => Math.min(prev + 1, 18));
  };

  const handleZoomOut = () => {
    setZoom((prev) => Math.max(prev - 1, 8));
  };

  const handleToggleManualMode = () => {
    setIsTrackingMode(false);
  };

  const handleReCenter = () => {
    // Re-center and rotate, but keep current zoom level
    setIsTrackingMode(true);
  };



  const handleToggleOrientation = () => {
    setIsLandscape(!isLandscape);
  };

  const handleToggleRadarMode = () => {
    setRadarMode((prev) => (prev === 'history' ? 'now' : 'history'));
  };

  const currentRadarFrame = radarData?.frames[currentFrameIndex];

  return (
    <IonPage>
      <IonContent fullscreen className="radar-content">
        {gpsData ? (
          <div className={`radar-container ${isLandscape ? 'landscape' : 'portrait'}`}>
            {/* Info Area: instruments + timeline */}
            <div className="info-area">
              <InfoBar
                heading={gpsData.heading}
                speed={gpsData.speed}
                isTracking={isTracking}
                isTrackingMode={isTrackingMode}
              />
              {radarData && radarData.frames.length > 0 && (
                <RadarTimeline
                  frames={radarData.frames}
                  currentFrameIndex={currentFrameIndex}
                  radarMode={radarMode}
                  onToggleMode={handleToggleRadarMode}
                />
              )}
            </div>
            {/* Map Viewport: map + button overlay */}
            <div className="map-viewport">
              <RadarMap
                latitude={gpsData.latitude}
                longitude={gpsData.longitude}
                heading={gpsData.heading}
                zoom={zoom}
                radarImageUrl={currentRadarFrame?.url}
                isTrackingMode={isTrackingMode}
                showBaseMap={showBaseMap}
                showRadar={showRadar}
                showRangeRings={showRangeRings}
                radarOpacity={radarOpacity}
                isLandscape={isLandscape}
              />
              <RadarControls
                onZoomIn={handleZoomIn}
                onZoomOut={handleZoomOut}
                onRefresh={refresh}
                onReCenter={handleReCenter}
                onToggleManualMode={handleToggleManualMode}
                isTrackingMode={isTrackingMode}
                showBaseMap={showBaseMap}
                onToggleBaseMap={() => setShowBaseMap(!showBaseMap)}
                showRadar={showRadar}
                onToggleRadar={() => setShowRadar(!showRadar)}
                showRangeRings={showRangeRings}
                onToggleRangeRings={() => setShowRangeRings(!showRangeRings)}
                radarOpacity={radarOpacity}
                onRadarOpacityChange={setRadarOpacity}
                onToggleOrientation={handleToggleOrientation}
                isLandscape={isLandscape}
              />
            </div>
          </div>
        ) : (
          <div className="loading-container">
            <IonText color="medium">
              <h2>Initializing GPS...</h2>
              {gpsError && <p className="error-text">{gpsError}</p>}
            </IonText>
          </div>
        )}
        {radarError && (
          <div className="radar-error">
            <IonText color="warning">
              <small>Radar: {radarError}</small>
            </IonText>
          </div>
        )}
      </IonContent>
    </IonPage>
  );
};

export default Home;
