export function createInstallationView(document) {
  const button = document.getElementById('install-app'), dialog = document.getElementById('install-help'), steps = document.getElementById('install-steps'), close = document.getElementById('close-install-help');
  return {
    available: Boolean(button && dialog && steps && close),
    bind(actions) { close.addEventListener('click', () => dialog.close()); button.addEventListener('click', actions.install); },
    render({hidden, disabled, label}) { button.hidden = hidden; button.disabled = disabled; button.textContent = label; },
    help(message) { steps.textContent = message; if (!dialog.open) dialog.showModal(); },
    close() { if (dialog.open) dialog.close(); }
  };
}
