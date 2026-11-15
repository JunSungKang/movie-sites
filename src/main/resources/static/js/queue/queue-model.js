import {t} from '../shared/i18n/index.js?v=b21fadcdaa06a04e';
import {MAX_QUEUE,sanitizeQueue} from '../shared/api/index.js?v=c4556515510eebfb';
export {MAX_QUEUE};
export class PlaybackQueue {
  constructor(storage, rootId) {
    this.storage = storage; this.key = 'moviesites:playback-queue:v1:' + rootId;
    this.items = []; this.warning = '';
    try {
      const raw = storage?.getItem(this.key);
      if (raw) {
        const saved = JSON.parse(raw);
        if (saved.version !== 1 || !Array.isArray(saved.items)) throw new Error('Invalid queue');
        this.items = sanitizeQueue(saved.items);
      }
    } catch { this.warning = t('저장된 재생 대기열을 읽지 못했습니다. 이 화면에서는 계속 사용할 수 있습니다.'); }
  }
  save() {
    try {
      if (!this.storage) throw new Error('Storage unavailable');
      this.storage.setItem(this.key, JSON.stringify({version: 1, items: this.items}));
      this.warning = '';
      return true;
    } catch { this.warning = t('브라우저에 저장하지 못했습니다. 현재 대기열은 이 화면에서만 유지됩니다.'); return false; }
  }
  add(entries) { const before = this.items.length; this.items = sanitizeQueue([...this.items, ...entries]); this.save(); return this.items.length - before; }
  remove(path) { this.items = this.items.filter(entry => entry.path !== path); this.save(); }
  move(path, offset) {
    const from = this.items.findIndex(entry => entry.path === path), to = from + offset;
    if (from < 0 || to < 0 || to >= this.items.length) return;
    [this.items[from], this.items[to]] = [this.items[to], this.items[from]]; this.save();
  }
  clear() { this.items = []; this.save(); }
}
