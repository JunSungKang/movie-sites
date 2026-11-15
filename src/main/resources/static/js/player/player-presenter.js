import {t} from '../shared/i18n/index.js?v=b21fadcdaa06a04e';
import {PlaybackSession, transitionPlayer} from './player-model.js?v=816ad27637f18775';
export function createPlayerPresenter({view, media, fullscreen, api, browser, onChanged, onClose, onStart, onInvalidate, onRetry, toast}) {
  let current = null, timer, selection, label = '', selectedVersions = new Map(), phase = 'closed';
  const transition = event => { phase = transitionPlayer(phase, event); };
  const session = new PlaybackSession(show, () => { close(); toast(t('모든 영상의 재생이 끝났습니다.')); });
  function reset() { browser.cancel(timer); current?.controller.abort(); current = null; media.reset(); }
  function capture() { if (!view.open) selection = browser.captureSelection(); }
  function start(entries, sessionLabel = '', keepFullscreen = false) {
    if (!entries.length) return;
    onStart(); label = sessionLabel; selectedVersions = new Map(entries.filter(entry => typeof entry.version === 'string').map(entry => [entry.path, entry.version])); capture();
    if (session.start(entries) && !keepFullscreen) fullscreen.start();
  }
  async function show(entry, token) {
    reset(); transition('OPEN'); current = {entry, token, controller: browser.createAbortController(), loaded: false};
    const position = label ? `${session.index + 1} / ${session.items.length} · ${label}` : session.items.length > 1 ? t`${session.index + 1} / ${session.items.length} · 순서대로 재생` : t('영상 재생');
    view.show(entry, {position, hasPrevious: session.hasPrevious, hasNext: session.hasNext}); onChanged(); await poll(token, false);
  }
  function failure(message, options) { if (phase !== 'closed' && phase !== 'error') transition('FAIL'); view.failure(message, options); }
  async function resume(token) {
    if (!session.current(token) || !current?.loaded) return;
    try { await media.play(); if (session.current(token)) view.resumed(); }
    catch (error) {
      if (!session.current(token)) return;
      if (error.name === 'NotAllowedError' || error.name === 'AbortError') {
        if (phase === 'ready' || phase === 'playing') transition('PAUSE');
        view.awaitResume(t`${session.index + 1} / ${session.items.length} · 재생 시작 버튼을 눌러 주세요.`);
      } else failure(t('영상을 재생하지 못했습니다. 다시 확인하거나 다음 영상을 선택해 주세요.'));
    }
  }
  async function poll(token, retry) {
    if (!session.current(token) || current?.token !== token) return; const player = current;
    try {
      const info = await api.inspect({path: player.entry.path, play: 'true', ...(retry ? {retry: 'true'} : {})}, {signal: player.controller.signal});
      if (!session.current(token) || current !== player) return;
      if (info.state === 'ready') {
        if (selectedVersions.has(player.entry.path) && selectedVersions.get(player.entry.path) !== info.version) { failure(t('영상을 고른 뒤 파일이 바뀌었어요. 다음 영상을 선택하거나 닫고 목록을 다시 골라 주세요.'), {retry: false}); return; }
        player.loaded = true; transition('READY'); media.setSource(api.streamUrl({path: player.entry.path, v: info.version, token: info.streamToken})); view.ready(); await resume(token); return;
      }
      if (info.state === 'failed') { failure(info.message || t('영상을 준비하지 못했습니다. 다시 확인하거나 다음 영상을 선택해 주세요.')); return; }
      view.progress(info.state === 'preparing' ? t('브라우저에서 재생할 영상을 준비하고 있습니다.') : t('영상 정보를 확인하고 있습니다.')); timer = browser.schedule(() => poll(token, false), 1800);
    } catch (error) {
      if (!session.current(token) || current !== player || error.name === 'AbortError') return;
      if (error.status === 429) { timer = browser.schedule(() => poll(token, false), 2500); return; }
      failure((error.status === 404 ? t('영상 파일을 찾을 수 없습니다.') : error.message || t('영상을 불러오지 못했습니다.')) + t(' 다시 확인하거나 다음 영상을 선택해 주세요.'));
      if (error.status === 404 || error.status === 409) onInvalidate();
    }
  }
  function close(closeDialog = true) {
    onClose(); selectedVersions.clear(); fullscreen.close(); session.close(); reset(); if (phase !== 'closed') transition('CLOSE');
    if (closeDialog) view.close(); onChanged(); if (selection) { browser.restoreSelection(selection); selection = null; }
  }
  function retry() { if (onRetry()) return; if (current) { if (phase === 'error') transition('RETRY'); view.retrying(); poll(current.token, true); } }
  view.bind({previous: () => session.previous(), next: () => session.next(), resume: () => { fullscreen.request(); resume(session.token); }, fullscreen: () => fullscreen.request(),
    playing() { fullscreen.playing(); if (current?.loaded && (phase === 'ready' || phase === 'paused')) transition('PLAY'); },
    paused() { if (current?.loaded && (phase === 'playing' || phase === 'ready')) transition('PAUSE'); },
    ended() { if (current?.loaded && media.ended) session.ended(current.token); },
    error() { if (current?.loaded && media.error) { failure(t('재생 중 연결이 끊겼거나 영상이 변경되었습니다. 다시 확인하거나 다음 영상을 선택해 주세요.')); onInvalidate(); } }, close, retry});
  return {
    start, close, failure,
    prepareRandom(options) { session.close(); reset(); label = ''; selectedVersions.clear(); capture(); transition('OPEN'); view.random(options); fullscreen.start(); onChanged(); },
    progress: message => view.progress(message),
    get session() { return session; },
    get current() { return current; },
    get snapshot() { return {phase, active: session.active, currentPath: session.active ? session.items[session.index].path : null}; }
  };
}
