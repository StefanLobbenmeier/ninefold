package de.ninefold

internal actual fun observeAppUpdate(onChange: (String) -> Unit): () -> Unit =
    js("window.ninefoldUpdate.subscribe(onChange)")

internal actual fun applyAppUpdate() { js("window.ninefoldUpdate.apply()") }

internal actual fun setAppUpdatePaused(paused: Boolean) { js("window.ninefoldUpdate.setPaused(paused)") }
