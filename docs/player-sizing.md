# Player sizing investigation

Inspected SudokuPad Web v0.611.0 on 2026-10-18 using the collaborative browser,
DOM bounds after resize settled, and the public resize implementation in
https://sudokupad.app/script.js (App.resize and App.resizeBoard).

SudokuPad chooses orientation using the game area after its 40 px top bar. In landscape
it fits a centered frame with aspect ratio clamped to 1.7–2.1, reserves a square slot
at the left, and subtracts 5% for board margins. Controls scale independently and stop
at 1.3×. In portrait the frame's height/width ratio is clamped to 1.5–2.1, the board
gets a square at the top with a 5 px margin, and controls go below it. Outside artwork
can change the rendered grid's size inside the board bounds.

Ninefold now uses those frame and board rules. Its top bar remains 52 dp, accounting
for the approximately 11 px difference in height-limited landscape board sizes.
Titles/authors/timers occupy the sidebar in landscape and the area below the board
in portrait. Controls also scale down to fit short windows; long rules remain scrollable.
The controls have Ninefold's own proportions and reserve room for metadata, so their
scale and placement are intentionally approximate rather than identical to SudokuPad.

| Viewport (CSS px) | SudokuPad measured board | Ninefold board allocation |
| --- | ---: | ---: |
| 1366 × 768 | 691 | 680 |
| 1920 × 1080 | 988 | 977 |
| 3840 × 2160 | 2014 | 2003 |
| 3440 × 1440 | 1330 | 1319 |
| 1024 × 768 | 572 | 572 |
| 844 × 390 | 333 | 321 |
| 390 × 844 | 385 | 385 |
| 850 × 900 | 568 | 560 |

Ninefold allocations are derived from the layout and covered by JVM layout tests;
the 1366 × 768 allocation was also confirmed in the Web accessibility snapshot.
Pixel rounding can differ by one pixel. These measure the square board container,
including its space for outside clues, rather than individual cell outlines.

## Zoom

The browser automation does not expose browser zoom; the keyboard zoom shortcut did
not change the CSS viewport. Instead, tested the CSS viewport sizes corresponding to
75%, 100%, 125%, 150%, and 200% zoom on a 1920 × 1080 content area. This verifies
responsive layout, not native browser zoom, device scaling, or pinch zoom.

| Equivalent zoom | CSS viewport | SudokuPad measured board |
| --- | --- | ---: |
| 75% | 2560 × 1440 | 1330 |
| 100% | 1920 × 1080 | 988 |
| 125% | 1536 × 864 | 782 |
| 150% | 1280 × 720 | 646 |
| 200% | 960 × 540 | 475 |

Multiplying board sizes by the zoom factor gives nearly constant physical sizes;
the fixed-height top bar accounts for the small reduction as zoom increases.
Tests include the corresponding Ninefold game-area sizes and short landscape rotation.
Manual browser zoom and installed mobile device checks remain useful before release.
No published puzzle payload or puzzle-specific assertion is stored in the repository.
