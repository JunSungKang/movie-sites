import {createQueueView} from './queue-view.js?v=64707e277b96e173';
import {createQueuePresenter} from './queue-presenter.js?v=cc110357bc64996d';
export function createQueueFeature({document, ...options}) { return createQueuePresenter({view: createQueueView(document), ...options}); }
