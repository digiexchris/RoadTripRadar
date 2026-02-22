import { useRef } from 'react';

interface UseHeadingOptions {
    compassHeading: number | null;
    gpsHeading: number | null;
    isMoving: boolean;
    useCompassRotation: boolean;
}

type HeadingSource = 'gps' | 'compass';

interface HeadingState {
    heading: number;
    source: HeadingSource;
}

// EMA smoothing factor for compass readings (0-1).
// Lower = smoother but laggier, higher = more responsive but jittery.
const COMPASS_SMOOTH_ALPHA = 0.35;

/**
 * Apply exponential moving average to an angular value, handling
 * the 0°/360° wrap-around by operating on the shortest angular difference.
 */
function smoothAngle(current: number, target: number, alpha: number): number {
    let diff = target - current;
    // Shortest path around the circle
    if (diff > 180) diff -= 360;
    else if (diff < -180) diff += 360;
    return ((current + alpha * diff) % 360 + 360) % 360;
}

/**
 * Unified heading hook that selects between compass and GPS heading.
 *
 * - Moving + valid GPS heading: use GPS heading
 * - Stationary + compass rotation enabled: use smoothed compass heading
 * - Otherwise: keep last known GPS heading
 *
 * Compass readings are smoothed with an exponential moving average to
 * reduce jitter from noisy sensor data.
 */
export const useHeading = (options: UseHeadingOptions): HeadingState => {
    const { compassHeading, gpsHeading, isMoving, useCompassRotation } = options;
    const lastGPSHeadingRef = useRef<number>(0);
    const smoothedCompassRef = useRef<number | null>(null);

    if (isMoving && gpsHeading !== null) {
        lastGPSHeadingRef.current = gpsHeading;
        // Reset compass smoothing so it re-seeds when we stop moving
        smoothedCompassRef.current = null;
        return { heading: gpsHeading, source: 'gps' };
    }

    if (!isMoving && useCompassRotation && compassHeading !== null) {
        if (smoothedCompassRef.current === null) {
            smoothedCompassRef.current = compassHeading;
        } else {
            smoothedCompassRef.current = smoothAngle(
                smoothedCompassRef.current,
                compassHeading,
                COMPASS_SMOOTH_ALPHA
            );
        }
        return { heading: smoothedCompassRef.current, source: 'compass' };
    }

    return { heading: lastGPSHeadingRef.current, source: 'gps' };
};
