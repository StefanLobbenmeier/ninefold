package de.ninefold

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.*
import kotlin.math.abs
import kotlin.math.pow

private val sudokuPadClient by lazy { HttpClient() }
private val sudokuPadLink = Regex("^https?://(?:www\\.)?sudokupad\\.app/(?:api/puzzle/)?([^?#]+)", RegexOption.IGNORE_CASE)
private val safePuzzleId = Regex("[A-Za-z0-9_./-]+")

fun sudokuPadPuzzleId(input: String): String? {
    val trimmed = input.trim()
    val id = sudokuPadLink.find(trimmed)?.groupValues?.get(1) ?: trimmed.takeIf { safePuzzleId.matches(it) }
    return id?.trim('/')?.takeIf { it.isNotEmpty() && safePuzzleId.matches(it) && ".." !in it }
}

suspend fun importSudokuPadPuzzle(input: String): Puzzle = importSudokuPadPuzzle(input, sudokuPadClient)

internal suspend fun reimportSudokuPadPuzzle(input: String): Puzzle =
    importSudokuPadPuzzle(input, sudokuPadClient, forceRefresh = true)

internal suspend fun importSudokuPadPuzzle(input: String, client: HttpClient, forceRefresh: Boolean = false): Puzzle {
    val id = sudokuPadPuzzleId(input) ?: throw IllegalArgumentException("Enter a sudokupad.app puzzle link.")
    // SudokuPad's own loader uses this CORS-enabled endpoint. The main site's API
    // only allows https://localhost, which blocks our JS and Wasm apps.
    // A unique URL also bypasses older installed service workers and HTTP caches.
    val refresh = if (forceRefresh) "?reimport=${kotlin.random.Random.nextLong()}" else ""
    val path = id.split('/').joinToString("/") { it.encodeURLParameter() }
    val response: HttpResponse = client.get("https://sudokupad.svencodes.com/ctclegacy/$path$refresh")
    if (!response.status.isSuccess()) throw IllegalArgumentException("SudokuPad returned ${response.status.value} for this puzzle.")
    return parseSudokuPadPayload(response.body(), "https://sudokupad.app/$id")
}

internal fun parseSudokuPadPayload(payload: String, sourceUrl: String? = null): Puzzle {
    val packageText = payload.trim()
    require(packageText.length <= 5_000_000) { "This puzzle is too large to import." }
    val (format, encoded) = when {
        packageText.startsWith("fpuzzles") -> "fpuz" to packageText.removePrefix("fpuzzles")
        packageText.startsWith("fpuz") -> "fpuz" to packageText.removePrefix("fpuz")
        packageText.startsWith("scl") -> "scl" to packageText.removePrefix("scl")
        packageText.startsWith("ctc") -> "scl" to packageText.removePrefix("ctc")
        else -> "scl" to packageText
    }
    val decoded = if (encoded.startsWith("{") || encoded.startsWith("[")) encoded
        else lzStringDecompressFromBase64(percentDecode(encoded))
    val root = parsePackedJson(decoded)
    return puzzleFromJson(root, format == "fpuz", sourceUrl).let { puzzle ->
        puzzle.copy(importOmissions = sudokuPadOmissions(root, format == "fpuz", puzzle))
    }
}

private fun parsePackedJson(input: String): JsonObject {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }
    val direct = runCatching { json.parseToJsonElement(input) }.getOrNull()
    val parsed = direct ?: json.parseToJsonElement(unzipPuzzleJson(input))
    return expandPackedProperties(parsed).jsonObject
}

private val packedProperties = mapOf(
    "c" to "color", "ca" to "cages", "ct" to "center", "c1" to "borderColor",
    "c2" to "backgroundColor", "ce" to "cells", "cs" to "cellSize", "a" to "arrows",
    "o" to "overlays", "u" to "underlays", "w" to "width", "h" to "height", "v" to "value",
    "vd" to "videos", "l" to "lines", "r" to "rounded", "re" to "regions", "fs" to "fontSize",
    "th" to "thickness", "hl" to "headLength", "wp" to "wayPoints", "t" to "title",
    "te" to "text", "dur" to "duration", "d2" to "d",
)

