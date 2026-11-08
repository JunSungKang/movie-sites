import assert from 'node:assert/strict';
import {readFile,stat} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {resolve,relative} from 'node:path';
import {selectTheme,validateManifest} from '../src/main/resources/static/themes/runtime.js';

const root=fileURLToPath(new URL('../src/main/resources/static/themes/',import.meta.url));
const catalog=JSON.parse(await readFile(resolve(root,'catalog.json'),'utf8'));
assert.equal(catalog.defaultTheme,'child-toy');
const baseline=['child-toy','10s-neon-lab','10s-pop-sticker','10s-pixel-arcade','20s-midnight-stream','20s-soft-minimal','20s-color-block','30s-studio-grid','30s-calm-gallery','30s-home-cinema','40s-classic-library','40s-forest-view','40s-slate-dashboard','50s-paper-light','50s-navy-theater','50s-warm-lounge','60s-clear-blue','60s-warm-comfort','60s-bold-contrast'];
for(const id of baseline) assert(catalog.themes.some(t=>t.id===id),'Preserve public theme ID: '+id);
const groups=['child','10s','20s','30s','40s','50s','60s'];
for(const group of groups) assert(catalog.themes.filter(t=>t.group===group).length >= (group==='child'?1:3),'Baseline group: '+group);
const styles=new Set();
for(const item of catalog.themes){
  assert(groups.includes(item.group),'Extend preview grouping before adding a new group');
  const selection=selectTheme(catalog,item.id,null);assert.equal(selection.theme.id,item.id);assert.equal(selection.preview,false);
  const selected=selectTheme(catalog,'child-toy',item.id);assert.equal(selected.theme.id,item.id);assert.equal(selected.preview,true);
  const dir=resolve(root,item.directory);assert(!relative(root,dir).startsWith('..'));
  const manifest=validateManifest(JSON.parse(await readFile(resolve(dir,'theme.json'),'utf8')),item.id);
  assert.equal(manifest.group,item.group);assert.equal(manifest.name,item.name);
  const css=await readFile(resolve(dir,'theme.css'),'utf8');assert(!styles.has(css),'Distinct style definition: '+item.id);styles.add(css);
  for(const url of [...css.matchAll(/url\(["']?([^\)"']+)["']?\)/g)].map(m=>m[1])){
    assert(!url.includes(':')&&!url.startsWith('/'),'Local theme asset only');const asset=resolve(dir,url);assert(!relative(root,asset).startsWith('..'));assert((await stat(asset)).isFile());
  }
  for(const asset of [manifest.hero,'assets/preview.svg']){
    const svg=await readFile(resolve(dir,asset),'utf8');assert(svg.startsWith('<svg'));assert(!/<(?:script|image|foreignObject)\b|https?:\/\/(?!www.w3.org)/i.test(svg),'Self-contained original SVG');
  }
}
assert.equal(selectTheme(catalog,'child-toy','../../outside').theme.id,'child-toy');
assert.equal(selectTheme(catalog,'child-toy','../../outside').invalidPreview,true);
assert.throws(()=>selectTheme(catalog,'missing',null));
assert.throws(()=>selectTheme({...catalog,themes:[{id:'x',directory:'../../outside'}]},'x',null));
assert.throws(()=>validateManifest({id:'x',hero:'https://example.org'},'x'));
const extended={...catalog,themes:[...catalog.themes,{id:'community-example',group:'20s',directory:'20s/community-example'}]};
assert.equal(selectTheme(extended,'child-toy','community-example').theme.id,'community-example');
console.log(`${catalog.themes.length} theme selections, compatible baseline IDs, extensible catalog, local assets and unsafe overrides verified.`);
