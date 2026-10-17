package de.ninefold

import androidx.compose.runtime.Composable

/** Formats an ISO calendar date using the device's date preferences. */
@Composable
internal expect fun deviceDate(isoDate: String): String
