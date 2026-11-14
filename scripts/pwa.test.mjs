import {setLocale} from '../src/main/resources/static/js/i18n.js';
setLocale('ko');
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import {initPwa} from '../src/main/resources/static/js/pwa.js';

const flush = () => new Promise(resolve => setImmediate(resolve));
function browser({secure = true, ios = false, standalone = false, registrationFails = false} = {}) {
  class Element extends EventTarget {
    hidden = true; disabled = false; open = false; textContent = '';
    showModal() { this.open = true; }
    close() { this.open = false; }
  }
  const elements = new Map(['install-app', 'install-help', 'install-steps', 'close-install-help'].map(id => [id, new Element()]));
  const display = Object.assign(new EventTarget(), {matches: standalone});
  const window = Object.assign(new EventTarget(), {isSecureContext: secure, matchMedia: () => display});
  const registrations = [];
  const navigator = {userAgent: ios ? 'iPhone Safari' : 'Chrome', platform: ios ? 'iPhone' : 'Linux', maxTouchPoints: ios ? 5 : 0,
    serviceWorker: {register: (...args) => { registrations.push(args); return registrationFails ? Promise.reject(new Error('offline')) : Promise.resolve(); }}};
  initPwa({window, document: {getElementById: id => elements.get(id)}, navigator});
  const click = id => elements.get(id).dispatchEvent(new Event('click'));
  const native = (outcome = 'accepted') => {
    let prompted = 0;
    const event = Object.assign(new Event('beforeinstallprompt', {cancelable: true}), {prompt: async () => {prompted++;}, userChoice: Promise.resolve({outcome})});
    window.dispatchEvent(event);
    return {event, prompted: () => prompted};
  };
  return {window, elements, registrations, display, click, native};
}
test('native installation is user initiated, consumes one prompt and hides after acceptance', async () => {
  const b = browser();
  assert.equal(b.elements.get('install-app').hidden, true);
  assert.deepEqual(b.registrations, [['/service-worker.js', {scope: '/', updateViaCache: 'none'}]]);
  const pending = b.native();
  assert.equal(pending.event.defaultPrevented, true);
  assert.equal(pending.prompted(), 0);
  assert.equal(b.elements.get('install-app').hidden, false);
  b.click('install-app'); await flush();
  assert.equal(pending.prompted(), 1);
  assert.equal(b.elements.get('install-app').hidden, true);
  assert.equal(b.elements.get('install-app').disabled, false);
});
test('dismissed prompts are not reused; a later native event can enable installation again', async () => {
  const b = browser(); const pending = b.native('dismissed');
  b.click('install-app'); await flush();
  assert.equal(pending.prompted(), 1);
  assert.equal(b.elements.get('install-app').hidden, true);
  b.native(); assert.equal(b.elements.get('install-app').hidden, false);
});
test('iPhone gets readable manual guidance and installing closes it without affecting playback', async () => {
  const b = browser({ios: true, registrationFails: true}); await flush();
  assert.equal(b.elements.get('install-app').hidden, false);
  b.click('install-app');
  assert.equal(b.elements.get('install-help').open, true);
  assert.match(b.elements.get('install-steps').textContent, /Safari.*홈 화면에 추가/);
  b.click('close-install-help'); assert.equal(b.elements.get('install-help').open, false);
  b.click('install-app'); b.window.dispatchEvent(new Event('appinstalled'));
  assert.equal(b.elements.get('install-help').open, false);
  assert.equal(b.elements.get('install-app').hidden, true);
});
test('insecure origins never register a worker or promote installation; standalone hides the button', () => {
  const insecure = browser({secure: false, ios: true});
  insecure.native(); assert.equal(insecure.registrations.length, 0); assert.equal(insecure.elements.get('install-app').hidden, true);
  const installed = browser({standalone: true, ios: true}); assert.equal(installed.elements.get('install-app').hidden, true);
  const b = browser({ios: true}); b.display.matches = true; b.display.dispatchEvent(new Event('change'));
  assert.equal(b.elements.get('install-app').hidden, true);
});

function worker({offline = false} = {}) {
  const events = new Map(), added = [], removed = [], fetched = [];
  const fallback = new Response('generic offline page');
  const self = {location: {origin: 'https://media.example.org'}, clients: {claim: async () => {}}, addEventListener: (type, handler) => events.set(type, handler)};
  const caches = {open: async () => ({addAll: async paths => added.push(...paths)}), keys: async () => ['unrelated-app-cache', 'moviesites-offline-old'], delete: async key => removed.push(key), match: async (path, options) => {assert.ok(['/offline.html', '/pwa/offline.css', '/pwa/icon-192.png'].includes(path)); assert.match(options.cacheName, /^moviesites-offline-/); return fallback.clone();}};
  const context = vm.createContext({self, caches, URL, Response, fetch: async request => {fetched.push(request.url); if (offline) throw new TypeError('offline'); return new Response('live');}});
  vm.runInContext(readFileSync('src/main/resources/static/service-worker.js', 'utf8'), context);
  function request(path, {method = 'GET', mode = 'navigate', range = false} = {}) {
    let response;
    events.get('fetch')({request: {url: new URL(path, self.location.origin).href, method, mode, headers: new Headers(range ? {Range: 'bytes=0-31'} : {})}, respondWith: value => {response = value;}});
    return response;
  }
  async function lifecycle(name) {
    let pending; events.get(name)({waitUntil: value => {pending = value;}}); await pending;
  }
  return {added, removed, fetched, request, lifecycle};
}
test('worker installs only a fixed tiny generic fallback, and cleans only its own older caches', async () => {
  const w = worker(); await w.lifecycle('install'); await w.lifecycle('activate');
  assert.deepEqual(w.added, ['/offline.html', '/pwa/offline.css', '/pwa/icon-192.png']);
  assert.deepEqual(w.removed, ['moviesites-offline-old']);
});
test('worker never intercepts catalog/API/media/range/cross-origin or non-GET requests', () => {
  const w = worker({offline: true});
  for (const path of ['/api/browse?path=private', '/api/config', '/api/media/stream?token=secret', '/api/media/thumbnail?path=private', '/api/media/folder-cover', '/themes/catalog.json', 'https://other.example.org/']) {
    assert.equal(w.request(path), undefined, path);
  }
  assert.equal(w.request('/', {range: true}), undefined);
  assert.equal(w.request('/', {method: 'POST'}), undefined);
  assert.equal(w.request('/', {mode: 'cors'}), undefined);
  assert.deepEqual(w.fetched, []);
});
test('navigation always uses network without caching titles/paths and fails to a generic offline page', async () => {
  const online = worker(); assert.equal(await (await online.request('/?path=private')).text(), 'live');
  assert.deepEqual(online.added, []);
  const offline = worker({offline: true}); assert.equal(await (await offline.request('/?path=private')).text(), 'generic offline page');
  assert.equal(await (await offline.request('/about')).text(), 'generic offline page');
});

test('offline fallback styles and icon are available without navigation, with no query/range expansion', async () => {
  const w = worker({offline: true});
  for (const path of ['/pwa/offline.css', '/pwa/icon-192.png', '/offline.html']) {
    assert.equal(await (await w.request(path, {mode: 'cors'})).text(), 'generic offline page');
    assert.equal(w.request(path + '?private=title', {mode: 'cors'}), undefined);
    assert.equal(w.request(path, {mode: 'cors', range: true}), undefined);
  }
  assert.deepEqual(w.fetched, []);
});
