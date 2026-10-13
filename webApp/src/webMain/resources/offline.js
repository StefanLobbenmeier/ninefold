if ('serviceWorker' in navigator) {
    // Check for a new worker without delaying startup of the cached app.
    navigator.serviceWorker.register('sw.js', { updateViaCache: 'none' }).then(async () => {
        const registration = await navigator.serviceWorker.ready;
        const cacheFonts = entries => registration.active?.postMessage({
            type: 'cache-fonts',
            urls: entries.map(entry => entry.name).filter(url => new URL(url).origin === 'https://fonts.gstatic.com'),
        });
        cacheFonts(performance.getEntriesByType('resource'));
        const observer = new PerformanceObserver(list => cacheFonts(list.getEntries()));
        observer.observe({ type: 'resource' });
    }).catch(error => console.warn('Offline cache unavailable', error));
}
