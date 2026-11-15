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
