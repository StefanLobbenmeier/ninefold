import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))

            implementation(libs.compose.ui)
        }
    }
}

// Include every emitted chunk, Wasm module, font and other runtime resource.
// Run after distribution copying so sw.js always starts from the source template.
listOf("js", "wasmJs").forEach { target ->
    val preparation = providers.exec {
        commandLine(
            "python3", rootProject.file("scripts/prepare-offline-release.py"),
            layout.buildDirectory.dir("dist/$target/productionExecutable").get().asFile,
        )
    }.result
    tasks.named("${target}BrowserDistribution") {
        inputs.file(rootProject.file("scripts/prepare-offline-release.py"))
        doLast { preparation.get().assertNormalExitValue() }
    }
}
