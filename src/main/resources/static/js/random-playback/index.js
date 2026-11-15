import {createRandomView} from './random-view.js?v=b2b836cfef0e6365';
import {createRandomPresenter} from './random-presenter.js?v=352f3133cdfa27ea';
export function createRandomPlaybackFeature({document, ...options}) { return createRandomPresenter({view: createRandomView(document), ...options}); }
