import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import {PlaybackQueue,PlaybackSession,MAX_QUEUE} from '../src/main/resources/static/js/playback.js';
import {DirectoryCache,PageHistory} from '../src/main/resources/static/js/cache.js';
import {durationGroups,topicLabels,waitForPreparation} from '../src/main/resources/static/js/library.js';
const entry=name=>({path:`folder/${name}.mp4`,name:`${name}.mp4`,size:12});
const flush=async()=>{for(let i=0;i<15;i++)await Promise.resolve();};
// Run the actual app event wiring with a small DOM/media adapter. No duplicated player logic.
async function app({inspect,play=()=>Promise.resolve(),entries=[]}={}) {
 let document;
 class Element extends EventTarget {
  constructor(tag='div'){super();this.tagName=tag;this.children=[];this.dataset={};this.attributes={};this.classList={add:()=>{}};this.hidden=false;this.isConnected=true;this.paused=true;this.open=false;this.ended=false;this.error=null;this.namespaceURI='svg';}
  append(...children){this.children.push(...children);for(const c of children)c.parent=this;}
  prepend(child){this.children.unshift(child);child.parent=this;}
  replaceChildren(...children){this.children=[];this.append(...children);}
  setAttribute(key,value){this.attributes[key]=String(value);}
  removeAttribute(key){delete this.attributes[key];if(key==='src')this.src='';}
  querySelectorAll(selector){return this.children.flatMap(c=>[...(selector==='button'&&c.tagName==='button'||selector==='.enqueue-button'&&c.className?.split(' ').includes('enqueue-button')?[c]:[]),...c.querySelectorAll(selector)]);}
  querySelector(){return null;}
  focus(){document.activeElement=this;}
  contains(el){return el===this||this.children.some(c=>c.contains(el));}
  showModal(){this.open=true;}
  close(){this.open=false;queueMicrotask(()=>this.dispatchEvent(new Event('close')));}
  pause(){this.paused=true;}
  load(){this.ended=false;this.error=null;this.currentTime=0;}
  async play(){await play();this.paused=false;}
 }
 const ids=new Map([...readFileSync('src/main/resources/static/index.html','utf8').matchAll(/id="([^"]+)"/g)].map(m=>[m[1],new Element()]));
 document={activeElement:new Element('button'),getElementById:id=>ids.get(id),createElement:tag=>new Element(tag),createElementNS:(ns,tag)=>new Element(tag),querySelectorAll:()=>[]};
 const disk=new Map(),localStorage={getItem:key=>disk.get(key),setItem:(key,value)=>disk.set(key,value)};
 const window={localStorage,addEventListener:()=>{},scrollTo:(x,y)=>{if(typeof x==='object'){context.scrollY=x.top;}else{context.scrollX=x;context.scrollY=y;}}};
 const response=data=>({ok:true,json:async()=>data});
 const ready={state:'ready',version:'video-v1',streamToken:'original.signature'};
 const context=vm.createContext({document,window,location:{href:'http://test/?path=folder',reload(){}},history:{pushState(){}},scrollX:0,scrollY:321,URL,URLSearchParams,AbortController,Event,queueMicrotask,
  setTimeout:(fn,ms)=>{if(ms<2000)queueMicrotask(fn);return 1;},clearTimeout:()=>{},IntersectionObserver:class{observe(){}disconnect(){}},PlaybackQueue,PlaybackSession,MAX_QUEUE,DirectoryCache,PageHistory,
  durationGroups,topicLabels,waitForPreparation,applyTheme:async()=>({rootTitle:'Videos',rootCollection:'Collection'}),
  fetch:async(url,options)=>url==='/api/config'?response({rootId:'r',cacheTtlSeconds:120,folderLabels:{},sourceUrl:'#'}):url.startsWith('/api/browse')?response({entries,state:'ready'}):inspect?inspect(url,options,response,ready):response(ready)});
 const source=readFileSync('src/main/resources/static/js/app.js','utf8').replace(/^import .*;$/gm,'');
 const exposed=await vm.runInContext(`(async()=>{${source}\nreturn {startPlayback,closePlayer,playback,state,playbackQueue,enrich,renderVideo};})()`,context);
 return {...exposed,ids,context,click:id=>ids.get(id).dispatchEvent(new Event('click')),end:()=>{ids.get('video-player').ended=true;ids.get('video-player').dispatchEvent(new Event('ended'));}};
}
test('actual player wiring advances on ended, closes final popup and preserves selection, scroll and saved queue',async()=>{
 const a=await app();a.playbackQueue.add([entry('first'),entry('second')]);a.startPlayback(a.playbackQueue.items);await flush();
 assert.equal(a.ids.get('player-dialog').open,true);assert.equal(a.ids.get('video-player').paused,false);assert.match(a.ids.get('player-title').textContent,/first/);
 a.end();await flush();assert.match(a.ids.get('player-title').textContent,/second/);assert.equal(a.ids.get('player-dialog').open,true);
 a.context.scrollY=0;a.end();await flush();assert.equal(a.ids.get('player-dialog').open,false);assert.equal(a.context.scrollY,321);assert.equal(a.context.location.href,'http://test/?path=folder');assert.equal(a.playbackQueue.items.length,2);assert.equal(a.ids.get('video-player').src,'');
});
test('autoplay denial exposes a working resume button',async()=>{
 let allowed=false;const a=await app({play:()=>allowed?Promise.resolve():Promise.reject(Object.assign(new Error(),{name:'NotAllowedError'}))});
 a.startPlayback([entry('first')]);await flush();assert.equal(a.ids.get('player-resume').hidden,false);assert.equal(a.ids.get('video-player').paused,true);
 allowed=true;a.click('player-resume');await flush();assert.equal(a.ids.get('player-resume').hidden,true);assert.equal(a.ids.get('video-player').paused,false);
 a.closePlayer();
});
test('closing while inspection is pending aborts and prevents late response from reopening or playing',async()=>{
 let resolve,signal;const pending=new Promise(r=>resolve=r);
 const a=await app({inspect:async(url,options,response,ready)=>{signal=options.signal;await pending;return response(ready);}});
 a.startPlayback([entry('first')]);a.click('close-player');assert.equal(signal.aborted,true);resolve();await flush();
 assert.equal(a.ids.get('player-dialog').open,false);assert.equal(a.state.player,null);assert.equal(a.ids.get('video-player').src,'');assert.equal(a.ids.get('video-player').paused,true);
});
test('missing file stays recoverable, next skips it, and Escape cancels pending playback',async()=>{
 const a=await app({inspect:async(url,options,response,ready)=>url.includes('missing')?{ok:false,status:404,json:async()=>({message:'Missing'})}:response(ready)});
 a.startPlayback([entry('missing'),entry('good')]);await flush();assert.equal(a.ids.get('retry-player').hidden,false);assert.match(a.ids.get('player-state').textContent,/찾을 수 없/);
 a.click('player-next');await flush();assert.equal(a.ids.get('video-player').paused,false);assert.match(a.ids.get('player-title').textContent,/good/);
 const cancel=new Event('cancel',{cancelable:true});a.ids.get('player-dialog').dispatchEvent(cancel);await flush();assert.equal(cancel.defaultPrevented,true);assert.equal(a.ids.get('player-dialog').open,false);assert.equal(a.playback.active,false);
});

test('early curated thumbnail does not stop metadata polling before duration is available',async()=>{
 let requests=0;
 const a=await app({inspect:async(url,options,response)=>response({state:++requests===1?'analyzing':'ready',thumbnailState:'ready',thumbnailVersion:'selected-v1',version:'video-v1',duration:requests===1?null:3,width:requests===1?null:640,height:requests===1?null:360})});
 const row=a.renderVideo(entry('first'));await a.enrich({entry:entry('first'),row,generation:a.state.generation});
 assert.equal(requests,2);assert.equal(row._time.textContent,'0:03');assert.match(row._image.src,/v=selected-v1/);
});
test('actual list renders duration separators, initial duration badges and honest topic chips',async()=>{
 const a=await app({entries:[{...entry('short'),kind:'video',duration:90,tags:['식습관교육'],tagSource:'filename'},{...entry('exact-five'),kind:'video',duration:300,tags:['<custom topic>'],tagSource:'metadata'},{...entry('broken'),kind:'video',duration:null,tags:[],tagSource:'none'}]});
 const children=a.ids.get('video-list').children;
 assert.deepEqual(children.filter(e=>e.className==='duration-divider').map(e=>e.textContent),['5분 미만','5분 이상 · 10분 미만','길이를 확인하지 못한 영상']);
 const rows=children.filter(e=>e.className==='video-entry');assert.equal(rows.length,3);assert.equal(rows[0]._time.textContent,'1:30');assert.equal(rows[1]._time.textContent,'5:00');
 const tags=rows.map(row=>row.children[1].children.find(e=>e.className==='topic-tags'));
 assert.equal(tags[0].attributes['aria-label'],'파일명 기반 추천 주제');assert.equal(tags[0].children[1].textContent,'식습관교육');
 assert.equal(tags[1].children[0].textContent,'<custom topic>');assert.equal(tags[1].children[0].children.length,0);
 assert.equal(tags[2].children[0].textContent,'주제 미지정');
});
