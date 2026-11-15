#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
node scripts/update-assets.mjs --check
node scripts/validate-themes.mjs
node --test scripts/*.test.mjs
python3 -B - <<'PY'
from pathlib import Path
import subprocess, zipfile, hashlib, re
resources=Path('src/main/resources')
assets=resources/'static'
for script in assets.rglob('*.js'):
 subprocess.run(['node', '--check', str(script)], check=True)
java=Path('src/main/java/xyz/gg_jskang/moviesites/support/MediaFiles.java').read_text()
formats=set(re.findall(r'"([^"]+)"', re.search(r'EXTENSIONS\s*=\s*Set.of\(([^)]+)\)',java)[1]))
build=Path('build.gradle.kts').read_text()
packaging_formats=set(re.findall(r'"([^"]+)"', re.search(r'val externalMediaExtensions = setOf\(([^)]+)\)',build)[1]))
assert formats == packaging_formats, 'Keep video resource exclusions aligned with supported extensions'
html=(assets/'index.html').read_text()
assert {x for x in re.findall(r'(?:src|href)="([^"]+)"', html)} == {x for x in re.findall(r'(?:src|href)="([^"]+)"', (assets/'index.ko.html').read_text())}, 'Keep localized assets synchronized'
offline_version=hashlib.sha256(b''.join((assets/name).read_bytes() for name in ['offline.html','pwa/offline.css','pwa/icon-192.png'])).hexdigest()[:16]
assert "PREFIX + '"+offline_version+"'" in (assets/'service-worker.js').read_text(), 'Update the offline cache revision'
files=subprocess.check_output(['git','ls-files','-z']).decode().split('\0')
assert not [p for p in files if p.startswith('movie/') and p!='movie/.gitkeep']
assert not [p for p in files if p.startswith(('runtime/','.cache/','.codex-work/','build/','.gradle/'))]
jar=Path('build/libs/moviesites.jar')
assert jar.is_file(), 'Build the application before checking the package'
with zipfile.ZipFile(jar) as z:
 names=z.namelist()
 assert 'BOOT-INF/classes/xyz/gg_jskang/moviesites/MovieSitesApplication.class' in names
 assert not any(n.startswith('BOOT-INF/classes/io/moviesites/') for n in names), 'Obsolete Java namespace in JAR'
 assert 'BOOT-INF/classes/static/index.html' in names
 for asset in resources.rglob('*'):
  if asset.is_file() and asset.suffix.lower().lstrip('.') not in formats:
   name='BOOT-INF/classes/'+asset.relative_to(resources).as_posix()
   assert name in names, 'Missing packaged resource: '+name
   assert z.read(name)==asset.read_bytes(), 'Stale packaged resource: '+name
 assert not any(n.startswith(('movie/','runtime/','BOOT-INF/classes/movie/','BOOT-INF/classes/runtime/')) for n in names)
 assert not any(n.rsplit('.',1)[-1].lower() in formats for n in names)
print('Git exclusions, static assets and JAR separation verified.')
PY
