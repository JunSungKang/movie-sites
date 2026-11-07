#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
node --check src/main/resources/static/js/app.js
node --check src/main/resources/static/js/cache.js
node --test scripts/cache.test.mjs
python3 -B - <<'PY'
from pathlib import Path
import subprocess, zipfile
files=subprocess.check_output(['git','ls-files','-z']).decode().split('\0')
assert not [p for p in files if p.startswith('movie/') and p!='movie/.gitkeep']
assert not [p for p in files if p.startswith(('runtime/','.cache/','.codex-work/'))]
jar=Path('target/moviesites.jar')
assert jar.is_file(), 'Build the application before checking the package'
with zipfile.ZipFile(jar) as z:
 names=z.namelist()
 assert 'BOOT-INF/classes/static/index.html' in names
 assert not any(n.startswith(('movie/','runtime/','BOOT-INF/classes/movie/','BOOT-INF/classes/runtime/')) for n in names)
 assert not any(n.lower().endswith(('.mp4','.mov','.mkv','.webm')) for n in names)
assert not any(p.exists() for p in [Path('charts'),Path('helm'),Path('k8s')])
print('Git exclusions, static assets and JAR separation verified.')
PY
