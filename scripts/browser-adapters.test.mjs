import test from 'node:test';
import assert from 'node:assert/strict';
import {assetModule} from './frontend-test-support.mjs';

const {createBrowserAdapter} = await assetModule('js/shared/browser/index.js');

test('timer adapters retain their Window receiver when called as adapter methods', async () => {
  const win = {location: {href: 'https://example.test/'}, history: {}};
  const calls = [];
  const browser = createBrowserAdapter({
    window: win, document: {},
    setTimeout(callback, milliseconds) {
      assert.equal(this, win, 'browser timers require their Window receiver');
      calls.push(['schedule', milliseconds]);
      callback();
      return 41;
    },
    clearTimeout(timer) {
      assert.equal(this, win, 'cancellation must retain the same Window receiver');
      calls.push(['cancel', timer]);
    }
  });
  let invoked = false;
  const timer = browser.schedule(() => {invoked = true;}, 10);
  browser.cancel(timer);
  await browser.sleep(20);
  assert.equal(invoked, true);
  assert.deepEqual(calls, [['schedule', 10], ['cancel', 41], ['schedule', 20]]);
});