private fun expandPackedProperties(element: JsonElement): JsonElement = when (element) {
    is JsonObject -> JsonObject(element.map { (key, value) ->
        (packedProperties[key] ?: key) to expandPackedProperties(value)
    }.toMap())
    is JsonArray -> JsonArray(element.map(::expandPackedProperties))
    else -> element
}

/** Reverses SudokuPad's PuzzleZipper representation before normal JSON parsing. */
private fun unzipPuzzleJson(packed: String): String {
    val quoted = singleQuotedStringsToJson(packed)
    val strings = mutableListOf<String>()
    val skeleton = buildString {
        var index = 0
        while (index < quoted.length) {
            if (quoted[index] != '"') { append(quoted[index++]); continue }
            val start = index++
            while (index < quoted.length) {
                if (quoted[index] == '\\') index += 2
                else if (quoted[index++] == '"') break
                else Unit
            }
            strings += quoted.substring(start, index)
            append("\"__{S${strings.lastIndex}}__\"")
        }
    }
    var json = skeleton
        .replace(Regex("([,\\{\\[])([a-zA-Z0-9]+):"), "$1\"$2\":")
        .replace(Regex("([,\\{\\[])(?=[,}\\]])"), "$1_")
        .replace("{_}", "{}")
        .replace(Regex("([,\\{\\[])_"), "$1{}")
        .replace(Regex(":f([,}\\]])"), ":false$1")
        .replace(Regex(":t([,}\\]])"), ":true$1")
        .replace(Regex(":#0([,}\\]])"), ":\"#000000\"$1")
        .replace(Regex(":#F([,}\\]])"), ":\"#FFFFFF\"$1")
        .replace(Regex(":([0-9a-fA-F]{6})([,}\\]])"), ":\"#$1\"$2")
    strings.forEachIndexed { index, value -> json = json.replace("\"__{S$index}__\"", value) }
    return json
}

private fun singleQuotedStringsToJson(input: String): String {
    if ('\'' !in input) return input
    return buildString {
        var index = 0
        var inString = false
        while (index < input.length) {
            val char = input[index++]
            if (!inString) {
                if (char == '\'') { append('"'); inString = true } else append(char)
            } else when {
                char == '\'' -> { append('"'); inString = false }
                char == '\\' && index < input.length && input[index] == '\'' -> { append('\''); index++ }
                char == '"' -> append("\\\"")
                else -> append(char)
            }
        }
        require(!inString) { "The SudokuPad data contains an unfinished string." }
    }
}

