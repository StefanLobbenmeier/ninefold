package de.ninefold

import androidx.compose.runtime.*
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
        var updateState by remember { mutableStateOf(AppUpdateState.None) }
        DisposableEffect(Unit) {
            val unsubscribe = observeAppUpdate { updateState = AppUpdateState.valueOf(it) }
            onDispose { unsubscribe() }
        }
        CompositionLocalProvider(LocalUriHandler provides ExternalUriHandler) {
            WithStartupFonts {
                App(
                    BrowserIndexCache, initialShareLink = pageUrl, shareBaseUrl = pageUrl,
                    appUpdateState = updateState, onApplyUpdate = ::applyAppUpdate,
                    onPlayingChange = ::setAppUpdatePaused,
                )
            }
        }
    }
}

internal expect fun browserPageUrl(): String
internal expect fun openBrowserExternalLink(uri: String)

internal expect fun observeAppUpdate(onChange: (String) -> Unit): () -> Unit
internal expect fun applyAppUpdate()

internal expect fun setAppUpdatePaused(paused: Boolean)
