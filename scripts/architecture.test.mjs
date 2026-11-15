import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,readdirSync} from 'node:fs';
import {resolve,relative} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {moduleImports,sourceTokens} from './module-imports.mjs';

const staticRoot=resolve(fileURLToPath(new URL('../src/main/resources/static/',import.meta.url)));
const features=['catalog','player','queue','random-playback','installation'];
const selectors=new Set(['querySelector','querySelectorAll','getElementById','getElementsByClassName','getElementsByTagName','createElement','createElementNS','innerHTML','outerHTML','insertAdjacentHTML','textContent','addEventListener']);
const network=new Set(['fetch','XMLHttpRequest','WebSocket','EventSource']);
const feature=file=>/^js\/([^/]+)\//.exec(file)?.[1];
const shared=file=>/^js\/shared\/([^/]+)\//.exec(file)?.[1];
function productionModules(){
  const modules=new Map();
  function visit(directory){
    for(const entry of readdirSync(directory,{withFileTypes:true})){
      const path=resolve(directory,entry.name);
      if(entry.isDirectory())visit(path);
      else if(entry.isFile()&&path.endsWith('.js'))modules.set(relative(staticRoot,path).replaceAll('\\','/'),readFileSync(path,'utf8'));
    }
  }
  visit(staticRoot);
  return modules;
}
function dependency(file,specifier){
  assert.ok(specifier.startsWith('.'),`${file}: use packaged relative module imports (${specifier})`);
  const url=new URL(specifier,new URL(file,'https://modules.invalid/static/'));
  const path=decodeURIComponent(url.pathname);
  assert.equal(url.origin,'https://modules.invalid',`${file}: external module import`);
  assert.ok(path.startsWith('/static/'),`${file}: import escapes packaged static root`);
  return path.slice('/static/'.length);
}
function validateArchitecture(modules){
  for(const name of features)assert.ok(modules.has(`js/${name}/index.js`),`Missing public entry for ${name}`);
  const graph=new Map();
  for(const [file,source]of modules){
    const owner=feature(file),ownFeature=features.includes(owner)?owner:null,ownShared=shared(file);
    const imports=moduleImports(source),dependencies=[];
    for(const entry of imports){
      const target=dependency(file,entry.specifier),targetOwner=feature(target),targetFeature=features.includes(targetOwner)?targetOwner:null,targetShared=shared(target);
      assert.ok(modules.has(target),`${file}: missing module ${target}`);
      if(targetFeature){
        if(ownFeature)assert.equal(ownFeature,targetFeature,`${file}: features communicate through app.js callbacks, not imports`);
        else{
          assert.ok(['js/app.js','js/pwa.js'].includes(file),`${file}: only the composition roots may import a feature`);
          assert.equal(target,`js/${targetFeature}/index.js`,`${file}: use the feature public index.js`);
        }
      }
      if(targetShared&&ownShared!==targetShared)assert.equal(target,`js/shared/${targetShared}/index.js`,`${file}: use the shared public index.js`);
      if(file.endsWith('-view.js')){
        assert.ok(targetShared!=='api',`${file}: views must not import API adapters`);
        assert.ok(!/-(?:model|presenter|api)\.js$/.test(target),`${file}: views render and emit events; models/presenters/API adapters stay outside the view`);
      }
      dependencies.push(target);
    }
    const tokens=sourceTokens(source),identifiers=tokens.filter((token,index)=>token.type==='identifier'||(token.type==='string'&&tokens[index-1]?.value==='['&&tokens[index+1]?.value===']'));
    if(file.endsWith('-presenter.js')||file.endsWith('-model.js')){
      for(const token of identifiers)assert.ok(!['document','window','HTMLElement','Element','Document'].includes(token.value)&&!selectors.has(token.value),`${file}: models/presenters must use their injected view instead of DOM access (${token.value})`);
    }
    if(file.endsWith('-view.js'))for(const token of identifiers)assert.ok(!network.has(token.value),`${file}: views must not perform network requests (${token.value})`);
    if(['js/app.js','js/pwa.js'].includes(file)){
      for(const token of identifiers){
        const index=tokens.indexOf(token),next=tokens[index+1]?.value;
        const invocation=next==='('||(next===']'&&tokens[index+2]?.value==='(')||(['.','?.'].includes(next)&&['call','apply'].includes(tokens[index+2]?.value)&&tokens[index+3]?.value==='(');
        // Passing an existing fetch function into an adapter is composition;
        // invoking it here is feature IO. Views retain the stronger ban above.
        assert.ok(!(network.has(token.value)&&invocation)&&!selectors.has(token.value)&&!['setTimeout','setInterval','requestAnimationFrame'].includes(token.value),`${file}: composition only; move IO/DOM/timers to a feature or shared adapter (${token.value})`);
      }
      for(const token of tokens)if(token.type==='string'||token.type==='template'){
        const value=token.type==='string'?token.value:source.slice(token.start+1,token.end-1);
        assert.ok(!/^\/api(?:\/|\?)/.test(value),`${file}: endpoint policy belongs in an API adapter`);
      }
    }
    graph.set(file,dependencies);
  }
  const active=new Set(),complete=new Set();
  function visit(file,parents=[]){
    assert.ok(!active.has(file),`Module dependency cycle: ${[...parents,file].join(' -> ')}`);
    if(complete.has(file))return;
    active.add(file);for(const next of graph.get(file))visit(next,[...parents,file]);active.delete(file);complete.add(file);
  }
  for(const file of graph.keys())visit(file);
  return graph;
}
function v8StaticImports(modules){
  // The parser is Node's own V8 module parser. An isolated child enables the
  // experimental VM API without changing contributors' normal test invocation.
  const child=spawnSync(process.execPath,['--experimental-vm-modules','--input-type=module','-e',`
    import {SourceTextModule} from 'node:vm';
    let source='';for await(const bytes of process.stdin)source+=bytes;
    const result={};for(const [name,body]of JSON.parse(source))result[name]=new SourceTextModule(body,{identifier:name}).dependencySpecifiers;
    process.stdout.write(JSON.stringify(result));
  `],{encoding:'utf8',input:JSON.stringify([...modules]),env:{...process.env,NODE_NO_WARNINGS:'1'},timeout:20000,maxBuffer:4*1024*1024});
  assert.equal(child.status,0,child.stderr||child.error?.message||'V8 module parsing failed');
  return JSON.parse(child.stdout);
}
function compareParser(modules){
  const actual=v8StaticImports(modules);
  for(const [file,source]of modules){
    const lexical=moduleImports(source).filter(entry=>entry.kind==='static').map(entry=>entry.specifier);
    assert.deepEqual([...new Set(lexical)].sort(),[...new Set(actual[file])].sort(),`${file}: import locations must match V8's actual module dependency graph`);
    for(const entry of moduleImports(source))assert.equal(source[entry.start-1],source[entry.end],`${file}: import replacement must retain its quote boundaries`);
  }
}
function fixture(overrides={}){
  return new Map(Object.entries({
    ...Object.fromEntries(features.map(name=>[`js/${name}/index.js`,'export const createFeature=()=>({});'])),
    'js/shared/api/index.js':'export const request=()=>{};',
    'js/shared/api/client.js':'export const client=()=>{};',
    'js/shared/ui/index.js':'export const render=()=>{};',
    'js/app.js':"import {createFeature} from './catalog/index.js?v=123';createFeature();",
    ...overrides
  }));
}

