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
