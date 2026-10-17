package de.ninefold

/** Reports current browser visibility immediately; returns a listener cleanup function. */
internal expect fun observePageVisibility(onChange: (Boolean) -> Unit): () -> Unit
