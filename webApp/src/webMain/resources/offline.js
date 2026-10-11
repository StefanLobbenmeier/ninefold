// Keep the app shell and lazily loaded Kotlin/Wasm assets available after the first visit.
if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').then(async () => {
        const registration = await navigator.serviceWorker.ready;
        const cacheAssets = (urls) => registration.active?.postMessage({ type: 'cache-assets', urls });
        cacheAssets(performance.getEntriesByType('resource').map(entry => entry.name));
        const observer = new PerformanceObserver(list => cacheAssets(list.getEntries().map(entry => entry.name)));
        observer.observe({ type: 'resource' });
    }).catch(error => console.warn('Offline cache unavailable', error));
}
