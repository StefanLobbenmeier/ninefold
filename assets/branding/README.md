# Ninefold icon

`ninefold-icon-source.png` is the approved folded-grid concept, generated with the
built-in imagegen tool. Keep the square background opaque; platforms apply masks.

Regenerate all platform exports with Pillow installed:

```sh
python3 scripts/generate-icons.py
```

Maskable PWA and adaptive Android exports add room for platform crops and motion.

Generation prompt: A polished square app icon for Ninefold, a calm premium Sudoku
app. Dark moss background (#28564C), a warm ivory square paper tile (#F7F5EF) with
a clean 3 by 3 grid of nine cells, thick dark moss grid gaps, lower right paper
corner folded up revealing a sage reverse (#DDE9DD) and a small shadow. No
numerals. Simple iconic origami detail, top-down, not perspective. Full-bleed opaque
square canvas, no external rounded corners, labels, wordmark, or watermark.
