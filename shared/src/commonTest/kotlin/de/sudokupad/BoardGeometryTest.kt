package de.sudokupad

import kotlin.test.Test
import kotlin.test.assertEquals

class BoardGeometryTest {
    @Test fun boxDividersReserveExtraSpaceWithoutReducingCellInteriors() {
        val axis = BoardAxis(452f, 1f)
        assertEquals(50f, axis.cellSize)
        for (index in 0..8) {
            assertEquals(index / 9f + .5f / 9, axis.normalized(axis.at(index + .5)), .00001f)
            if (index < 8) {
                val expectedGap = if (index % 3 == 2) 1f else 0f
                assertEquals(expectedGap, axis.start(index + 1) - axis.start(index) - axis.cellSize)
            }
        }
        assertEquals(150.5f, axis.at(3.0))
        assertEquals(301.5f, axis.at(6.0))
        assertEquals(1 / 3f, axis.normalized(150.5f))
        assertEquals(2 / 3f, axis.normalized(301.5f))
        assertEquals(452f, axis.at(9.0))
        assertEquals(-25f, axis.at(-.5))
        assertEquals(477f, axis.at(9.5))
    }
}
