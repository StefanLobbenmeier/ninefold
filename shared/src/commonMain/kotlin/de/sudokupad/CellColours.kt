package de.sudokupad

import androidx.compose.ui.graphics.Color

/** The nine swatches from SudokuPad's default Colour tool (https://sudokupad.app). */
internal enum class CellColour(val label: String, val swatch: Color) {
    LightGrey("Light grey", Color(0xFFE6E6E6)),
    Grey("Grey", Color(0xFFB0B0B0)),
    DarkGrey("Dark grey", Color(0xFF505050)),
    Green("Green", Color(0xFFD1EFA5)),
    Purple("Purple", Color(0xFFF1B0F6)),
    Orange("Orange", Color(0xFFF3B48F)),
    Red("Red", Color(0xFFF39390)),
    Yellow("Yellow", Color(0xFFFAE799)),
    Blue("Blue", Color(0xFF8AC1F9));

    val number: Int get() = ordinal + 1
    companion object {
        fun fromNumber(number: Int): CellColour? = entries.getOrNull(number - 1)
    }
}

@kotlinx.serialization.Serializable
internal data class AutomaticColourRule(val enabled: Boolean = false, val numbers: Set<Int> = emptySet())

internal val DefaultAutomaticColours = mapOf(
    CellColour.Green.number to AutomaticColourRule(numbers = setOf(1, 2, 3)),
    CellColour.Yellow.number to AutomaticColourRule(numbers = setOf(4, 5, 6)),
    CellColour.Red.number to AutomaticColourRule(numbers = setOf(7, 8, 9)),
    CellColour.Blue.number to AutomaticColourRule(numbers = setOf(2, 4, 6, 8)),
    CellColour.Orange.number to AutomaticColourRule(numbers = setOf(1, 3, 5, 7, 9)),
)

/** Only finalized, uncoloured cells participate; palette order breaks overlapping matches. */
internal fun applyAutomaticColours(
    cells: List<CellState>, finalized: Set<CellPosition>, rules: Map<Int, AutomaticColourRule>,
): List<CellState> = cells.mapIndexed { index, cell ->
    if (CellPosition(index / 9, index % 9) !in finalized || cell.colours.isNotEmpty()) cell
    else {
        val numbers = cell.value?.let { setOf(it) } ?: cell.centreNotes
        val colour = CellColour.entries.firstOrNull { colour ->
            val rule = rules[colour.number]
            rule?.enabled == true && numbers.isNotEmpty() && numbers.all { it in rule.numbers }
        }
        if (colour == null) cell else cell.copy(colours = setOf(colour.number))
    }
}

/** Retain the last finalized content while selected cells are being edited. */
internal fun finalizedCells(cells: List<CellState>, previous: List<CellState>, editing: Set<CellPosition>): List<CellState> =
    cells.mapIndexed { index, cell -> if (CellPosition(index / 9, index % 9) in editing) previous[index] else cell }

internal fun PuzzleCheckResult.excluding(editing: Set<CellPosition>): PuzzleCheckResult {
    val indices = editing.map { it.index }.toSet()
    return copy(cells = cells - indices, pencilMarks = pencilMarks.filterKeys { it !in indices })
}