private fun puzzleFromJson(root: JsonObject, fpuzzles: Boolean, sourceUrl: String?): Puzzle {
    // Older exports store metadata as non-rendering cage values, e.g. "title: ...".
    // Explicit metadata takes precedence when both representations are present.
    val legacyMetadata = root.array("cages").orEmpty().mapNotNull { element ->
        val value = element.jsonObject.string("value") ?: return@mapNotNull null
        val key = value.substringBefore(':').trim().lowercase()
        if (':' !in value || key !in setOf("title", "author", "rules", "solution")) return@mapNotNull null
        key to JsonPrimitive(value.substringAfter(':').trim())
    }.toMap()
    val metadata = JsonObject(legacyMetadata + (root.obj("metadata") ?: root))
    val sourceCells = (root.array(if (fpuzzles) "grid" else "cells") ?: root.array("cells"))
        ?: throw IllegalArgumentException("The puzzle has no grid.")
    require(sourceCells.size == 9 && sourceCells.all { it.jsonArray.size == 9 }) {
        "Only 9×9 SudokuPad puzzles are currently supported."
    }
    val initialValues = mutableMapOf<Int, Int>()
    val givens = sourceCells.flatMap { row -> row.jsonArray.map { cell ->
        cell.jsonObject.int("value")?.takeIf { it in 1..9 }?.digitToChar() ?: '.'
    } }.joinToString("")
    val fixedGivens = givens.mapIndexed { index, digit ->
        val cell = sourceCells[index / 9].jsonArray[index % 9].jsonObject
        if (fpuzzles && cell.boolean("given") == false && digit in '1'..'9') {
            initialValues[index] = digit.digitToInt()
            '.'
        } else digit
    }.joinToString("")
    val cellShading = sourceCells.flatMapIndexed { r, row -> row.jsonArray.mapIndexedNotNull { c, element ->
        element.jsonObject.string("color")?.takeIf(::isImportedCellColor)?.let { color ->
            PuzzleOverlay(GridPoint(r + .5, c + .5), 1.0, 1.0, color, rounded = false)
        }
    } }
    val solution = metadata.string("solution") ?: root.array("solution")?.mapNotNull(JsonElement::asInt)?.joinToString("")
    val rulesText = metadata.string("rules") ?: metadata.string("ruleset") ?: "Normal sudoku rules apply."
    val rules = rulesText.trim().takeIf(String::isNotEmpty)?.lines()?.map(String::trim)
        ?: listOf("Normal sudoku rules apply.")
    val successMessage = importedSuccessMessage(root, metadata)

    val rawCages = root.array(if (fpuzzles) "killercage" else "cages") ?: root.array("cages") ?: JsonArray(emptyList())
    val fog = mutableSetOf<CellPosition>()
    root.array("foglight")?.forEach { parseCell(it)?.let(fog::add) }
    val fogLinks = root.array("triggereffect")?.mapNotNull { element ->
        val entry = element.jsonObject
        val effect = entry.obj("effect") ?: return@mapNotNull null
        if (effect.string("type") != "foglight") return@mapNotNull null
        val trigger = entry.obj("trigger") ?: return@mapNotNull null
        FogLink(parseFogCells(trigger["cell"]), parseFogCells(effect["cells"]))
    }
    // SCL cages are graphical unless uniqueness is explicit or stated in the rules.
    // f-puzzles killercage entries carry killer semantics by definition.
    val defaultUnique = fpuzzles || Regex("killer", RegexOption.IGNORE_CASE).containsMatchIn(rulesText) ||
        Regex("digits (?:cannot|can not|must not) repeat in (?:a )?cages?", RegexOption.IGNORE_CASE).containsMatchIn(rulesText)
    val sharedCageTotal = rules.mapNotNull { rule ->
        Regex("^(?:all )?cages sum to (\\d+|fifteen)[.!]?$", RegexOption.IGNORE_CASE)
            .matchEntire(rule)?.groupValues?.get(1)?.let { if (it.equals("fifteen", true)) 15 else it.toIntOrNull() }
    }.distinct().singleOrNull()?.takeIf { it > 0 }
    val cages = rawCages.mapNotNull { element ->
        val cage = element.jsonObject
        val cells = cage.array("cells").orEmpty().mapNotNull(::parseCell).toSet()
        val value = cage.int("value")
        if (cage.string("value")?.startsWith("foglight", ignoreCase = true) == true) {
            fog += cells
            fog += parseFogCells(JsonPrimitive(cage.string("value")!!.substringAfter(':', "")))
            null
        } else if (cells.isNotEmpty() && (value != null || cage.string("value").isNullOrBlank())) {
            KillerCage(value, cells, unique = cage.boolean("unique") ?: defaultUnique,
                ruleTotal = sharedCageTotal.takeIf { value == null })
        } else null
    }
    val lines = parseLines(root, fpuzzles)
    val rawOverlays = root.array("overlays").orEmpty().mapNotNull(::parseOverlay) + if (fpuzzles) parseFpuzzlesOverlays(root) else emptyList()
    val (overlays, quadruples) = importQuadruples(rawOverlays, rulesText)
    val underlays = cellShading + root.array("underlays").orEmpty().mapNotNull(::parseOverlay) + if (fpuzzles) parseFpuzzlesCellShading(root) else emptyList()
    val arrows = parseArrows(root, fpuzzles, overlays)
    val outsideClues = parseOutsideClues(rulesText, overlays, arrows)
    val hasFog = fogLinks != null || root.containsKey("foglight") || rawCages.any {
        it.jsonObject.string("value")?.startsWith("foglight", ignoreCase = true) == true
    }
    return Puzzle(
        title = metadata.string("title") ?: "Imported SudokuPad puzzle",
        author = metadata.string("author") ?: "Unknown setter",
        givens = fixedGivens,
        initialValues = initialValues,
        solution = solution?.filter(Char::isDigit)?.takeIf { it.length == 81 },
        cages = cages,
        rules = rules,
        antiKnight = metadata.boolean("antiknight") ?: root.boolean("antiknight") ?: false,
        lines = lines,
        arrows = arrows,
        overlays = overlays,
        underlays = underlays,
        outsideClues = outsideClues,
        quadruples = quadruples,
        fogLights = fog.takeIf { hasFog },
        fogLinks = fogLinks,
        sourceUrl = sourceUrl,
        videoUrl = (metadata.string("video") ?: metadata.string("videoUrl")
            ?: (metadata.array("videos") ?: root.array("videos"))?.firstOrNull()?.let { video ->
                (video as? JsonPrimitive)?.contentOrNull ?: (video as? JsonObject)?.string("url")
            })?.let { video ->
                when {
                    video.startsWith("https://") -> video
                    Regex("[A-Za-z0-9_-]{11}").matches(video) -> "https://www.youtube.com/watch?v=$video"
                    else -> null
                }
            },
        successMessage = successMessage,
    )
}

