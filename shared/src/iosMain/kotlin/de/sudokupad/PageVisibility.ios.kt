package de.sudokupad

internal actual fun observePageVisibility(onChange: (Boolean) -> Unit): () -> Unit {
    onChange(true)
    return {}
}
