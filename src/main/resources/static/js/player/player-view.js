import {t} from '../shared/i18n/index.js?v=b21fadcdaa06a04e';
import {createDom, title, size} from '../shared/ui/index.js?v=50e99f871f8b453e';
export function createPlayerView(document) {
  const {$} = createDom(document);
  return {
    bind(actions) {
      $('player-previous').addEventListener('click', actions.previous); $('player-next').addEventListener('click', actions.next);
      $('player-resume').addEventListener('click', actions.resume); $('player-fullscreen-button').addEventListener('click', actions.fullscreen);
      $('video-player').addEventListener('playing', actions.playing); $('video-player').addEventListener('pause', actions.paused); $('video-player').addEventListener('ended', actions.ended); $('video-player').addEventListener('error', actions.error);
      $('player-dialog').addEventListener('cancel', event => { event.preventDefault(); actions.close(); }); $('close-player').addEventListener('click', () => actions.close());
      $('player-dialog').addEventListener('close', () => { if (!$('player-dialog').open) actions.close(false); }); $('retry-player').addEventListener('click', actions.retry);
    },
    get open() { return $('player-dialog').open; },
    show(entry, {position, hasPrevious, hasNext}) {
      $('retry-player').textContent = t('다시 확인'); $('player-title').textContent = title(entry.name); $('player-folder').textContent = entry.path.split('/').slice(0, -1).join(' / '); $('player-file').textContent = entry.name + (entry.size ? ' · ' + size(entry.size) : '');
      $('video-player').hidden = true; $('player-preparing').hidden = false; $('player-progress').hidden = false; $('retry-player').hidden = true; $('player-resume').hidden = true; $('player-state').textContent = t('영상을 확인하고 있습니다.');
      $('player-position').textContent = position; $('player-previous').disabled = !hasPrevious; $('player-next').disabled = !hasNext;
      if (!$('player-dialog').open) $('player-dialog').showModal();
    },
    random({minutes, scope}) {
      $('player-title').textContent = t`최대 ${minutes}분 랜덤 재생`; $('player-folder').textContent = scope; $('player-file').textContent = t('영상은 중간에 자르지 않고 끝까지 재생해요.');
      $('video-player').hidden = true; $('player-preparing').hidden = false; $('player-progress').hidden = false; $('retry-player').hidden = true; $('player-resume').hidden = true;
      $('player-state').textContent = t('함께 볼 영상을 고르고 있어요.'); $('player-position').textContent = t('랜덤 목록 준비 중'); $('player-previous').disabled = true; $('player-next').disabled = true;
      if (!$('player-dialog').open) $('player-dialog').showModal();
    },
    progress: message => { $('player-state').textContent = message; },
    ready() { $('video-player').hidden = false; $('player-preparing').hidden = true; },
    resumed: () => { $('player-resume').hidden = true; },
    awaitResume(position) { $('player-resume').hidden = false; $('player-position').textContent = position; },
    failure(message, {retry = true, random = false} = {}) { $('video-player').hidden = true; $('player-preparing').hidden = false; $('player-state').textContent = message; $('player-progress').hidden = true; $('retry-player').hidden = !retry; $('player-resume').hidden = true; if (random) $('retry-player').textContent = t('다시 고르기'); },
    retrying() { $('retry-player').hidden = true; $('player-progress').hidden = false; $('player-state').textContent = t('다시 확인하고 있습니다.'); },
    fullscreen({supported, active, expanded}) { $('player-fullscreen-button').hidden = !supported || active; $('player-dialog').dataset.expanded = String(expanded); },
    close: () => { if ($('player-dialog').open) $('player-dialog').close(); }
  };
}