/** Some exporters split a quadruple into a circle and one tiny overlay per digit. */
private fun importQuadruples(overlays: List<PuzzleOverlay>, rules: String): Pair<List<PuzzleOverlay>, List<QuadrupleClue>> {
    if (!rules.contains("quadruple", ignoreCase = true)) return overlays to emptyList()
    val labels = mutableSetOf<PuzzleOverlay>()
    val replacements = mutableMapOf<PuzzleOverlay, PuzzleOverlay>()
    val clues = overlays.mapNotNull { circle ->
        val row = circle.center.row.toInt()
        val column = circle.center.column.toInt()
        if (!circle.rounded || circle.width !in .3..1.0 || circle.height !in .3..1.0 ||
            row !in 1..8 || column !in 1..8 ||
            abs(circle.center.row - row) > .001 || abs(circle.center.column - column) > .001) return@mapNotNull null
        val parts = overlays.filter { it != circle && it.width <= .2 && it.height <= .2 &&
            abs(it.center.row - circle.center.row) < circle.height / 2 &&
            abs(it.center.column - circle.center.column) < circle.width / 2 &&
            it.text?.toIntOrNull() in 1..9
        }
        val digits = if (parts.isNotEmpty()) parts.map { it.text!!.toInt() }
            else circle.text.orEmpty().filter(Char::isDigit).map { it.digitToInt() }
        if (digits.isEmpty() || digits.size > 4 || digits.any { it !in 1..9 }) return@mapNotNull null
        labels += parts
        replacements[circle] = circle.copy(text = digits.chunked(2).joinToString("\n") { it.joinToString(" ") })
        QuadrupleClue(digits, listOf(CellPosition(row - 1, column - 1), CellPosition(row - 1, column),
            CellPosition(row, column - 1), CellPosition(row, column)))
    }
    return overlays.filterNot { it in labels }.map { replacements[it] ?: it } to clues
}