test('production modules follow public feature entries, MVP boundaries and an acyclic dependency graph',()=>validateArchitecture(productionModules()));
test('production import locations match V8 static module dependencies',()=>compareParser(productionModules()));
test('module import locations ignore comments, strings, regexps and template text',()=>{
  const source=String.raw`
    // import bad from './comment.js';
    /* export {bad} from './block.js'; */
    const text="import bad from './string.js'";
    const regexp=/import bad from '.\/regexp.js'/g;
    if(true) /export bad from '.\/conditional.js'/.test(text);
    const template=\`import bad from './template.js'\`;
    const quotient=20 / 2;
    import './side-effect.js';
    import value,{other as renamed} from './real.js?v=123';
    export {value} from './export.js';
    export * as namespace from './namespace.js';
    import.meta.url;
    const lazy=import('./lazy.js');
  `.replaceAll('\\`','`');
  const entries=moduleImports(source);
  assert.deepEqual(entries.map(entry=>entry.specifier),['./side-effect.js','./real.js?v=123','./export.js','./namespace.js','./lazy.js']);
  for(const entry of entries)assert.equal(source.slice(entry.start,entry.end),entry.specifier);
  compareParser(new Map([['fixture.js',source]]));
});
test('template expressions contribute literal dynamic imports and reject variable specifiers',()=>{
  assert.deepEqual(moduleImports('const x=`text ${import("./lazy.js")}`;').map(entry=>entry.specifier),['./lazy.js']);
  assert.throws(()=>moduleImports('import(path);'),/literal specifier/);
  assert.throws(()=>moduleImports('const x=`${import(path)}`;'),/literal specifier/);
});
test('deliberate feature and shared entry bypasses fail',()=>{
  assert.throws(()=>validateArchitecture(fixture({'js/app.js':"import './catalog/catalog-model.js';",'js/catalog/catalog-model.js':'export const value=1;'})),/public index/);
  assert.throws(()=>validateArchitecture(fixture({'js/catalog/index.js':"import '../player/index.js';"})),/features communicate/);
  assert.throws(()=>validateArchitecture(fixture({'themes/runtime.js':"import '../js/player/index.js';"})),/composition roots/);
  assert.throws(()=>validateArchitecture(fixture({'js/shared/api/index.js':"import '../../player/index.js';"})),/composition roots/);
  assert.throws(()=>validateArchitecture(fixture({'js/catalog/index.js':"import '../shared/api/client.js';"})),/shared public index/);
});
test('deliberate Presenter DOM and View network/model access fail',()=>{
  assert.throws(()=>validateArchitecture(fixture({'js/catalog/catalog-presenter.js':'document.querySelector("main");'})),/presenters must use/);
  assert.throws(()=>validateArchitecture(fixture({'js/catalog/catalog-presenter.js':'const text=`${window.location}`;'})),/presenters must use/);
  assert.throws(()=>validateArchitecture(fixture({'js/catalog/catalog-model.js':'globalThis["document"];'})),/presenters must use/);
  assert.throws(()=>validateArchitecture(fixture({'js/catalog/catalog-view.js':'fetch("/api/browse");'})),/views must not perform/);
  assert.throws(()=>validateArchitecture(fixture({'js/catalog/catalog-view.js':'globalThis["fetch"]("/api/browse");'})),/views must not perform/);
  assert.throws(()=>validateArchitecture(fixture({'js/catalog/catalog-view.js':"import '../shared/api/index.js';"})),/views must not import/);
  assert.throws(()=>validateArchitecture(fixture({'js/catalog/catalog-view.js':"import './catalog-model.js';",'js/catalog/catalog-model.js':'export const policy=()=>{};'})),/models\/presenters/);
  validateArchitecture(fixture({'js/catalog/catalog-presenter.js':'// document.querySelector("x")\nconst text="window.fetch";const pattern=/document\\.querySelector/;export const run=view=>view.render(text);'}));
});
test('deliberate dependency cycles, missing modules and app business IO fail',()=>{
  assert.throws(()=>validateArchitecture(fixture({'js/catalog/index.js':"import './a.js';",'js/catalog/a.js':"import './b.js';",'js/catalog/b.js':"import './a.js';"})),/dependency cycle/);
  assert.throws(()=>validateArchitecture(fixture({'js/app.js':"import './missing.js';"})),/missing module/);
  assert.throws(()=>validateArchitecture(fixture({'js/app.js':'document.querySelector("main");'})),/composition only/);
  assert.throws(()=>validateArchitecture(fixture({'js/app.js':'fetch("/api/config");'})),/composition only/);
  assert.throws(()=>validateArchitecture(fixture({'js/pwa.js':'fetch("/api/config");'})),/composition only/);
  assert.throws(()=>validateArchitecture(fixture({'js/app.js':'globalThis["fetch"](endpoint);'})),/composition only/);
  assert.throws(()=>validateArchitecture(fixture({'js/app.js':'environment.fetch.call(environment,endpoint);'})),/composition only/);
  validateArchitecture(fixture({'js/app.js':'const api=createAdapter(environment.fetch);createApplication({fetch:window.fetch.bind(window)});'}));
  assert.throws(()=>validateArchitecture(fixture({'js/app.js':'const endpoint="/api/browse";'})),/endpoint policy/);
  assert.throws(()=>validateArchitecture(fixture({'js/app.js':'const endpoint=`/api/${name}`;'})),/endpoint policy/);
});
