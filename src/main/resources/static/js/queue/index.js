import {createQueueView} from './queue-view.js?v=64707e277b96e173';
import {createQueuePresenter} from './queue-presenter.js?v=5ce44e406ea5e483';
export function createQueueFeature({document, ...options}) { return createQueuePresenter({view: createQueueView(document), ...options}); }
