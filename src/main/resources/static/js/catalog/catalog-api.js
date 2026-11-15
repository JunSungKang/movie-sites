import {t} from '../shared/i18n/index.js?v=b21fadcdaa06a04e';
import {waitForPreparation} from '../shared/api/index.js?v=c4556515510eebfb';
export function createCatalogApi(api) {
  return {async get(path, cursor, force, signal, preparing) {
    let query;
    while (true) {
      const data = await api.browse({path, sort: 'duration', ...(cursor ? {cursor} : query ? {query} : force ? {refresh: 'true'} : {})}, {signal, cache: force ? 'reload' : 'no-cache'});
      if (data.state === 'failed') throw new Error(data.message || t('목록을 정리하지 못했습니다. 잠시 후 새로고침해 주세요.'));
      if (data.state !== 'preparing') return data;
      query = data.query;
      if (typeof query !== 'string' || !query) throw new Error(t('목록 준비 상태를 확인하지 못했습니다. 새로고침해 주세요.'));
      preparing(data); await waitForPreparation(signal);
    }
  }};
}
