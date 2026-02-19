import { Preferences } from '@capacitor/preferences';

export interface AppSettings {
    defaultZoom: number;
    frameCount: number;
    playbackSpeed: number; // in seconds
    movementThreshold: number; // in meters
    speedUnit: 'kmh' | 'mph'; // speed unit preference
    keepScreenOn: boolean; // prevent screen from sleeping
    mapCenterPosition: number; // 10-90, percentage of map ahead of user dot (90=dot near top, 10=dot near bottom)
    defaultOrientation: 'portrait' | 'landscape'; // default screen orientation
    defaultRadarMode: 'history' | 'now'; // default timeline mode
    useCompassRotation: boolean; // rotate map using compass when stationary
    motionSensitivity: number; // 1-5: samples needed to change moving/stationary state (1=very sensitive, 5=least sensitive)
}

const DEFAULT_SETTINGS: AppSettings = {
    defaultZoom: 7,
    frameCount: 5,
    playbackSpeed: 1.0,
    movementThreshold: 10,
    speedUnit: 'kmh',
    keepScreenOn: false,
    mapCenterPosition: 33,
    defaultOrientation: 'portrait',
    defaultRadarMode: 'history',
    useCompassRotation: false,
    motionSensitivity: 3,
};

const SETTINGS_KEY = 'app_settings';

export const settingsService = {
    async loadSettings(): Promise<AppSettings> {
        try {
            const { value } = await Preferences.get({ key: SETTINGS_KEY });
            if (value) {
                return { ...DEFAULT_SETTINGS, ...JSON.parse(value) };
            }
        } catch (error) {
            console.error('Error loading settings:', error);
        }
        return DEFAULT_SETTINGS;
    },

    async saveSettings(settings: AppSettings): Promise<void> {
        try {
            await Preferences.set({
                key: SETTINGS_KEY,
                value: JSON.stringify(settings),
            });
        } catch (error) {
            console.error('Error saving settings:', error);
        }
    },

    getDefaultSettings(): AppSettings {
        return { ...DEFAULT_SETTINGS };
    },
};
