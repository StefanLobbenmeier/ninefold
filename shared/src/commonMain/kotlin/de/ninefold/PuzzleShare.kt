package de.ninefold

/** Invoked directly from a click to preserve browser user activation.
 * Calls onUnavailable when native sharing is unsupported or fails; cancellation is ignored.
 */
internal expect fun sharePuzzle(url: String, title: String, onUnavailable: () -> Unit)
