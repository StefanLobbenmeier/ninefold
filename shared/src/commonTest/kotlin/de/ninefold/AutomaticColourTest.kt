package de.ninefold

import kotlin.test.*

class AutomaticColourTest {
    private val rules = mapOf(7 to AutomaticColourRule(true, setOf(1, 2, 3)))

    @Test fun matchesDigitsAndNonemptySubsetsOfCentreMarksOnly() {
        val cells = listOf(
            CellState(value = 2), CellState(centreNotes = setOf(1, 3)),
            CellState(centreNotes = setOf(1, 4)), CellState(),
            CellState(cornerNotes = setOf(1, 3)), CellState(value = 4),
            CellState(value = 2, colours = setOf(9)), CellState(value = 2, given = true),
            CellState(value = 4, centreNotes = setOf(1)),
        )
        assertEquals(listOf(setOf(7), setOf(7), emptySet(), emptySet(), emptySet(), emptySet(), setOf(7, 9), setOf(7), emptySet()), cells.map { it.displayColours(rules) })
        assertEquals(cells.map { it.colours }, cells.map { it.displayColours(mapOf(7 to AutomaticColourRule(false, setOf(1, 2, 3)))) })
    }

    @Test fun overlappingRulesCombineWithManualColoursAndEmptyRulesNeverMatch() {
        val cell = CellState(value = 2, colours = setOf(9))
        assertEquals(setOf(4, 7, 9), cell.displayColours(rules + (4 to AutomaticColourRule(true, setOf(2)))))
        assertEquals(setOf(9), cell.displayColours(mapOf(1 to AutomaticColourRule(true))))
        assertEquals(setOf(9), cell.colours)
    }

    @Test fun editingOrChangingRulesRecalculatesColoursWithoutChangingManualColours() {
        val cell = CellState(centreNotes = setOf(1, 2), colours = setOf(9))
        assertEquals(setOf(7, 9), cell.displayColours(rules))
        assertEquals(setOf(9), cell.copy(centreNotes = setOf(1, 4)).displayColours(rules))
        assertEquals(setOf(9), cell.displayColours(mapOf(7 to AutomaticColourRule(true, setOf(3)))))
        assertEquals(setOf(9), cell.displayColours(emptyMap()))
        assertEquals(setOf(9), cell.colours)
    }

    @Test fun solutionChecksWaitForFinalizationButRuleChecksStayLive() {
        val puzzle = SudokuFixtures.empty.copy(solution = "1".repeat(81))
        val original = puzzle.startingCells()
        val editing = setOf(CellPosition(0, 0))
        val live = original.toMutableList().also {
            it[0] = CellState(value = 2)
            it[1] = CellState(value = 2)
        }
        val finalized = finalizedCells(live, original, editing)
        assertEquals(setOf(0, 1), checkSudokuRules(puzzle, live).cells)
        assertEquals(setOf(1), checkAgainstSolution(puzzle, finalized).excluding(editing).cells)
        assertEquals(setOf(0, 1), checkAgainstSolution(puzzle, finalizedCells(live, finalized, emptySet())).cells)
    }

    @Test fun colourSettingsPersistPerGameWithoutPersistingDerivedColours() {
        val storage = MemoryAppStorage()
        val repository = GameRepository(storage)
        val puzzle = SudokuFixtures.empty
        val other = puzzle.copy(title = "Another game")
        val cells = puzzle.startingCells().toMutableList().also { it[0] = CellState(value = 2, colours = setOf(9)) }
        val configured = rules + (9 to AutomaticColourRule(false, setOf(5, 6)))
        repository.saveGame(SavedGame(puzzle, cells, true, automaticColours = configured))
        repository.saveGame(SavedGame(other, other.startingCells(), true))

        val restored = GameRepository(storage)
        val game = assertNotNull(restored.game(puzzle))
        assertEquals(DefaultAutomaticColours + configured, game.automaticColours)
        assertEquals(DefaultAutomaticColours, restored.game(other)?.automaticColours)
        assertEquals(setOf(7, 9), game.cells[0].displayColours(game.automaticColours))
        assertEquals(setOf(9), game.cells[0].colours)
        assertEquals(emptyList(), game.history)
        assertEquals(emptyList(), game.redo)
    }

    @Test fun defaultsAreDisabledAndOldGlobalRulesDoNotAffectGames() {
        val fresh = SavedGame(SudokuFixtures.empty, SudokuFixtures.empty.startingCells(), true).automaticColours
        assertEquals(setOf(1, 2, 3), fresh[CellColour.Green.number]?.numbers)
        assertEquals(setOf(4, 5, 6), fresh[CellColour.Yellow.number]?.numbers)
        assertEquals(setOf(7, 8, 9), fresh[CellColour.Red.number]?.numbers)
        assertEquals(setOf(2, 4, 6, 8), fresh[CellColour.Blue.number]?.numbers)
        assertEquals(setOf(1, 3, 5, 7, 9), fresh[CellColour.Orange.number]?.numbers)
        assertTrue(fresh.values.all { !it.enabled })

        val storage = MemoryAppStorage()
        storage.write("ninefold.settings.v1", """{"inputMode":"Centre","automaticColours":{"7":{"enabled":true,"numbers":[2]}}}""")
        val puzzle = SudokuFixtures.empty
        val repository = GameRepository(storage)
        assertEquals("Centre", repository.settings.inputMode)
        repository.saveGame(SavedGame(puzzle, puzzle.startingCells(), true))
        assertEquals(fresh, GameRepository(storage).game(puzzle)?.automaticColours)
    }
}
