const CACHE_NAME = 'weather-radar-tiles-v2';
const TILE_MAX_AGE = 30 * 60 * 1000; // 30 minutes max cache for radar tiles

// Install service worker
self.addEventListener('install', (event) => {
    console.log('Service Worker installing...');
    self.skipWaiting();
});

// Activate service worker — clear old caches
self.addEventListener('activate', (event) => {
    console.log('Service Worker activating...');
    event.waitUntil(
        caches.keys().then((keys) =>
            Promise.all(
                keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))
            )
        ).then(() => self.clients.claim())
    );
});

// Intercept fetch requests
self.addEventListener('fetch', (event) => {
    const url = event.request.url;

    // API metadata must ALWAYS go to network (never cache)
    if (url.includes('api.rainviewer.com')) {
        event.respondWith(
            fetch(event.request).catch(() => {
                // If network fails, try cache as last resort
                return caches.match(event.request).then((r) => r || Response.error());
            })
        );
        return;
    }

    // Only intercept tile requests (RainViewer tiles and OpenStreetMap)
    if (url.includes('tilecache.rainviewer.com') ||
        url.includes('tile.openstreetmap.org')) {

        event.respondWith(
            caches.open(CACHE_NAME).then(async (cache) => {
                // Check cache first
                const cached = await cache.match(event.request);
                if (cached) {
                    // For radar tiles, check age — evict if stale
                    const dateHeader = cached.headers.get('sw-cached-at');
                    if (dateHeader && url.includes('tilecache.rainviewer.com')) {
                        const age = Date.now() - parseInt(dateHeader, 10);
                        if (age > TILE_MAX_AGE) {
                            // Stale — fetch fresh
                            await cache.delete(event.request);
                        } else {
                            return cached;
                        }
                    } else {
                        return cached;
                    }
                }

                // Not in cache or stale, fetch from network
                try {
                    const response = await fetch(event.request, {
                        mode: 'cors',
                        credentials: 'omit'
                    });

                    // Cache successful responses with a timestamp header
                    if (response.ok) {
                        const headers = new Headers(response.headers);
                        headers.set('sw-cached-at', Date.now().toString());
                        const timedResponse = new Response(await response.clone().blob(), {
                            status: response.status,
                            statusText: response.statusText,
                            headers
                        });
                        cache.put(event.request, timedResponse);
                    }

                    return response;
                } catch (error) {
                    console.error('Tile fetch failed:', error);
                    // Return a transparent 1x1 PNG as fallback
                    const base64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';
                    const binary = atob(base64);
                    const bytes = new Uint8Array(binary.length);
                    for (let i = 0; i < binary.length; i++) {
                        bytes[i] = binary.charCodeAt(i);
                    }
                    return new Response(bytes, {
                        status: 200,
                        headers: { 'Content-Type': 'image/png' }
                    });
                }
            })
        );
    }
    // Let other requests pass through
});
