package de.ninefold

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

/** Infer first-open defaults only; saved settings, including disabled rules, always win. */
internal fun Puzzle.initialAutomaticColours(saved: SavedGame?): Map<Int, AutomaticColourRule> {
    saved?.let { return it.automaticColours }
    val text = rules.joinToString("\n")
    fun mentions(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
    val parity = mentions("\\b(?:odd|odds|even|evens|parity)\\b")
    val xv = mentions("\\bXV\\b")
    val v = xv || mentions("\\bV\\b")
    val x = xv || mentions("\\bX\\b")
    val whispers = mentions("\\bgerman[\\s-]+whispers?\\b")
    val enabled = buildSet {
        if (parity) addAll(listOf(CellColour.Blue, CellColour.Orange))
        if (v || x || whispers) add(CellColour.Green)
        if (x || whispers) add(CellColour.Red)
    }.map { it.number }.toSet()
    return DefaultAutomaticColours.mapValues { (colour, rule) -> rule.copy(enabled = colour in enabled) }
}

/** Display colours are derived from live content; only manual colours belong to cell state. */
internal fun CellState.displayColours(rules: Map<Int, AutomaticColourRule>): Set<Int> {
    val numbers = value?.let { setOf(it) } ?: centreNotes
    return colours + CellColour.entries.filter { colour ->
        val rule = rules[colour.number]
        rule?.enabled == true && numbers.isNotEmpty() && numbers.all { it in rule.numbers }
    }.map { it.number }
}

/** Retain the last finalized content while selected cells are being edited. */
internal fun finalizedCells(cells: List<CellState>, previous: List<CellState>, editing: Set<CellPosition>): List<CellState> =
    cells.mapIndexed { index, cell -> if (CellPosition(index / 9, index % 9) in editing) previous[index] else cell }

internal fun PuzzleCheckResult.excluding(editing: Set<CellPosition>): PuzzleCheckResult {
    val indices = editing.map { it.index }.toSet()
    return copy(cells = cells - indices, pencilMarks = pencilMarks.filterKeys { it !in indices })
}
