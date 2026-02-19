import { useState, useEffect, useRef } from 'react';
import { Geolocation, Position } from '@capacitor/geolocation';

interface GPSData {
    latitude: number;
    longitude: number;
    heading: number; // degrees from north (0-360)
    speed: number; // meters per second
    accuracy: number;
    isMoving: boolean; // true if moving beyond threshold
}

const calculateHeading = (
    lat1: number,
    lon1: number,
    lat2: number,
    lon2: number
): number => {
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const y = Math.sin(dLon) * Math.cos(lat2 * Math.PI / 180);
    const x = Math.cos(lat1 * Math.PI / 180) * Math.sin(lat2 * Math.PI / 180) -
        Math.sin(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.cos(dLon);
    const bearing = Math.atan2(y, x) * 180 / Math.PI;
    return (bearing + 360) % 360;
};

const calculateDistance = (
    lat1: number,
    lon1: number,
    lat2: number,
    lon2: number
): number => {
    const R = 6371e3; // Earth radius in meters
    const φ1 = lat1 * Math.PI / 180;
    const φ2 = lat2 * Math.PI / 180;
    const Δφ = (lat2 - lat1) * Math.PI / 180;
    const Δλ = (lon2 - lon1) * Math.PI / 180;

    const a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
        Math.cos(φ1) * Math.cos(φ2) *
        Math.sin(Δλ / 2) * Math.sin(Δλ / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return R * c; // Distance in meters
};

interface UseGPSOptions {
    compassHeading: number | null;
    movementThreshold: number; // in meters
    motionSensitivity?: number; // 1-5: samples needed to transition state
}

// Minimum GPS speed (m/s) to consider the user moving (~3.6 km/h / ~2.2 mph)
const MIN_MOVING_SPEED = 1.0;
// Maximum acceptable accuracy (meters) — ignore noisy fixes
const MAX_ACCURACY = 30;

export const useGPS = (options?: UseGPSOptions) => {
    const { compassHeading = null, movementThreshold = 10, motionSensitivity = 3 } = options || {};
    const [gpsData, setGpsData] = useState<GPSData | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [isTracking, setIsTracking] = useState(false);
    const anchorPosition = useRef<{ lat: number; lon: number } | null>(null);
    const lastGPSHeading = useRef<number>(0); // Default to North
    const isMovingRef = useRef<boolean>(false);
    const compassHeadingRef = useRef<number | null>(null);
    const watchIdRef = useRef<string | null>(null);
    const retryTimeoutRef = useRef<NodeJS.Timeout | null>(null);
    const movingVoteCount = useRef<number>(0); // positive = moving votes, negative = stationary votes

    // Keep compass heading ref updated
    useEffect(() => {
        compassHeadingRef.current = compassHeading;
    }, [compassHeading]);

    // Update heading when compass changes and user is stationary
    useEffect(() => {
        if (gpsData && !isMovingRef.current && compassHeading !== null) {
            setGpsData(prev => prev ? { ...prev, heading: compassHeading } : null);
        }
    }, [compassHeading]);

    useEffect(() => {
        let watchId: string;

        const startTracking = async () => {
            try {
                // Request permissions
                const permission = await Geolocation.checkPermissions();
                if (permission.location !== 'granted') {
                    const requested = await Geolocation.requestPermissions();
                    if (requested.location !== 'granted') {
                        setError('Location permission denied');
                        return;
                    }
                }

                // Start watching position (try coarse first, then high accuracy)
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
                            // Retry after 3 seconds on timeout
                            retryTimeoutRef.current = setTimeout(() => {
                                if (watchIdRef.current) {
                                    Geolocation.clearWatch({ id: watchIdRef.current }).catch(() => { });
                                }
                                startTracking();
                            }, 3000);
                            return;
                        }

                        if (position) {
                            const { latitude, longitude, speed, accuracy } = position.coords;
                            let heading = lastGPSHeading.current;
                            let isMoving = isMovingRef.current;

                            // Skip noisy fixes with poor accuracy
                            if (accuracy > MAX_ACCURACY) {
                                return;
                            }

                            // Determine movement using GPS speed as primary signal,
                            // with distance-from-anchor as secondary confirmation
                            const gpsSpeed = speed || 0;
                            let votesMoving = false;

                            if (gpsSpeed >= MIN_MOVING_SPEED) {
                                // GPS hardware says we're moving
                                votesMoving = true;
                            } else if (anchorPosition.current) {
                                // Speed is low/zero — check distance from anchor
                                const distFromAnchor = calculateDistance(
                                    anchorPosition.current.lat,
                                    anchorPosition.current.lon,
                                    latitude,
                                    longitude
                                );
                                votesMoving = distFromAnchor >= movementThreshold;
                            }

                            // Accumulate votes for state transition (hysteresis)
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

                            // Only transition state after enough consecutive votes
                            if (!isMoving && movingVoteCount.current >= motionSensitivity) {
                                isMoving = true;
                            } else if (isMoving && movingVoteCount.current <= -motionSensitivity) {
                                isMoving = false;
                                // Reset anchor to current position when becoming stationary
                                anchorPosition.current = { lat: latitude, lon: longitude };
                            }

                            // Calculate heading from anchor when moving
                            if (isMoving) {
                                // Prefer hardware GPS heading when available (updated every fix)
                                const gpsHeading = position.coords.heading;
                                if (gpsHeading !== null && gpsHeading !== undefined && !isNaN(gpsHeading) && gpsSpeed >= MIN_MOVING_SPEED) {
                                    heading = gpsHeading;
                                    lastGPSHeading.current = heading;
                                    // Keep anchor reasonably current
                                    if (anchorPosition.current) {
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
                                } else if (anchorPosition.current) {
                                    // Fallback: calculate heading from anchor displacement
                                    const distFromAnchor = calculateDistance(
                                        anchorPosition.current.lat,
                                        anchorPosition.current.lon,
                                        latitude,
                                        longitude
                                    );
                                    if (distFromAnchor >= movementThreshold) {
                                        heading = calculateHeading(
                                            anchorPosition.current.lat,
                                            anchorPosition.current.lon,
                                            latitude,
                                            longitude
                                        );
                                        lastGPSHeading.current = heading;
                                        anchorPosition.current = { lat: latitude, lon: longitude };
                                    }
                                }
                            } else if (!isMoving && compassHeadingRef.current !== null) {
                                heading = compassHeadingRef.current;
                            }

                            // Set initial anchor
                            if (!anchorPosition.current) {
                                anchorPosition.current = { lat: latitude, lon: longitude };
                                if (compassHeadingRef.current !== null) {
                                    heading = compassHeadingRef.current;
                                }
                            }

                            isMovingRef.current = isMoving;

                            setGpsData({
                                latitude,
                                longitude,
                                heading,
                                speed: speed || 0,
                                accuracy: accuracy || 0,
                                isMoving,
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
                Geolocation.clearWatch({ id: watchIdRef.current }).catch(() => {
                    // Ignore errors during cleanup
                });
                watchIdRef.current = null;
            }
        };
    }, [movementThreshold]); // Removed compassHeading to prevent constant restarts

    return { gpsData, error, isTracking };
};
