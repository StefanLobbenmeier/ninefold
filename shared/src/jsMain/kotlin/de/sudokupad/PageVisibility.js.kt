package de.sudokupad

internal actual fun observePageVisibility(onChange: (Boolean) -> Unit): () -> Unit = js("""
    (() => {
        const listener = () => onChange(document.visibilityState === 'visible');
        const hide = () => onChange(false);
        // A restored iOS PWA may return through pageshow/focus without visibilitychange.
        // Ignore focus while hidden, and do not pause on blur (e.g. opening a system sheet).
        const resume = () => { if (document.visibilityState === 'visible') onChange(true); };
        document.addEventListener('visibilitychange', listener);
        window.addEventListener('pagehide', hide);
        window.addEventListener('pageshow', resume);
        window.addEventListener('focus', resume);
        listener();
        return () => {
            document.removeEventListener('visibilitychange', listener);
            window.removeEventListener('pagehide', hide);
            window.removeEventListener('pageshow', resume);
            window.removeEventListener('focus', resume);
        };
    })()
""")
