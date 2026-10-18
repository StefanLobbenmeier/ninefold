// Replaced by prepare-offline-release.py after the production distribution is built.
const RELEASE = {"version":"b9e6546f24f635605486fb51c887ddf7336bddae9409aa136304f889cf3ada60","assets":[{"path":"658820f72b4617efb380.wasm","sha256":"08e904ec38a6a81a4809a853acf68022f83905286827e804e88b262c37f2954d"},{"path":"914cb72b59b226f48940.wasm","sha256":"d0f702163679e9f340a615e376753372dba963de43a7b730674513b65e326aca"},{"path":"apple-touch-icon.png","sha256":"dc0f3d482b254c410324d10828e06cd3803c0aaa8070c21fcc7d3dad5f927bb1"},{"path":"composeResources/ninefold.shared.generated.resources/drawable/compose-multiplatform.xml","sha256":"8db35d9bcb1ad5b0e61fa2fd73bb0dc6180eac708f1dd8a22860acc3cdbcec3b"},{"path":"composeResources/ninefold.shared.generated.resources/drawable/ninefold_icon.png","sha256":"6f460b1923584c698f5e71368987f54a0f281c56b2dfb69c4548aa3763d8bf7c"},{"path":"composeResources/ninefold.shared.generated.resources/font/lora.ttf","sha256":"822a6621ccbe8d97d20ac88c1c41f5615c9c2c202eaa75f272cd452aac6475a7"},{"path":"external-links.js","sha256":"4d5efa1d7193a6e02c3e5270354196e29aaf1eacc5a56d594e7104a3f34e4d47"},{"path":"favicon.ico","sha256":"e02e52ef8c95faea36e4c0f2c0a85057bacbd2833779dbad25b550cadcc05d4a"},{"path":"icons/icon-192.png","sha256":"4cb6d09f6006c2ec99312cd4285612f780abf0c162f8b70cc12e64b33fdea735"},{"path":"icons/icon-512.png","sha256":"9b967d942b5a41f9dd85a8a058f6f388b2f5c96d1dd4cd003764a8c0a37965b3"},{"path":"icons/icon-maskable-192.png","sha256":"abf42172e804b0ebfd493b900506e8435ad862e4cd882b6c8b3aa03651357afc"},{"path":"icons/icon-maskable-512.png","sha256":"caf90b19802fa96280138507a8cf356d715898a8f5d9cadb8bfa82dcac9b5724"},{"path":"index.html","sha256":"5d08d1c6ad6af6aaf3f94f21e36848f8ee0429d464f1cafeb762976535a0fd1e"},{"path":"manifest.webmanifest","sha256":"420322e28bf6574eebac32c461edf49bd863b3d48cd6233787e314e0e3730448"},{"path":"offline.js","sha256":"4ab924510fef953dfc8fd0288fc92fe62b260c1b21ad792d249da1f9a2223982"},{"path":"styles.css","sha256":"adeed4cf2e4f76faf945f1f0adf8a05fce135eb43b383a06ee336aa936ff9e5c"},{"path":"viewport-recovery.js","sha256":"bb56029da96cb47921d2a1ed8159cfd4b4e566203475617292f34b56ea45acc0"},{"path":"webApp.js","sha256":"4457638c1186bf6a075d1b8ec6b98c5a25c48c122ebbcab66bc69426b41cfe3d"},{"path":"webApp.js.LICENSE.txt","sha256":"85a703daf937b69e6a45c75947dcea43f4026a835b84a7ea4300843217311baf"}]};
const scope = new URL(self.registration.scope);
// CacheStorage is shared by every worker on this origin, including PR previews.
const APP_CACHE_PREFIX = `ninefold-app-${encodeURIComponent(scope.href)}:`;
const APP_CACHE = APP_CACHE_PREFIX + RELEASE?.version;
const DATA_CACHE = 'ninefold-offline-data-v1';
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
