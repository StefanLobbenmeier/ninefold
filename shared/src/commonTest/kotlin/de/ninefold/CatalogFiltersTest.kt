package de.ninefold

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogFiltersTest {
    @Test fun durationBucketsHaveNoGapsOrOverlaps() {
        val cases = mapOf(
            null to CatalogDifficulty.Unknown, 0 to CatalogDifficulty.Unknown,
            -1 to CatalogDifficulty.Unknown, 1 to CatalogDifficulty.Easy,
            1799 to CatalogDifficulty.Easy, 1800 to CatalogDifficulty.Medium,
            3599 to CatalogDifficulty.Medium, 3600 to CatalogDifficulty.Hard,
        )
        cases.forEach { (seconds, expected) ->
            assertEquals(listOf(CatalogDifficulty.All, expected), CatalogDifficulty.entries.filter { it.matches(seconds) })
        }
    }
}
