package de.ninefold

internal actual fun readBrowserIndex(): String? = js("localStorage.getItem('ninefold.sudoku-index.v1')") as String?
internal actual fun writeBrowserIndex(payload: String) { js("localStorage.setItem('ninefold.sudoku-index.v1', payload)") }

internal actual fun browserPageUrl(): String = js("window.location.href") as String

internal actual fun openBrowserExternalLink(uri: String) {
    js("window.ninefoldOpenExternalLink(uri)")
}
