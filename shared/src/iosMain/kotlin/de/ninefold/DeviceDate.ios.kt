package de.ninefold

import androidx.compose.runtime.Composable
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale

@Composable
internal actual fun deviceDate(isoDate: String): String {
    val parser = NSDateFormatter().apply {
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
        dateFormat = "yyyy-MM-dd"
    }
    val date = parser.dateFromString(isoDate) ?: return isoDate
    return NSDateFormatter().apply { dateStyle = NSDateFormatterShortStyle }.stringFromDate(date)
}
