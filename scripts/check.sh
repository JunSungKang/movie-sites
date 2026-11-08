#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
node --check src/main/resources/static/js/app.js
node --check src/main/resources/static/js/cache.js
node --check src/main/resources/static/js/playback.js
node --check src/main/resources/static/themes/runtime.js
node --check src/main/resources/static/themes/preview.js
node scripts/validate-themes.mjs
node --test scripts/cache.test.mjs scripts/playback.test.mjs scripts/player-ui.test.mjs
python3 -B - <<'PY'
from pathlib import Path
import subprocess, zipfile, hashlib, re
assets=Path('src/main/resources/static')
html=(assets/'index.html').read_text()
app=(assets/'js/app.js').read_text()
for name,content in [('js/app.js',html),('css/app.css',html),('js/cache.js',app),('themes/runtime.js',app),('js/playback.js',app)]:
 expected=hashlib.sha256((assets/name).read_bytes()).hexdigest()[:16]
 assert name.split('/')[-1]+'?v='+expected in content, 'Update static content version: '+name
files=subprocess.check_output(['git','ls-files','-z']).decode().split('\0')
assert not [p for p in files if p.startswith('movie/') and p!='movie/.gitkeep']
assert not [p for p in files if p.startswith(('runtime/','.cache/','.codex-work/','build/','.gradle/'))]
jar=Path('build/libs/moviesites.jar')
assert jar.is_file(), 'Build the application before checking the package'
with zipfile.ZipFile(jar) as z:
 names=z.namelist()
 assert 'BOOT-INF/classes/static/index.html' in names
 for asset in (assets/'themes').rglob('*'):
  if asset.is_file():
   name='BOOT-INF/classes/static/'+asset.relative_to(assets).as_posix()
   assert name in names, 'Missing packaged theme asset: '+name
   assert z.read(name)==asset.read_bytes(), 'Stale packaged theme asset: '+name
 assert not any(n.startswith(('movie/','runtime/','BOOT-INF/classes/movie/','BOOT-INF/classes/runtime/')) for n in names)
 assert not any(n.lower().endswith(('.mp4','.mov','.mkv','.webm')) for n in names)
assert not any(p.exists() for p in [Path('charts'),Path('helm'),Path('k8s')])
print('Git exclusions, static assets and JAR separation verified.')
PY
