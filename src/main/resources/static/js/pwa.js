import {t, setLocale, localeTag} from './i18n.js';
/** Installation is optional; browsing and playback never depend on its availability. */
export function initPwa({window: win, document: doc, navigator: nav}) {
  const button = doc.getElementById('install-app');
  const dialog = doc.getElementById('install-help');
  const steps = doc.getElementById('install-steps');
  const close = doc.getElementById('close-install-help');
  if (!button || !dialog || !steps || !close || !win.isSecureContext) return;

  const standalone = win.matchMedia('(display-mode: standalone)');
  const ios = /iPad|iPhone|iPod/.test(nav.userAgent) || (nav.platform === 'MacIntel' && nav.maxTouchPoints > 1);
  const safari = /Safari/.test(nav.userAgent) && !/Chrome|Chromium|CriOS|Edg|OPR|FxiOS/.test(nav.userAgent);
  const macSafari = safari && /Mac/.test(nav.platform) && !ios;
  let prompt = null;
  let installed = false;

  function update() {
    button.hidden = installed || standalone.matches || nav.standalone === true || (!prompt && !ios && !macSafari);
    button.textContent = prompt ? t('앱으로 설치') : ios ? t('홈 화면에 추가') : t('앱 설치 안내');
  }
  function help() {
    steps.textContent = ios
      ? t('Safari에서 이 페이지를 열고, 공유 메뉴의 ‘홈 화면에 추가’를 선택해 주세요. ‘웹 앱으로 열기’가 보이면 켠 뒤 ‘추가’를 누르면 됩니다.')
      : macSafari
        ? t('Safari의 파일 메뉴에서 ‘Dock에 추가’를 선택해 주세요. 이 기능을 지원하는 macOS에서 이용할 수 있어요.')
        : t('브라우저 주소창의 설치 아이콘이나 메뉴에서 ‘앱 설치’를 선택해 주세요. 브라우저와 기기에 따라 메뉴 이름이 다를 수 있어요.');
    if (!dialog.open) dialog.showModal();
  }
  close.addEventListener('click', () => dialog.close());
  button.addEventListener('click', async () => {
    if (!prompt) { help(); return; }
    const pending = prompt;
    prompt = null;
    button.disabled = true;
    try {
      await pending.prompt();
      const choice = await pending.userChoice;
      if (choice.outcome === 'accepted') installed = true;
    } catch {
      help();
    } finally {
      button.disabled = false;
      update();
    }
  });
  win.addEventListener('beforeinstallprompt', event => {
    event.preventDefault();
    prompt = event;
    update();
  });
  win.addEventListener('appinstalled', () => {
    installed = true;
    prompt = null;
    if (dialog.open) dialog.close();
    update();
  });
  standalone.addEventListener('change', update);
  update();

  if ('serviceWorker' in nav) {
    // Normal waiting-worker activation avoids reloading a playing video on release.
    nav.serviceWorker.register('/service-worker.js', {scope: '/', updateViaCache: 'none'}).catch(() => {});
  }
}

if (typeof window !== 'undefined') initPwa({window, document, navigator});
