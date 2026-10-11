package de.sudokupad

internal actual fun readBrowserIndex(): String? = js("localStorage.getItem('ninefold.sudoku-index.v1')") as String?
internal actual fun writeBrowserIndex(payload: String) { js("localStorage.setItem('ninefold.sudoku-index.v1', payload)") }
