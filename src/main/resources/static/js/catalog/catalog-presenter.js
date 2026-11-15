import {t} from '../shared/i18n/index.js?v=b21fadcdaa06a04e';
import {durationGroups, topicLabels} from './catalog-model.js?v=6776eecb4201b239';
export function createCatalogPresenter({view, source, api, browser, history, onBeforeLoad, onPlay, onEnqueue, onRendered}) {
  const state = {config: null, path: '', listing: null, generation: 0, metadata: new Map(), pending: [], workers: 0};
  function invalidate() { source.invalidate(state.path); }
  function navigate(path) { if (path !== state.path) browser.navigate(path); return load(path); }
  async function load(path, {force = false, keepPage = false} = {}) {
    if (!state.config) return;
    onBeforeLoad(path, force, path !== state.path); const generation = ++state.generation; state.pending = []; state.path = path; source.cancel();
    if (!keepPage) history.reset(); if (force) { invalidate(); state.metadata.clear(); }
    view.begin(path, browser.previewExit());
    try {
      const result = await source.get(path, history.current, force, progress => { if (state.path === path && generation === state.generation) view.preparing(progress); });
      if (generation !== state.generation) return;
      state.listing = result.data;
      const data = {...result.data, entries: result.data.entries.map(entry => entry.kind === 'folder' ? {...entry, coverUrl: api.coverUrl({path: entry.path, ...(entry.coverVersion ? {v: entry.coverVersion} : {})})} : {...entry, topicLabels: topicLabels(entry)})};
      view.render({data, groups: durationGroups(data.entries.filter(entry => entry.kind === 'video')), path, history, cached: result.cached}); onRendered();
    } catch (error) {
      if (generation !== state.generation || error.name === 'AbortError') return;
      if (error.status === 410) { invalidate(); view.failure(t('목록 조회 시간이 만료되었습니다. 새로고침하면 처음부터 다시 확인할 수 있습니다.')); }
      else view.failure(error.message || t('폴더를 불러오지 못했습니다.'));
    } finally { if (generation === state.generation) view.finish(); }
  }
  async function enrich(entry, generation) {
    const key = entry.path + '\0' + entry.modified + '\0' + entry.size; let info = state.metadata.get(key);
    for (let attempt = 0; attempt < 40; attempt++) {
      if (generation !== state.generation || !view.present(entry.path)) return;
      if (!info) { try { info = await api.inspect({path: entry.path}); } catch (error) { if (error.status === 429) { await browser.sleep(1800); continue; } return; } }
      if (generation !== state.generation || !view.present(entry.path)) return;
      view.enrich(entry.path, {...info, thumbnailUrl: info.thumbnailState === 'ready' ? api.thumbnailUrl({path: entry.path, v: info.thumbnailVersion || info.version}) : null});
      if (info.thumbnailState === 'ready' && !['queued', 'analyzing'].includes(info.state)) { state.metadata.delete(key); state.metadata.set(key, info); while (state.metadata.size > 200) state.metadata.delete(state.metadata.keys().next().value); return; }
      if (info.state === 'failed' || info.thumbnailState === 'unavailable') return;
      info = null; await browser.sleep(1500);
    }
  }
  function pump() { while (state.workers < 2 && state.pending.length) { const item = state.pending.shift(); if (item.generation !== state.generation || !view.present(item.entry.path)) continue; state.workers++; enrich(item.entry, item.generation).catch(() => {}).finally(() => { state.workers--; pump(); }); } }
  const presenter = {
    configure(config, copy) { state.config = config; view.configure(config, copy); }, load, navigate, invalidate,
    enqueuePage() { onEnqueue((state.listing?.entries || []).filter(entry => entry.kind === 'video')); },
    get snapshot() { return {path: state.path, listing: state.listing, generation: state.generation}; },
    dispose() { state.generation++; state.pending = []; source.cancel(); view.dispose(); }
  };
  view.bind({home: () => navigate(''), parent: () => navigate(state.path.includes('/') ? state.path.slice(0, state.path.lastIndexOf('/')) : ''), navigate,
    refresh: () => load(state.path, {force: true}), first: () => load(state.path), previous: () => { if (history.canPrevious) { history.previous(); load(state.path, {keepPage: true}); } }, next: () => { if (state.listing?.nextCursor) { history.next(state.listing.nextCursor); load(state.path, {keepPage: true}); } },
    play: entry => onPlay([entry]), enqueue: entry => onEnqueue([entry]), visible: entry => { state.pending.push({entry, generation: state.generation}); pump(); }});
  return presenter;
}
