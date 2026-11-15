import {createCatalogApi} from './catalog-api.js?v=e2f71a6970317ea2';
import {createCatalogPresenter} from './catalog-presenter.js?v=45ee14dcd4ae56a6';
import {createCatalogView} from './catalog-view.js?v=d771bf4a3951fca5';
import {DirectoryCache, CachedCatalogApi, PageHistory} from '../shared/cache/index.js?v=a9b4c80acbd47314';
export function createCatalogFeature({document, api, browser, ...callbacks}) {
  const cache = new DirectoryCache(), source = new CachedCatalogApi(createCatalogApi(api), cache);
  const presenter = createCatalogPresenter({view: createCatalogView(document, browser), source, api, browser, history: new PageHistory(), ...callbacks});
  return {...presenter, get snapshot() { return presenter.snapshot; }, configure(config, copy) {
    cache.ttlMs = config.cacheTtlSeconds * 1000; source.scope = `${config.rootId ?? ''}\0${config.instanceId ?? ''}\0`;
    cache.useStorage(browser.sessionStorage, 'moviesites:directory-cache:v2', source.scope); presenter.configure(config, copy);
  }};
}
