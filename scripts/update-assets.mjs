import {createHash} from 'node:crypto';
import {readdir, readFile, writeFile, realpath, lstat} from 'node:fs/promises';
import {dirname, resolve, posix} from 'node:path';
import {fileURLToPath} from 'node:url';
import {moduleImports} from './module-imports.mjs';

const digest = bytes => createHash('sha256').update(bytes).digest('hex').slice(0, 16);

function target(owner, specifier) {
  if (/^(?:[a-z][a-z\d+.-]*:|\/\/)/i.test(specifier)) return null;
  const split = specifier.search(/[?#]/);
  const pathname = split < 0 ? specifier : specifier.slice(0, split);
  const name = posix.normalize(pathname.startsWith('/') ? pathname.slice(1) : posix.join(posix.dirname(owner), pathname));
  if (name === '..' || name.startsWith('../')) throw new Error(`Asset escapes static root: ${owner}: ${specifier}`);
  return name;
}

function versioned(specifier, hash) {
  const [withoutFragment, fragment] = specifier.split('#', 2);
  const split = withoutFragment.indexOf('?');
  const pathname = split < 0 ? withoutFragment : withoutFragment.slice(0, split);
  const query = new URLSearchParams(split < 0 ? '' : withoutFragment.slice(split + 1));
  query.set('v', hash);
  return pathname + '?' + query + (fragment === undefined ? '' : '#' + fragment);
}

const preloadStart = '<!-- modulepreload:start -->', preloadEnd = '<!-- modulepreload:end -->';

function clearModulePreloads(source) {
  const starts = source.split(preloadStart).length - 1, ends = source.split(preloadEnd).length - 1;
  if (!starts && !ends) return {source, enabled: false};
  const first = source.indexOf(preloadStart), last = source.indexOf(preloadEnd);
  if (starts !== 1 || ends !== 1 || first > last) throw new Error('Expected one paired modulepreload marker block');
  // Stale hints may name modules that have since been removed from the graph.
  return {source: source.slice(0, first) + preloadStart + preloadEnd + source.slice(last + preloadEnd.length), enabled: true};
}

function modulePreloadMarkup(owner, source, files) {
  const urls = new Set();
  const html = source.replace(/<!--[\s\S]*?-->/g, '');
  const attribute = (attributes, name) => new RegExp('(?:^|\\s)' + name + '\\s*=\\s*(["\'])(.*?)\\1', 'i').exec(attributes)?.[2];
  for (const script of html.matchAll(/<script\b([^>]*)>[\s\S]*?<\/script\s*>/gi)) {
    if (attribute(script[1], 'type') !== 'module') continue;
    const src = attribute(script[1], 'src');
    if (!src) continue;
    const root = target(owner, src);
    if (root === null) continue;
    if (!root.endsWith('.js') || !files.has(root)) throw new Error(`Missing module entry: ${owner}: ${src}`);
    for (const entry of moduleImports(files.get(root).toString('utf8'))) {
      if (entry.kind !== 'static') continue;
      const dependency = target(root, entry.specifier);
      if (dependency === null || !files.has(dependency)) throw new Error(`Missing preload module: ${root}: ${entry.specifier}`);
      const split = entry.specifier.search(/[?#]/);
      urls.add('/' + dependency + (split < 0 ? '' : entry.specifier.slice(split)));
    }
  }
  const escape = value => value.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll("'", '&#39;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
  return preloadStart + [...urls].map(url => '\n  <link rel="modulepreload" href="' + escape(url) + '">').join('') + '\n  ' + preloadEnd;
}

/** Version dependencies before their importers so immutable URLs identify final bytes. */
export function fingerprintAssets(input) {
  const files = new Map([...input].map(([name, bytes]) => [name, Buffer.from(bytes)]));
  const visiting = new Set(), done = new Set();
  function visit(name) {
    if (done.has(name)) return;
    if (visiting.has(name)) throw new Error(`Cyclic module dependency: ${[...visiting, name].join(' -> ')}`);
    const bytes = files.get(name);
    if (!bytes) throw new Error(`Missing module: ${name}`);
    visiting.add(name);
    let source = bytes.toString('utf8');
    const edits = [];
    for (const item of moduleImports(source)) {
      const specifier = item.specifier;
      if (!specifier.startsWith('.') && !specifier.startsWith('/')) throw new Error(`Only local browser modules are supported: ${name}: ${specifier}`);
      const dependency = target(name, specifier);
      if (!dependency?.endsWith('.js')) throw new Error(`Expected a JavaScript module: ${name}: ${specifier}`);
      visit(dependency);
      // Rewritten contents must remain valid inside either JavaScript quote delimiter.
      const value = JSON.stringify(versioned(specifier, digest(files.get(dependency)))).slice(1, -1).replaceAll("'", "\\'");
      edits.push({...item, value});
    }
    for (const edit of edits.sort((a, b) => b.start - a.start)) source = source.slice(0, edit.start) + edit.value + source.slice(edit.end);
    files.set(name, Buffer.from(source));
    visiting.delete(name); done.add(name);
  }
  for (const name of files.keys()) if (name.endsWith('.js')) visit(name);
  for (const [name, bytes] of files) if (name.endsWith('.html')) {
    // The service worker caches only exact fallback URLs without queries.
    if (name === 'offline.html') continue;
    const preload = clearModulePreloads(bytes.toString('utf8'));
    let source = preload.source.replace(/\b(?:src|href)\s*=\s*(["'])([^"']+\.(?:js|css)(?:\?[^"'#]*)?(?:#[^"']*)?)\1/g, (match, quote, specifier) => {
      const dependency = target(name, specifier);
      if (dependency === null) return match;
      if (!files.has(dependency)) throw new Error(`Missing HTML asset: ${name}: ${specifier}`);
      return match.replace(quote + specifier + quote, quote + versioned(specifier, digest(files.get(dependency))) + quote);
    });
    if (preload.enabled) source = source.replace(preloadStart + preloadEnd, modulePreloadMarkup(name, source, files));
    files.set(name, Buffer.from(source));
  }
  // Keep the revision tied to the exact, query-free fallback resources.
  if (files.has('service-worker.js')) {
    const offline = ['offline.html', 'pwa/offline.css', 'pwa/icon-192.png'];
    for (const name of offline) if (!files.has(name)) throw new Error(`Missing offline asset: ${name}`);
    const revision = digest(Buffer.concat(offline.map(name => files.get(name))));
    const source = files.get('service-worker.js').toString('utf8');
    if (!/PREFIX \+ '[a-f0-9]{16}'/.test(source)) throw new Error('Unknown offline cache revision declaration');
    files.set('service-worker.js', Buffer.from(source.replace(/PREFIX \+ '[a-f0-9]{16}'/, `PREFIX + '${revision}'`)));
  }
  return files;
}

async function main() {
  if (process.argv.slice(2).some(arg => arg !== '--check') || process.argv.length > 3) throw new Error('Usage: node scripts/update-assets.mjs [--check]');
  const repository = await realpath(resolve(dirname(fileURLToPath(import.meta.url)), '..'));
  const root = await realpath(resolve(repository, 'src/main/resources/static'));
  if (!root.startsWith(repository + '/')) throw new Error('Static assets must remain inside the repository');
  const files = new Map();
  async function read(directory, prefix = '') {
    for (const entry of await readdir(directory, {withFileTypes: true})) {
      const name = prefix + entry.name, path = resolve(directory, entry.name);
      if (entry.isSymbolicLink()) throw new Error(`Symlinked static asset: ${name}`);
      if (entry.isDirectory()) await read(path, name + '/');
      else if (entry.isFile()) files.set(name, await readFile(path));
    }
  }
  await read(root);
  const updated = fingerprintAssets(files);
  const changed = [...updated].filter(([name, bytes]) => !bytes.equals(files.get(name)));
  if (process.argv.includes('--check')) {
    if (changed.length) throw new Error('Stale asset fingerprints; run node scripts/update-assets.mjs: ' + changed.map(([name]) => name).join(', '));
    console.log('All module dependencies, HTML assets and offline revision have current content fingerprints.');
  } else {
    for (const [name, bytes] of changed) {
      const path = resolve(root, name);
      if (!path.startsWith(root + '/') || (await lstat(path)).isSymbolicLink() || !(await realpath(path)).startsWith(root + '/')) throw new Error('Asset destination outside static root');
      await writeFile(path, bytes);
    }
    console.log(`Updated ${changed.length} asset fingerprints.`);
  }
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main().catch(error => {console.error(error.message); process.exitCode = 1;});
