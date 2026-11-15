import {t} from '../shared/i18n/index.js?v=b21fadcdaa06a04e';
import {PlaybackQueue, MAX_QUEUE} from './queue-model.js?v=9a2733637198587a';
export function createQueuePresenter({view, storage, getSession, onPlay, onAddPage, toast}) {
  let model;
  function render() { if (!model) return; const session = getSession(); view.render({items: model.items, warning: model.warning, active: session.active, currentPath: session.currentPath}); }
  view.bind({play: () => model && onPlay(model.items), clear: () => { model?.clear(); render(); }, addPage: onAddPage,
    remove: path => { model.remove(path); render(); }, move: (path, offset) => { model.move(path, offset); render(); }});
  return {
    initialize(rootId) { model = new PlaybackQueue(storage, rootId); render(); },
    enqueue(entries) { if (!model) return; const count = model.add(entries); render(); toast(count ? t`${count}개 영상을 담았습니다.` : model.items.length >= MAX_QUEUE ? t`최대 ${MAX_QUEUE}개까지 담을 수 있습니다.` : t('이미 대기열에 담긴 영상입니다.')); },
    render,
    get model() { return model; }
  };
}
