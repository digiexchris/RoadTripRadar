import React, { useRef, useEffect, useMemo, useCallback } from 'react';
import Map, { MapRef } from 'react-map-gl/maplibre';
import type { GeoJSONSource } from 'maplibre-gl';
import { NavigationControl } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { useSettings } from '../contexts/SettingsContext';
import './RadarMap.css';

// Ring interval in km for each integer zoom level (index = zoom)
const RING_INTERVALS_KM: Record<number, number> = {
    5: 100, 6: 50, 7: 50, 8: 25, 9: 20, 10: 10,
    11: 5, 12: 2, 13: 1, 14: 0.5, 15: 0.25,
    16: 0.1, 17: 0.05, 18: 0.025,
};

/** Generate a GeoJSON circle polygon at a given center and radius (km). */
function generateCircle(centerLng: number, centerLat: number, radiusKm: number, points = 64): GeoJSON.Feature {
    const coords: [number, number][] = [];
    const latRad = centerLat * Math.PI / 180;

    for (let i = 0; i <= points; i++) {
        const angle = (i / points) * 2 * Math.PI;
        // Approximate offset in degrees
        const dLat = (radiusKm / 111.32) * Math.cos(angle);
        const dLng = (radiusKm / (111.32 * Math.cos(latRad))) * Math.sin(angle);
        coords.push([centerLng + dLng, centerLat + dLat]);
    }

    return {
        type: 'Feature',
        geometry: { type: 'Polygon', coordinates: [coords] },
        properties: { radiusKm },
    };
}

/** Generate label points at the top of each ring (north). */
function generateLabel(centerLng: number, centerLat: number, radiusKm: number): GeoJSON.Feature {
    const dLat = radiusKm / 111.32;
    const label = radiusKm >= 1
        ? `${radiusKm} km`
        : `${Math.round(radiusKm * 1000)} m`;
    return {
        type: 'Feature',
        geometry: { type: 'Point', coordinates: [centerLng, centerLat + dLat] },
        properties: { label },
    };
}

/** Build range ring + label GeoJSON for a given position and zoom. */
function buildRangeRingData(lng: number, lat: number, zoom: number) {
    const floorZoom = Math.max(5, Math.min(18, Math.floor(zoom)));
    const intervalKm = RING_INTERVALS_KM[floorZoom] ?? 50;
    const ringCount = 5;

    const rings: GeoJSON.Feature[] = [];
    const labels: GeoJSON.Feature[] = [];
    for (let i = 1; i <= ringCount; i++) {
        const r = intervalKm * i;
        rings.push(generateCircle(lng, lat, r));
        labels.push(generateLabel(lng, lat, r));
    }

    return {
        rings: { type: 'FeatureCollection' as const, features: rings },
        labels: { type: 'FeatureCollection' as const, features: labels },
    };
}

interface RadarMapProps {
    latitude: number;
    longitude: number;
    heading: number;
    zoom?: number;
    radarImageUrl?: string;
    isTrackingMode: boolean;
    showBaseMap?: boolean;
    showRadar?: boolean;
    showRangeRings?: boolean;
    radarOpacity?: number;
    isLandscape?: boolean;
}

