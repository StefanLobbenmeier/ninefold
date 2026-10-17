package de.ninefold

internal actual fun observePageVisibility(onChange: (Boolean) -> Unit): () -> Unit {
    onChange(true)
    return {}
}
