package de.sudokupad

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameShortcutsTest {
    @Test fun digitsAndNumpadDigitsUseCurrentOrTemporaryModes() {
        assertEquals(GameShortcut.Enter(4, null), gameShortcut(Key.Four, false, false))
        assertEquals(GameShortcut.Enter(4, InputMode.Centre), gameShortcut(Key.NumPad4, true, false))
        assertEquals(GameShortcut.Enter(4, InputMode.Corner), gameShortcut(Key.Four, false, true))
        assertEquals(GameShortcut.Enter(4, InputMode.Colour), gameShortcut(Key.NumPad4, true, true))
        assertEquals(GameShortcut.Enter(null, null), gameShortcut(Key.Zero, false, false))
    }

    @Test fun selectionAndHistoryBindingsTakePrecedenceOverToolKeys() {
        assertEquals(GameShortcut.Undo, gameShortcut(Key.Z, true, false))
        assertEquals(GameShortcut.Redo, gameShortcut(Key.Y, true, false))
        assertEquals(GameShortcut.SelectAll, gameShortcut(Key.A, true, false))
        assertEquals(GameShortcut.ClearSelection, gameShortcut(Key.A, true, true))
        assertEquals(GameShortcut.InvertSelection, gameShortcut(Key.I, true, false))
        assertEquals(GameShortcut.SelectMode(InputMode.Digit), gameShortcut(Key.Y, false, false))
    }

    @Test fun arrowsAndModeCyclingRespectModifiers() {
        assertEquals(GameShortcut.MoveSelection(-1, 0, false), gameShortcut(Key.DirectionUp, false, false))
        assertEquals(GameShortcut.MoveSelection(0, 1, true), gameShortcut(Key.DirectionRight, false, true))
        assertEquals(GameShortcut.CycleMode(1), gameShortcut(Key.Spacebar, false, false))
        assertEquals(GameShortcut.CycleMode(-1), gameShortcut(Key.Spacebar, true, false))
        assertEquals(GameShortcut.CycleMode(-1), gameShortcut(Key.PageUp, false, false))
        assertNull(gameShortcut(Key.C, false, false, alt = true))
    }
}
