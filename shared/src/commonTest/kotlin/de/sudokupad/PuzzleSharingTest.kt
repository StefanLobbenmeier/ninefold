package de.sudokupad

import kotlin.io.encoding.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PuzzleSharingTest {
    @Test
    fun importsPastedSharedLinksLocallyAndKeepsSudokuPadSupport() = runTest {
        val puzzle = SudokuFixtures.classic.copy(sourceUrl = "https://sudokupad.app/example")
        var downloads = 0
        val fetch: suspend (String) -> Puzzle = { downloads++; puzzle }
        assertEquals(puzzle, importPuzzleLink("  " + puzzleShareLink(puzzle) + "\n", fetch))
        assertEquals(0, downloads)
        assertFailsWith<IllegalArgumentException> { importPuzzleLink("https://example.com/#puzzle=v1.???", fetch) }
        assertFailsWith<IllegalArgumentException> { importPuzzleLink("https://example.com/#puzzle=v2.abc", fetch) }
        assertFailsWith<IllegalArgumentException> { importPuzzleLink("https://example.com/", fetch) }
        assertEquals(0, downloads)
        assertEquals(puzzle, importPuzzleLink(" https://sudokupad.app/example ", fetch))
        assertEquals(1, downloads)
    }

    @Test
    fun sharesClassicImportsWithoutASource() {
        val puzzle = SudokuFixtures.classic.copy(title = "Screenshot / manual import", author = "Me", solution = null)
        val link = puzzleShareLink(puzzle)
        assertTrue(link.startsWith(DEFAULT_SHARE_BASE_URL + "#puzzle=v1."))
        assertEquals(puzzle, puzzleFromShareLink(link))
    }

    @Test
    fun preservesCatalogAndVariantPuzzleDataIncludingUnicode() {
        val puzzle = SudokuFixtures.classic.copy(
            title = "霧 & Flüstern 🧩", author = "Zoë", sourceUrl = "https://sudokupad.app/example",
            videoUrl = "https://example.com/video", rules = listOf("差 ≥ 5", "Follow the arrows."),
            cages = listOf(KillerCage(8, setOf(CellPosition(0, 0), CellPosition(0, 1)))),
            lines = listOf(PuzzleLine(listOf(GridPoint(.5, .5), GridPoint(1.5, .5)), rule = LineRule.GermanWhisper)),
            arrows = listOf(PuzzleArrow(listOf(GridPoint(.5, .5), GridPoint(.5, 1.5)), circle = CellPosition(0, 0), pathCells = listOf(CellPosition(0, 1)))),
            overlays = listOf(PuzzleOverlay(GridPoint(1.0, 1.0), text = "×")),
            underlays = listOf(PuzzleOverlay(GridPoint(2.5, 2.5), backgroundColor = "#ff0000")),
            outsideClues = listOf(OutsideClue(3, GridPoint(-.5, .5), List(9) { CellPosition(it, 0) }, OutsideClueType.Skyscraper)),
            fogLights = setOf(CellPosition(0, 0)),
        )
        val link = puzzleShareLink(puzzle, "https://example.com/nested/index.html?old=1#old")
        assertTrue(link.startsWith("https://example.com/nested/index.html#puzzle=v1."))
        assertTrue(link.substringAfter("v1.").all { it.isLetterOrDigit() || it in "-_=" })
        assertEquals(puzzle, puzzleFromShareLink(link))
        assertEquals(puzzle.storageId(), puzzleFromShareLink(link)!!.storageId())
        assertEquals(emptySet(), puzzleFromShareLink(puzzleShareLink(puzzle.copy(fogLights = emptySet())))!!.fogLights)
    }

    @Test
    fun rejectsBrokenUnsupportedAndUnsafePayloads() {
        assertNull(puzzleFromShareLink("https://example.com/"))
        assertNull(puzzleFromShareLink("https://example.com/#other"))
        listOf("#puzzle=v2.abc", "#puzzle=v1.", "#puzzle=v1.???", "#puzzle=v1." + "a".repeat(1_000_001)).forEach {
            assertFailsWith<IllegalArgumentException> { puzzleFromShareLink(it) }
        }
        val invalid = listOf(
            SudokuFixtures.classic.copy(givens = "1"), SudokuFixtures.classic.copy(solution = ".".repeat(81)),
            SudokuFixtures.classic.copy(cages = listOf(KillerCage(3, setOf(CellPosition(9, 0))))),
        )
        invalid.forEach { puzzle ->
            val link = "#puzzle=v1." + Base64.UrlSafe.encode(Json.encodeToString(puzzle).encodeToByteArray())
            assertFailsWith<IllegalArgumentException> { puzzleFromShareLink(link) }
        }
    }
}