private fun importedSuccessMessage(root: JsonObject, metadata: JsonObject): String? {
    val explicit = listOf(
        metadata.string("successMessage"), root.string("successMessage"),
        metadata.string("msgcorrect"), root.string("msgcorrect"),
    ).firstOrNull { !it.isNullOrBlank() }
    if (explicit != null) return explicit.trim()

    val marker = Regex("^msgcorrect:\\s*([\\s\\S]+)$", RegexOption.IGNORE_CASE)
    return listOf("cages", "killercage", "cage").asSequence()
        .flatMap { root.array(it).orEmpty().asSequence() }
        .mapNotNull { element -> element.jsonObject.string("value") }
        .mapNotNull { value -> marker.matchEntire(value.trim())?.groupValues?.get(1)?.trim() }
        .firstOrNull { it.isNotEmpty() }
}

private fun parseOutsideClues(rules: String, overlays: List<PuzzleOverlay>, arrows: List<PuzzleArrow>): List<OutsideClue> =
    overlays.mapNotNull { overlay ->
        val value = overlay.text?.toIntOrNull() ?: return@mapNotNull null
        val point = overlay.center
        if (point.row in 0.0..9.0 && point.column in 0.0..9.0) return@mapNotNull null
        val nearbyArrow = arrows.firstOrNull { arrow -> arrow.points.any {
            abs(it.row - point.row) < .75 && abs(it.column - point.column) < .75
        } }
        val type = when {
            nearbyArrow != null && rules.contains("diagonal", ignoreCase = true) -> OutsideClueType.DiagonalSum
            rules.contains("skyscraper", ignoreCase = true) -> OutsideClueType.Skyscraper
            else -> OutsideClueType.Unspecified
        }
        val cells = when (type) {
            OutsideClueType.DiagonalSum -> diagonalCells(point, requireNotNull(nearbyArrow))
            OutsideClueType.Skyscraper -> edgeCells(point)
            OutsideClueType.Unspecified -> emptyList()
        }
        OutsideClue(value, point, cells, type)
    }

private fun edgeCells(point: GridPoint): List<CellPosition> = when {
    point.column < 0 -> List(9) { CellPosition(point.row.toInt().coerceIn(0, 8), it) }
    point.column >= 9 -> List(9) { CellPosition(point.row.toInt().coerceIn(0, 8), 8 - it) }
    point.row < 0 -> List(9) { CellPosition(it, point.column.toInt().coerceIn(0, 8)) }
    point.row >= 9 -> List(9) { CellPosition(8 - it, point.column.toInt().coerceIn(0, 8)) }
    else -> emptyList()
}

private fun diagonalCells(clue: GridPoint, arrow: PuzzleArrow): List<CellPosition> {
    val first = arrow.points.first(); val last = arrow.points.last()
    val rowStep = (last.row - first.row).compareTo(0.0)
    val columnStep = (last.column - first.column).compareTo(0.0)
    var row = when { clue.row < 0 -> 0; clue.row >= 9 -> 8; else -> clue.row.toInt() + rowStep }
    var column = when { clue.column < 0 -> 0; clue.column >= 9 -> 8; else -> clue.column.toInt() + columnStep }
    return buildList {
        while (row in 0..8 && column in 0..8) { add(CellPosition(row, column)); row += rowStep; column += columnStep }
    }
}

