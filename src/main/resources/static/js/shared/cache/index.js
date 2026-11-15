export {DirectoryCache, PageHistory} from './directory-cache.js?v=cb07f2467980d05a';
// Decorates only catalog reads. It preserves the remaining server freshness and bounds duplicate work.
export class CachedCatalogApi {
  constructor(source, cache, scope = '') { this.source = source; this.cache = cache; this.scope = scope; this.requests = new Map(); }
  key(path, cursor) { return `${this.scope}${path}\0${cursor ?? ''}`; }
  invalidate(path) { this.cache.invalidatePath(`${this.scope}${path}\0`); }
  cancel() { for (const request of this.requests.values()) request.controller.abort(); this.requests.clear(); }
  get(path, cursor, force, preparing) {
    const key = this.key(path, cursor);
    if (!force) { const saved = this.cache.get(key); if (saved) return Promise.resolve({data: saved, cached: true}); }
    for (const [other, request] of this.requests) if (other !== key || force) { request.controller.abort(); this.requests.delete(other); }
    if (this.requests.has(key)) return this.requests.get(key).promise;
    const controller = new AbortController(); const request = {controller, promise: null};
    request.promise = this.source.get(path, cursor, force, controller.signal, preparing).then(data => {
      if (controller.signal.aborted) { const error = new Error('Catalog request was cancelled'); error.name = 'AbortError'; throw error; }
      this.cache.put(key, data, data.cacheTtlMs ?? this.cache.ttlMs); return {data, cached: false};
    }).finally(() => { if (this.requests.get(key) === request) this.requests.delete(key); });
    this.requests.set(key, request); return request.promise;
  }
}
