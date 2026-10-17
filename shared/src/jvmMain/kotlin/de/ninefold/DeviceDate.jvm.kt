package de.ninefold

import androidx.compose.runtime.Composable

@Composable
internal actual fun deviceDate(isoDate: String): String {
    val formatter = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT)
    return runCatching {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).apply { isLenient = false }
        formatter.format(requireNotNull(parser.parse(isoDate)))
    }.getOrDefault(isoDate)
}
