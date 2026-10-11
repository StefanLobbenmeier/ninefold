package de.sudokupad

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageRecognitionTest {
    private val clues = "530070000600195000098000060800060003400803001700020006060000280000419005000080079".replace('0', '.')
    private fun screenshot(font: String, cell: Int, dark: Boolean, grid: String = clues): GrayImage {
        val image = BufferedImage(cell * 9 + 120, cell * 9 + 180, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = if (dark) Color(30, 33, 38) else Color.WHITE
        g.fillRect(0, 0, image.width, image.height)
        g.color = if (dark) Color(230, 235, 240) else Color(35, 40, 45)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.font = Font("SansSerif", Font.PLAIN, 18)
        g.drawString("Classic Sudoku 123 • Daily puzzle", 20, 30)
        val left = 45; val top = 70
        repeat(10) {
            g.stroke = BasicStroke(if (it % 3 == 0) 3f else 1f)
            g.drawLine(left, top + it * cell, left + 9 * cell, top + it * cell)
            g.drawLine(left + it * cell, top, left + it * cell, top + 9 * cell)
        }
        g.font = Font(font, Font.PLAIN, (cell * .60).toInt())
        grid.forEachIndexed { i, digit -> if (digit != '.') {
            val fm = g.fontMetrics
            g.drawString(digit.toString(), left + i % 9 * cell + (cell - fm.charWidth(digit)) / 2, top + i / 9 * cell + (cell - fm.height) / 2 + fm.ascent)
        } }
        g.dispose()
        // Also usable as a browser upload fixture during manual smoke testing.
        if (font == "Arial" && cell == 50 && !dark) {
            val file = File("build/test-fixtures/classic.png"); file.parentFile.mkdirs(); ImageIO.write(image, "png", file)
        }
        return GrayImage(image.width, image.height, IntArray(image.width * image.height) { i ->
            val rgb = image.getRGB(i % image.width, i / image.width)
            (((rgb ushr 16) and 255) * 299 + ((rgb ushr 8) and 255) * 587 + (rgb and 255) * 114) / 1000
        })
    }

    @Test fun recognizesScreenshotsWithMarginsDifferentFontsSizesAndThemes() = runTest {
        val templates = createDigitTemplates(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr))
        for ((font, cell, dark) in listOf(Triple("Arial", 50, false), Triple("Georgia", 38, false), Triple("Verdana", 32, true), Triple("Times New Roman", 64, false))) {
            val image = screenshot(font, cell, dark)
            val bounds = assertNotNull(detectGrid(image), "$font grid not found")
            assertTrue(kotlin.math.abs(bounds.left - 45) <= 2)
            assertTrue(kotlin.math.abs(bounds.top - 70) <= 2)
            val result = recognizeGrid(image, bounds, templates)
            assertEquals(clues, result.grid, "$font / $cell / dark=$dark: uncertain=${result.uncertain}")
        }
    }

    @Test fun preservesCellPositionsIncludingSparseRowsAndColumns() = runTest {
        val templates = createDigitTemplates(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr))
        val sparse = CharArray(81) { '.' }.also { for (n in 1..9) it[(n - 1) * 10] = n.digitToChar() }.concatToString()
        val image = screenshot("Arial", 45, false, sparse)
        assertEquals(sparse, recognizeGrid(image, assertNotNull(detectGrid(image)), templates).grid)
    }

    @Test fun rejectsImagesWithoutAGrid() = runTest {
        assertNull(detectGrid(GrayImage(200, 300, IntArray(60000) { 255 })))
        val image = GrayImage(200, 300, IntArray(60000) { if (it / 200 % 20 < 2) 0 else 255 })
        assertNull(detectGrid(image))
    }
}
