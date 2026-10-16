package de.sudokupad

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont
import sudokupad.shared.generated.resources.Res
import sudokupad.shared.generated.resources.lora

/** Wait for Compose's decoded font cache, including when bytes come from the offline cache. */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun WithStartupFonts(content: @Composable () -> Unit) {
    val lora by preloadFont(Res.font.lora)
    if (lora != null) {
        content()
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Moss)
        }
    }
}
