package de.ninefold

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.yield

/** Pixel coordinates, with exclusive right/bottom bounds. */
data class GridBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

data class GrayImage(val width: Int, val height: Int, val pixels: IntArray) {
    init { require(width > 0 && height > 0 && pixels.size == width * height) }
    operator fun get(x: Int, y: Int) = pixels[y * width + x]
}

data class DigitShape(val pixels: BooleanArray, val aspect: Double) {
    val expanded = BooleanArray(pixels.size) { i ->
        val x = i % 20; val y = i / 20
        (-1..1).any { dy -> (-1..1).any { dx ->
            x + dx in 0..19 && y + dy in 0..27 && pixels[(y + dy) * 20 + x + dx]
        } }
    }
    // Closed counters distinguish similar silhouettes, especially 6 / 8 / 9.
    val holes: List<Double> = buildList {
        val seen = BooleanArray(pixels.size)
        for (i in pixels.indices) if (!pixels[i] && !seen[i]) {
            val queue = mutableListOf(i); seen[i] = true
            var head = 0; var edge = false
            while (head < queue.size) {
                val p = queue[head++]; val x = p % 20; val y = p / 20
                if (x == 0 || x == 19 || y == 0 || y == 27) edge = true
                for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                    if (x + dx !in 0..19 || y + dy !in 0..27) continue
                    val n = (y + dy) * 20 + x + dx
                    if (!pixels[n] && !seen[n]) { seen[n] = true; queue += n }
                }
            }
            if (!edge && queue.size >= 4) add(queue.map { it / 20 }.average() / 28)
        }
    }.sorted()
}
data class DigitTemplate(val digit: Int, val shape: DigitShape)
data class RecognizedGrid(val grid: String, val uncertain: Set<Int>)

/** Find ten regularly spaced horizontal lines and verify their vertical intersections.
 * Searching both polarities supports light and dark screenshots; surrounding UI is ignored.
 */
suspend fun detectGrid(image: GrayImage): GridBounds? {
    data class Line(val left: Int, val right: Int, val y: Int)
    var best: GridBounds? = null
    var bestScore = 0.0
    for (dark in listOf(true, false)) for (threshold in listOf(110, 170, 215, 245)) {
        fun ink(x: Int, y: Int) = if (dark) image[x, y] < threshold else image[x, y] > 255 - threshold
        val groups = mutableListOf<MutableList<Line>>()
        for (y in 0 until image.height) {
            if (y % 64 == 0) yield()
            var start = -1
            var last = -1
            fun addRun() {
                if (last - start < 90) return
                val line = Line(start, last, y)
                val tolerance = max(3, (last - start) / 100)
                val group = groups.firstOrNull {
                    abs(it[0].left - start) <= tolerance && abs(it[0].right - last) <= tolerance
                }
                if (group == null) groups.add(mutableListOf(line)) else group.add(line)
            }
            for (x in 0 until image.width) {
                if (ink(x, y)) { if (start == -1) start = x; last = x }
                else if (start != -1 && x - last > 2) { addRun(); start = -1 }
            }
            if (start != -1) addRun()
        }
        for (group in groups) {
            yield()
            // Merge the pixels of thick lines into a single centre.
            val centers = mutableListOf<Int>()
            var first = group.first().y
            var last = first
            for (line in group.drop(1)) {
                if (line.y - last > 2) { centers += (first + last) / 2; first = line.y }
                last = line.y
            }
            centers += (first + last) / 2
            if (centers.size < 10) continue
            for (top in centers) for (bottom in centers) {
                val height = bottom - top
                val width = group[0].right - group[0].left
                if (height < 90 || height.toDouble() / width !in 0.8..1.2) continue
                val step = height / 9.0
                if ((0..9).any { n -> centers.none { abs(it - (top + n * step)) <= max(2.0, step * .08) } }) continue
                val left = group.map { it.left }.sorted()[group.size / 2]
                val right = group.map { it.right }.sorted()[group.size / 2]
                var hits = 0
                // Check away from horizontal lines, so text/banners cannot masquerade as a grid.
                for (col in 0..9) for (row in 0..8) {
                    val x = (left + col * (right - left) / 9.0).roundToInt()
                    val y = (top + (row + .5) * step).roundToInt()
                    if ((-2..2).any { dx -> (x + dx) in 0 until image.width && ink(x + dx, y) }) hits++
                }
                if (hits < 85) continue
                val score = width.toDouble() * height * hits / 90
                if (score > bestScore) { bestScore = score; best = GridBounds(left, top, right, bottom) }
            }
        }
    }
    return best
}

