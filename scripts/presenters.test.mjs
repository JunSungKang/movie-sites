import test from 'node:test';
import assert from 'node:assert/strict';
import {assetModule} from './frontend-test-support.mjs';
const {setLocale} = await assetModule('js/shared/i18n/index.js');
const {createCatalogPresenter} = await assetModule('js/catalog/catalog-presenter.js');
const {createPlayerPresenter} = await assetModule('js/player/player-presenter.js');
const {createRandomPresenter} = await assetModule('js/random-playback/random-presenter.js');
const {transitionPlayer} = await assetModule('js/player/player-model.js');
const {transitionRandom} = await assetModule('js/random-playback/random-model.js');
const {DirectoryCache, CachedCatalogApi, PageHistory} = await assetModule('js/shared/cache/index.js');
setLocale('en');
const noop = () => {};
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return {promise, resolve}; };

test('catalog presenter keeps only the latest navigation without any DOM implementation', async () => {
  const first = deferred(), second = deferred(), rendered = [], loading = [], source = {cancel: noop, invalidate: noop, get: path => (path === 'old' ? first : second).promise};
  const view = {bind: noop, configure: noop, begin: path => loading.push(path), render: value => rendered.push(value), failure: message => assert.fail(message), finish: noop, present: () => false, dispose: noop};
  const catalog = createCatalogPresenter({view, source, api: {}, browser: {previewExit: () => '/'}, history: new PageHistory(), onBeforeLoad: noop, onPlay: noop, onEnqueue: noop, onRendered: noop});
  catalog.configure({}, {}); const old = catalog.load('old'), current = catalog.load('current');
  second.resolve({data: {state: 'ready', entries: []}, cached: false}); await current;
  first.resolve({data: {state: 'ready', entries: [{kind: 'video', path: 'stale.mp4'}]}, cached: false}); await old;
  assert.deepEqual(loading, ['old', 'current']); assert.equal(rendered.length, 1); assert.equal(rendered[0].path, 'current'); assert.equal(catalog.snapshot.path, 'current');
});

test('player presenter acquires fullscreen in the initiating call and ignores a late aborted inspection', async () => {
  const inspection = deferred(), order = []; let signal, handlers;
  const view = {open: false, bind: actions => { handlers = actions; }, show: () => order.push('show'), close: noop};
  const media = {reset: () => order.push('reset'), setSource: () => assert.fail('late source'), play: () => assert.fail('late playback')};
  const player = createPlayerPresenter({view, media, fullscreen: {start: () => order.push('fullscreen'), close: noop}, api: {inspect: (_parameters, options) => { signal = options.signal; return inspection.promise; }}, browser: {cancel: noop, createAbortController: () => new AbortController(), captureSelection: () => ({}), restoreSelection: noop}, onChanged: noop, onClose: noop, onStart: noop, onInvalidate: noop, onRetry: () => false, toast: noop});
  player.start([{name: 'one.mp4', path: 'one.mp4', size: 1}]); assert.equal(order.at(-1), 'fullscreen'); assert.equal(player.snapshot.phase, 'preparing');
  handlers.close(); assert.equal(signal.aborted, true); inspection.resolve({state: 'ready', version: 'one'}); await Promise.resolve(); await Promise.resolve();
  assert.equal(player.current, null); assert.equal(player.snapshot.phase, 'closed');
});

test('random presenter binds cancellation to its query and never publishes a late result', async () => {
  const pending = deferred(), cancelled = []; let signal, prepared;
  const view = {bind: noop, clear: noop, scope: noop, render: () => assert.fail('late result')};
  const api = {random: async (_parameters, options) => { signal = options.signal; return pending.promise; }, cancelRandom: value => cancelled.push(value)};
  const random = createRandomPresenter({view, api, browser: {createAbortController: () => new AbortController()}, getPath: () => 'category', onPrepare: value => { prepared = value; }, onProgress: noop, onFailure: () => assert.fail('late failure'), onPlay: () => assert.fail('late playback'), onClose: noop});
  random.configure({}); const request = random.start(10); assert.equal(prepared.minutes, 10); random.cancel(); assert.equal(signal.aborted, true);
  pending.resolve({state: 'ready', path: 'category', minutes: 10, query: 'old', entries: []}); await request; assert.deepEqual(cancelled, []); assert.equal(random.snapshot.phase, 'idle');
});

test('catalog cache decorator shares in-flight work and never extends the server remaining freshness', async () => {
  let now = 0, calls = 0; const pending = deferred(); const cache = new DirectoryCache({clock: () => now});
  const source = {get: () => { calls++; return pending.promise; }}; const api = new CachedCatalogApi(source, cache, 'root\0instance\0');
  const first = api.get('folder', null, false, noop), duplicate = api.get('folder', null, false, noop); assert.equal(first, duplicate); assert.equal(calls, 1);
  pending.resolve({entries: [], cacheTtlMs: 1000}); await first; now = 500; assert.equal((await api.get('folder', null, false, noop)).cached, true); now = 1000;
  await api.get('folder', null, false, noop); assert.equal(calls, 2);
});

test('explicit state transitions reject impossible readiness and completion events', () => {
  assert.throws(() => transitionPlayer('closed', 'READY'), /Invalid player/);
  assert.equal(transitionPlayer(transitionPlayer('closed', 'OPEN'), 'READY'), 'ready');
  assert.throws(() => transitionRandom('idle', 'COMPLETE'), /Invalid random/);
  assert.equal(transitionRandom(transitionRandom('idle', 'START'), 'CANCEL'), 'idle');
});

test('catalog decorator rejects a late response after cancellation without refreshing its cache', async () => {
  const pending = deferred(), cache = new DirectoryCache();
  const api = new CachedCatalogApi({get: () => pending.promise}, cache, 'root\0');
  const request = api.get('old', null, false, noop); api.cancel(); pending.resolve({entries: [], cacheTtlMs: 120000});
  await assert.rejects(request, {name: 'AbortError'}); assert.equal(cache.entries.size, 0);
});
