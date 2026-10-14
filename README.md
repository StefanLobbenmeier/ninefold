This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM).

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
      Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
      folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and
options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
    - Hot reload: `./gradlew :desktopApp:hotRun --auto`
    - Standard run: `./gradlew :desktopApp:run`
- Web app:
    - Wasm target (faster, modern browsers): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
    - JS target (slower, supports older browsers): `./gradlew :webApp:jsBrowserDevelopmentRun`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:jvmTest`
  - Includes a JVM UI startup smoke test that renders the app, loads a catalog through the real
    HTTP engine from a local server, and opens Import. No internet connection is needed for the test.
    On Linux without a display, use `xvfb-run --auto-servernum ./gradlew :shared:jvmTest` (as in CI).
- Web tests:
    - Wasm target: `./gradlew :shared:wasmJsTest`
    - JS target: `./gradlew :shared:jsTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack
channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
### Screenshot import

Import → Image opens the system image picker. The shared Kotlin implementation finds an axis-aligned
9×9 grid, recognizes printed digits locally, and maps each cell by its position (including empty rows
and columns). No screenshot is uploaded and no OCR service, API key, or model download is required.
The recognizer uses normalized glyph templates rendered from the platform's sans, serif, and monospace
fonts, with stroke and enclosed-shape comparisons. It is intended for clear classic-sudoku screenshots;
unusual fonts, pencil marks, handwriting, rotated/perspective photos, and very low-resolution images
can require corrections. Automatic recognition is not guaranteed for every screenshot.

Every import opens an editable review before changing the current puzzle:

- Compare the original screenshot and imported clues; amber cells indicate uncertain readings.
- Select any cell and use the number buttons or Erase to correct it. Adjust grid area lets you mark
  the grid's opposite corners and rerun recognition; if detection fails, manual entry remains available.
- Shared validation checks duplicate clues in rows, columns, and boxes, then searches for up to two
  solutions. It distinguishes invalid, unsolvable, uniquely solvable, and ambiguous starting grids.
  Checks rerun after edits, are cancellable, and stop after five seconds if still inconclusive.
- Confirm that you have double-checked the clues before importing. Invalid or unsolvable grids may
  still be imported with an explicit warning. Cancelling preserves the current game and its history.

Recognition, crop coordinates, validation, and review UI live in `shared/src/commonMain`.
FileKit supplies the native/browser image pickers; Desktop initializes FileKit with the application ID.
There are no separate platform OCR implementations to add.

### SudokuPad URL import

Import → Puzzle link accepts links such as `https://sudokupad.app/hDNMJqPmTh`. The app fetches the
corresponding package through SudokuPad's own CORS-enabled
`https://sudokupad.svencodes.com/ctclegacy/{encoded-id}` endpoint and decodes both SudokuPad's
`scl`/compact-JSON representation and the common `fpuz` representation. The main site's
`/api/puzzle/{id}` endpoint restricts cross-origin requests to `https://localhost`; the alternate
endpoint works from the JS and Wasm web builds as well as the native builds.

Imported puzzles retain metadata, givens, solutions, killer cages, sum arrows, colored lines, fog,
cell marks, and generic overlays such as Kropki/XV marks and outside-grid clues. Explicit fpuz
thermometers and palindromes retain their semantics. Styled custom lines default to display-only; the
review's line-rule dialog lets the solver assign German/Dutch whisper, renban, palindrome, thermometer,
or region-sum meaning without guessing based only on a color. Killer sums, sum arrows, assigned simple
line rules, and recognizable skyscraper/diagonal-sum outside clues participate in optional rule checks.
Unknown artwork is still rendered and its original rule text is kept.

Validation: `./gradlew :shared:jvmTest` includes solver cases and generated screenshot fixtures covering
margins, sparse grids, multiple fonts, sizes, and dark backgrounds. The Web upload/review/edit flow was
also checked in the browser. JS, Wasm, JVM, Android, and iOS simulator source compilation is checked.
Before shipping native builds, smoke-test on devices:

