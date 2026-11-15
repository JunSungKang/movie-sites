import {setLocale} from './shared/i18n/index.js?v=b21fadcdaa06a04e';
import {createApiClient} from './shared/api/index.js?v=c4556515510eebfb';
import {createBrowserAdapter} from './shared/browser/index.js?v=95c908b3eff4d6a9';
import {createApplicationView} from './shared/ui/index.js?v=50e99f871f8b453e';
import {createCatalogFeature} from './catalog/index.js?v=b2955b8f87bd55a5';
import {createPlayerFeature} from './player/index.js?v=8c00245ef923b5ce';
import {createQueueFeature} from './queue/index.js?v=c1a546306757d1a0';
import {createRandomPlaybackFeature} from './random-playback/index.js?v=e823ec3abec2acb2';
import {applyTheme} from '../themes/runtime.js?v=dc9c3c55396f09dc';

export async function createApplication(environment) {
  const {document} = environment, browser = createBrowserAdapter(environment);
  const api = createApiClient(environment.fetch), application = createApplicationView(document, browser);
  let catalog, player, queue, random;
  application.bind();
  player = createPlayerFeature({document, api, browser, toast: application.toast, onChanged: () => queue?.render(), onClose: () => { random?.cancel(); random?.clearRetry(); }, onStart: () => { random?.cancel(); random?.clearRetry(); }, onInvalidate: () => catalog.invalidate(), onRetry: () => random.retry()});
  queue = createQueueFeature({document, storage: browser.storage, getSession: () => player.snapshot, onPlay: player.start, onAddPage: () => catalog.enqueuePage(), toast: application.toast});
  random = createRandomPlaybackFeature({document, api, browser, getPath: () => catalog.snapshot.path, onPrepare: player.prepareRandom, onProgress: player.progress, onFailure: player.failure, onPlay: player.start, onClose: player.close});
  catalog = createCatalogFeature({document, api, browser, onBeforeLoad: random.navigation, onPlay: player.start, onEnqueue: queue.enqueue, onRendered: queue.render});
  browser.onNavigation(() => catalog.load(browser.path())); browser.onLeave(() => { player.close(); catalog.dispose(); });
  try {
    const config = await api.configuration(); setLocale(config.locale);
    const copy = await (environment.applyTheme || applyTheme)(config, environment);
    application.configure(config); queue.initialize(config.rootId); random.configure(config); catalog.configure(config, copy);
    await catalog.load(browser.path());
  } catch (error) { application.bootFailure(error); }
  return {catalog, player, queue, random};
}
if (typeof window !== 'undefined') createApplication({window, document, navigator, location, history, fetch: window.fetch.bind(window)});
