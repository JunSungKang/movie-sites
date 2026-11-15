import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {fingerprintAssets} from './update-assets.mjs';

const hash = text => createHash('sha256').update(text).digest('hex').slice(0, 16);
const fixture = entries => new Map(entries.map(([name, text]) => [name, Buffer.from(text)]));

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
