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
    const [localSettings, setLocalSettings] = useState(settings);
    const [showTerms, setShowTerms] = useState(false);

    const handleSave = async () => {
        await updateSettings(localSettings);
    };

    const handleReset = () => {
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
        };
        setLocalSettings(defaults);
    };

    return (
        <IonPage>
            <IonHeader>
                <IonToolbar>
                    <IonButtons slot="start">
                        <IonBackButton defaultHref="/home" />
                    </IonButtons>
                    <IonTitle>Settings</IonTitle>
                    <IonButtons slot="end">
                        <IonButton onClick={handleSave} color="primary">
                            Save
                        </IonButton>
                    </IonButtons>
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
                            min={8}
                            max={18}
                            step={1}
                            value={localSettings.defaultZoom}
                            pin={true}
                            onIonChange={(e) =>
                                setLocalSettings({ ...localSettings, defaultZoom: e.detail.value as number })
                            }
                        >
                            <IonLabel slot="start">8</IonLabel>
                            <IonLabel slot="end">18</IonLabel>
                        </IonRange>
                    </IonItem>
                    <IonItem lines="none">
                        <IonNote slot="end">Current: {localSettings.defaultZoom}</IonNote>
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
                            value={localSettings.frameCount}
                            pin={true}
                            snaps={true}
                            ticks={true}
                            onIonChange={(e) =>
                                setLocalSettings({ ...localSettings, frameCount: e.detail.value as number })
                            }
                        >
                            <IonLabel slot="start">1</IonLabel>
                            <IonLabel slot="end">10</IonLabel>
                        </IonRange>
                    </IonItem>
                    <IonItem lines="none">
                        <IonNote slot="end">
                            {localSettings.frameCount === 1 ? 'Current only' : `${localSettings.frameCount} frames`}
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
                            value={localSettings.playbackSpeed}
                            pin={true}
                            onIonChange={(e) =>
                                setLocalSettings({ ...localSettings, playbackSpeed: e.detail.value as number })
                            }
                        >
                            <IonLabel slot="start">0.5s</IonLabel>
                            <IonLabel slot="end">5.0s</IonLabel>
                        </IonRange>
                    </IonItem>
                    <IonItem lines="none">
                        <IonNote slot="end">{localSettings.playbackSpeed.toFixed(1)}s per frame</IonNote>
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
                            value={localSettings.movementThreshold}
                            pin={true}
                            snaps={true}
                            onIonChange={(e) =>
                                setLocalSettings({ ...localSettings, movementThreshold: e.detail.value as number })
                            }
                        >
                            <IonLabel slot="start">5m</IonLabel>
                            <IonLabel slot="end">50m</IonLabel>
                        </IonRange>
                    </IonItem>
                    <IonItem lines="none">
                        <IonNote slot="end">{localSettings.movementThreshold} meters</IonNote>
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
                            value={localSettings.speedUnit}
                            onIonChange={(e) =>
                                setLocalSettings({ ...localSettings, speedUnit: e.detail.value })
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
                            checked={localSettings.useCompassRotation}
                            onIonChange={(e) =>
                                setLocalSettings({ ...localSettings, useCompassRotation: e.detail.checked })
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
                            checked={localSettings.keepScreenOn}
                            onIonChange={(e) =>
                                setLocalSettings({ ...localSettings, keepScreenOn: e.detail.checked })
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
                            value={localSettings.mapCenterPosition}
                            pin={true}
                            snaps={true}
                            onIonChange={(e) =>
                                setLocalSettings({ ...localSettings, mapCenterPosition: e.detail.value as number })
                            }
                        >
                            <IonLabel slot="start">Bottom</IonLabel>
                            <IonLabel slot="end">Top</IonLabel>
                        </IonRange>
                    </IonItem>
                    <IonItem lines="none">
                        <IonNote slot="end">{localSettings.mapCenterPosition}% ahead</IonNote>
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
                            value={localSettings.defaultOrientation}
                            onIonChange={(e) =>
                                setLocalSettings({ ...localSettings, defaultOrientation: e.detail.value })
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
                            value={localSettings.defaultRadarMode}
                            onIonChange={(e) =>
                                setLocalSettings({ ...localSettings, defaultRadarMode: e.detail.value })
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
