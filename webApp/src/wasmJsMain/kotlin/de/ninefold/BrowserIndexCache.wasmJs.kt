@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package de.ninefold

@JsFun("() => localStorage.getItem('ninefold.sudoku-index.v1')")
internal actual external fun readBrowserIndex(): String?

@JsFun("(payload) => localStorage.setItem('ninefold.sudoku-index.v1', payload)")
internal actual external fun writeBrowserIndex(payload: String)

@JsFun("() => window.location.href")
internal actual external fun browserPageUrl(): String

@JsFun("(uri) => window.ninefoldOpenExternalLink(uri)")
internal actual external fun openBrowserExternalLink(uri: String)
