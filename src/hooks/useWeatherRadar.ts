import { useState, useEffect, useCallback } from 'react';
import axios from 'axios';

export interface RadarFrame {
    url: string;
    timestamp: number;
}

interface WeatherRadarData {
    frames: RadarFrame[];
    currentFrame: number;
}

const POLL_INTERVAL = 60 * 1000; // 1 minute

export const useWeatherRadar = (
    latitude: number | null,
    longitude: number | null,
    frameCount: number = 5,
    playbackSpeed: number = 1.0, // in seconds
    showCurrentOnly: boolean = false
) => {
    const [radarData, setRadarData] = useState<WeatherRadarData | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);
    const [currentFrameIndex, setCurrentFrameIndex] = useState(0);

    const fetchRadarData = useCallback(async () => {
        if (!latitude || !longitude) {
            return;
        }

        setLoading(true);
        try {
            // Using RainViewer API - completely free, no API key required
            // First, get the available radar timestamps
            const response = await axios.get('https://api.rainviewer.com/public/weather-maps.json');

            if (response.data && response.data.radar && response.data.radar.past) {
                const radarTimes = response.data.radar.past;
                const host = response.data.host;

                // Get the requested number of frames (or all available if fewer)
                const framesToFetch = Math.min(frameCount, radarTimes.length);
                const frames: RadarFrame[] = radarTimes.slice(-framesToFetch).map((item: any) => ({
                    url: `${host}${item.path}/256/{z}/{x}/{y}/2/1_1.png`,
                    timestamp: item.time
                }));

                setRadarData({
                    frames,
                    currentFrame: 0
                });
                setError(null);
            } else {
                console.error('Invalid radar data structure:', response.data);
                setError('No radar data available');
            }
        } catch (err) {
            console.error('Error fetching radar data:', err);
            setError(err instanceof Error ? err.message : 'Failed to fetch radar data');
        } finally {
            setLoading(false);
        }
    }, [latitude, longitude, frameCount]);

    // Fetch data on location change or frameCount change
    useEffect(() => {
        fetchRadarData();
    }, [fetchRadarData]);

    // Poll for new data every 5 minutes
    useEffect(() => {
        const interval = setInterval(fetchRadarData, POLL_INTERVAL);
        return () => clearInterval(interval);
    }, [fetchRadarData]);

    // Animate frames with configurable speed
    useEffect(() => {
        if (!radarData || radarData.frames.length === 0) return;

        // In "now" mode, pin to the last frame
        if (showCurrentOnly) {
            setCurrentFrameIndex(radarData.frames.length - 1);
            return;
        }

        const interval = setInterval(() => {
            setCurrentFrameIndex((prev) => (prev + 1) % radarData.frames.length);
        }, playbackSpeed * 1000); // Convert seconds to milliseconds

        return () => clearInterval(interval);
    }, [radarData, playbackSpeed, showCurrentOnly]);

    const refresh = useCallback(() => {
        fetchRadarData();
    }, [fetchRadarData]);

    return {
        radarData,
        currentFrameIndex,
        error,
        loading,
        refresh
    };
};
