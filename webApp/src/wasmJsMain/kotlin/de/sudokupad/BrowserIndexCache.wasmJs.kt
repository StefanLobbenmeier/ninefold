@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package de.sudokupad

@JsFun("() => localStorage.getItem('ninefold.sudoku-index.v1')")
internal actual external fun readBrowserIndex(): String?

@JsFun("(payload) => localStorage.setItem('ninefold.sudoku-index.v1', payload)")
internal actual external fun writeBrowserIndex(payload: String)
