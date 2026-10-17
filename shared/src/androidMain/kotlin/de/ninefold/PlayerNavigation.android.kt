package de.ninefold
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
@Composable
internal actual fun PlayerBackHandler(onBack: () -> Unit) { BackHandler(onBack = onBack) }
