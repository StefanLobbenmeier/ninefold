package de.ninefold

import io.github.vinceglb.filekit.FileKit
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() {
    FileKit.init(appId = "de.ninefold")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Ninefold",
        ) {
            App()
        }
    }
}
