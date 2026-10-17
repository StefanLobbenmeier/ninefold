# Test fixtures

- Real SudokuPad game links or downloaded published puzzles may be used temporarily to reproduce an issue. Before finishing, replace them with small, synthetic, repository-owned fixtures that reproduce the relevant behavior; remove the original payload and game-specific assertions.
- Tests must not fetch live puzzles. Use injected fetchers or Ktor MockEngine for import/network behavior. Synthetic SudokuPad URLs are fine when testing URL parsing, routing, or metadata.
- Reuse `SudokuFixtures` in `shared/src/commonTest/kotlin/de/ninefold/SudokuFixtures.kt`. Start with an empty or classic Sudoku and use `copy(...)` for the specific clues, fog, artwork, or metadata under test. Puzzle setup should usually take 1–3 lines; avoid copying whole puzzles or coupling tests to the app's demo puzzle.
- For importer tests, use the synthetic payload builders and add only the format fields needed by the test. Keep unusual source-format details explicit so regressions remain understandable.
- Run `./gradlew :shared:jvmTest` after changing shared tests. Common fixtures must remain usable by all test targets.

Typical setup:

```kotlin
val puzzle = SudokuFixtures.empty.copy(fogLights = setOf(CellPosition(0, 0)))
val classic = SudokuFixtures.classic.copy(title = "Imported puzzle")
val payload = SudokuFixtures.sudokuPadPayload(properties = "\"triggereffect\":[]")
```
