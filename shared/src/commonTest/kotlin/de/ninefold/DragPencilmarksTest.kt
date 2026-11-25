package de.ninefold

import kotlin.test.Test
import kotlin.test.assertEquals

class DragPencilmarksTest {
    private val empty = SudokuFixtures.empty
    private val a = CellPosition(0, 0)
    private val b = CellPosition(0, 1)
    private val c = CellPosition(0, 2)
    private val d = CellPosition(0, 3)
    private fun notes(vararg digits: Int) = CellState(centreNotes = digits.toSet())
    private fun board(vararg entries: Pair<CellPosition, CellState>) =
        empty.startingCells().toMutableList().apply { entries.forEach { (p, state) -> this[p.index] = state } }

    private fun drag(cells: List<CellState>, vararg path: CellPosition, puzzle: Puzzle = empty) =
        clearDraggedPencilmarks(puzzle, cells, path.toList(), path.toSet())

    @Test fun placedAndNewlySolvedDigitsClearOnlyLaterCellsInTheGroup() {
        val cells = board(a to CellState(value = 2), b to notes(2, 8), c to notes(3, 8), d to notes(2, 8))
        val result = drag(cells, a, b, c)
        assertEquals(listOf(2, 8, 3), result.take(3).map { it.value })
        assertEquals(cells[d.index], result[d.index])
        assertEquals(cells, drag(cells, c, b, a))
    }

    @Test fun aDigitStopsCarryingWhenTheDragLeavesItsGroup() {
        val outside = CellPosition(1, 3)
        val cells = board(a to CellState(value = 2), d to notes(2, 8))
        assertEquals(cells, drag(cells, a, outside, d))
    }

    @Test fun pairMustBeVisitedBeforeItsTarget() {
        val cells = board(a to notes(2, 8), b to notes(2, 8), c to notes(2, 3, 8))
        assertEquals(3, drag(cells, a, b, c)[c.index].value)
        assertEquals(cells, drag(cells, a, c, b, c))
        assertEquals(cells, clearDraggedPencilmarks(empty, cells, listOf(a, b, c), setOf(b, c)))
    }

    @Test fun xFillsTheComplementEvenWithoutPencilmarks() {
        val puzzle = empty.copy(overlays = listOf(PuzzleOverlay(GridPoint(.5, 1.0), text = "X")))
        val cells = board(b to CellState(value = 7))
        assertEquals(3, drag(cells, b, a, puzzle = puzzle)[a.index].value)
        assertEquals(cells, drag(cells, b, c, puzzle = puzzle))
        assertEquals(cells, drag(cells, b, a, puzzle = puzzle.copy(fogLights = setOf(b), fogLinks = emptyList())))
    }

    @Test fun aFastReverseThermometerDragUsesPointerOrder() {
        val puzzle = empty.copy(lines = listOf(PuzzleLine(
            listOf(GridPoint(.5, .5), GridPoint(.5, 1.5), GridPoint(.5, 2.5)), rule = LineRule.Thermometer,
        )))
        val cells = board(a to notes(1, 2, 3), b to notes(1, 2, 3), c to notes(1, 2, 3))
        val reverse = boardCellsAlongSegment(2.5f / 9, .5f / 9, .5f / 9, .5f / 9)
        assertEquals(listOf(c, b, a), reverse.toList())
        val down = clearDraggedPencilmarks(puzzle, cells, reverse.toList(), reverse)
        assertEquals(listOf(setOf(1), setOf(1, 2), setOf(1, 2, 3)), down.take(3).map { it.centreNotes })
        val up = drag(cells, a, b, c, puzzle = puzzle)
        assertEquals(listOf(setOf(1, 2, 3), setOf(2, 3), setOf(3)), up.take(3).map { it.centreNotes })
        assertEquals(listOf(1, 2, 3), drag(up, c, b, a, puzzle = puzzle).take(3).map { it.value })
    }

    @Test fun slowThermometerCanStayEqualThenRiseAgain() {
        val middle = CellPosition(1, 1)
        val tip = CellPosition(2, 2)
        val puzzle = empty.copy(regions = emptyList(), lines = listOf(PuzzleLine(
            listOf(GridPoint(.5, .5), GridPoint(1.5, 1.5), GridPoint(1.5, 2.5), GridPoint(2.5, 2.5)),
            rule = LineRule.SlowThermometer,
        )))
        val bend = CellPosition(1, 2)
        val cells = board(a to CellState(value = 1), middle to notes(1, 2, 3),
            bend to notes(1, 2, 3), tip to notes(1, 2, 3))
        val result = drag(cells, a, middle, bend, tip, puzzle = puzzle)
        assertEquals(setOf(1, 2, 3), result[middle.index].centreNotes)
        assertEquals(setOf(2, 3), result[bend.index].centreNotes)
        assertEquals(3, result[tip.index].value)
    }

    @Test fun reverseDragUsesTheImportedSlowThermometerPath() {
        val payload = SudokuFixtures.sudokuPadPayload(properties = """
            "cages":[{"value":"rules: Normal sudoku rules apply. Slow thermometers: digits along each thermometer shape must either increase or stay the same at each step, from the round bulb to the tip."}],
            "lines":[{"wayPoints":[[3.5,3.5],[1.5,1.5]],"color":"#AAAAAA","thickness":15}],
            "underlays":[{"center":[1.5,1.5],"width":0.7,"height":0.7,"rounded":true,"backgroundColor":"#AAAAAA"}]
        """.trimIndent())
        val puzzle = parseSudokuPadPayload(payload)
        val line = puzzle.lines.single()
        assertEquals(LineRule.SlowThermometer, line.rule)
        assertEquals(listOf(GridPoint(1.5, 1.5), GridPoint(2.5, 2.5), GridPoint(3.5, 3.5)), line.points)
        assertEquals(false, line.drawThermometerBulb)
        val tip = CellPosition(3, 3)
        val middle = CellPosition(2, 2)
        val bulb = CellPosition(1, 1)
        val cells = board(tip to CellState(value = 8, given = true), middle to notes(4, 8, 9), bulb to notes(5, 8, 9))
        val result = drag(cells, tip, middle, bulb, puzzle = puzzle.copy(rules = emptyList()))
        assertEquals(setOf(4, 8), result[middle.index].centreNotes)
        assertEquals(5, result[bulb.index].value)
        val artworkOnly = parseSudokuPadPayload(payload.replace("rules: Normal sudoku rules apply. Slow", "rules: Slow variant. Slow"))
        assertEquals(LineRule.Unspecified, artworkOnly.lines.single().rule)
    }

    @Test fun whisperUsesCentreCandidatesAndKeepsContradictoryCentresIntact() {
        val puzzle = empty.copy(lines = listOf(PuzzleLine(
            listOf(GridPoint(.5, .5), GridPoint(.5, 1.5)), rule = LineRule.GermanWhisper,
        )))
        val cells = board(a to notes(3, 4), b to notes(6, 7, 8).copy(cornerNotes = setOf(7, 8)))
        val result = drag(cells, a, b, puzzle = puzzle)
        assertEquals(CellState(value = 8, centreNotes = setOf(8), cornerNotes = setOf(8)), result[b.index])
        val contradictory = board(a to notes(3, 4), b to notes(6, 7))
        assertEquals(contradictory, drag(contradictory, a, b, puzzle = puzzle))
    }
}