/** Tight crop and normalize a glyph. Shared by templates and screenshot cells. */
fun digitShape(mask: BooleanArray, width: Int, height: Int): DigitShape? {
    var left = width; var right = -1; var top = height; var bottom = -1
    mask.forEachIndexed { i, value -> if (value) {
        left = min(left, i % width); right = max(right, i % width)
        top = min(top, i / width); bottom = max(bottom, i / width)
    } }
    if (right < left) return null
    val w = right - left + 1; val h = bottom - top + 1
    val normalized = BooleanArray(20 * 28) { i ->
        val x = left + ((i % 20 + .5) * w / 20).toInt().coerceAtMost(w - 1)
        val y = top + ((i / 20 + .5) * h / 28).toInt().coerceAtMost(h - 1)
        mask[y * width + x]
    }
    return DigitShape(normalized, w.toDouble() / h)
}

private fun shapeDistance(a: DigitShape, b: DigitShape): Double {
    // Tolerate one normalized pixel of antialiasing / stroke-width variation.
    fun unmatched(source: BooleanArray, target: BooleanArray): Double {
        var misses = 0; var count = 0
        source.forEachIndexed { i, ink -> if (ink) {
            count++
            if (!target[i]) misses++
        } }
        return misses.toDouble() / max(1, count)
    }
    val topology = if (a.holes.size != b.holes.size) abs(a.holes.size - b.holes.size) * .12
        else a.holes.zip(b.holes).sumOf { (x, y) -> abs(x - y) } * .15
    val union = a.pixels.indices.count { a.pixels[it] || b.pixels[it] }.coerceAtLeast(1)
    val difference = a.pixels.indices.count { a.pixels[it] != b.pixels[it] }.toDouble() / union
    return (unmatched(a.pixels, b.expanded) + unmatched(b.pixels, a.expanded)) / 2 +
        abs(a.aspect - b.aspect) * .15 + difference * .15 + topology
}

suspend fun recognizeGrid(image: GrayImage, bounds: GridBounds, templates: List<DigitTemplate>): RecognizedGrid {
    require(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= image.width && bounds.bottom <= image.height)
    require(bounds.width >= 90 && bounds.height >= 90 && templates.isNotEmpty())
    val grid = CharArray(81) { '.' }
    val uncertain = mutableSetOf<Int>()
    for (index in 0..80) {
        yield()
        val cellWidth = bounds.width / 9.0; val cellHeight = bounds.height / 9.0
        val left = (bounds.left + (index % 9 + .10) * cellWidth).roundToInt()
        val top = (bounds.top + (index / 9 + .10) * cellHeight).roundToInt()
        val width = (cellWidth * .8).toInt(); val height = (cellHeight * .8).toInt()
        val pixels = IntArray(width * height) { image[left + it % width, top + it / width] }
        val sorted = pixels.sorted()
        // The background occupies most of a classic cell, even for bold digits.
        val background = sorted[sorted.size / 2]
        val dark = background >= 128
        val foreground = if (dark) sorted[sorted.size / 30] else sorted[sorted.size * 29 / 30]
        if (abs(background - foreground) < 35) continue
        val threshold = (background + foreground) / 2
        val mask = BooleanArray(pixels.size) { if (dark) pixels[it] < threshold else pixels[it] > threshold }
        // Connected components discard stray grid edges, compression specks and pencil marks.
        val seen = BooleanArray(mask.size)
        val components = mutableListOf<List<Int>>()
        for (i in mask.indices) if (mask[i] && !seen[i]) {
            val queue = mutableListOf(i); seen[i] = true
            var head = 0
            while (head < queue.size) {
                val p = queue[head++]; val x = p % width; val y = p / width
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val n = ny * width + nx
                    if (mask[n] && !seen[n]) { seen[n] = true; queue += n }
                }
            }
            val h = queue.maxOf { it / width } - queue.minOf { it / width } + 1
            val w = queue.maxOf { it % width } - queue.minOf { it % width } + 1
            if (h >= cellHeight * .28 && h < cellHeight * .85 && w < cellWidth * .8 && queue.size >= 5) components += queue
        }
        if (components.isEmpty()) {
            if (mask.count { it } > pixels.size * .015) uncertain += index
            continue
        }
        val component = components.maxBy { it.size }
        val glyph = BooleanArray(mask.size)
        component.forEach { glyph[it] = true }
        val shape = digitShape(glyph, width, height) ?: continue
        val scores = templates.groupBy { it.digit }.map { (digit, examples) ->
            digit to examples.minOf { shapeDistance(shape, it.shape) }
        }.sortedBy { it.second }
        val best = scores.first()
        if (best.second > .20) { uncertain += index; continue }
        grid[index] = best.first.digitToChar()
        if (components.size > 1 || best.second > .075 || scores[1].second - best.second < .025) uncertain += index
    }
    return RecognizedGrid(grid.concatToString(), uncertain)
}
