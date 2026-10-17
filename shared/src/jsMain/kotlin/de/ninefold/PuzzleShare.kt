package de.ninefold

internal actual fun sharePuzzle(url: String, title: String, onUnavailable: () -> Unit) {
    js("""
    (() => {
    const data = {url, title};
    try {
        if (!navigator.share || (navigator.canShare && !navigator.canShare(data))) {
            onUnavailable();
            return;
        }
        navigator.share(data).catch(error => {
            if (error.name !== 'AbortError') onUnavailable();
        });
    } catch (error) {
        onUnavailable();
    }
    })()
    """)
}
