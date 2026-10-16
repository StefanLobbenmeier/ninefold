package de.sudokupad

import androidx.compose.ui.input.key.Key

internal sealed interface GameShortcut {
    data class Enter(val number: Int?, val mode: InputMode?) : GameShortcut
    data class MoveSelection(val rowDelta: Int, val columnDelta: Int, val extend: Boolean) : GameShortcut
    data class SelectMode(val mode: InputMode) : GameShortcut
    data class CycleMode(val delta: Int) : GameShortcut
    data object SelectAll : GameShortcut
    data object ClearSelection : GameShortcut
    data object InvertSelection : GameShortcut
    data object Undo : GameShortcut
    data object Redo : GameShortcut
}

/** Maps hardware keys without depending on a particular desktop or mobile native event type. */
internal fun gameShortcut(key: Key, primaryModifier: Boolean, shift: Boolean, alt: Boolean = false): GameShortcut? {
    if (alt) return null

    val digit = when (key) {
        Key.Zero, Key.NumPad0 -> 0
        Key.One, Key.NumPad1 -> 1
        Key.Two, Key.NumPad2 -> 2
        Key.Three, Key.NumPad3 -> 3
        Key.Four, Key.NumPad4 -> 4
        Key.Five, Key.NumPad5 -> 5
        Key.Six, Key.NumPad6 -> 6
        Key.Seven, Key.NumPad7 -> 7
        Key.Eight, Key.NumPad8 -> 8
        Key.Nine, Key.NumPad9 -> 9
        else -> null
    }
    if (digit != null) {
        val temporaryMode = when {
            primaryModifier && shift -> InputMode.Colour
            primaryModifier -> InputMode.Centre
            shift -> InputMode.Corner
            else -> null
        }
        return GameShortcut.Enter(digit.takeUnless { it == 0 }, temporaryMode)
    }

    if (primaryModifier) when (key) {
        Key.Z -> return GameShortcut.Undo
        Key.Y -> return GameShortcut.Redo
        Key.A -> return if (shift) GameShortcut.ClearSelection else GameShortcut.SelectAll
        Key.I -> return GameShortcut.InvertSelection
        Key.Spacebar -> return GameShortcut.CycleMode(-1)
    }

    val extend = primaryModifier || shift
    when (key) {
        Key.DirectionUp -> return GameShortcut.MoveSelection(-1, 0, extend)
        Key.DirectionDown -> return GameShortcut.MoveSelection(1, 0, extend)
        Key.DirectionLeft -> return GameShortcut.MoveSelection(0, -1, extend)
        Key.DirectionRight -> return GameShortcut.MoveSelection(0, 1, extend)
        Key.PageUp -> return GameShortcut.CycleMode(-1)
        Key.PageDown -> return GameShortcut.CycleMode(1)
        Key.Backspace, Key.Delete, Key.NumPadDelete -> return GameShortcut.Enter(null, null)
        Key.Escape -> return GameShortcut.ClearSelection
    }
    if (primaryModifier || shift) return null
    return when (key) {
        Key.Spacebar -> GameShortcut.CycleMode(1)
        // Y shares Z's physical position on a German keyboard layout.
        Key.Z, Key.Y -> GameShortcut.SelectMode(InputMode.Digit)
        Key.X -> GameShortcut.SelectMode(InputMode.Corner)
        Key.C -> GameShortcut.SelectMode(InputMode.Centre)
        Key.V -> GameShortcut.SelectMode(InputMode.Colour)
        else -> null
    }
}