- Android: gallery selection/cancellation, PNG/JPEG decoding, and review on a small screen.
- iOS: Photos picker presentation/dismissal, file decoding, and safe-area/scroll behavior.
- Desktop: native picker behavior on each packaged OS (including Linux's desktop portal).

### Puzzle catalog and offline browsing

The app starts with the newest puzzles from
`https://stefanlobbenmeier.github.io/sudoku-index/index.json`. Select Play to load a puzzle into the
player, or Puzzles to return to the list. Import remains available from either screen.

The app saves the last valid index locally and displays it immediately on subsequent
visits while refreshing. Failed requests or invalid index responses preserve the saved list. Refresh
retries the request; first visits without a connection show an error until a download succeeds.
All catalog puzzles are eagerly fetched, converted, and saved with up to four downloads at once,
including while playing. Each entry shows waiting, fetching/converting, ready offline, or failed status.
Ready offline means the converted puzzle was successfully stored; opening it makes no puzzle request.
Refresh or an entry's Retry button retries failures. Preparing a large catalog can take longer than a
few seconds, depending on the connection. Production builds prepare a service worker that downloads
and verifies the complete app (including all Wasm modules and chunks) before installing a release.
Subsequent launches use that cached release immediately, even on a slow connection. Updates download
into a separate cache; failed or interrupted downloads preserve the working release. A complete update
takes over after all app tabs close. Fetched puzzles and fallback fonts have a separate cache that
survives app updates. The first visit needs a connection until the app download completes.
Browser storage must be available for persistent
offline access.

Outbound links, including source videos and original SudokuPad pages, use a shared web link handler.
They open in a separate browser tab, or hand off to the system from an iOS home-screen app without
creating a blank popup. The browser and OS decide whether an installed app handles the link;
iOS may present external web pages in a browser sheet. Verify the YouTube app handoff and return
to the puzzle on a device when changing this behavior.

Every move (including pencil marks, erase, undo, and redo) saves the board and undo/redo history.
The timer saves independently whenever its displayed second changes and when leaving the player.
Reopening resumes from the last saved board and elapsed time; time away from the player does not count.
In the browser/PWA, the timer also pauses while the page is hidden (such as when switching
tabs or apps) and resumes when visible. Elapsed time is saved immediately on visibility changes.
Completed grids retain their frozen timer. The menu has Continue sudokus and Solved sudokus sections,
including manually imported games. Checking preferences, input mode, and multiple selection persist
across puzzles and restarts. Native storage uses Android preferences, iOS user defaults, and local files
in `~/.ninefold` on Desktop. Failed local saves display an error.

Catalog parsing, refresh, cache preservation, and unavailable storage are covered by
`./gradlew :shared:jvmTest`.

### Playing on a phone

The player keeps the board and a T9-style number keypad within the available screen height,
including native safe areas. Each new puzzle opens its rules first, with links to the source
video and original SudokuPad when available. Tap Okay to start the timer; a correct completed
grid freezes it, and undoing the final digit resumes it. Completion uses the supplied solution,
or the supported Sudoku rules when a solution is unavailable.

Drag across cells to select a group, or enable Select multiple to add to the current selection
by tapping or dragging. Start on a selected cell to remove it and any cells you drag across;
untouched cells stay selected. Adjacent selected cells share a bold outside outline. The keypad places 1–9 in three rows with 0 below as an erase shortcut;
Digit, Corner, Centre, and Colour sit beside the keypad. Choose Corner or
Centre to enter pencil marks in all empty selected cells. A note is added to every selected cell
if any are missing it; tapping that number again removes it from all of them. Filled digits and
givens are preserved when adding notes. Erase clears editable selected cells; Undo restores the
whole group in one step.

Choose Colour to replace the digits with SudokuPad's nine default swatches: light grey, grey,
dark grey, green, purple, orange, red, yellow, and blue. Colours apply to all selected cells,
including givens and filled digits. Selecting a colour adds it to the group if any cell lacks it;
selecting it again removes it from the group. Multiple colours appear as stripes behind the
puzzle clues and digits. In Colour mode, Erase and 0 clear only colours. Colours and colour
undo/redo history are saved with the game.

Long-press a colour swatch to configure automatic colouring. Each colour has its own enable switch
and matching numbers, saved across puzzles and restarts. Disabled presets are green for 1–3,
yellow for 4–6, red for 7–9, blue for even numbers, and orange for odd numbers. An A marks enabled swatches. When a cell
leaves the selection, its entered digit or nonempty centre marks must all belong to the configured
numbers. Corner marks do not trigger colouring. Existing colours are preserved; overlapping rules
use the first matching colour in palette order. Automatic colour additions can be undone and redone.
Solution feedback and completion wait until you leave the cell; Sudoku rule feedback remains live.
Keypad presses and input-mode changes keep the current selection.

### Sharing puzzles

Rules includes a selectable Puzzle link and Copy puzzle link for every puzzle, including catalog,
SudokuPad, screenshot, and manual imports. Opening the link in the web app imports the puzzle and
shows its rules. The link contains the complete puzzle (including variant clues, assigned line rules,
artwork, metadata, and solution when available); moves, notes, and elapsed time are not shared.
Existing progress on the receiving device resumes if it already has that puzzle saved.
The puzzle data is encoded in the URL fragment, so no upload or puzzle hosting service is needed.
Native apps generate links to the hosted web app; web builds use their current host and path.

On Desktop, iOS, Android, or an installed PWA, open Import → Puzzle link, paste the shared URL,
and select Load puzzle. Review it, confirm the clues, and select Import puzzle. Shared links decode
locally and work offline. This tab also accepts SudokuPad URLs (which require a connection).
Opening a shared HTTPS link directly currently targets the web app; native app deep linking is not
configured. Pasting inside the installed app or PWA imports into that app’s own saved games.