private fun parseLines(root: JsonObject, fpuzzles: Boolean): List<PuzzleLine> {
    val source = root.array("lines") ?: root.array("line") ?: JsonArray(emptyList())
    val generic = source.flatMap { element ->
        val line = element.jsonObject
        val groups = if (fpuzzles && line.array("lines") != null) line.array("lines").orEmpty().map(JsonElement::jsonArray)
            else listOf(line.array("wayPoints") ?: JsonArray(emptyList()))
        groups.mapNotNull { points ->
            val parsed = points.mapNotNull(::parsePoint)
            parsed.takeIf { it.size > 1 }?.let {
                // f-puzzles stores generic line colors in outlineC.
                val color = (if (fpuzzles) line.string("outlineC") else null)
                    ?: line.string("lineC") ?: line.string("color") ?: "#A0A0A0"
                PuzzleLine(it, color, line.double("thickness") ?: 6.0)
            }
        }
    }
    if (!fpuzzles) return generic
    fun explicit(feature: String, rule: LineRule, color: String, thickness: Double) = root.array(feature).orEmpty().flatMap { element ->
        element.jsonObject.array("lines").orEmpty().mapNotNull { group ->
            val points = group.jsonArray.mapNotNull(::parsePoint)
            points.takeIf { it.size > 1 }?.let { PuzzleLine(it, color, thickness, rule) }
        }
    }
    return generic + explicit("thermometer", LineRule.Thermometer, "#B8B8B8", 12.0) +
        explicit("palindrome", LineRule.Palindrome, "#CFCFCF", 6.0)
}

private fun parseFpuzzlesOverlays(root: JsonObject): List<PuzzleOverlay> = buildList {
    // Decorative circles may sit between two cells or at a four-cell intersection.
    root.array("circle").orEmpty().forEach { element ->
        val circle = element.jsonObject
        val cells = circle.array("cells").orEmpty().mapNotNull(::parseCell)
        if (cells.isNotEmpty()) add(PuzzleOverlay(
            center = GridPoint(cells.map { it.row + .5 }.average(), cells.map { it.column + .5 }.average()),
            width = circle.double("width") ?: .5,
            height = circle.double("height") ?: .5,
            backgroundColor = circle.string("baseC") ?: "#FFFFFF",
            borderColor = circle.string("outlineC") ?: "#000000",
            textColor = circle.string("fontC") ?: "#000000",
            text = circle.string("value"),
            rounded = true,
        ))
    }
    fun pairMarks(feature: String, background: String, border: String?, defaultText: String? = null) {
        root.array(feature).orEmpty().forEach { element ->
            val mark = element.jsonObject
            val cells = mark.array("cells").orEmpty().mapNotNull(::parseCell)
            if (cells.size == 2) {
                val center = GridPoint((cells[0].row + cells[1].row + 1) / 2.0, (cells[0].column + cells[1].column + 1) / 2.0)
                add(PuzzleOverlay(center, .33, .33, background, border, if (background == "#000000") "#FFFFFF" else "#000000", mark.string("value") ?: defaultText, true))
            }
        }
    }
    pairMarks("difference", "#FFFFFF", "#000000")
    pairMarks("ratio", "#000000", null)
    pairMarks("xv", "#FFFFFF", "#000000")
    root.array("arrow").orEmpty().forEach { element ->
        element.jsonObject.array("cells").orEmpty().mapNotNull(::parseCell).forEach { cell ->
            add(PuzzleOverlay(GridPoint(cell.row + .5, cell.column + .5), .8, .8, "#FFFFFF", "#999999", rounded = true))
        }
    }
}

private fun parseFpuzzlesCellShading(root: JsonObject): List<PuzzleOverlay> = buildList {
    root.array("even").orEmpty().forEach { element -> parseCell(element.jsonObject["cell"] ?: element)?.let { cell ->
        add(PuzzleOverlay(GridPoint(cell.row + .5, cell.column + .5), .55, .55, "#CFCFCF", rounded = false))
    } }
    root.array("odd").orEmpty().forEach { element -> parseCell(element.jsonObject["cell"] ?: element)?.let { cell ->
        add(PuzzleOverlay(GridPoint(cell.row + .5, cell.column + .5), .55, .55, "#FFFFFF", "#777777", rounded = true))
    } }
}

