import React, { useRef, useEffect, useMemo, useState, useCallback } from 'react';
import Map, { MapRef } from 'react-map-gl/maplibre';
import type { GeoJSONSource } from 'maplibre-gl';
import { NavigationControl } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { useSettings } from '../contexts/SettingsContext';
import './RadarMap.css';

const LIGHT_STYLE_URL = 'https://tiles.openfreemap.org/styles/liberty';
const DARK_STYLE_URL = 'https://tiles.openfreemap.org/styles/dark';

const OVERLAY_LAYER_IDS = new Set([
    'radar-layer', 'range-rings-layer', 'range-ring-labels-layer', 'position-arrow',
]);

function createArrowImage(): ImageData {
    const size = 48;
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d')!;
    ctx.translate(size / 2, size / 2);
    ctx.beginPath();
    ctx.moveTo(0, -18);
    ctx.lineTo(12, 14);
    ctx.lineTo(0, 6);
    ctx.lineTo(-12, 14);
    ctx.closePath();
    ctx.fillStyle = '#4285F4';
    ctx.fill();
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = 2.5;
    ctx.lineJoin = 'round';
    ctx.stroke();
    return ctx.getImageData(0, 0, size, size);
}

const OVERLAY_SOURCES = {
    'radar': {
        type: 'raster' as const,
        tiles: [] as string[],
        tileSize: 256,
        maxzoom: 7,
        minzoom: 0,
    },
    'current-position': {
        type: 'geojson' as const,
        data: {
            type: 'Feature' as const,
            geometry: { type: 'Point' as const, coordinates: [0, 0] },
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
};

const OVERLAY_LAYERS = [
    {
        id: 'radar-layer',
        type: 'raster' as const,
        source: 'radar',
        paint: {
            'raster-opacity': 0.6,
            'raster-fade-duration': 0,
        },
    },
    {
        id: 'range-rings-layer',
        type: 'line' as const,
        source: 'range-rings',
        paint: {
            'line-color': 'rgba(0, 0, 0, 0.6)',
            'line-width': 1.5,
            'line-dasharray': [4, 4],
        },
    },
    {
        id: 'range-ring-labels-layer',
        type: 'symbol' as const,
        source: 'range-ring-labels',
        layout: {
            'text-field': ['get', 'label'] as unknown as string,
            'text-size': 12,
            'text-anchor': 'bottom' as const,
            'text-offset': [0, -0.3] as [number, number],
            'text-allow-overlap': true,
            'text-ignore-placement': true,
            'text-rotation-alignment': 'viewport' as const,
            'text-pitch-alignment': 'viewport' as const,
            'text-font': ['Noto Sans Regular'],
        },
        paint: {
            'text-color': 'rgba(0, 0, 0, 0.7)',
            'text-halo-color': 'rgba(255, 255, 255, 0.6)',
            'text-halo-width': 1.5,
        },
    },
    {
        id: 'position-arrow',
        type: 'symbol' as const,
        source: 'current-position',
        layout: {
            'icon-image': 'position-arrow',
            'icon-size': 1,
            'icon-rotate': ['get', 'heading'] as unknown as number,
            'icon-rotation-alignment': 'map' as const,
            'icon-allow-overlap': true,
            'icon-ignore-placement': true,
        },
    },
];

function mergeWithOverlays(baseStyle: any): any {
    return {
        ...baseStyle,
        sources: { ...baseStyle.sources, ...OVERLAY_SOURCES },
        layers: [...baseStyle.layers, ...OVERLAY_LAYERS],
    };
}

// Ring interval in km for each integer zoom level (index = zoom)
const RING_INTERVALS_KM: Record<number, number> = {
    0: 5000, 1: 2500, 2: 1000, 3: 500, 4: 200,
    5: 100, 6: 50, 7: 50, 8: 25, 9: 20, 10: 10,
    11: 5, 12: 2, 13: 1, 14: 0.5, 15: 0.25,
    16: 0.1, 17: 0.05, 18: 0.025,
};

/** Generate a GeoJSON circle LineString at a given center and radius (km). */
function generateCircle(centerLng: number, centerLat: number, radiusKm: number, points = 64): GeoJSON.Feature {
    const coords: [number, number][] = [];
    const latRad = centerLat * Math.PI / 180;

    for (let i = 0; i <= points; i++) {
        const angle = (i / points) * 2 * Math.PI;
        const dLat = (radiusKm / 111.32) * Math.cos(angle);
        const dLng = (radiusKm / (111.32 * Math.cos(latRad))) * Math.sin(angle);
        coords.push([centerLng + dLng, centerLat + dLat]);
    }

    return {
        type: 'Feature',
        geometry: { type: 'LineString', coordinates: coords },
        properties: { radiusKm },
    };
}

/** Generate label point at the top of the screen (in the direction of travel). */
function generateLabel(centerLng: number, centerLat: number, radiusKm: number, bearingDeg: number): GeoJSON.Feature {
    const bearingRad = bearingDeg * Math.PI / 180;
    const latRad = centerLat * Math.PI / 180;
    const dLat = (radiusKm / 111.32) * Math.cos(bearingRad);
    const dLng = (radiusKm / (111.32 * Math.cos(latRad))) * Math.sin(bearingRad);
    const label = radiusKm >= 1
        ? `${radiusKm} km`
        : `${Math.round(radiusKm * 1000)} m`;
    return {
        type: 'Feature',
        geometry: { type: 'Point', coordinates: [centerLng + dLng, centerLat + dLat] },
        properties: { label },
    };
}

/** Build range ring GeoJSON (circles only — heading-independent). */
function buildRingData(lng: number, lat: number, zoom: number) {
    const floorZoom = Math.max(0, Math.min(18, Math.floor(zoom)));
    const intervalKm = RING_INTERVALS_KM[floorZoom] ?? 50;
    const ringCount = 5;

    const features: GeoJSON.Feature[] = [];
    for (let i = 1; i <= ringCount; i++) {
        features.push(generateCircle(lng, lat, intervalKm * i));
    }
    return { type: 'FeatureCollection' as const, features };
}

/** Build range ring label GeoJSON (positioned at the given bearing). */
function buildLabelData(lng: number, lat: number, zoom: number, heading: number) {
    const floorZoom = Math.max(0, Math.min(18, Math.floor(zoom)));
    const intervalKm = RING_INTERVALS_KM[floorZoom] ?? 50;
    const ringCount = 5;

    const features: GeoJSON.Feature[] = [];
    for (let i = 1; i <= ringCount; i++) {
        features.push(generateLabel(lng, lat, intervalKm * i, heading));
    }
    return { type: 'FeatureCollection' as const, features };
}

// Critically damped spring for bearing animation (2*sqrt(STIFFNESS) = DAMPING)
const BEARING_STIFFNESS = 25;
const BEARING_DAMPING = 10;

interface RadarMapProps {
    latitude: number;
    longitude: number;
    heading: number;
    zoom?: number;
    radarImageUrl?: string;
    isTrackingMode: boolean;
    showBaseMap?: boolean;
    darkBaseMap?: boolean;
    showRadar?: boolean;
    showRangeRings?: boolean;
    darkRings?: boolean;
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
    darkBaseMap = false,
    showRadar = true,
    showRangeRings = true,
    darkRings = true,
    radarOpacity = 0.6,
    isLandscape = false,
}) => {
    const mapRef = useRef<MapRef>(null);
    const latRef = useRef(latitude);
    const lngRef = useRef(longitude);
    const zoomRef = useRef(zoom);
    const headingRef = useRef(heading);
    latRef.current = latitude;
    lngRef.current = longitude;
    zoomRef.current = zoom;
    headingRef.current = heading;
    const { settings } = useSettings();

    // --- Vector tile style management ---
    const [mapStyle, setMapStyle] = useState<any>(null);
    const [styleVersion, setStyleVersion] = useState(0);
    const baseStylesRef = useRef<{ light: any; dark: any }>({ light: null, dark: null });

    // Fetch both styles on mount
    useEffect(() => {
        let cancelled = false;
        Promise.all([
            fetch(LIGHT_STYLE_URL).then(r => r.json()),
            fetch(DARK_STYLE_URL).then(r => r.json()),
        ]).then(([light, dark]) => {
            if (cancelled) return;
            baseStylesRef.current = { light, dark };
            setMapStyle(mergeWithOverlays(darkBaseMap ? dark : light));
        }).catch(err => {
            console.error('Failed to fetch map styles:', err);
        });
        return () => { cancelled = true; };
    }, []);

    // Rebuild merged style when dark mode toggles
    useEffect(() => {
        if (!baseStylesRef.current.light) return;
        const base = darkBaseMap ? baseStylesRef.current.dark : baseStylesRef.current.light;
        setMapStyle(mergeWithOverlays(base));
    }, [darkBaseMap]);

    // Calculate padding to position the user dot.
    const calcPadding = (containerHeight: number, containerWidth: number) => {
        const dotFraction = (100 - settings.mapCenterPosition) / 100;

        if (isLandscape) {
            const offset = 2 * dotFraction * containerWidth - containerWidth;
            if (offset >= 0) {
                return { top: 0, bottom: 0, left: Math.round(offset), right: 0 };
            } else {
                return { top: 0, bottom: 0, left: 0, right: Math.round(-offset) };
            }
        } else {
            const offset = 2 * dotFraction * containerHeight - containerHeight;
            if (offset >= 0) {
                return { top: Math.round(offset), bottom: 0, left: 0, right: 0 };
            } else {
                return { top: 0, bottom: Math.round(-offset), left: 0, right: 0 };
            }
        }
    };

    // Memoize position marker data to prevent recreation (fallback for manual mode)
    const positionData = useMemo(() => ({
        type: 'Feature' as const,
        geometry: {
            type: 'Point' as const,
            coordinates: [longitude, latitude],
        },
        properties: { heading },
    }), [longitude, latitude, heading]);

    // Unified animation loop for tracking mode.
    // Handles position (exponential interpolation), zoom, and bearing (spring)
    // in a single rAF loop, applied atomically via jumpTo each frame.
    // This avoids all easeTo/rotateTo animation conflicts.
    useEffect(() => {
        if (!mapRef.current || !isTrackingMode) return;

        const map = mapRef.current.getMap();
        const container = map.getContainer();
        let rafId: number;
        let lastTime = 0;

        const pos = {
            lat: map.getCenter().lat,
            lng: map.getCenter().lng,
            zoom: map.getZoom(),
        };

        const spring = {
            current: map.getBearing(),
            velocity: 0,
        };

        const POSITION_HALF_LIFE = 0.12;

        const tick = (now: number) => {
            if (lastTime === 0) {
                lastTime = now;
                rafId = requestAnimationFrame(tick);
                return;
            }
            const dt = Math.min((now - lastTime) / 1000, 0.05);
            lastTime = now;

            const decay = Math.pow(0.5, dt / POSITION_HALF_LIFE);
            pos.lat += (latRef.current - pos.lat) * (1 - decay);
            pos.lng += (lngRef.current - pos.lng) * (1 - decay);
            pos.zoom += (zoomRef.current - pos.zoom) * (1 - decay);

            const target = headingRef.current;
            let diff = target - spring.current;
            if (diff > 180) diff -= 360;
            else if (diff < -180) diff += 360;

            const force = BEARING_STIFFNESS * diff - BEARING_DAMPING * spring.velocity;
            spring.velocity += force * dt;
            spring.current += spring.velocity * dt;

            if (Math.abs(diff) < 0.1 && Math.abs(spring.velocity) < 0.1) {
                spring.current = ((spring.current % 360) + 360) % 360;
            }

            map.jumpTo({
                center: [pos.lng, pos.lat],
                zoom: pos.zoom,
                bearing: spring.current,
                padding: calcPadding(container.offsetHeight, container.offsetWidth),
            });

            const ringsSource = map.getSource('range-rings');
            if (ringsSource && ringsSource.type === 'geojson') {
                (ringsSource as GeoJSONSource).setData(
                    buildRingData(pos.lng, pos.lat, pos.zoom)
                );
            }
            const labelsSource = map.getSource('range-ring-labels');
            if (labelsSource && labelsSource.type === 'geojson') {
                (labelsSource as GeoJSONSource).setData(
                    buildLabelData(pos.lng, pos.lat, pos.zoom, spring.current)
                );
            }

            const posSource = map.getSource('current-position');
            if (posSource && posSource.type === 'geojson') {
                (posSource as GeoJSONSource).setData({
                    type: 'Feature',
                    geometry: { type: 'Point', coordinates: [pos.lng, pos.lat] },
                    properties: { heading: spring.current },
                });
            }

            rafId = requestAnimationFrame(tick);
        };

        rafId = requestAnimationFrame(tick);
        return () => cancelAnimationFrame(rafId);
    }, [isTrackingMode, settings.mapCenterPosition, isLandscape, styleVersion]);

    // Apply zoom changes in manual (non-tracking) mode
    useEffect(() => {
        if (!mapRef.current || isTrackingMode) return;
        const map = mapRef.current.getMap();
        map.easeTo({ zoom, duration: 300 });
    }, [zoom, isTrackingMode]);

    // Reset padding when entering manual mode, preserving visual position
    useEffect(() => {
        if (!mapRef.current || isTrackingMode) return;
        const map = mapRef.current.getMap();

        const container = map.getContainer();
        const visualCenter = map.unproject([
            container.offsetWidth / 2,
            container.offsetHeight / 2,
        ]);

        map.jumpTo({
            center: [visualCenter.lng, visualCenter.lat],
            padding: { top: 0, bottom: 0, left: 0, right: 0 },
        });
    }, [isTrackingMode]);

    // --- Overlay effects (all include styleVersion to re-apply after style swaps) ---

    // Update radar tiles when radarImageUrl changes or after style reload
    useEffect(() => {
        if (!mapRef.current) return;

        const map = mapRef.current.getMap();
        if (!map.isStyleLoaded()) return;

        const source = map.getSource('radar');

        if (source && radarImageUrl) {
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
            }, 'range-rings-layer');
        }
    }, [radarImageUrl, styleVersion]);

    // Update position data when it changes or after style reload
    useEffect(() => {
        if (!mapRef.current) return;

        const map = mapRef.current.getMap();
        if (!map.isStyleLoaded()) return;
        const source = map.getSource('current-position');

        if (source && source.type === 'geojson') {
            (source as GeoJSONSource).setData(positionData);
        }
    }, [positionData, styleVersion]);

    // Update radar opacity & visibility dynamically
    useEffect(() => {
        if (!mapRef.current) return;
        const map = mapRef.current.getMap();
        if (!map.isStyleLoaded()) return;

        if (map.getLayer('radar-layer')) {
            map.setPaintProperty('radar-layer', 'raster-opacity', showRadar ? radarOpacity : 0);
        }
    }, [showRadar, radarOpacity, styleVersion]);

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
    }, [showRangeRings, styleVersion]);

    // Update range ring colors (dark/light) dynamically
    useEffect(() => {
        if (!mapRef.current) return;
        const map = mapRef.current.getMap();
        if (!map.isStyleLoaded()) return;

        if (map.getLayer('range-rings-layer')) {
            map.setPaintProperty('range-rings-layer', 'line-color',
                darkRings ? 'rgba(0, 0, 0, 0.6)' : 'rgba(255, 255, 255, 0.6)');
        }
        if (map.getLayer('range-ring-labels-layer')) {
            map.setPaintProperty('range-ring-labels-layer', 'text-color',
                darkRings ? 'rgba(0, 0, 0, 0.7)' : 'rgba(255, 255, 255, 0.7)');
            map.setPaintProperty('range-ring-labels-layer', 'text-halo-color',
                darkRings ? 'rgba(255, 255, 255, 0.6)' : 'rgba(0, 0, 0, 0.6)');
        }
    }, [darkRings, styleVersion]);

    // Update range ring geometry when position or zoom changes (or after style reload).
    // Label positions are updated in the rAF loop during tracking mode for smooth sync
    // with the spring-driven bearing; this effect handles the initial/manual-mode case.
    useEffect(() => {
        if (!mapRef.current) return;

        const map = mapRef.current.getMap();
        if (!map.isStyleLoaded()) return;
        const ringsSource = map.getSource('range-rings');
        const labelsSource = map.getSource('range-ring-labels');

        if (ringsSource && ringsSource.type === 'geojson') {
            (ringsSource as GeoJSONSource).setData(buildRingData(longitude, latitude, zoom));
        }
        if (labelsSource && labelsSource.type === 'geojson') {
            (labelsSource as GeoJSONSource).setData(
                buildLabelData(longitude, latitude, zoom, heading)
            );
        }
    }, [longitude, latitude, zoom, heading, styleVersion]);

    // Toggle base map layer visibility (hides/shows all non-overlay layers)
    useEffect(() => {
        if (!mapRef.current) return;
        const map = mapRef.current.getMap();
        if (!map.isStyleLoaded()) return;

        const style = map.getStyle();
        if (!style) return;

        const vis = showBaseMap ? 'visible' : 'none';
        for (const layer of style.layers) {
            if (!OVERLAY_LAYER_IDS.has(layer.id)) {
                map.setLayoutProperty(layer.id, 'visibility', vis);
            }
        }
    }, [showBaseMap, styleVersion]);

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
            bearing: isTrackingMode ? heading : 0,
            padding: isTrackingMode
                ? calcPadding(window.innerHeight, window.innerWidth)
                : { top: 0, bottom: 0, left: 0, right: 0 },
        };
    };

    // On initial map load: set up style.load listener for future style swaps,
    // then trigger the first styleVersion bump so overlay effects run.
    const handleMapLoad = useCallback(() => {
        const map = mapRef.current?.getMap();
        if (!map) return;

        if (!map.hasImage('position-arrow')) {
            map.addImage('position-arrow', createArrowImage());
        }

        map.on('style.load', () => {
            if (!map.hasImage('position-arrow')) {
                map.addImage('position-arrow', createArrowImage());
            }
            setStyleVersion(v => v + 1);
        });

        setStyleVersion(v => v + 1);
    }, []);

    if (!mapStyle) {
        return <div className="radar-map-wrapper" />;
    }

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
