import {createDom} from '../shared/ui/index.js?v=50e99f871f8b453e';
export function createRandomView(document) {
  const {$, node} = createDom(document);
  return {
    bind(actions, times) { for (const minutes of times) $('random-' + minutes).addEventListener('click', () => actions.start(minutes)); $('random-replay').addEventListener('click', actions.replay); },
    scope(message, enabled, times) { $('random-scope').textContent = message; for (const minutes of times) $('random-' + minutes).disabled = !enabled; },
    clear: () => { $('random-playlist').hidden = true; },
    render({entries, summary, note}) { $('random-results').replaceChildren(...entries.map(text => node('li', '', text))); $('random-summary').textContent = summary; $('random-note').textContent = note; $('random-playlist').hidden = false; }
  };
}
