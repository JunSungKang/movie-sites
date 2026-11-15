import {t} from '../shared/i18n/index.js?v=b21fadcdaa06a04e';
import {installationState} from './installation-model.js?v=9a304506dd678b1f';
export function createInstallationPresenter({view, browser}) {
  if (!view.available || !browser.secure) return;
  let prompt = null, state = {installed: false, hasPrompt: false, busy: false};
  const update = () => view.render({hidden: state.installed || browser.standalone() || !state.hasPrompt && browser.device === 'other', disabled: state.busy, label: state.hasPrompt ? t('앱으로 설치') : browser.device === 'ios' ? t('홈 화면에 추가') : t('앱 설치 안내')});
  function help() { view.help(browser.device === 'ios' ? t('Safari에서 이 페이지를 열고, 공유 메뉴의 ‘홈 화면에 추가’를 선택해 주세요. ‘웹 앱으로 열기’가 보이면 켠 뒤 ‘추가’를 누르면 됩니다.') : browser.device === 'mac-safari' ? t('Safari의 파일 메뉴에서 ‘Dock에 추가’를 선택해 주세요. 이 기능을 지원하는 macOS에서 이용할 수 있어요.') : t('브라우저 주소창의 설치 아이콘이나 메뉴에서 ‘앱 설치’를 선택해 주세요. 브라우저와 기기에 따라 메뉴 이름이 다를 수 있어요.')); }
  async function install() {
    if (!prompt) { help(); return; }
    const pending = prompt; prompt = null; state = installationState(state, 'REQUEST'); update();
    try { await pending.prompt(); const choice = await pending.userChoice; if (choice.outcome === 'accepted') state = installationState(state, 'INSTALLED'); }
    catch { help(); }
    finally { state = installationState(state, 'SETTLED'); update(); }
  }
  view.bind({install}); browser.bind({prompt: event => { prompt = event; state = installationState(state, 'PROMPT'); update(); }, installed: () => { prompt = null; state = installationState(state, 'INSTALLED'); view.close(); update(); }, changed: update});
  update(); browser.registerWorker();
  return {get snapshot() { return {...state}; }};
}
