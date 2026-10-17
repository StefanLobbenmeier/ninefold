package de.ninefold

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun deviceDate(isoDate: String): String {
    val formatter = android.text.format.DateFormat.getDateFormat(LocalContext.current)
    return runCatching {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).apply { isLenient = false }
        formatter.format(requireNotNull(parser.parse(isoDate)))
    }.getOrDefault(isoDate)
}
