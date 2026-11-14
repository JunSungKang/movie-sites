import {t, setLocale, localeTag} from './i18n.js';
// One saved queue; playback uses a snapshot so edits apply to the next play-through.
export const MAX_QUEUE = 200;
function valid(entry) {
  return entry && typeof entry.path === 'string' && entry.path.length > 0 && entry.path.length <= 4096
    && !entry.path.startsWith('/') && !/[\\\0]/.test(entry.path)
    && entry.path.split('/').every(part => part && part !== '.' && part !== '..')
    && typeof entry.name === 'string' && entry.name.length > 0 && entry.name.length <= 4096;
}
function clean(entries) {
  const seen = new Set();
  return entries.filter(valid).filter(entry => !seen.has(entry.path) && seen.add(entry.path)).slice(0, MAX_QUEUE)
    .map(({path, name, size}) => ({path, name, size: Number.isFinite(size) && size >= 0 ? size : 0}));
}
export class PlaybackQueue {
  constructor(storage, rootId) {
    this.storage = storage; this.key = 'moviesites:playback-queue:v1:' + rootId;
    this.items = []; this.warning = '';
    try {
      const raw = storage?.getItem(this.key);
      if (raw) {
        const saved = JSON.parse(raw);
        if (saved.version !== 1 || !Array.isArray(saved.items)) throw new Error('Invalid queue');
        this.items = clean(saved.items);
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
  add(entries) { const before = this.items.length; this.items = clean([...this.items, ...entries]); this.save(); return this.items.length - before; }
  remove(path) { this.items = this.items.filter(entry => entry.path !== path); this.save(); }
  move(path, offset) {
    const from = this.items.findIndex(entry => entry.path === path), to = from + offset;
    if (from < 0 || to < 0 || to >= this.items.length) return;
    [this.items[from], this.items[to]] = [this.items[to], this.items[from]]; this.save();
  }
  clear() { this.items = []; this.save(); }
}
export class PlaybackSession {
  constructor(select, finish) { this.select = select; this.finish = finish; this.serial = 0; this.items = []; this.index = -1; }
  get active() { return this.index >= 0; }
  get token() { return this.serial; }
  get hasPrevious() { return this.active && this.index > 0; }
  get hasNext() { return this.active && this.index < this.items.length - 1; }
  current(token) { return this.active && token === this.serial; }
  start(entries) {
    this.close(); this.items = clean(entries);
    if (!this.items.length) return false;
    this.index = 0; this.show(); return true;
  }
  show() { this.serial++; this.select(this.items[this.index], this.serial); }
  previous() { if (this.hasPrevious) { this.index--; this.show(); } }
  next() { if (this.hasNext) { this.index++; this.show(); } }
  ended(token) {
    if (!this.current(token)) return;
    if (this.hasNext) this.next();
    else { this.close(); this.finish(); }
  }
  close() { this.serial++; this.items = []; this.index = -1; }
}

// Request from the original click, before media inspection consumes user activation.
// A dialog itself cannot be fullscreen; its non-dialog player wrapper can.
// Viewport expansion lasts until close; delayed OS notifications never resize a new session.
export class PlaybackFullscreen {
  constructor(document, container, video, changed = () => {}) {
    this.document = document; this.container = container; this.video = video;
    this.changed = changed; this.enabled = false; this.nativePending = false; this.expanded = false;
    const update = () => {
      if (this.active) this.nativePending = false;
      if (!this.enabled && this.active) this.exit();
      changed();
    };
    for (const name of ['fullscreenchange', 'webkitfullscreenchange']) document.addEventListener?.(name, update);
    video.addEventListener('webkitbeginfullscreen', update);
    video.addEventListener('webkitendfullscreen', update);
  }
  get standardRequest() { return this.container.requestFullscreen || this.container.webkitRequestFullscreen; }
  get supported() { return Boolean(this.standardRequest || this.video.webkitEnterFullscreen); }
  get active() {
    const element = this.document.fullscreenElement || this.document.webkitFullscreenElement;
    return Boolean(element && (element === this.container || this.container.contains(element)) || this.video.webkitDisplayingFullscreen);
  }
  start() { this.enabled = true; this.nativePending = true; this.request(); }
  request() {
    if (!this.enabled) return;
    this.expanded = true; this.changed();
    if (this.active) { this.nativePending = false; return; }
    if (this.pending) return;
    const request = this.standardRequest;
    if (request) {
      try {
        const result = request === this.container.requestFullscreen
          ? request.call(this.container, {navigationUI: 'hide'}) : request.call(this.container);
        this.pending = Promise.resolve(result).then(() => {
          if (!this.enabled) this.exit();
        }).catch(() => {}).then(() => { this.pending = null; this.changed(); });
      } catch { this.changed(); }
    } else this.native();
  }
  playing() {
    if (this.enabled && this.nativePending && !this.standardRequest) this.native();
  }
  native() {
    if (!this.enabled || this.active || !this.video.webkitEnterFullscreen || this.video.readyState < 1) return;
    this.nativePending = false;
    try { this.video.webkitEnterFullscreen(); } catch { /* Native controls/manual retry remain available. */ }
    this.changed();
  }
  exit() {
    const element = this.document.fullscreenElement || this.document.webkitFullscreenElement;
    const exit = this.document.exitFullscreen || this.document.webkitExitFullscreen;
    if (element && (element === this.container || this.container.contains(element)) && exit && !this.exiting) {
      try {
        // Set the guard before calling: fullscreenchange can fire synchronously.
        this.exiting = true;
        Promise.resolve(exit.call(this.document)).then(
          () => { this.exiting = false; this.changed(); },
          () => { this.exiting = false; this.changed(); });
      } catch { this.exiting = false; this.changed(); }
    }
    if (this.video.webkitDisplayingFullscreen && this.video.webkitExitFullscreen && !this.nativeExiting) {
      // WebKit's void call may be ignored during transition; a late begin event retries it.
      try { this.nativeExiting = true; this.video.webkitExitFullscreen(); }
      catch { /* Native controls remain available if the platform refuses exit. */ }
      finally { this.nativeExiting = false; }
    }
  }
  close() { this.enabled = false; this.nativePending = false; this.expanded = false; this.exit(); this.changed(); }
}
