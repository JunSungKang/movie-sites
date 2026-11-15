export function createInstallationBrowser(win, navigator) {
  const standalone = win.isSecureContext ? win.matchMedia('(display-mode: standalone)') : null;
  const ios = /iPad|iPhone|iPod/.test(navigator.userAgent) || navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1;
  const safari = /Safari/.test(navigator.userAgent) && !/Chrome|Chromium|CriOS|Edg|OPR|FxiOS/.test(navigator.userAgent);
  return {
    secure: Boolean(win.isSecureContext), device: ios ? 'ios' : safari && /Mac/.test(navigator.platform) ? 'mac-safari' : 'other',
    standalone: () => Boolean(standalone?.matches || navigator.standalone === true),
    bind({prompt, installed, changed}) {
      win.addEventListener('beforeinstallprompt', event => { event.preventDefault(); prompt(event); }); win.addEventListener('appinstalled', installed); standalone.addEventListener('change', changed);
    },
    registerWorker() { if ('serviceWorker' in navigator) navigator.serviceWorker.register('/service-worker.js', {scope: '/', updateViaCache: 'none'}).catch(() => {}); }
  };
}
