import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
// Use the browser's content-addressed URL so a direct test import shares dependencies with the real app.
export async function assetModule(relative) {
  const url = new URL('../src/main/resources/static/' + relative, import.meta.url);
  url.searchParams.set('v', createHash('sha256').update(readFileSync(url)).digest('hex').slice(0, 16));
  return import(url.href);
}
