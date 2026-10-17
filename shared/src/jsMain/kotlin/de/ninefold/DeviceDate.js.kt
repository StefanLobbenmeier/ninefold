package de.ninefold

import androidx.compose.runtime.Composable

@Composable
internal actual fun deviceDate(isoDate: String): String = localizedDate(isoDate)

private fun localizedDate(isoDate: String): String = js("""((isoDate) => {
    const date = new Date(isoDate + 'T12:00:00');
    return Number.isNaN(date.getTime()) ? isoDate : date.toLocaleDateString();
})(isoDate)""") as String