private fun parseArrows(root: JsonObject, fpuzzles: Boolean, overlays: List<PuzzleOverlay>): List<PuzzleArrow> {
    val source = root.array("arrows") ?: root.array("arrow") ?: JsonArray(emptyList())
    return source.flatMap { element ->
        val arrow = element.jsonObject
        if (fpuzzles && arrow.array("lines") != null) {
            val circleCells = arrow.array("cells").orEmpty().mapNotNull(::parseCell)
            arrow.array("lines").orEmpty().mapNotNull { line ->
                val points = line.jsonArray.mapNotNull(::parsePoint)
                points.takeIf { it.size > 1 }?.let {
                    PuzzleArrow(it, circle = circleCells.firstOrNull(), pathCells = points.mapNotNull(GridPoint::cellPositionOrNull).distinct())
                }
            }
        } else {
            val points = arrow.array("wayPoints").orEmpty().mapNotNull(::parsePoint)
            if (points.size < 2) emptyList() else {
                val start = points.first()
                val bulb = overlays.filter { it.rounded && it.width in .5..1.1 && it.height in .5..1.1 }
                    .minByOrNull { (it.center.row - start.row).pow(2) + (it.center.column - start.column).pow(2) }
                    ?.takeIf { abs(it.center.row - start.row) <= .7 && abs(it.center.column - start.column) <= .7 }
                listOf(PuzzleArrow(
                    points, arrow.string("color") ?: "#777777", arrow.double("thickness") ?: 3.0,
                    arrow.double("headLength") ?: .3, bulb?.center?.cellPositionOrNull(),
                    points.mapNotNull(GridPoint::cellPositionOrNull).distinct(),
                ))
            }
        }
    }
}

private fun parseOverlay(element: JsonElement): PuzzleOverlay? {
    val value = element.jsonObject
    val center = value["center"]?.let(::parsePoint) ?: return null
    return PuzzleOverlay(
        center, value.double("width") ?: .3, value.double("height") ?: .3,
        value.string("backgroundColor"), value.string("borderColor") ?: value.string("stroke"),
        value.string("color") ?: value.string("textColor"), value["text"]?.asString(),
        value.boolean("rounded") ?: (value.int("rounded") == 1), value.double("thickness") ?: 1.0,
    )
}

private fun parsePoint(element: JsonElement): GridPoint? {
    if (element is JsonPrimitive && element.isString) return parseCell(element)?.let { GridPoint(it.row + .5, it.column + .5) }
    val pair = element as? JsonArray ?: return null
    return GridPoint(pair.getOrNull(0)?.asDouble() ?: return null, pair.getOrNull(1)?.asDouble() ?: return null)
}

/** SudokuPad cell lists concatenate rNcN coordinates and rectangular ranges. */
private fun parseFogCells(element: JsonElement?): Set<CellPosition> {
    if (element is JsonArray) return element.mapNotNull(::parseCell).filter { it.row in 0..8 && it.column in 0..8 }.toSet()
    val text = element?.asString()?.replace(Regex("[, ]+"), "") ?: return emptySet()
    return buildSet {
        Regex("r(\\d+)c(\\d+)(?:-r(\\d+)c(\\d+))?", RegexOption.IGNORE_CASE).findAll(text).forEach { match ->
            val (r1, c1, r2, c2) = match.destructured
            val rows = r1.toInt().coerceAtLeast(1)..(r2.toIntOrNull() ?: r1.toInt()).coerceAtMost(9)
            val columns = c1.toInt().coerceAtLeast(1)..(c2.toIntOrNull() ?: c1.toInt()).coerceAtMost(9)
            for (row in rows) for (column in columns) {
                if (row in 1..9 && column in 1..9) add(CellPosition(row - 1, column - 1))
            }
        }
    }
}

private fun parseCell(element: JsonElement): CellPosition? {
    if (element is JsonArray) return CellPosition(element.getOrNull(0)?.asInt() ?: return null, element.getOrNull(1)?.asInt() ?: return null)
    val match = Regex("R(\\d+)C(\\d+)", RegexOption.IGNORE_CASE).matchEntire(element.asString() ?: return null) ?: return null
    return CellPosition(match.groupValues[1].toInt() - 1, match.groupValues[2].toInt() - 1)
}

