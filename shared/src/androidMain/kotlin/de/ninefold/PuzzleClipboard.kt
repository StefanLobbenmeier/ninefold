package de.ninefold

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun puzzleLinkClipEntry(text: String): ClipEntry = ClipEntry(android.content.ClipData.newPlainText("Puzzle link", text))
