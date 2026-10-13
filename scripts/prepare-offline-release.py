#!/usr/bin/env python3
"""Embed a complete, content-verified asset list into a built service worker."""
import hashlib
import json
from pathlib import Path
import sys


def prepare_release(directory: Path):
    worker = directory / 'sw.js'
    template = worker.read_text()
    marker = 'const RELEASE = null;'
    if marker not in template:
        raise ValueError('Expected the original service worker template')
    assets = [
        {'path': path.relative_to(directory).as_posix(),
         'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
        for path in sorted(directory.rglob('*'))
        if path.is_file() and path != worker
        and not path.name.startswith('.') and path.suffix != '.map'
    ]
    paths = {asset['path'] for asset in assets}
    if not {'index.html', 'styles.css', 'offline.js', 'webApp.js'} <= paths:
        raise ValueError('App shell is incomplete')
    payload = json.dumps(assets, separators=(',', ':'))
    # Worker logic changes must produce a new cache even if assets are unchanged.
    version = hashlib.sha256((template + payload).encode()).hexdigest()
    release = json.dumps({'version': version, 'assets': assets}, separators=(',', ':'))
    worker.write_text(template.replace(marker, f'const RELEASE = {release};', 1))


if __name__ == '__main__':
    prepare_release(Path(sys.argv[1]))
