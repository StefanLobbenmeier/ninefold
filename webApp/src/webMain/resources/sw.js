// Replaced by prepare-offline-release.py after the production distribution is built.
const RELEASE = null;
const APP_CACHE_PREFIX = 'ninefold-app-';
const APP_CACHE = APP_CACHE_PREFIX + RELEASE?.version;
const DATA_CACHE = 'ninefold-offline-data-v1';
const scope = new URL(self.registration.scope);
const assetUrls = new Set((RELEASE?.assets || []).map(asset => new URL(asset.path, scope).href));
const isFontAsset = url => url.origin === 'https://fonts.gstatic.com';

self.addEventListener('install', event => {
    event.waitUntil((async () => {
        if (!RELEASE) throw new Error('Offline release has not been prepared');
        const cache = await caches.open(APP_CACHE);
        // A failed install must never expose a partially downloaded release.
        try {
            for (const asset of RELEASE.assets) {
                const url = new URL(asset.path, scope).href;
                const response = await fetch(url, { cache: 'no-store' });
                if (!response.ok) throw new Error(`Failed to download ${asset.path}`);
                const bytes = await response.clone().arrayBuffer();
                const digest = await crypto.subtle.digest('SHA-256', bytes);
                const hash = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
                if (hash !== asset.sha256) throw new Error(`Release mismatch for ${asset.path}`);
                await cache.put(url, response);
            }
        } catch (error) {
            await caches.delete(APP_CACHE);
            throw error;
        }
        // Let existing tabs finish using their release. The complete new release
        // activates once those tabs close or the user accepts the in-app update prompt.
    })());
});

self.addEventListener('activate', event => {
    event.waitUntil((async () => {
        for (const name of await caches.keys()) {
            if (name.startsWith(APP_CACHE_PREFIX) && name !== APP_CACHE) await caches.delete(name);
        }
    })());
});

// Fonts fetched by the first, uncontrolled page can also be saved for offline use.
self.addEventListener('message', event => {
    if (event.data?.type === 'activate-update') {
        event.waitUntil(self.skipWaiting());
        return;
    }
    if (event.data?.type !== 'cache-fonts' || !Array.isArray(event.data.urls)) return;
    event.waitUntil((async () => {
        const cache = await caches.open(DATA_CACHE);
        await Promise.allSettled(event.data.urls.filter(value => {
            try { return isFontAsset(new URL(value)); } catch { return false; }
        }).map(async url => {
            if (!await cache.match(url)) await cache.add(url);
        }));
    })());
});

self.addEventListener('fetch', event => {
    if (event.request.method !== 'GET') return;
    const url = new URL(event.request.url);
    const navigation = event.request.mode === 'navigate' && url.origin === scope.origin && url.pathname.startsWith(scope.pathname);
    if (navigation || assetUrls.has(url.href)) {
        event.respondWith((async () => {
            const cache = await caches.open(APP_CACHE);
            const saved = await cache.match(navigation ? new URL('index.html', scope).href : url.href);
            // Never combine this release's HTML with files from another release.
            return saved || new Response('The offline app is unavailable. Please reconnect and reopen the app.', {
                status: 503, headers: { 'Content-Type': 'text/plain' },
            });
        })());
        return;
    }
    const puzzle = (url.origin === 'https://sudokupad.svencodes.com' && url.pathname.startsWith('/ctclegacy/')) ||
        (url.origin === 'https://sudokupad.app' && url.pathname.startsWith('/api/puzzle/'));
    // Explicit reimports must reach the network and must not accumulate nonce cache entries.
    if (puzzle && url.searchParams.has('reimport')) return;
    if (!puzzle && !isFontAsset(url)) return;
    event.respondWith((async () => {
        const cache = await caches.open(DATA_CACHE);
        const saved = await cache.match(event.request);
        if (saved) return saved;
        const response = await fetch(event.request);
        if (response.ok) {
            try { await cache.put(event.request, response.clone()); } catch { /* Cache may be full. */ }
        }
        return response;
    })());
});
