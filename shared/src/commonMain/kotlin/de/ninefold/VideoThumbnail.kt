package de.ninefold

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
internal fun VideoThumbnail(url: String, durationSeconds: Int?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().aspectRatio(16f / 9).background(Sage), contentAlignment = Alignment.Center) {
        AppIcon(Glyph.Grid, Modifier.size(48.dp))
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Surface(Modifier.align(Alignment.BottomStart).padding(12.dp), color = Ink.copy(alpha = .85f), contentColor = Color.White, shape = RoundedCornerShape(6.dp)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(Glyph.Play, Modifier.size(14.dp)); Spacer(Modifier.width(6.dp))
                Text(durationSeconds?.let { formatElapsed(it.toLong()) } ?: "Video puzzle", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
