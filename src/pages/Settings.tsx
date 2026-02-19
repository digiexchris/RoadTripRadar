import React, { useState } from 'react';
import {
    IonPage,
    IonHeader,
    IonToolbar,
    IonTitle,
    IonContent,
    IonList,
    IonItem,
    IonLabel,
    IonRange,
    IonButton,
    IonButtons,
    IonBackButton,
    IonNote,
    IonSelect,
    IonSelectOption,
    IonToggle,
    IonModal,
} from '@ionic/react';
import { useSettings } from '../contexts/SettingsContext';
import TermsContent from '../components/TermsContent';
import './Settings.css';

const Settings: React.FC = () => {
    const { settings, updateSettings } = useSettings();
    const [showTerms, setShowTerms] = useState(false);

    const handleReset = async () => {
        const defaults = {
            defaultZoom: 7,
            frameCount: 5,
            playbackSpeed: 1.0,
            movementThreshold: 10,
            speedUnit: 'kmh' as 'kmh' | 'mph',
            keepScreenOn: false,
            mapCenterPosition: 33,
            defaultOrientation: 'portrait' as 'portrait' | 'landscape',
            defaultRadarMode: 'history' as 'history' | 'now',
            useCompassRotation: false,
            motionSensitivity: 3,
        };
        await updateSettings(defaults);
    };

    return (
        <IonPage>
            <IonHeader>
                <IonToolbar>
                    <IonButtons slot="start">
                        <IonBackButton defaultHref="/home" />
                    </IonButtons>
                    <IonTitle>Settings</IonTitle>
                </IonToolbar>
            </IonHeader>
            <IonContent className="settings-content">
                <IonList>
                    {/* Default Zoom */}
                    <IonItem>
                        <IonLabel>
                            <h2>Default Zoom Level</h2>
                            <p>Set the default map zoom when app starts</p>
                        </IonLabel>
                    </IonItem>
                    <IonItem>
                        <IonRange
                            min={0}
                            max={18}
                            step={1}
                            value={settings.defaultZoom}
                            pin={true}
                            onIonChange={(e) =>
                                updateSettings({ ...settings, defaultZoom: e.detail.value as number })
                            }
                        >
                            <IonLabel slot="start">0</IonLabel>
                            <IonLabel slot="end">18</IonLabel>
                        </IonRange>
                    </IonItem>
                    <IonItem lines="none">
                        <IonNote slot="end">Current: {settings.defaultZoom}</IonNote>
                    </IonItem>

                    {/* Frame Count */}
                    <IonItem className="setting-item-header">
                        <IonLabel>
                            <h2>Radar History Frames</h2>
                            <p>Number of past radar frames to display (1 = current only)</p>
                        </IonLabel>
                    </IonItem>
                    <IonItem>
                        <IonRange
                            min={1}
                            max={10}
                            step={1}
                            value={settings.frameCount}
                            pin={true}
                            snaps={true}
                            ticks={true}
                            onIonChange={(e) =>
                                updateSettings({ ...settings, frameCount: e.detail.value as number })
                            }
                        >
                            <IonLabel slot="start">1</IonLabel>
                            <IonLabel slot="end">10</IonLabel>
                        </IonRange>
                    </IonItem>
                    <IonItem lines="none">
                        <IonNote slot="end">
                            {settings.frameCount === 1 ? 'Current only' : `${settings.frameCount} frames`}
                        </IonNote>
                    </IonItem>

                    {/* Playback Speed */}
                    <IonItem className="setting-item-header">
                        <IonLabel>
                            <h2>Animation Speed</h2>
                            <p>Seconds per frame in radar animation</p>
                        </IonLabel>
                    </IonItem>
                    <IonItem>
                        <IonRange
                            min={0.5}
                            max={5.0}
                            step={0.1}
                            value={settings.playbackSpeed}
                            pin={true}
                            onIonChange={(e) =>
                                updateSettings({ ...settings, playbackSpeed: e.detail.value as number })
                            }
                        >
                            <IonLabel slot="start">0.5s</IonLabel>
                            <IonLabel slot="end">5.0s</IonLabel>
                        </IonRange>
                    </IonItem>
                    <IonItem lines="none">
                        <IonNote slot="end">{settings.playbackSpeed.toFixed(1)}s per frame</IonNote>
                    </IonItem>

                    {/* Movement Threshold */}
                    <IonItem className="setting-item-header">
                        <IonLabel>
                            <h2>Movement Threshold</h2>
                            <p>Distance to travel before using GPS heading instead of compass</p>
                        </IonLabel>
                    </IonItem>
                    <IonItem>
                        <IonRange
                            min={5}
                            max={50}
                            step={5}
                            value={settings.movementThreshold}
                            pin={true}
                            snaps={true}
                            onIonChange={(e) =>
                                updateSettings({ ...settings, movementThreshold: e.detail.value as number })
                            }
                        >
                            <IonLabel slot="start">5m</IonLabel>
                            <IonLabel slot="end">50m</IonLabel>
                        </IonRange>
                    </IonItem>
                    <IonItem lines="none">
                        <IonNote slot="end">{settings.movementThreshold} meters</IonNote>
                    </IonItem>

                    {/* Motion Sensitivity */}
                    <IonItem className="setting-item-header">
                        <IonLabel>
                            <h2>Motion Detection Sensitivity</h2>
                            <p>How quickly the app switches between moving and stationary modes</p>
                        </IonLabel>
                    </IonItem>
                    <IonItem>
                        <IonRange
                            min={1}
                            max={5}
                            step={1}
                            value={settings.motionSensitivity}
                            pin={true}
                            snaps={true}
                            onIonChange={(e) =>
                                updateSettings({ ...settings, motionSensitivity: e.detail.value as number })
                            }
                        >
                            <IonLabel slot="start">Fast</IonLabel>
                            <IonLabel slot="end">Stable</IonLabel>
                        </IonRange>
                    </IonItem>
                    <IonItem lines="none">
                        <IonNote slot="end">
                            {settings.motionSensitivity === 1 && 'Very Sensitive (instant)'}
                            {settings.motionSensitivity === 2 && 'Sensitive (quick)'}
                            {settings.motionSensitivity === 3 && 'Normal (balanced)'}
                            {settings.motionSensitivity === 4 && 'Less Sensitive (stable)'}
                            {settings.motionSensitivity === 5 && 'Least Sensitive (very stable)'}
                        </IonNote>
                    </IonItem>

                    {/* Speed Unit */}
                    <IonItem className="setting-item-header">
                        <IonLabel>
                            <h2>Speed Unit</h2>
                            <p>Display speed in kilometers per hour or miles per hour</p>
                        </IonLabel>
                    </IonItem>
                    <IonItem>
                        <IonLabel>Unit</IonLabel>
                        <IonSelect
                            value={settings.speedUnit}
                            onIonChange={(e) =>
                                updateSettings({ ...settings, speedUnit: e.detail.value })
                            }
                        >
                            <IonSelectOption value="kmh">km/h</IonSelectOption>
                            <IonSelectOption value="mph">mph</IonSelectOption>
                        </IonSelect>
                    </IonItem>
                    {/* Compass Rotation */}
                    <IonItem className="setting-item-header">
                        <IonLabel>
                            <h2>Compass Rotation</h2>
                            <p>Rotate map using compass when stationary (off = keep last GPS heading)</p>
                        </IonLabel>
                        <IonToggle
                            slot="end"
                            checked={settings.useCompassRotation}
                            onIonChange={(e) =>
                                updateSettings({ ...settings, useCompassRotation: e.detail.checked })
                            }
                        />
                    </IonItem>

                    {/* Keep Screen On */}
                    <IonItem className="setting-item-header">
                        <IonLabel>
                            <h2>Keep Screen On</h2>
                            <p>Prevent the screen from turning off while the app is open</p>
                        </IonLabel>
                        <IonToggle
                            slot="end"
                            checked={settings.keepScreenOn}
                            onIonChange={(e) =>
                                updateSettings({ ...settings, keepScreenOn: e.detail.checked })
                            }
                        />
                    </IonItem>

                    {/* Map Center Position */}
                    <IonItem className="setting-item-header">
                        <IonLabel>
                            <h2>Map Center Position</h2>
                            <p>Percentage of map visible ahead of your location (higher = dot closer to top)</p>
                        </IonLabel>
                    </IonItem>
                    <IonItem>
                        <IonRange
                            min={10}
                            max={90}
                            step={5}
                            value={settings.mapCenterPosition}
                            pin={true}
                            snaps={true}
                            onIonChange={(e) =>
                                updateSettings({ ...settings, mapCenterPosition: e.detail.value as number })
                            }
                        >
                            <IonLabel slot="start">Bottom</IonLabel>
                            <IonLabel slot="end">Top</IonLabel>
                        </IonRange>
                    </IonItem>
                    <IonItem lines="none">
                        <IonNote slot="end">{settings.mapCenterPosition}% ahead</IonNote>
                    </IonItem>

                    {/* Default Orientation */}
                    <IonItem className="setting-item-header">
                        <IonLabel>
                            <h2>Default Orientation</h2>
                            <p>Screen orientation when the app starts</p>
                        </IonLabel>
                    </IonItem>
                    <IonItem>
                        <IonLabel>Orientation</IonLabel>
                        <IonSelect
                            value={settings.defaultOrientation}
                            onIonChange={(e) =>
                                updateSettings({ ...settings, defaultOrientation: e.detail.value })
                            }
                        >
                            <IonSelectOption value="portrait">Portrait</IonSelectOption>
                            <IonSelectOption value="landscape">Landscape</IonSelectOption>
                        </IonSelect>
                    </IonItem>

                    {/* Default Radar Mode */}
                    <IonItem className="setting-item-header">
                        <IonLabel>
                            <h2>Default Radar Mode</h2>
                            <p>Start with animated history or current-only radar</p>
                        </IonLabel>
                    </IonItem>
                    <IonItem>
                        <IonLabel>Mode</IonLabel>
                        <IonSelect
                            value={settings.defaultRadarMode}
                            onIonChange={(e) =>
                                updateSettings({ ...settings, defaultRadarMode: e.detail.value })
                            }
                        >
                            <IonSelectOption value="history">History (Animated)</IonSelectOption>
                            <IonSelectOption value="now">Now (Current Only)</IonSelectOption>
                        </IonSelect>
                    </IonItem>
                </IonList>

                <div className="settings-actions">
                    <IonButton expand="block" onClick={handleReset} fill="outline" color="medium">
                        Reset to Defaults
                    </IonButton>
                    <IonButton expand="block" onClick={() => setShowTerms(true)} fill="outline" color="secondary" style={{ marginTop: '0.75rem' }}>
                        View Terms &amp; Conditions
                    </IonButton>
                </div>

                <IonModal isOpen={showTerms} onDidDismiss={() => setShowTerms(false)}>
                    <IonPage>
                        <IonHeader>
                            <IonToolbar>
                                <IonTitle>Terms &amp; Conditions</IonTitle>
                                <IonButtons slot="end">
                                    <IonButton onClick={() => setShowTerms(false)}>Close</IonButton>
                                </IonButtons>
                            </IonToolbar>
                        </IonHeader>
                        <IonContent className="ion-padding">
                            <TermsContent />
                        </IonContent>
                    </IonPage>
                </IonModal>
            </IonContent>
        </IonPage>
    );
};

export default Settings;
