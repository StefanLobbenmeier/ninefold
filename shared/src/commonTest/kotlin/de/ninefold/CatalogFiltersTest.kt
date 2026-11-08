package de.ninefold

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogFiltersTest {
    private fun entry(seconds: Int?, sourceId: String = "synthetic-channel") = SudokuIndexEntry(
        "synthetic", "Test puzzle", "Test source", "2026-10-12", "https://sudokupad.app/synthetic",
        durationSeconds = seconds, sourceId = sourceId,
    )

    @Test fun generallyApproachableComesBeforeEasy() {
        assertEquals(CatalogDifficulty.GenerallyApproachable, CatalogDifficulty.entries[1])
        assertEquals(CatalogDifficulty.Easy, CatalogDifficulty.entries[2])
    }

    @Test fun luciansPlaceIsExclusivelyGasRegardlessOfVideoLength() {
        listOf(null, -1, 0, 1, 1799, 1800, 3599, 3600).forEach { seconds ->
            val puzzle = entry(seconds, "lucians-place").copy(source = "Renamed channel")
            assertEquals(
                listOf(CatalogDifficulty.All, CatalogDifficulty.GenerallyApproachable),
                CatalogDifficulty.entries.filter { it.matches(puzzle) },
            )
        }
    }

    @Test fun durationBucketsHaveNoGapsOrOverlaps() {
        val cases = mapOf(
            null to CatalogDifficulty.Unknown, 0 to CatalogDifficulty.Unknown,
            -1 to CatalogDifficulty.Unknown, 1 to CatalogDifficulty.Easy,
            1799 to CatalogDifficulty.Easy, 1800 to CatalogDifficulty.Medium,
            3599 to CatalogDifficulty.Medium, 3600 to CatalogDifficulty.Hard,
        )
        cases.forEach { (seconds, expected) ->
            assertEquals(listOf(CatalogDifficulty.All, expected), CatalogDifficulty.entries.filter { it.matches(entry(seconds)) })
        }
    }
}
