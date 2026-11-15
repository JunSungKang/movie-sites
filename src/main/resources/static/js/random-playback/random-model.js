import {t} from '../shared/i18n/index.js?v=b21fadcdaa06a04e';
import {MAX_QUEUE} from '../shared/api/index.js?v=d58f5843b24a8ea7';
export const RANDOM_TIMES = [10, 20, 30, 40, 50];
export function transitionRandom(state, event) {
  const next = {idle: {START: 'preparing'}, preparing: {START: 'preparing', COMPLETE: 'ready', FAIL: 'failed', CANCEL: 'idle'}, ready: {START: 'preparing'}, failed: {START: 'preparing', CANCEL: 'idle'}}[state]?.[event];
  if (!next) throw new Error('Invalid random playback transition: ' + state + ' / ' + event); return next;
}
export function validateRandomPage(data, request) {
  if (typeof data.query !== 'string' || !data.query || data.path !== request.path || data.minutes !== request.minutes) throw new Error(t('랜덤 목록을 확인하지 못했어요. 다시 골라 주세요.'));
  if (data.state === 'preparing' || data.state === 'failed') return {millis: 0};
  if (data.state !== 'ready' || !Array.isArray(data.entries) || data.entries.length > MAX_QUEUE) throw new Error(t('랜덤 목록을 확인하지 못했어요.'));
  let millis = 0; const paths = new Set();
  for (const entry of data.entries) {
    if (typeof entry.path !== 'string' || entry.path.length > 4096 || entry.path.startsWith('/') || /[\\\0]/.test(entry.path) || entry.path.split('/').some(part => !part || part === '.' || part === '..') || request.path && !entry.path.startsWith(request.path + '/') || paths.has(entry.path) || typeof entry.name !== 'string' || !entry.name || entry.name.length > 4096 || !Number.isFinite(entry.duration) || entry.duration <= 0) throw new Error(t('랜덤 목록의 영상 범위를 확인하지 못했어요.'));
    if (typeof entry.version !== 'string' || !entry.version || entry.version.length > 256) throw new Error(t('선택한 영상의 상태를 확인하지 못했어요.'));
    paths.add(entry.path); millis += Math.ceil(entry.duration * 1000);
  }
  if (millis > request.minutes * 60000) throw new Error(t('선택한 시간에 맞는 목록을 다시 구성해 주세요.'));
  if (!data.entries.length) throw new Error(data.message || t('이 시간에 맞는 영상을 찾지 못했어요. 다른 시간으로 골라 주세요.'));
  return {millis};
}
