import {t} from '../shared/i18n/index.js?v=b21fadcdaa06a04e';
import {duration, title, folderName} from '../shared/ui/index.js?v=50e99f871f8b453e';
import {waitForPreparation} from '../shared/api/index.js?v=c4556515510eebfb';
import {RANDOM_TIMES, transitionRandom, validateRandomPage} from './random-model.js?v=96b751e7262146cd';
export function createRandomPresenter({view, api, browser, getPath, onPrepare, onProgress, onFailure, onPlay, onClose}) {
  let configured = false, request = null, retry = null, playlist = null, phase = 'idle', labels = new Map();
  const refreshScopes = new Set(), transition = event => { phase = transitionRandom(phase, event); };
  const release = pending => { if (pending?.query) api.cancelRandom({path: pending.path, minutes: pending.minutes, query: pending.query}); };
  function cancel() { if (request) { const pending = request; request = null; pending.controller.abort(); release(pending); transition('CANCEL'); } }
  async function start(minutes) {
    if (!configured || !RANDOM_TIMES.includes(minutes)) return;
    cancel(); retry = null; playlist = null; view.clear(); transition('START');
    const pending = {path: getPath(), minutes, controller: browser.createAbortController(), query: null}; request = pending;
    onPrepare({minutes, scope: pending.path ? labels.get(folderName(pending.path)) || folderName(pending.path) : t('모든 카테고리')});
    const refresh = refreshScopes.has(pending.path);
    try {
      while (request === pending) {
        const data = await api.random({path: pending.path, minutes, ...(pending.query ? {query: pending.query} : refresh ? {refresh: 'true'} : {})}, {signal: pending.controller.signal});
        if (request !== pending) return;
        const {millis} = validateRandomPage(data, pending); pending.query = data.query;
        if (data.state === 'failed') throw new Error(data.message || t('영상을 고르지 못했어요. 잠시 후 다시 시도해 주세요.'));
        if (data.state === 'preparing') { onProgress(data.checked ? t`${data.checked}편의 길이를 확인하며 고르고 있어요.` : t`${data.videos || 0}편을 찾았어요. 카테고리를 확인하고 있어요.`); await waitForPreparation(pending.controller.signal); continue; }
        refreshScopes.delete(pending.path); playlist = {entries: data.entries, label: t`${minutes}분 랜덤 · 약 ${duration(millis / 1000)}`};
        view.render({entries: data.entries.map(entry => `${title(entry.name)} · ${duration(entry.duration)}`), summary: t`이번에 고른 ${data.entries.length}편 · 약 ${duration(millis / 1000)} · 목록 보기`, note: [data.sampled || data.limited ? t('많은 영상 중 일부를 무작위로 살펴 골랐어요.') : '', data.skipped ? t('확인할 수 없는 영상은 제외했어요.') : '', t('저장한 재생 대기열은 그대로 유지해요.')].filter(Boolean).join(' ')});
        request = null; transition('COMPLETE'); release(pending); onPlay(playlist.entries, playlist.label, true); return;
      }
    } catch (error) {
      if (request !== pending || error.name === 'AbortError') return;
      request = null; release(pending); retry = {path: pending.path, minutes}; transition('FAIL'); onFailure(error.message || t('영상을 고르지 못했어요. 다시 시도해 주세요.'), {random: true});
    }
  }
  view.bind({start, replay: () => { if (playlist) onPlay(playlist.entries, playlist.label); }}, RANDOM_TIMES);
  return {
    configure(config) { configured = true; labels = new Map(Object.entries(config.folderLabels || {})); }, start, cancel,
    clearRetry() { retry = null; if (phase === 'failed') transition('CANCEL'); },
    retry() { if (!retry) return false; const saved = retry; if (saved.path === getPath()) start(saved.minutes); return true; },
    navigation(path, force, changed) {
      if (request || retry) onClose();
      if (changed || force) { playlist = null; view.clear(); }
      if (force) { refreshScopes.add(path); while (refreshScopes.size > 20) refreshScopes.delete(refreshScopes.values().next().value); }
      const label = labels.get(folderName(path)) || folderName(path); view.scope(path ? t`${label} 안의 영상만 무작위로 골라드려요.` : t('모든 카테고리의 영상에서 무작위로 골라드려요.'), configured, RANDOM_TIMES);
    },
    get snapshot() { return {phase, path: request?.path, minutes: request?.minutes, hasRetry: Boolean(retry)}; }
  };
}
