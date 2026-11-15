import {createInstallationView} from './installation-view.js?v=aaf7d2f30969a5cb';
import {createInstallationPresenter} from './installation-presenter.js?v=5f1bec8dd694db0b';
import {createInstallationBrowser} from '../shared/browser/index.js?v=95c908b3eff4d6a9';
export function initInstallation({window, document, navigator}) { return createInstallationPresenter({view: createInstallationView(document), browser: createInstallationBrowser(window, navigator)}); }
