package de.sudokupad

import io.github.vinceglb.filekit.FileKit
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() {
    FileKit.init(appId = "de.sudokupad")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "sudokupad",
        ) {
            App()
        }
    }
}
