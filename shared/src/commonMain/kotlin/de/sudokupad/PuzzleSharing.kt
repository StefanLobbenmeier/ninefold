package de.sudokupad

import kotlin.io.encoding.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val DEFAULT_SHARE_BASE_URL = "https://stefanlobbenmeier.github.io/sudokupad/"
private const val SHARE_PREFIX = "puzzle=v1."
private const val MAX_SHARE_LENGTH = 1_000_000
private val shareJson = Json { ignoreUnknownKeys = true }

/** The complete puzzle travels in the fragment, without a server or the sender's progress. */
fun puzzleShareLink(puzzle: Puzzle, baseUrl: String = DEFAULT_SHARE_BASE_URL): String {
    val payload = Base64.UrlSafe.encode(shareJson.encodeToString(puzzle).encodeToByteArray())
    require(payload.length <= MAX_SHARE_LENGTH) { "This puzzle is too large to share as a link." }
    return baseUrl.substringBefore('#').substringBefore('?') + "#" + SHARE_PREFIX + payload
}

/** Returns null for ordinary app URLs; malformed puzzle links report a recoverable error. */
fun puzzleFromShareLink(link: String): Puzzle? {
    val fragment = link.substringAfter('#', "")
    if (!fragment.startsWith("puzzle=")) return null
    require(fragment.startsWith(SHARE_PREFIX)) { "This puzzle link uses an unsupported version." }
    val payload = fragment.removePrefix(SHARE_PREFIX)
    require(payload.length in 1..MAX_SHARE_LENGTH) { "This puzzle link is empty or too large." }
    val puzzle = try {
        shareJson.decodeFromString<Puzzle>(Base64.UrlSafe.decode(payload).decodeToString(throwOnInvalidSequence = true))
    } catch (error: Exception) {
        throw IllegalArgumentException("This puzzle link is damaged or incomplete.", error)
    }
    require(puzzle.givens.length == 81 && puzzle.givens.all { it == '.' || it in '0'..'9' }) { "This puzzle link has an invalid grid." }
    require(puzzle.solution == null || (puzzle.solution.length == 81 && puzzle.solution.all { it in '1'..'9' })) { "This puzzle link has an invalid solution." }
    fun CellPosition.valid() = row in 0..8 && column in 0..8
    fun GridPoint.valid() = row.isFinite() && column.isFinite()
    require(puzzle.cages.all { cage -> cage.cells.all { it.valid() } } &&
        puzzle.fogLights.orEmpty().all { it.valid() } &&
        puzzle.fogLinks.orEmpty().all { link -> (link.triggers + link.cells).all { it.valid() } } &&
        puzzle.lines.all { line -> line.points.all { it.valid() } && line.thickness.isFinite() } &&
        puzzle.arrows.all { arrow -> arrow.points.all { it.valid() } && arrow.pathCells.all { it.valid() } &&
            (arrow.circle?.valid() != false) && arrow.thickness.isFinite() && arrow.headLength.isFinite() } &&
        (puzzle.overlays + puzzle.underlays).all { it.center.valid() && it.width.isFinite() && it.height.isFinite() && it.thickness.isFinite() } &&
        puzzle.quadruples.all { clue -> clue.cells.size == 4 && clue.cells.distinct().size == 4 &&
            clue.cells.all { it.valid() } && clue.digits.size in 1..4 && clue.digits.all { it in 1..9 } } &&
        puzzle.outsideClues.all { clue -> clue.position.valid() && clue.cells.all { it.valid() } }
    ) { "This puzzle link has invalid clue positions." }
    return puzzle
}

/** Shared links are decoded locally; only SudokuPad links need a download. */
internal suspend fun importPuzzleLink(
    input: String,
    fetchSudokuPad: suspend (String) -> Puzzle = ::importSudokuPadPuzzle,
): Puzzle {
    val link = input.trim()
    puzzleFromShareLink(link)?.let { return it }
    require(sudokuPadPuzzleId(link) != null) { "Paste a Ninefold share link or a sudokupad.app puzzle URL." }
    return fetchSudokuPad(link)
}
