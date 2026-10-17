package de.ninefold

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.decodeToImageBitmap

private val thumbnailClient by lazy { HttpClient() }
// Bound memory usage when scrolling a large collection.
private val thumbnails = mutableMapOf<String, ImageBitmap>()

@Composable
internal fun VideoThumbnail(url: String, durationSeconds: Int?) {
    var bitmap by remember(url) { mutableStateOf(thumbnails[url]) }
    LaunchedEffect(url) {
        if (bitmap == null) try {
            val loaded = withTimeout(10_000) {
                val response = thumbnailClient.get(url)
                require(response.status.isSuccess())
                response.bodyAsBytes().decodeToImageBitmap()
            }
            if (thumbnails.size >= 24) thumbnails.remove(thumbnails.keys.first())
            thumbnails[url] = loaded
            bitmap = loaded
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) { /* Show the fallback. */ }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { /* The video remains useful without a network image. */ }
    }
    Box(Modifier.fillMaxWidth().heightIn(max = 220.dp).aspectRatio(16f / 9).background(Sage), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else AppIcon(Glyph.Grid, Modifier.size(48.dp))
        Surface(Modifier.align(Alignment.BottomStart).padding(12.dp), color = Ink.copy(alpha = .85f), contentColor = Color.White, shape = RoundedCornerShape(6.dp)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(Glyph.Play, Modifier.size(14.dp)); Spacer(Modifier.width(6.dp))
                Text(durationSeconds?.let { formatElapsed(it.toLong()) } ?: "Video puzzle", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
