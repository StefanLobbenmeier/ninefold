package de.sudokupad

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val pageUrl = browserPageUrl()
    ComposeViewport {
        App(BrowserIndexCache, initialShareLink = pageUrl, shareBaseUrl = pageUrl)
    }
}

internal expect fun browserPageUrl(): String
