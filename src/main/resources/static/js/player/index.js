import {createPlayerView} from './player-view.js?v=d71584ff8975fed7';
import {createPlayerPresenter} from './player-presenter.js?v=d0e882552e8f6652';
import {PlaybackFullscreen, createMediaElementAdapter} from '../shared/browser/index.js?v=95c908b3eff4d6a9';
export function createPlayerFeature({document, ...options}) {
  const view = createPlayerView(document);
  let fullscreen;
  const update = () => { if (fullscreen) view.fullscreen({supported: fullscreen.supported, active: fullscreen.active, expanded: fullscreen.expanded}); };
  fullscreen = new PlaybackFullscreen(document, document.getElementById('player-fullscreen'), document.getElementById('video-player'), update); update();
  return createPlayerPresenter({view, media: createMediaElementAdapter(document.getElementById('video-player')), fullscreen, ...options});
}
