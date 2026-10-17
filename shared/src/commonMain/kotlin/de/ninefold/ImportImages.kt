package de.ninefold

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.yield

fun ImageBitmap.importSize(): ImageBitmap {
    require(width.toLong() * height <= 40_000_000) { "This image is too large. Choose a smaller screenshot." }
    val scale = 1400.0 / max(width, height)
    if (scale >= 1) return this
    val size = IntSize((width * scale).roundToInt(), (height * scale).roundToInt())
    return ImageBitmap(size.width, size.height).also {
        Canvas(it).drawImageRect(this, dstSize = size, paint = Paint())
    }
}

fun ImageBitmap.grayscale(): GrayImage {
    val pixels = IntArray(width * height)
    readPixels(pixels)
    for (i in pixels.indices) {
        val argb = pixels[i]
        val alpha = (argb ushr 24) and 255
        val gray = (((argb ushr 16) and 255) * 299 + ((argb ushr 8) and 255) * 587 + (argb and 255) * 114) / 1000
        pixels[i] = (gray * alpha + 255 * (255 - alpha)) / 255
    }
    return GrayImage(width, height, pixels)
}

/** Build local printed-digit OCR templates using the platform's sans, serif and mono fonts.
 * Nothing is downloaded and no screenshot is sent to a service.
 */
suspend fun createDigitTemplates(measurer: TextMeasurer): List<DigitTemplate> {
    val templates = mutableListOf<DigitTemplate>()
    for (family in listOf(FontFamily.SansSerif, FontFamily.Serif, FontFamily.Monospace)) {
        for (weight in listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.Bold)) {
            for (digit in 1..9) {
                yield()
                val layout = measurer.measure(digit.toString(), TextStyle(fontFamily = family, fontWeight = weight, fontSize = 48.sp, color = Color.Black))
                val bitmap = ImageBitmap(layout.size.width + 8, layout.size.height + 8)
                val canvas = Canvas(bitmap)
                canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), Paint().apply { color = Color.White })
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
                    drawText(layout, topLeft = Offset(4f, 4f))
                }
                val gray = bitmap.grayscale()
                val shape = digitShape(BooleanArray(gray.pixels.size) { gray.pixels[it] < 160 }, gray.width, gray.height)
                if (shape != null) templates += DigitTemplate(digit, shape)
            }
        }
    }
    return templates
}
