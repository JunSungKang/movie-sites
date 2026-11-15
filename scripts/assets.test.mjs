import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {fingerprintAssets} from './update-assets.mjs';

const hash = text => createHash('sha256').update(text).digest('hex').slice(0, 16);
const fixture = entries => new Map(entries.map(([name, text]) => [name, Buffer.from(text)]));
const preloadBlock = '<!-- modulepreload:start --><!-- modulepreload:end -->';
const preloads = bytes => [...bytes.toString().matchAll(/<link rel="modulepreload" href="([^"]+)">/g)].map(match => match[1]);

test('a leaf change propagates through re-exports and importers to the HTML URL', () => {
  const source = fixture([
    ['js/leaf.js', 'export const value = 1;'],
    ['js/feature/index.js', 'export {value} from "../leaf.js?v=old";'],
    ['js/app.js', 'import {value} from "./feature/index.js?v=old";'],
    ['index.html', '<script type="module" src="/js/app.js?v=old"></script>'],
  ]);
  const first = fingerprintAssets(source);
  assert.match(first.get('js/feature/index.js').toString(), new RegExp('v=' + hash(source.get('js/leaf.js'))));
  assert.match(first.get('js/app.js').toString(), new RegExp('v=' + hash(first.get('js/feature/index.js'))));
  assert.match(first.get('index.html').toString(), new RegExp('v=' + hash(first.get('js/app.js'))));
  assert.deepEqual(fingerprintAssets(first), first, 'updating current URLs must be idempotent');
  source.set('js/leaf.js', Buffer.from('export const value = 2;'));
  const second = fingerprintAssets(source);
  assert.notDeepEqual(second.get('index.html'), first.get('index.html'));
});

test('fingerprinting rejects missing, cyclic and escaping module references', () => {
  assert.throws(() => fingerprintAssets(fixture([['js/app.js', 'import "./missing.js";']])), /Missing module/);
  assert.throws(() => fingerprintAssets(fixture([['a.js', 'import "./b.js";'], ['b.js', 'import "./a.js";']])), /Cyclic module/);
  assert.throws(() => fingerprintAssets(fixture([['a.js', 'import "../outside.js";']])), /escapes static root/);
});

test('HTML dependencies preserve queries/fragments and offline revision reflects exact fallback bytes', () => {
  const source = fixture([
    ['offline.html', '<link href="/pwa/offline.css"><p>Reconnect</p>'], ['pwa/offline.css', 'body{}'], ['pwa/icon-192.png', 'fixture image bytes'],
    ['service-worker.js', "const CACHE = PREFIX + '0000000000000000';"],
    ['theme.css', 'body{color:red}'], ['index.html', '<link href="theme.css?mode=wide&v=old#main">'],
  ]);
  const result = fingerprintAssets(source);
  assert.match(result.get('index.html').toString(), new RegExp('mode=wide&v=' + hash(source.get('theme.css')) + '#main'));
  const expected = hash(Buffer.concat(['offline.html', 'pwa/offline.css', 'pwa/icon-192.png'].map(name => result.get(name))));
  assert.match(result.get('service-worker.js').toString(), new RegExp(expected));
  assert.equal(result.get('offline.html').toString(), '<link href="/pwa/offline.css"><p>Reconnect</p>');
  assert.deepEqual(fingerprintAssets(result), result);
});

