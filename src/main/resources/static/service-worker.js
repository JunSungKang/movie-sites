/* A fixed generic fallback only. All catalog, image and video traffic uses normal HTTP. */
const PREFIX = 'moviesites-offline-';
const CACHE = PREFIX + 'd49960520ed6cd1a';
const OFFLINE = '/offline.html';
const ASSETS = [OFFLINE, '/pwa/offline.css', '/pwa/icon-192.png'];
const PAGES = new Set(['/', '/index.html', '/about']);

self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(ASSETS)));
});
self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys.filter(key => key.startsWith(PREFIX) && key !== CACHE).map(key => caches.delete(key)));
    await self.clients.claim();
  })());
});
self.addEventListener('fetch', event => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== 'GET' || request.headers.has('Range') || url.origin !== self.location.origin) return;
  // Only the exact packaged fallback URLs may be served from this bounded cache.
  // Never match a private query or let a general asset rule grow the offline cache.
  if (!url.search && ASSETS.includes(url.pathname)) {
    event.respondWith(caches.match(url.pathname, {cacheName: CACHE}).then(cached => cached || fetch(request)));
    return;
  }
  if (request.mode !== 'navigate' || !PAGES.has(url.pathname)) return;
  // Never cache navigation responses or their queries (which may name a private folder).
  event.respondWith(fetch(request).catch(async () => (await caches.match(OFFLINE, {cacheName: CACHE})) || Response.error()));
});
