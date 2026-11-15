export {PlaybackFullscreen} from './fullscreen.js?v=b1b4b1c51aa6496d';
export {createInstallationBrowser} from './installation.js?v=3feb9894e21d0514';
export function createBrowserAdapter({window: win, document: doc, location = win.location, history = win.history,
  setTimeout: schedule = globalThis.setTimeout, clearTimeout: cancel = globalThis.clearTimeout,
  IntersectionObserver = globalThis.IntersectionObserver}) {
  const storage = name => { try { return win[name]; } catch { return undefined; } };
  return {
    storage: storage('localStorage'), sessionStorage: storage('sessionStorage'),
    schedule: (callback, milliseconds) => schedule.call(win, callback, milliseconds),
    cancel: timer => cancel.call(win, timer),
    sleep: milliseconds => new Promise(resolve => schedule.call(win, resolve, milliseconds)),
    createAbortController: () => new AbortController(),
    path: () => new URL(location.href).searchParams.get('path') || '',
    previewExit() { const url = new URL(location.href); url.searchParams.delete('preview-theme'); return url.pathname + url.search; },
    navigate(path) {
      const url = new URL(location.href);
      if (path) url.searchParams.set('path', path); else url.searchParams.delete('path');
      history.pushState({path}, '', url); win.scrollTo({top: 0, behavior: 'auto'});
    },
    onNavigation: callback => win.addEventListener('popstate', callback),
    onLeave: callback => win.addEventListener('pagehide', callback),
    reload: () => location.reload(),
    captureSelection: () => ({x: win.scrollX ?? globalThis.scrollX ?? 0, y: win.scrollY ?? globalThis.scrollY ?? 0, focus: doc.activeElement}),
    restoreSelection(selection) {
      if (selection.focus?.isConnected) selection.focus.focus({preventScroll: true});
      win.scrollTo(selection.x, selection.y);
    },
    observeVisible(entries, callback) {
      if (!IntersectionObserver) { for (const entry of entries) callback(entry); return {disconnect() {}}; }
      const observer = new IntersectionObserver(items => {
        for (const item of items) if (item.isIntersecting && !item.target._queued) {
          item.target._queued = true; callback(item.target._entry);
        }
      }, {rootMargin: '200px'});
      for (const entry of entries) observer.observe(entry);
      return observer;
    }
  };
}
export function createMediaElementAdapter(video) {
  return {
    reset() { video.pause(); video.removeAttribute('src'); video.load(); },
    setSource: source => { video.src = source; },
    play: () => video.play(),
    get ended() { return video.ended; },
    get error() { return video.error; }
  };
}
