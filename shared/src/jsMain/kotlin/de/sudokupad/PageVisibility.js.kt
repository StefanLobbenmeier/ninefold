package de.sudokupad

internal actual fun observePageVisibility(onChange: (Boolean) -> Unit): () -> Unit = js("""
    (() => {
        const listener = () => onChange(document.visibilityState === 'visible');
        document.addEventListener('visibilitychange', listener);
        listener();
        return () => document.removeEventListener('visibilitychange', listener);
    })()
""")
