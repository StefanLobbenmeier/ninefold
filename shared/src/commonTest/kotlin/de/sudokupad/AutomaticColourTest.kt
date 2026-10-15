package de.sudokupad

import kotlin.test.*

class AutomaticColourTest {
    private val rules = mapOf(7 to AutomaticColourRule(true, setOf(1, 2, 3)))
    private val positions = (0..8).map { CellPosition(0, it) }.toSet()

    @Test fun matchesDigitsAndNonemptySubsetsOfCentreMarksOnly() {
        val cells = listOf(
            CellState(value = 2), CellState(centreNotes = setOf(1, 3)),
            CellState(centreNotes = setOf(1, 4)), CellState(),
            CellState(cornerNotes = setOf(1, 3)), CellState(value = 4),
            CellState(value = 2, colours = setOf(9)), CellState(value = 2, given = true),
            CellState(value = 4, centreNotes = setOf(1)),
        )
        val result = applyAutomaticColours(cells, positions, rules)
        assertEquals(listOf(setOf(7), setOf(7), emptySet(), emptySet(), emptySet(), emptySet(), setOf(9), setOf(7), emptySet()), result.map { it.colours })
        assertEquals(cells, applyAutomaticColours(cells, emptySet(), rules))
        assertEquals(cells, applyAutomaticColours(cells, positions, mapOf(7 to AutomaticColourRule(false, setOf(1, 2, 3)))))
    }

    @Test fun overlappingRulesUsePaletteOrderAndEmptyRulesNeverMatch() {
        val cells = listOf(CellState(value = 2))
        assertEquals(setOf(4), applyAutomaticColours(cells, positions, rules + (4 to AutomaticColourRule(true, setOf(2))))[0].colours)
        assertEquals(cells, applyAutomaticColours(cells, positions, mapOf(1 to AutomaticColourRule(true))))
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

    @Test fun colourSettingsPersistWithIndependentEnableAndNumbers() {
        val storage = MemoryAppStorage()
        val repository = GameRepository(storage)
        repository.saveSettings(repository.settings.copy(automaticColours = rules + (9 to AutomaticColourRule(false, setOf(5, 6)))))
        assertEquals(repository.settings, GameRepository(storage).settings)
    }
    @Test fun defaultsAreDisabledAndOlderSettingsKeepCustomRules() {
        val fresh = GameRepository(MemoryAppStorage()).settings.automaticColours
        assertEquals(setOf(1, 2, 3), fresh[CellColour.Green.number]?.numbers)
        assertEquals(setOf(4, 5, 6), fresh[CellColour.Yellow.number]?.numbers)
        assertEquals(setOf(7, 8, 9), fresh[CellColour.Red.number]?.numbers)
        assertEquals(setOf(2, 4, 6, 8), fresh[CellColour.Blue.number]?.numbers)
        assertEquals(setOf(1, 3, 5, 7, 9), fresh[CellColour.Orange.number]?.numbers)
        assertTrue(fresh.values.all { !it.enabled })

        val storage = MemoryAppStorage()
        storage.write("ninefold.settings.v1", """{"automaticColours":{"7":{"enabled":true,"numbers":[2]},"9":{"numbers":[]}}}""")
        val migrated = GameRepository(storage)
        assertEquals(AutomaticColourRule(true, setOf(2)), migrated.settings.automaticColours[7])
        assertEquals(AutomaticColourRule(), migrated.settings.automaticColours[9])
        assertEquals(fresh[4], migrated.settings.automaticColours[4])
        migrated.saveSettings(migrated.settings)
        assertEquals(migrated.settings, GameRepository(storage).settings)
    }

}