test('marked HTML derives and deduplicates only the module entries first static dependencies', () => {
  const source = fixture([
    ['js/deep.js', 'export const value=1;'],
    ['js/shared.js', 'export {value} from "./deep.js";'],
    ['js/feature.js', 'export const feature=1;'], ['js/lazy.js', 'export const lazy=1;'],
    ['js/pwa.js', 'import "./shared.js";'],
    ['js/app.js', 'import "./shared.js"; import "./feature.js"; const later=()=>import("./lazy.js");'],
    ['index.html', '<script type="module" src="/js/pwa.js"></script><script src="/js/app.js" type="module"></script>'+preloadBlock],
    ['plain.html', '<script type="module" src="/js/app.js"></script>'],
  ]);
  const result = fingerprintAssets(source);
  assert.deepEqual(preloads(result.get('index.html')), ['/js/shared.js?v='+hash(result.get('js/shared.js')), '/js/feature.js?v='+hash(result.get('js/feature.js'))]);
  assert.equal(preloads(result.get('plain.html')).length, 0, 'unmarked pages keep their existing discovery behavior');
  assert.deepEqual(fingerprintAssets(result), result);
  source.set('js/deep.js', Buffer.from('export const value=2;'));
  const changed = fingerprintAssets(source);
  assert.notEqual(preloads(changed.get('index.html'))[0], preloads(result.get('index.html'))[0], 'a transitive leaf changes its directly preloaded importer fingerprint');
});

test('adding and removing static imports regenerates hints even after a stale hinted file is deleted', () => {
  let source = fixture([
    ['js/first.js', 'export const first=1;'], ['js/second.js', 'export const second=2;'],
    ['js/app.js', 'import "./first.js";'],
    ['index.html', '<script type="module" src="/js/app.js"></script>'+preloadBlock],
  ]);
  source = fingerprintAssets(source); assert.equal(preloads(source.get('index.html')).length, 1);
  source.set('js/app.js', Buffer.from('import "./first.js"; import "./second.js";'));
  source = fingerprintAssets(source); assert.equal(preloads(source.get('index.html')).length, 2);
  source.set('js/app.js', Buffer.from('import "./second.js";')); source.delete('js/first.js');
  const result = fingerprintAssets(source);
  assert.deepEqual(preloads(result.get('index.html')), ['/js/second.js?v='+hash(result.get('js/second.js'))]);
  assert.deepEqual(fingerprintAssets(result), result);
});

test('preload URLs preserve exact query and fragment identities while escaping HTML attributes', () => {
  const first='./query.js?mode=wide&label=two#part"quoted', second='/js/query.js?mode=narrow#other';
  const source = fixture([
    ['js/query.js', 'export const value=1;'],
    ['js/app.js', 'import '+JSON.stringify(first)+'; import '+JSON.stringify('/js/'+first.slice(2))+'; import '+JSON.stringify(second)+';'],
    ['index.html', '<script src="/js/app.js" type=\'module\'></script>'+preloadBlock],
  ]);
  const result = fingerprintAssets(source), urls=preloads(result.get('index.html'));
  assert.equal(urls.length, 2, 'relative and absolute imports deduplicate by the complete resolved URL');
  assert.equal(urls[0], '/js/query.js?mode=wide&amp;label=two&amp;v='+hash(result.get('js/query.js'))+'#part&quot;quoted');
  assert.equal(urls[1], '/js/query.js?mode=narrow&amp;v='+hash(result.get('js/query.js'))+'#other');
  assert.deepEqual(fingerprintAssets(result), result);
});

test('modulepreload discovery ignores commented, inline, non-module and external script entries', () => {
  const source = fixture([
    ['js/dependency.js', 'export const value=1;'], ['js/ignored.js', 'import "./dependency.js";'],
    ['index.html', '<!-- <script type="module" src="/js/ignored.js"></script> --><script data-type="module" src="/js/ignored.js"></script><script type="module" data-src="/js/ignored.js"></script><script type="module">const ready=true;</script><script type="module" src="https://example.org/app.js"></script>'+preloadBlock],
  ]);
  assert.deepEqual(preloads(fingerprintAssets(source).get('index.html')), []);
});

test('marked pages reject missing entry modules and malformed generation markers', () => {
  assert.throws(() => fingerprintAssets(fixture([['index.html','<script type="module" src="/missing.js"></script>'+preloadBlock]])), /Missing HTML asset/);
  for (const invalid of ['<!-- modulepreload:start -->','<!-- modulepreload:end -->',preloadBlock+preloadBlock,'<!-- modulepreload:end --><!-- modulepreload:start -->']) {
    assert.throws(() => fingerprintAssets(fixture([['index.html',invalid]])), /paired modulepreload marker/);
  }
});
