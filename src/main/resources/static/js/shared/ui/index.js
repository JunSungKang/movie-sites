import {t, localeTag} from '../i18n/index.js?v=b21fadcdaa06a04e';
export function createDom(document) {
  const $ = id => document.getElementById(id);
  function node(tag, className, text) { const element = document.createElement(tag); if (className) element.className = className; if (text !== undefined) element.textContent = text; return element; }
  function icon(name) { const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg'); svg.classList.add('icon'); svg.setAttribute('aria-hidden', 'true'); const use = document.createElementNS(svg.namespaceURI, 'use'); use.setAttribute('href', '#i-' + name); svg.append(use); return svg; }
  function button(label, handler, className = 'text-button') { const element = node('button', className, label); element.type = 'button'; element.addEventListener('click', handler); return element; }
  return {$, node, icon, button};
}
export const title = name => name.replace(/\.[^.]+$/, '');
export function size(bytes) { if (!Number.isFinite(bytes) || bytes < 0) return ''; if (bytes < 1024) return bytes + ' B'; for (const [threshold, label] of [[1024 ** 4, 'TB'], [1024 ** 3, 'GB'], [1024 ** 2, 'MB'], [1024, 'KB']]) if (bytes >= threshold) return (bytes / threshold).toLocaleString(localeTag(), {maximumFractionDigits: 1}) + ' ' + label; return ''; }
export function duration(seconds) { if (!Number.isFinite(seconds)) return ''; const n = Math.floor(seconds); return n >= 3600 ? `${Math.floor(n / 3600)}:${String(Math.floor(n / 60) % 60).padStart(2, '0')}:${String(n % 60).padStart(2, '0')}` : `${Math.floor(n / 60)}:${String(n % 60).padStart(2, '0')}`; }
export function date(value) { const d = new Date(value); return Number.isNaN(d.valueOf()) ? '' : new Intl.DateTimeFormat(localeTag(), {year: 'numeric', month: '2-digit', day: '2-digit'}).format(d) + t(' 수정'); }
export const folderName = path => path.split('/').filter(Boolean).at(-1) || t('라이브러리');
export function createApplicationView(document, browser) {
  const {$} = createDom(document); let toastTimer;
  return {
    configure: config => { $('source-link').href = config.sourceUrl; },
    bind: () => $('boot-retry').addEventListener('click', browser.reload),
    bootFailure(error) { $('boot-message').textContent = error.message || t('서비스에 연결하지 못했습니다. 다시 연결해 주세요.'); $('boot-retry').hidden = false; },
    toast(message) { $('toast').textContent = message; $('toast').hidden = false; browser.cancel(toastTimer); toastTimer = browser.schedule(() => { $('toast').hidden = true; }, 4500); }
  };
}
