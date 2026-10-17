@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package de.ninefold

@JsFun("""(url, title, onUnavailable) => {
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
}""")
internal actual external fun sharePuzzle(url: String, title: String, onUnavailable: () -> Unit)
