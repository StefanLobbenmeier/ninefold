package de.ninefold

internal actual fun sharePuzzle(url: String, title: String, onUnavailable: () -> Unit) = onUnavailable()
