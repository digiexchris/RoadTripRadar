import { useEffect, useState, useRef } from 'react';
import { CapgoCompass } from '@capgo/capacitor-compass';

export interface CompassData {
    heading: number | null; // 0-359 degrees, null if unavailable
    accuracy: number | null; // accuracy in degrees, null if unavailable
}

export const useCompass = () => {
    const [compassData, setCompassData] = useState<CompassData>({
        heading: null,
        accuracy: null,
    });
    const [isActive, setIsActive] = useState(false);
    const listenerRef = useRef<{ remove: () => Promise<void> } | null>(null);

    useEffect(() => {
        const startCompass = async () => {
            try {
                // Add listener for heading changes
                listenerRef.current = await CapgoCompass.addListener('headingChange', (event) => {
                    // event.value is compass heading in degrees (0-360),
                    // natively tilt-compensated and orientation-aware on Android.
                    setCompassData({
                        heading: event.value,
                        accuracy: null,
                    });
                });

                // Start the compass sensor
                await CapgoCompass.startListening({
                    minInterval: 100,     // update every 100ms
                    minHeadingChange: 1.0, // only fire on 1°+ change
                });

                setIsActive(true);
            } catch (error) {
                console.error('Error starting compass:', error);
                setIsActive(false);
            }
        };

        startCompass();

        return () => {
            CapgoCompass.stopListening().catch(() => {});
            if (listenerRef.current) {
                listenerRef.current.remove().catch(() => {});
                listenerRef.current = null;
            }
            setIsActive(false);
        };
    }, []);

    return { ...compassData, isActive };
};
