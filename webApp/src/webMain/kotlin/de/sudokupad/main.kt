package de.sudokupad

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.window.ComposeViewport

private object ExternalUriHandler : UriHandler {
    override fun openUri(uri: String) = openBrowserExternalLink(uri)
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val pageUrl = browserPageUrl()
    ComposeViewport {
        CompositionLocalProvider(LocalUriHandler provides ExternalUriHandler) {
            App(BrowserIndexCache, initialShareLink = pageUrl, shareBaseUrl = pageUrl)
        }
    }
}

internal expect fun browserPageUrl(): String
internal expect fun openBrowserExternalLink(uri: String)
