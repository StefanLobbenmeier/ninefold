package de.ninefold

import ninefold.shared.generated.resources.lora
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

internal enum class Glyph { Back, Play, Grid, Add, Erase, Multiple, Undo, Redo, Rules, Check, Settings, Refresh }

/** Small, consistent stroke icons; labels live on their interactive parents. */
@Composable
internal fun AppIcon(glyph: Glyph, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(22.dp)) {
        val scale = size.width / 24f
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(x * scale, y * scale), Offset(x2 * scale, y2 * scale), 1.8f * scale, StrokeCap.Round)
        fun path(vararg points: Pair<Float, Float>, close: Boolean = false) {
            val p = Path().apply { moveTo(points[0].first * scale, points[0].second * scale); points.drop(1).forEach { lineTo(it.first * scale, it.second * scale) }; if (close) close() }
            drawPath(p, color, style = Stroke(1.8f * scale, cap = StrokeCap.Round))
        }
        when (glyph) {
            Glyph.Back -> { line(4f,12f,20f,12f); path(10f to 5f, 3f to 12f, 10f to 19f) }
            Glyph.Play -> path(8f to 4f, 20f to 12f, 8f to 20f, close = true)
            Glyph.Add -> { line(12f,5f,12f,19f); line(5f,12f,19f,12f) }
            Glyph.Grid -> { drawRect(color, Offset(3*scale,3*scale), Size(18*scale,18*scale), style = Stroke(1.8f*scale)); for(i in listOf(9f,15f)) { line(i,3f,i,21f); line(3f,i,21f,i) } }
            Glyph.Multiple -> { path(8f to 3f,21f to 3f,21f to 16f); drawRect(color,Offset(3*scale,8*scale),Size(13*scale,13*scale),style=Stroke(1.8f*scale)) }
            Glyph.Erase -> { path(9f to 6f,21f to 6f,21f to 18f,9f to 18f,3f to 12f,close=true); line(12f,9f,17f,15f); line(17f,9f,12f,15f) }
            Glyph.Check -> path(4f to 12f,9f to 17f,20f to 6f)
            Glyph.Rules -> { path(12f to 6f,3f to 4f,3f to 19f,12f to 21f,21f to 19f,21f to 4f,12f to 6f,12f to 21f); line(6f,9f,9f,10f); line(15f,10f,18f,9f) }
            Glyph.Undo -> { path(8f to 4f,3f to 9f,8f to 14f); path(3f to 9f,15f to 9f,20f to 13f,20f to 18f) }
            Glyph.Redo -> { path(16f to 4f,21f to 9f,16f to 14f); path(21f to 9f,9f to 9f,4f to 13f,4f to 18f) }
            Glyph.Settings -> { for (y in listOf(6f,12f,18f)) line(3f,y,21f,y); for ((x,y) in listOf(8f to 6f,16f to 12f,8f to 18f)) drawCircle(color,3*scale,Offset(x*scale,y*scale)) }
            Glyph.Refresh -> { drawArc(color, 30f, 290f, false, Offset(4*scale,4*scale),Size(16*scale,16*scale),style=Stroke(1.8f*scale)); path(21f to 4f,21f to 10f,15f to 10f) }
        }
    }
}

/** Bundled so editorial headings render consistently, including in offline web builds. */
@Composable
internal fun editorialFont() = androidx.compose.ui.text.font.FontFamily(
    org.jetbrains.compose.resources.Font(ninefold.shared.generated.resources.Res.font.lora),
)
