#!/usr/bin/env python3
"""Export the approved icon. Requires Pillow: python3 -m pip install Pillow."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Image.open(ROOT / 'assets/branding/ninefold-icon-source.png').convert('RGB')


def icon(size, scale=1):
    image = Image.new('RGB', (size, size), '#28564c')
    artwork = SOURCE.resize((round(size * scale),) * 2, Image.Resampling.LANCZOS)
    offset = (size - artwork.width) // 2
    image.paste(artwork, (offset, offset))
    return image


def save(image, path, **options):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target, **options)


web = 'webApp/src/webMain/resources'
for size in (192, 512):
    save(icon(size), f'{web}/icons/icon-{size}.png')
    # Keep the grid corners inside the central 80% safe circle.
    save(icon(size, .8), f'{web}/icons/icon-maskable-{size}.png')
save(icon(180), f'{web}/apple-touch-icon.png')
save(icon(256), f'{web}/favicon.ico', sizes=[(16, 16), (32, 32), (48, 48)])

android = 'androidApp/src/main/res'
for density, size in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)):
    save(icon(size), f'{android}/mipmap-{density}/ic_launcher.png')
    # Extra breathing room protects corners under the circular legacy mask.
    rounded = icon(size, .85).convert('RGBA')
    mask = Image.new('L', (size * 4, size * 4))
    ImageDraw.Draw(mask).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    rounded.putalpha(mask.resize((size, size), Image.Resampling.LANCZOS))
    save(rounded, f'{android}/mipmap-{density}/ic_launcher_round.png')
# Launchers show the central 72 of 108 dp; leave space for masks and motion.
save(icon(432, .7), f'{android}/drawable-nodpi/ic_launcher_foreground.png')
save(icon(1024), 'iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png')
for extension, size in (('png', 512), ('icns', 1024), ('ico', 256)):
    options = {'sizes': [(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]} if extension == 'ico' else {}
    save(icon(size), f'desktopApp/icons/ninefold.{extension}', **options)
save(icon(256), 'desktopApp/src/main/composeResources/drawable/ninefold_icon.png')
