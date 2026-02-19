import React, { useState } from 'react';
import {
    IonButton,
    IonIcon,
    IonModal,
    IonContent,
    IonList,
    IonItem,
    IonLabel,
    IonToggle,
    IonRange,
} from '@ionic/react';
import { addOutline, removeOutline, refreshOutline, navigateOutline, settingsOutline, layersOutline, lockOpenOutline, phonePortraitOutline, phoneLandscapeOutline, closeOutline } from 'ionicons/icons';
import { useHistory } from 'react-router-dom';
import { App } from '@capacitor/app';
import './RadarControls.css';

interface RadarControlsProps {
    onZoomIn: () => void;
    onZoomOut: () => void;
    onRefresh: () => void;
    onReCenter: () => void;
    onToggleManualMode: () => void;
    isTrackingMode: boolean;
    showBaseMap: boolean;
    onSetShowBaseMap: (v: boolean) => void;
    darkBaseMap: boolean;
    onSetDarkBaseMap: (v: boolean) => void;
    showRadar: boolean;
    onSetShowRadar: (v: boolean) => void;
    showRangeRings: boolean;
    onSetShowRangeRings: (v: boolean) => void;
    darkRings: boolean;
    onSetDarkRings: (v: boolean) => void;
    radarOpacity: number;
    onRadarOpacityChange: (opacity: number) => void;
    onToggleOrientation: () => void;
    isLandscape: boolean;
}

export const RadarControls: React.FC<RadarControlsProps> = ({
    onZoomIn,
    onZoomOut,
    onRefresh,
    onReCenter,
    onToggleManualMode,
    isTrackingMode,
    showBaseMap,
    onSetShowBaseMap,
    darkBaseMap,
    onSetDarkBaseMap,
    showRadar,
    onSetShowRadar,
    showRangeRings,
    onSetShowRangeRings,
    darkRings,
    onSetDarkRings,
    radarOpacity,
    onRadarOpacityChange,
    onToggleOrientation,
    isLandscape,
}) => {
    const history = useHistory();
    const [showLayersModal, setShowLayersModal] = useState(false);

    const handleSettings = () => {
        history.push('/settings');
    };

    const handleExit = () => {
        App.exitApp();
    };

    return (
        <div className="radar-controls">
            {/* Zoom Controls */}
            <div className="zoom-controls">
                <IonButton onClick={onZoomIn} className="zoom-button">
                    <IonIcon icon={addOutline} />
                </IonButton>
                <IonButton onClick={onZoomOut} className="zoom-button">
                    <IonIcon icon={removeOutline} />
                </IonButton>
            </div>

            {/* Manual/Tracking Mode Toggle Button */}
            <div className="mode-control">
                <IonButton
                    onClick={isTrackingMode ? onToggleManualMode : onReCenter}
                    className="mode-button"
                    color={isTrackingMode ? "medium" : "primary"}
                >
                    <IonIcon icon={isTrackingMode ? lockOpenOutline : navigateOutline} />
                </IonButton>
            </div>

            {/* Refresh Button */}
            <div className="refresh-control">
                <IonButton onClick={onRefresh} className="refresh-button">
                    <IonIcon icon={refreshOutline} />
                </IonButton>
            </div>

            {/* Settings Button */}
            <div className="settings-control">
                <IonButton onClick={handleSettings} className="settings-button">
                    <IonIcon icon={settingsOutline} />
                </IonButton>
            </div>

            {/* Layers Button */}
            <div className="layers-control">
                <IonButton
                    onClick={() => setShowLayersModal(true)}
                    className="layers-button"
                >
                    <IonIcon icon={layersOutline} />
                </IonButton>
            </div>

            {/* Orientation Toggle Button */}
            <div className="orientation-control">
                <IonButton
                    onClick={onToggleOrientation}
                    className="orientation-button"
                >
                    <IonIcon icon={isLandscape ? phonePortraitOutline : phoneLandscapeOutline} />
                </IonButton>
            </div>

            {/* Exit Button */}
            <div className="exit-control">
                <IonButton onClick={handleExit} className="exit-button" color="danger">
                    <IonIcon icon={closeOutline} />
                </IonButton>
            </div>

            {/* Layers Modal */}
            <IonModal
                isOpen={showLayersModal}
                onDidDismiss={() => setShowLayersModal(false)}
                initialBreakpoint={0.55}
                breakpoints={[0, 0.55, 0.75]}
                className="layers-modal"
                handleBehavior="cycle"
            >
                <IonContent>
                    <div className="layers-modal-title">Map Layers</div>
                    <IonList>
                        <IonItem>
                            <IonLabel>Base Map</IonLabel>
                            <IonToggle
                                slot="end"
                                checked={showBaseMap}
                                onIonChange={(e) => onSetShowBaseMap(e.detail.checked)}
                            />
                        </IonItem>
                        <IonItem>
                            <IonLabel>Dark Base Map</IonLabel>
                            <IonToggle
                                slot="end"
                                checked={darkBaseMap}
                                disabled={!showBaseMap}
                                onIonChange={(e) => onSetDarkBaseMap(e.detail.checked)}
                            />
                        </IonItem>
                        <IonItem>
                            <IonLabel>Weather Radar</IonLabel>
                            <IonToggle
                                slot="end"
                                checked={showRadar}
                                onIonChange={(e) => onSetShowRadar(e.detail.checked)}
                            />
                        </IonItem>
                        <IonItem>
                            <IonLabel>
                                <p>Radar Opacity</p>
                            </IonLabel>
                        </IonItem>
                        <IonItem>
                            <IonRange
                                min={0}
                                max={100}
                                step={5}
                                value={Math.round(radarOpacity * 100)}
                                pin={true}
                                pinFormatter={(value: number) => `${value}%`}
                                disabled={!showRadar}
                                onIonChange={(e) =>
                                    onRadarOpacityChange((e.detail.value as number) / 100)
                                }
                            />
                        </IonItem>
                        <IonItem>
                            <IonLabel>Range Rings</IonLabel>
                            <IonToggle
                                slot="end"
                                checked={showRangeRings}
                                onIonChange={(e) => onSetShowRangeRings(e.detail.checked)}
                            />
                        </IonItem>
                        <IonItem>
                            <IonLabel>Dark Rings</IonLabel>
                            <IonToggle
                                slot="end"
                                checked={darkRings}
                                disabled={!showRangeRings}
                                onIonChange={(e) => onSetDarkRings(e.detail.checked)}
                            />
                        </IonItem>
                    </IonList>
                </IonContent>
            </IonModal>
        </div>
    );
};
