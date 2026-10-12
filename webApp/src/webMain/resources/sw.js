const CACHE = 'ninefold-offline-v1';
const scope = new URL(self.registration.scope);
const isAppAsset = url => url.origin === scope.origin && url.pathname.startsWith(scope.pathname) && !url.pathname.endsWith('/sw.js');

self.addEventListener('install', event => {
    event.waitUntil(caches.open(CACHE).then(cache => cache.addAll([
        scope.href, new URL('index.html', scope).href, new URL('styles.css', scope).href,
        new URL('offline.js', scope).href, new URL('webApp.js', scope).href,
    ])).then(() => self.skipWaiting()));
});
self.addEventListener('activate', event => {
    event.waitUntil(self.clients.claim());
});
self.addEventListener('message', event => {
    if (event.data?.type !== 'cache-assets' || !Array.isArray(event.data.urls)) return;
    event.waitUntil(caches.open(CACHE).then(cache => Promise.allSettled(
        event.data.urls.filter(value => {
            try { return isAppAsset(new URL(value)); } catch { return false; }
        }).map(async url => {
            if (!await cache.match(url)) await cache.add(url);
        })
    )));
});
self.addEventListener('fetch', event => {
    if (event.request.method !== 'GET') return;
    const url = new URL(event.request.url);
    const puzzle = (url.origin === 'https://sudokupad.svencodes.com' && url.pathname.startsWith('/ctclegacy/')) ||
        (url.origin === 'https://sudokupad.app' && url.pathname.startsWith('/api/puzzle/'));
    if (!isAppAsset(url) && !puzzle) return;
    event.respondWith((async () => {
        const cache = await caches.open(CACHE);
        try {
            const response = await fetch(event.request);
            if (response.ok) {
                // A storage failure must not turn a successful request into a failure.
                try { await cache.put(event.request, response.clone()); } catch { /* Cache may be full. */ }
                return response;
            }
            const saved = await cache.match(event.request);
            return saved || response;
        } catch (error) {
            const saved = await cache.match(event.request) ||
                (event.request.mode === 'navigate' ? await cache.match(scope.href) : undefined);
            if (saved) return saved;
            throw error;
        }
    })());
});
