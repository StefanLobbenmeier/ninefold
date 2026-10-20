// The Compose UI subscribes after startup; retain the state for late subscribers.
window.ninefoldUpdate = (() => {
    let registration;
    let state = 'None';
    let activationTimeout;
    const listeners = new Set();
    const publish = value => {
        state = value;
        listeners.forEach(listener => listener(value));
    };
    const ready = () => {
        if (state !== 'Applying') publish(registration?.waiting ? 'Ready' : 'None');
    };
    const api = {
        subscribe(listener) {
            listeners.add(listener);
            listener(state);
            return () => listeners.delete(listener);
        },
        apply() {
            const waiting = registration?.waiting;
            if (!waiting || state === 'Applying') return;
            publish('Applying');
            // Keep the action retryable if activation fails or the worker disappears.
            activationTimeout = setTimeout(() => publish('Failed'), 15000);
            try { waiting.postMessage({ type: 'activate-update' }); }
            catch { clearTimeout(activationTimeout); publish('Failed'); }
        },
    };

    if ('serviceWorker' in navigator) {
        // All open app tabs switch together when the user accepts an update in any tab.
        // Initial installation must not reload an uncontrolled first visit.
        let controlled = Boolean(navigator.serviceWorker.controller);
        let reloading = false;
        navigator.serviceWorker.addEventListener('controllerchange', () => {
            if (!navigator.serviceWorker.controller) return;
            if (controlled && !reloading) {
                reloading = true;
                clearTimeout(activationTimeout);
                window.location.reload();
            }
            controlled = true;
        });
        navigator.serviceWorker.register('sw.js', { updateViaCache: 'none' }).then(value => {
            registration = value;
            const watchInstallation = () => {
                const worker = registration.installing;
                if (!worker) return;
                worker.addEventListener('statechange', ready);
                ready();
            };
            registration.addEventListener('updatefound', watchInstallation);
            watchInstallation();
            ready(); // An update may already be waiting from an earlier launch.

            let checking = false;
            const check = async () => {
                if (checking) return;
                checking = true;
                try { await registration.update(); }
                catch (error) { console.warn('Update check unavailable', error); }
                finally { checking = false; ready(); }
            };
            check();
            setInterval(check, 5 * 60 * 1000);
            const resume = () => { if (document.visibilityState === 'visible') check(); };
            document.addEventListener('visibilitychange', resume);
            window.addEventListener('pageshow', resume);
            window.addEventListener('focus', resume);
        }).catch(error => console.warn('Offline cache unavailable', error));

        navigator.serviceWorker.ready.then(registration => {
            const cacheFonts = entries => registration.active?.postMessage({
                type: 'cache-fonts',
                urls: entries.map(entry => entry.name).filter(url => new URL(url).origin === 'https://fonts.gstatic.com'),
            });
            cacheFonts(performance.getEntriesByType('resource'));
            const observer = new PerformanceObserver(list => cacheFonts(list.getEntries()));
            observer.observe({ type: 'resource' });
        }).catch(error => console.warn('Font cache unavailable', error));
    }
    return api;
})();
