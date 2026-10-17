@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package de.ninefold

import androidx.compose.runtime.Composable

@JsFun("""(isoDate) => {
    const date = new Date(isoDate + 'T12:00:00');
    return Number.isNaN(date.getTime()) ? isoDate : date.toLocaleDateString();
}""")
private external fun localizedDate(isoDate: String): String

@Composable
internal actual fun deviceDate(isoDate: String): String = localizedDate(isoDate)
