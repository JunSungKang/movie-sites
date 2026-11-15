import {t} from '../i18n/index.js?v=b21fadcdaa06a04e';
export {waitForPreparation} from './wait.js?v=863d24cca355772d';
export const MAX_QUEUE = 200;
export function sanitizeQueue(entries) {
  const seen = new Set();
  return entries.filter(entry => entry && typeof entry.path === 'string' && entry.path.length > 0 && entry.path.length <= 4096
    && !entry.path.startsWith('/') && !/[\\\0]/.test(entry.path) && entry.path.split('/').every(part => part && part !== '.' && part !== '..')
    && typeof entry.name === 'string' && entry.name.length > 0 && entry.name.length <= 4096)
    .filter(entry => !seen.has(entry.path) && seen.add(entry.path)).slice(0, MAX_QUEUE)
    .map(({path, name, size}) => ({path, name, size: Number.isFinite(size) && size >= 0 ? size : 0}));
}
export function createApiClient(fetcher) {
  async function request(path, options = {}) {
    const response = await fetcher(path, {cache: 'no-cache', ...options});
    if (!response.ok) {
      let data;
      try { data = await response.json(); } catch { data = {message: t('요청을 처리하지 못했습니다.')}; }
      const error = new Error(data.message); error.status = response.status; throw error;
    }
    const data = await response.json();
    const age = response.headers?.get('Cache-Control')?.match(/(?:^|,)\s*max-age=(\d+)/);
    return {data, ttlMs: age ? Number(age[1]) * 1000 : undefined};
  }
  const data = async (path, options) => (await request(path, options)).data;
  return {
    configuration: () => data('/api/config'),
    browse: async (parameters, options) => {
      const result = await request('/api/browse?' + new URLSearchParams(parameters), options);
      return {...result.data, ...(result.ttlMs === undefined ? {} : {cacheTtlMs: result.ttlMs})};
    },
    inspect: (parameters, options) => data('/api/media/info?' + new URLSearchParams(parameters), options),
    random: (parameters, options) => data('/api/random-playback?' + new URLSearchParams(parameters), options),
    cancelRandom: parameters => fetcher('/api/random-playback?' + new URLSearchParams(parameters), {method: 'DELETE', cache: 'no-store'}).catch(() => {}),
    streamUrl: parameters => '/api/media/stream?' + new URLSearchParams(parameters),
    thumbnailUrl: parameters => '/api/media/thumbnail?' + new URLSearchParams(parameters),
    coverUrl: parameters => '/api/media/folder-cover?' + new URLSearchParams({w: 640, ...parameters})
  };
}
