import {t} from '../shared/i18n/index.js?v=b21fadcdaa06a04e';
import {createDom, title} from '../shared/ui/index.js?v=50e99f871f8b453e';
export function createQueueView(document) {
  const {$, node, button} = createDom(document); let actions;
  return {
    bind(next) { actions = next; $('queue-play').addEventListener('click', next.play); $('queue-clear').addEventListener('click', next.clear); $('queue-add-page').addEventListener('click', next.addPage); },
    render({items, warning, active, currentPath}) {
      $('queue-warning').hidden = !warning; if (warning) $('queue-warning').textContent = warning;
      $('queue-count').textContent = items.length + t('개'); $('queue-empty').hidden = items.length > 0; $('queue-play').disabled = !items.length; $('queue-clear').disabled = !items.length;
      $('queue-description').textContent = active ? t('재생 중 바꾼 순서는 다음 전체 재생부터 적용됩니다.') : t('보고 싶은 영상을 담고 순서대로 재생하세요.');
      const focusPath = document.activeElement?.dataset.queuePath, focusAction = document.activeElement?.dataset.queueAction; $('queue-list').replaceChildren();
      for (const [index, entry] of items.entries()) {
        const row = node('li', 'queue-entry'), copy = node('div', 'queue-copy'), current = active && currentPath === entry.path; if (current) row.setAttribute('aria-current', 'true');
        copy.append(node('span', 'queue-title', `${index + 1}. ${title(entry.name)}`), node('span', 'queue-folder', (current ? t('재생 중 · ') : '') + entry.path.split('/').slice(0, -1).join(' / ')));
        const actionsBox = node('div', 'queue-item-actions');
        for (const [label, action, offset, disabled] of [[t('위로'), 'up', -1, index === 0], [t('아래로'), 'down', 1, index === items.length - 1], [t('삭제'), 'remove', 0, false]]) {
          const element = button(label, () => action === 'remove' ? actions.remove(entry.path) : actions.move(entry.path, offset), 'queue-button'); element.disabled = disabled; element.setAttribute('aria-label', title(entry.name) + ' ' + label); element.dataset.queuePath = entry.path; element.dataset.queueAction = action; actionsBox.append(element);
        }
        row.append(copy, actionsBox); $('queue-list').append(row);
      }
      if (focusPath) { const replacement = [...$('queue-list').querySelectorAll('button')].find(element => element.dataset.queuePath === focusPath && element.dataset.queueAction === focusAction && !element.disabled); (replacement || $('queue-play')).focus({preventScroll: true}); }
      for (const element of document.querySelectorAll('.enqueue-button')) { const added = items.some(entry => entry.path === element.dataset.path); element.textContent = added ? t('대기열에 담김') : t('대기열에 담기'); element.setAttribute('aria-pressed', String(added)); }
    }
  };
}
