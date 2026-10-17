import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.filekit.dialogs.compose)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "de.ninefold.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            modules("jdk.security.auth")
            packageName = "de.ninefold"
            packageVersion = "1.0.0"
            macOS { iconFile.set(project.file("icons/ninefold.icns")) }
            windows { iconFile.set(project.file("icons/ninefold.ico")) }
            linux { iconFile.set(project.file("icons/ninefold.png")) }
        }
    }
}