export const RadarMap: React.FC<RadarMapProps> = ({
    latitude,
    longitude,
    heading,
    zoom = 11,
    radarImageUrl,
    isTrackingMode,
    showBaseMap = true,
    showRadar = true,
    showRangeRings = true,
    radarOpacity = 0.6,
    isLandscape = false,
}) => {
    const mapRef = useRef<MapRef>(null);
    const isProgrammaticChange = useRef(false);
    const resetProgrammaticTimeout = useRef<NodeJS.Timeout | null>(null);
    const { settings } = useSettings();

    // Calculate padding to position the user dot.
    // mapCenterPosition = percentage of map ahead of dot.
    // In portrait: dot screen position from top = (100 - mapCenterPosition)% of height.
    // In landscape: dot screen position from left = (100 - mapCenterPosition)% of width.
    const calcPadding = (containerHeight: number, containerWidth: number) => {
        const dotFraction = (100 - settings.mapCenterPosition) / 100;

        if (isLandscape) {
            // In landscape, shift horizontally (left/right padding)
            const offset = 2 * dotFraction * containerWidth - containerWidth;
            if (offset >= 0) {
                return { top: 0, bottom: 0, left: Math.round(offset), right: 0 };
            } else {
                return { top: 0, bottom: 0, left: 0, right: Math.round(-offset) };
            }
        } else {
            // In portrait, shift vertically (top/bottom padding)
            const offset = 2 * dotFraction * containerHeight - containerHeight;
            if (offset >= 0) {
                return { top: Math.round(offset), bottom: 0, left: 0, right: 0 };
            } else {
                return { top: 0, bottom: Math.round(-offset), left: 0, right: 0 };
            }
        }
    };

    // Memoize position marker data to prevent recreation
    const positionData = useMemo(() => ({
        type: 'Feature' as const,
        geometry: {
            type: 'Point' as const,
            coordinates: [longitude, latitude],
        },
        properties: {},
    }), [longitude, latitude]);

    // Update map position and rotation in tracking mode
    useEffect(() => {
        if (!mapRef.current || !isTrackingMode) return;

        const map = mapRef.current.getMap();

        // Get container dimensions for padding calculation
        const container = map.getContainer();
        const height = container.offsetHeight;
        const width = container.offsetWidth;

        // Smooth rotation - handle wrap-around
        // MapLibre bearing = the compass direction that is "up" on screen,
        // which equals the user's heading directly (no negation).
        let targetBearing = heading;
        const currentBearing = map.getBearing();
        const diff = targetBearing - currentBearing;

        if (diff > 180) {
            targetBearing -= 360;
        } else if (diff < -180) {
            targetBearing += 360;
        }

        // Mark this as a programmatic change
        isProgrammaticChange.current = true;

        // Clear any existing timeout
        if (resetProgrammaticTimeout.current) {
            clearTimeout(resetProgrammaticTimeout.current);
        }

        // Use padding to position user dot based on mapCenterPosition setting.
        map.easeTo({
            center: [longitude, latitude],
            bearing: targetBearing,
            zoom: zoom,
            duration: 500,
            easing: (t) => t * (2 - t), // easeOutQuad
            padding: calcPadding(height, width),
        });

        // Reset the programmatic flag after animation completes
        resetProgrammaticTimeout.current = setTimeout(() => {
            isProgrammaticChange.current = false;
        }, 600); // Slightly longer than animation duration

        return () => {
            if (resetProgrammaticTimeout.current) {
                clearTimeout(resetProgrammaticTimeout.current);
            }
        };
    }, [latitude, longitude, heading, zoom, isTrackingMode, settings.mapCenterPosition, isLandscape]);

    // Reset padding when entering manual mode, preserving visual position
    useEffect(() => {
        if (!mapRef.current || isTrackingMode) return;
        const map = mapRef.current.getMap();

        // Get the geographic coordinates at the current visual center of the screen
        const container = map.getContainer();
        const visualCenter = map.unproject([
            container.offsetWidth / 2,
            container.offsetHeight / 2,
        ]);

        // Set that as the new center while removing padding — keeps the view in place
        map.jumpTo({
            center: [visualCenter.lng, visualCenter.lat],
            padding: { top: 0, bottom: 0, left: 0, right: 0 },
        });
    }, [isTrackingMode]);

    // In manual mode, leave bearing as-is (don't reset to north)
    // The user can rotate manually if they want

    const mapStyle = useMemo(() => ({
        version: 8 as const,
        sources: {
            'osm-tiles': {
                type: 'raster' as const,
                tiles: ['https://a.tile.openstreetmap.org/{z}/{x}/{y}.png'],
                tileSize: 256,
                attribution: '&copy; OpenStreetMap contributors',
            },
            'radar': {
                type: 'raster' as const,
                tiles: [],
                tileSize: 256,
                maxzoom: 7,
                minzoom: 0,
            },
            'current-position': {
                type: 'geojson' as const,
                data: {
                    type: 'Feature' as const,
                    geometry: {
                        type: 'Point' as const,
                        coordinates: [0, 0],
                    },
                    properties: {},
                },
            },
            'range-rings': {
                type: 'geojson' as const,
                data: { type: 'FeatureCollection' as const, features: [] },
            },
            'range-ring-labels': {
                type: 'geojson' as const,
                data: { type: 'FeatureCollection' as const, features: [] },
            },
        },
        layers: [
            ...(showBaseMap ? [{
                id: 'osm',
                type: 'raster' as const,
                source: 'osm-tiles',
            }] : []),
            {
                id: 'radar-layer',
                type: 'raster' as const,
                source: 'radar',
                paint: {
                    'raster-opacity': showRadar ? radarOpacity : 0,
                    'raster-fade-duration': 0,
                },
            },
            {
                id: 'range-rings-layer',
                type: 'line' as const,
                source: 'range-rings',
                layout: {
                    'visibility': (showRangeRings ? 'visible' : 'none') as 'visible' | 'none',
                },
                paint: {
                    'line-color': 'rgba(255, 255, 255, 0.45)',
                    'line-width': 1,
                    'line-dasharray': [4, 4],
                },
            },
            {
                id: 'range-ring-labels-layer',
                type: 'symbol' as const,
                source: 'range-ring-labels',
                layout: {
                    'visibility': (showRangeRings ? 'visible' : 'none') as 'visible' | 'none',
                    'text-field': ['get', 'label'] as unknown as string,
                    'text-size': 12,
                    'text-anchor': 'bottom' as const,
                    'text-offset': [0, -0.3] as [number, number],
                    'text-allow-overlap': true,
                    'text-ignore-placement': true,
                },
                paint: {
                    'text-color': 'rgba(255, 255, 255, 0.7)',
                    'text-halo-color': 'rgba(0, 0, 0, 0.6)',
                    'text-halo-width': 1.5,
                },
            },
            {
                id: 'position-circle',
                type: 'circle' as const,
                source: 'current-position',
                paint: {
                    'circle-radius': 8,
                    'circle-color': '#4285F4',
                    'circle-stroke-width': 2,
                    'circle-stroke-color': '#ffffff',
                },
            },
        ],
    }), [showBaseMap, showRadar, showRangeRings, radarOpacity]);

    // Update radar tiles when radarImageUrl changes
    useEffect(() => {
        if (!mapRef.current) return;

        const map = mapRef.current.getMap();
        if (!map.isStyleLoaded()) return;

        const source = map.getSource('radar');

        if (source && radarImageUrl) {
            // Remove layer and source, then re-add with new tiles
            if (map.getLayer('radar-layer')) {
                map.removeLayer('radar-layer');
            }
            map.removeSource('radar');
            map.addSource('radar', {
                type: 'raster',
                tiles: [radarImageUrl],
                tileSize: 256,
                maxzoom: 7,
                minzoom: 0,
            });
            map.addLayer({
                id: 'radar-layer',
                type: 'raster',
                source: 'radar',
                paint: {
                    'raster-opacity': showRadar ? radarOpacity : 0,
                    'raster-fade-duration': 0,
                },
            }, 'range-rings-layer'); // Insert before range rings (which are above radar)
        }
    }, [radarImageUrl]);

    // Update position data when it changes
    useEffect(() => {
        if (!mapRef.current) return;

        const map = mapRef.current.getMap();
        const source = map.getSource('current-position');

        if (source && source.type === 'geojson') {
            (source as GeoJSONSource).setData(positionData);
        }
    }, [positionData]);

    // Update radar opacity & visibility dynamically
    useEffect(() => {
        if (!mapRef.current) return;
        const map = mapRef.current.getMap();
        if (!map.isStyleLoaded()) return;

        if (map.getLayer('radar-layer')) {
            map.setPaintProperty('radar-layer', 'raster-opacity', showRadar ? radarOpacity : 0);
        }
    }, [showRadar, radarOpacity]);

    // Update range ring visibility dynamically
    useEffect(() => {
        if (!mapRef.current) return;
        const map = mapRef.current.getMap();
        if (!map.isStyleLoaded()) return;

        const vis = showRangeRings ? 'visible' : 'none';
        if (map.getLayer('range-rings-layer')) {
            map.setLayoutProperty('range-rings-layer', 'visibility', vis);
        }
        if (map.getLayer('range-ring-labels-layer')) {
            map.setLayoutProperty('range-ring-labels-layer', 'visibility', vis);
        }
    }, [showRangeRings]);

    // Update range rings when position or zoom changes
    useEffect(() => {
        if (!mapRef.current) return;

        const map = mapRef.current.getMap();
        const ringsSource = map.getSource('range-rings');
        const labelsSource = map.getSource('range-ring-labels');

        if (ringsSource && labelsSource) {
            const { rings, labels } = buildRangeRingData(longitude, latitude, zoom);
            (ringsSource as GeoJSONSource).setData(rings);
            (labelsSource as GeoJSONSource).setData(labels);
        }
    }, [longitude, latitude, zoom]);

    // Add navigation control (compass) on mount
    useEffect(() => {
        if (!mapRef.current) return;

        const map = mapRef.current.getMap();
        const nav = new NavigationControl({
            showCompass: true,
            showZoom: false,
            visualizePitch: false,
        });

        map.addControl(nav, 'top-right');

        return () => {
            map.removeControl(nav);
        };
    }, []);

    // Calculate initial view state
    const getInitialViewState = () => {
        return {
            longitude,
            latitude,
            zoom,
            bearing: isTrackingMode ? -heading : 0,
            padding: isTrackingMode
                ? calcPadding(window.innerHeight, window.innerWidth)
                : { top: 0, bottom: 0, left: 0, right: 0 },
        };
    };

    const handleMapLoad = () => {
        if (!mapRef.current) return;
        const map = mapRef.current.getMap();
        const source = map.getSource('current-position');
        if (source && source.type === 'geojson') {
            (source as GeoJSONSource).setData(positionData);
        }
        // Set initial range rings
        const ringsSource = map.getSource('range-rings');
        const labelsSource = map.getSource('range-ring-labels');
        if (ringsSource && labelsSource) {
            const { rings, labels } = buildRangeRingData(longitude, latitude, zoom);
            (ringsSource as GeoJSONSource).setData(rings);
            (labelsSource as GeoJSONSource).setData(labels);
        }
    };

    return (
        <div className="radar-map-wrapper">
            <Map
                ref={mapRef}
                initialViewState={getInitialViewState()}
                style={{ width: '100%', height: '100%' }}
                mapStyle={mapStyle}
                onLoad={handleMapLoad}
                dragPan={true}
                dragRotate={true}
                scrollZoom={true}
                touchZoomRotate={true}
                touchPitch={false}
                doubleClickZoom={true}
                attributionControl={false}
            />
        </div>
    );
};
