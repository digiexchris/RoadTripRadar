import { useState, useEffect, useRef } from 'react';
import { Geolocation, Position } from '@capacitor/geolocation';

export interface PositionData {
    latitude: number;
    longitude: number;
    speed: number; // meters per second
    accuracy: number;
    isMoving: boolean;
    gpsHeading: number | null; // raw GPS heading, null when unavailable
}

const calculateDistance = (
    lat1: number,
    lon1: number,
    lat2: number,
    lon2: number
): number => {
    const R = 6371e3;
    const φ1 = lat1 * Math.PI / 180;
    const φ2 = lat2 * Math.PI / 180;
    const Δφ = (lat2 - lat1) * Math.PI / 180;
    const Δλ = (lon2 - lon1) * Math.PI / 180;

    const a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
        Math.cos(φ1) * Math.cos(φ2) *
        Math.sin(Δλ / 2) * Math.sin(Δλ / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return R * c;
};

interface UsePositionOptions {
    movementThreshold?: number; // in meters
    motionSensitivity?: number; // 1-5: samples needed to transition state
}

// Minimum GPS speed (m/s) to consider the user moving (~3.6 km/h / ~2.2 mph)
const MIN_MOVING_SPEED = 1.0;
// Maximum acceptable accuracy (meters) — ignore noisy fixes
const MAX_ACCURACY = 30;

export const usePosition = (options?: UsePositionOptions) => {
    const { movementThreshold = 10, motionSensitivity = 3 } = options || {};
    const [positionData, setPositionData] = useState<PositionData | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [isTracking, setIsTracking] = useState(false);
    const anchorPosition = useRef<{ lat: number; lon: number } | null>(null);
    const isMovingRef = useRef<boolean>(false);
    const watchIdRef = useRef<string | null>(null);
    const retryTimeoutRef = useRef<NodeJS.Timeout | null>(null);
    const movingVoteCount = useRef<number>(0);

    useEffect(() => {
        let watchId: string;

        const startTracking = async () => {
            try {
                const permission = await Geolocation.checkPermissions();
                if (permission.location !== 'granted') {
                    const requested = await Geolocation.requestPermissions();
                    if (requested.location !== 'granted') {
                        setError('Location permission denied');
                        return;
                    }
                }

                const useHighAccuracy = permission.coarseLocation === 'granted' && permission.location === 'granted';
                watchId = await Geolocation.watchPosition(
                    {
                        enableHighAccuracy: useHighAccuracy,
                        timeout: 10000,
                        maximumAge: 0,
                    },
                    (position: Position | null, err) => {
                        if (err) {
                            setError(err.message);
                            retryTimeoutRef.current = setTimeout(() => {
                                if (watchIdRef.current) {
                                    Geolocation.clearWatch({ id: watchIdRef.current }).catch(() => { });
                                }
                                startTracking();
                            }, 3000);
                            return;
                        }

                        if (position) {
                            const { latitude, longitude, speed, accuracy, heading: rawHeading } = position.coords;
                            let isMoving = isMovingRef.current;

                            if (accuracy > MAX_ACCURACY) {
                                return;
                            }

                            const gpsSpeed = speed || 0;
                            let votesMoving = false;

                            if (gpsSpeed >= MIN_MOVING_SPEED) {
                                votesMoving = true;
                            } else if (anchorPosition.current) {
                                const distFromAnchor = calculateDistance(
                                    anchorPosition.current.lat,
                                    anchorPosition.current.lon,
                                    latitude,
                                    longitude
                                );
                                votesMoving = distFromAnchor >= movementThreshold;
                            }

                            if (votesMoving) {
                                movingVoteCount.current = Math.min(
                                    movingVoteCount.current + 1,
                                    motionSensitivity
                                );
                            } else {
                                movingVoteCount.current = Math.max(
                                    movingVoteCount.current - 1,
                                    -motionSensitivity
                                );
                            }

                            if (!isMoving && movingVoteCount.current >= motionSensitivity) {
                                isMoving = true;
                            } else if (isMoving && movingVoteCount.current <= -motionSensitivity) {
                                isMoving = false;
                                anchorPosition.current = { lat: latitude, lon: longitude };
                            }

                            // Keep anchor reasonably current when moving
                            if (isMoving && anchorPosition.current) {
                                const distFromAnchor = calculateDistance(
                                    anchorPosition.current.lat,
                                    anchorPosition.current.lon,
                                    latitude,
                                    longitude
                                );
                                if (distFromAnchor >= movementThreshold) {
                                    anchorPosition.current = { lat: latitude, lon: longitude };
                                }
                            }

                            if (!anchorPosition.current) {
                                anchorPosition.current = { lat: latitude, lon: longitude };
                            }

                            isMovingRef.current = isMoving;

                            // Expose raw GPS heading only when it's valid and we're moving fast enough
                            const gpsHeading = (
                                rawHeading !== null &&
                                rawHeading !== undefined &&
                                !isNaN(rawHeading) &&
                                gpsSpeed >= MIN_MOVING_SPEED
                            ) ? rawHeading : null;

                            setPositionData({
                                latitude,
                                longitude,
                                speed: speed || 0,
                                accuracy: accuracy || 0,
                                isMoving,
                                gpsHeading,
                            });
                            setIsTracking(true);
                            setError(null);
                        }
                    }
                );
                watchIdRef.current = watchId;
            } catch (err) {
                setError(err instanceof Error ? err.message : 'GPS error');
            }
        };

        startTracking();

        return () => {
            if (retryTimeoutRef.current) {
                clearTimeout(retryTimeoutRef.current);
                retryTimeoutRef.current = null;
            }
            if (watchIdRef.current) {
                Geolocation.clearWatch({ id: watchIdRef.current }).catch(() => { });
                watchIdRef.current = null;
            }
        };
    }, [movementThreshold, motionSensitivity]);

    return { positionData, error, isTracking };
};
