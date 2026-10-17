package de.ninefold

import io.github.vinceglb.filekit.FileKit
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.jetbrains.compose.resources.painterResource
import ninefold.desktopapp.generated.resources.Res
import ninefold.desktopapp.generated.resources.ninefold_icon

fun main() {
    FileKit.init(appId = "de.ninefold")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Ninefold",
            icon = painterResource(Res.drawable.ninefold_icon),
        ) {
            App()
        }
    }
}
