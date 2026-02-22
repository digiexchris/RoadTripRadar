import React, { useRef } from 'react';
import { IonText } from '@ionic/react';
import { useSettings } from '../contexts/SettingsContext';
import './InfoBar.css';

interface InfoBarProps {
    heading: number;
    speed: number; // in m/s
    isTracking: boolean;
    isTrackingMode: boolean;
}

/**
 * Track a continuous (unwrapped) rotation so CSS transitions always
 * take the shortest path across the 0°/360° boundary.
 */
function useContinuousRotation(targetDeg: number): number {
    const continuousRef = useRef(targetDeg);
    let diff = targetDeg - ((continuousRef.current % 360) + 360) % 360;
    if (diff > 180) diff -= 360;
    else if (diff < -180) diff += 360;
    continuousRef.current += diff;
    return continuousRef.current;
}

export const InfoBar: React.FC<InfoBarProps> = ({
    heading,
    speed,
    isTracking,
    isTrackingMode,
}) => {
    const { settings } = useSettings();
    const needleRotation = useContinuousRotation((-heading % 360 + 360) % 360);

    // Convert speed to selected unit
    const speedValue = settings.speedUnit === 'mph'
        ? Math.round(speed * 2.237) // m/s to mph
        : Math.round(speed * 3.6);   // m/s to km/h
    const speedLabel = settings.speedUnit === 'mph' ? 'mph' : 'km/h';

    return (
        <div className="info-bar">
            <div className="info-row">
                <div className="info-item">
                    <IonText color="medium">
                        <span className="info-label">Heading</span>
                    </IonText>
                    <IonText>
                        <h2 className="info-value">{Math.round(heading)}°</h2>
                    </IonText>
                </div>
                <div className="info-item">
                    <div className="north-arrow" style={{ '--rotation': `${needleRotation}deg` } as React.CSSProperties}>
                        <svg viewBox="0 0 24 32" width="24" height="32" className="compass-needle">
                            <polygon points="12,0 6,16 12,13 18,16" fill="#e53935" />
                            <polygon points="12,32 6,16 12,19 18,16" fill="#ccc" />
                        </svg>
                    </div>
                </div>
                <div className="info-item">
                    <IonText color="medium">
                        <span className="info-label">Speed</span>
                    </IonText>
                    <IonText>
                        <h2 className="info-value">{speedValue}</h2>
                    </IonText>
                    <IonText color="medium">
                        <span className="info-unit">{speedLabel}</span>
                    </IonText>
                </div>
            </div>
            {!isTracking && (
                <IonText color="warning">
                    <p className="status-text">Acquiring GPS signal...</p>
                </IonText>
            )}
            {!isTrackingMode && isTracking && (
                <IonText color="primary">
                    <p className="status-text">Manual control - Tap re-center to resume tracking</p>
                </IonText>
            )}
        </div>
    );
};