private fun JsonObject.obj(name: String): JsonObject? = get(name) as? JsonObject
private fun JsonObject.array(name: String): JsonArray? = get(name) as? JsonArray
private fun JsonObject.string(name: String): String? = get(name)?.asString()
private fun JsonObject.int(name: String): Int? = get(name)?.asInt()
private fun JsonObject.double(name: String): Double? = get(name)?.asDouble()
private fun JsonObject.boolean(name: String): Boolean? = (get(name) as? JsonPrimitive)?.booleanOrNull
private fun JsonElement.asString(): String? = (this as? JsonPrimitive)?.contentOrNull
private fun JsonElement.asInt(): Int? = (this as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
private fun JsonElement.asDouble(): Double? = (this as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

private fun percentDecode(value: String): String {
    val bytes = mutableListOf<Byte>()
    var index = 0
    while (index < value.length) {
        if (value[index] == '%' && index + 2 < value.length) {
            val byte = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (byte != null) { bytes += byte.toByte(); index += 3; continue }
        }
        // Encoded puzzle packages are ASCII; retain unexpected unicode as UTF-8 through its chars.
        bytes += value[index].code.toByte(); index++
    }
    return bytes.toByteArray().decodeToString()
}

/** LZ-String's `decompressFromBase64`, used by fpuz and scl packages. */
internal fun lzStringDecompressFromBase64(input: String): String {
    if (input.isEmpty()) return ""
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="
    return lzDecompress(input.length, 32) { index -> alphabet.indexOf(input[index]).coerceAtLeast(0) }
}

private fun lzDecompress(length: Int, resetValue: Int, nextValue: (Int) -> Int): String {
    val dictionary = mutableListOf("0", "1", "2")
    var enlargeIn = 4
    var dictSize = 4
    var numBits = 3
    var dataValue = nextValue(0)
    var dataPosition = resetValue
    var dataIndex = 1
    fun readBits(count: Int): Int {
        var bits = 0; var power = 1
        repeat(count) {
            val bit = dataValue and dataPosition
            dataPosition = dataPosition shr 1
            if (dataPosition == 0) { dataPosition = resetValue; dataValue = if (dataIndex < length) nextValue(dataIndex++) else 0 }
            if (bit > 0) bits = bits or power
            power = power shl 1
        }
        return bits
    }
    val first = when (readBits(2)) {
        0 -> readBits(8).toChar().toString()
        1 -> readBits(16).toChar().toString()
        else -> return ""
    }
    dictionary += first
    var w = first
    val result = StringBuilder(first)
    while (dataIndex <= length) {
        var code = readBits(numBits)
        when (code) {
            0 -> { dictionary += readBits(8).toChar().toString(); code = dictSize++; enlargeIn-- }
            1 -> { dictionary += readBits(16).toChar().toString(); code = dictSize++; enlargeIn-- }
            2 -> return result.toString()
        }
        if (enlargeIn == 0) { enlargeIn = 2.0.pow(numBits).toInt(); numBits++ }
        val entry = dictionary.getOrNull(code) ?: if (code == dictSize) w + w.first() else throw IllegalArgumentException("Invalid SudokuPad compression data.")
        result.append(entry)
        dictionary += w + entry.first()
        dictSize++
        enlargeIn--
        w = entry
        if (enlargeIn == 0) { enlargeIn = 2.0.pow(numBits).toInt(); numBits++ }
    }
    throw IllegalArgumentException("Incomplete SudokuPad compression data.")
}

/** Restrict authored shading to unambiguous CSS hex colors; other encodings remain reported. */
internal fun isImportedCellColor(value: String): Boolean =
    Regex("#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})").matches(value)
