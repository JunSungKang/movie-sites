export function installationState(state, event) {
  switch (event) {
    case 'PROMPT': return {...state, hasPrompt: true};
    case 'REQUEST': return {...state, hasPrompt: false, busy: true};
    case 'SETTLED': return {...state, busy: false};
    case 'INSTALLED': return {...state, installed: true, hasPrompt: false};
    default: throw new Error('Unknown installation event');
  }
}
