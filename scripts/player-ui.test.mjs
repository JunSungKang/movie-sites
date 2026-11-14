import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import {PlaybackQueue,PlaybackSession,PlaybackFullscreen,MAX_QUEUE} from '../src/main/resources/static/js/playback.js';
import {DirectoryCache,PageHistory} from '../src/main/resources/static/js/cache.js';
import {durationGroups,topicLabels,waitForPreparation} from '../src/main/resources/static/js/library.js';
const entry=name=>({path:`folder/${name}.mp4`,name:`${name}.mp4`,size:12});
const flush=async()=>{for(let i=0;i<15;i++)await Promise.resolve();};
// Run the actual app event wiring with a small DOM/media adapter. No duplicated player logic.
async function app({inspect,random,initialPath='folder',play=()=>Promise.resolve(),entries=[],denyFullscreen=false}={}) {
 let document;
 const fullscreenCalls={enter:0,exit:0};
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
  load(){this.ended=false;this.error=null;this.currentTime=0;this.readyState=0;}
  async play(){await play();this.paused=false;this.readyState=4;this.dispatchEvent(new Event('playing'));}
 }
 const ids=new Map([...readFileSync('src/main/resources/static/index.html','utf8').matchAll(/id="([^"]+)"/g)].map(m=>[m[1],new Element()]));
 document=Object.assign(new EventTarget(),{fullscreenElement:null,activeElement:new Element('button'),getElementById:id=>ids.get(id),createElement:tag=>new Element(tag),createElementNS:(ns,tag)=>new Element(tag),querySelectorAll:()=>[]});
 ids.get('player-fullscreen').append(ids.get('video-player'));ids.get('player-dialog').append(ids.get('player-fullscreen'));
 ids.get('player-fullscreen').requestFullscreen=()=>{fullscreenCalls.enter++;if(denyFullscreen)return Promise.reject(new Error('permission denied'));document.fullscreenElement=ids.get('player-fullscreen');document.dispatchEvent(new Event('fullscreenchange'));return Promise.resolve();};
 document.exitFullscreen=()=>{fullscreenCalls.exit++;document.fullscreenElement=null;document.dispatchEvent(new Event('fullscreenchange'));return Promise.resolve();};
 const disk=new Map(),localStorage={getItem:key=>disk.get(key),setItem:(key,value)=>disk.set(key,value)};
 const window={localStorage,addEventListener:()=>{},scrollTo:(x,y)=>{if(typeof x==='object'){context.scrollY=x.top;}else{context.scrollX=x;context.scrollY=y;}}};
 const response=data=>({ok:true,json:async()=>data});
 const ready={state:'ready',version:'video-v1',streamToken:'original.signature'};
 const context=vm.createContext({document,window,location:{href:'http://test/'+(initialPath?'?path='+encodeURIComponent(initialPath):''),reload(){}},history:{pushState(){}},scrollX:0,scrollY:321,URL,URLSearchParams,AbortController,Event,queueMicrotask,
  setTimeout:(fn,ms)=>{if(ms<2000)queueMicrotask(fn);return 1;},clearTimeout:()=>{},IntersectionObserver:class{observe(){}disconnect(){}},PlaybackQueue,PlaybackSession,PlaybackFullscreen,MAX_QUEUE,DirectoryCache,PageHistory,
  durationGroups,topicLabels,waitForPreparation,applyTheme:async()=>({rootTitle:'Videos',rootCollection:'Collection'}),
  fetch:async(url,options)=>url==='/api/config'?response({rootId:'r',cacheTtlSeconds:120,folderLabels:{},sourceUrl:'#'}):url.startsWith('/api/browse')?response({entries,state:'ready'}):url.startsWith('/api/random-playback')&&random?random(url,options,response):inspect?inspect(url,options,response,ready):response(ready)});
 const source=readFileSync('src/main/resources/static/js/app.js','utf8').replace(/^import .*;$/gm,'');
 const exposed=await vm.runInContext(`(async()=>{${source}\nreturn {startPlayback,startRandom,loadFolder,closePlayer,playback,state,playbackQueue,enrich,renderVideo};})()`,context);
 return {...exposed,ids,context,document,fullscreenCalls,click:id=>ids.get(id).dispatchEvent(new Event('click')),end:()=>{ids.get('video-player').ended=true;ids.get('video-player').dispatchEvent(new Event('ended'));}};
}
test('actual player wiring advances on ended, closes final popup and preserves selection, scroll and saved queue',async()=>{
 const a=await app();a.playbackQueue.add([entry('first'),entry('second')]);a.startPlayback(a.playbackQueue.items);await flush();
 assert.equal(a.ids.get('player-dialog').open,true);assert.equal(a.ids.get('video-player').paused,false);assert.match(a.ids.get('player-title').textContent,/first/);
 assert.equal(a.document.fullscreenElement,a.ids.get('player-fullscreen'));assert.equal(a.fullscreenCalls.enter,1);
 a.end();await flush();assert.match(a.ids.get('player-title').textContent,/second/);assert.equal(a.ids.get('player-dialog').open,true);
 assert.equal(a.document.fullscreenElement,a.ids.get('player-fullscreen'));assert.equal(a.fullscreenCalls.enter,1);
 a.context.scrollY=0;a.end();await flush();assert.equal(a.ids.get('player-dialog').open,false);assert.equal(a.context.scrollY,321);assert.equal(a.context.location.href,'http://test/?path=folder');assert.equal(a.playbackQueue.items.length,2);assert.equal(a.ids.get('video-player').src,'');
 assert.equal(a.document.fullscreenElement,null);assert.equal(a.fullscreenCalls.exit,1);
 assert.equal(a.ids.get('player-dialog').dataset.expanded,'false');
});
test('fullscreen request happens synchronously while media inspection is still waiting',async()=>{
 let release;const pending=new Promise(r=>release=r);
 const a=await app({inspect:async(url,options,response,ready)=>{await pending;return response(ready);}});
 a.startPlayback([entry('first')]);assert.equal(a.fullscreenCalls.enter,1);assert.equal(a.ids.get('video-player').paused,true);
 a.closePlayer();release();await flush();assert.equal(a.document.fullscreenElement,null);assert.equal(a.ids.get('player-dialog').open,false);
});
test('fullscreen denial keeps playback working and a manual retry can succeed without replaying media',async()=>{
 const a=await app({denyFullscreen:true});a.startPlayback([entry('first')]);await flush();
 assert.equal(a.ids.get('video-player').paused,false);assert.equal(a.ids.get('player-fullscreen-button').hidden,false);
 assert.equal(a.ids.get('player-dialog').dataset.expanded,'true');
 a.ids.get('player-fullscreen').requestFullscreen=()=>{a.fullscreenCalls.enter++;a.document.fullscreenElement=a.ids.get('player-fullscreen');a.document.dispatchEvent(new Event('fullscreenchange'));return Promise.resolve();};
 a.click('player-fullscreen-button');await flush();assert.equal(a.document.fullscreenElement,a.ids.get('player-fullscreen'));assert.equal(a.ids.get('video-player').paused,false);
 a.end();await flush();assert.equal(a.document.fullscreenElement,null);assert.equal(a.ids.get('player-dialog').open,false);
});
test('explicit OS fullscreen exit keeps the viewport expanded and playing does not force re-entry',async()=>{
 const a=await app();a.startPlayback([entry('first')]);await flush();
 a.document.fullscreenElement=null;a.document.dispatchEvent(new Event('fullscreenchange'));
 assert.equal(a.ids.get('player-dialog').dataset.expanded,'true');
 a.ids.get('video-player').dispatchEvent(new Event('playing'));assert.equal(a.fullscreenCalls.enter,1);
 a.closePlayer();
});
test('a previous asynchronous fullscreen exit does not shrink a newly started video',async()=>{
 const a=await app();a.startPlayback([entry('first')]);await flush();let finishExit;
 a.document.exitFullscreen=()=>new Promise(resolve=>{finishExit=()=>{a.document.fullscreenElement=null;a.document.dispatchEvent(new Event('fullscreenchange'));resolve();};});
 a.closePlayer();a.startPlayback([entry('second')]);finishExit();await flush();
 assert.equal(a.ids.get('player-dialog').open,true);assert.equal(a.ids.get('video-player').paused,false);assert.match(a.ids.get('player-title').textContent,/second/);
 assert.equal(a.ids.get('player-dialog').dataset.expanded,'true');assert.equal(a.document.fullscreenElement,null);assert.equal(a.fullscreenCalls.enter,1);
 a.closePlayer();finishExit();await flush();
});
test('autoplay denial exposes a working resume button',async()=>{
 let allowed=false;const a=await app({play:()=>allowed?Promise.resolve():Promise.reject(Object.assign(new Error(),{name:'NotAllowedError'}))});
 a.startPlayback([entry('first')]);await flush();assert.equal(a.ids.get('player-resume').hidden,false);assert.equal(a.ids.get('video-player').paused,true);
 allowed=true;a.click('player-resume');await flush();assert.equal(a.ids.get('player-resume').hidden,true);assert.equal(a.ids.get('video-player').paused,false);
 a.closePlayer();
});
test('native controls consume pending automatic entry even when autoplay was denied',async()=>{
 let allowed=false,enters=0;
 const a=await app({play:()=>allowed?Promise.resolve():Promise.reject(Object.assign(new Error(),{name:'NotAllowedError'}))});
 const target=a.ids.get('player-fullscreen'),video=a.ids.get('video-player');delete target.requestFullscreen;
 video.webkitEnterFullscreen=()=>{enters++;video.webkitDisplayingFullscreen=true;};
 video.webkitExitFullscreen=()=>{video.webkitDisplayingFullscreen=false;};
 a.startPlayback([entry('first')]);await flush();assert.equal(video.paused,true);assert.equal(enters,0);
 video.webkitDisplayingFullscreen=true;video.dispatchEvent(new Event('webkitbeginfullscreen'));
 video.webkitDisplayingFullscreen=false;video.dispatchEvent(new Event('webkitendfullscreen'));
 allowed=true;await video.play();await flush();
 assert.equal(enters,0);assert.equal(a.ids.get('player-dialog').dataset.expanded,'true');
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

const randomPage=(url,items,extra={})=>{const p=new URL(url,'http://test').searchParams;return {state:'ready',query:'random-id',path:p.get('path'),minutes:Number(p.get('minutes')),entries:items.map(e=>({...e,version:e.version??'video-v1'})),...extra};};
test('all five home duration buttons request the entire tree and leave the saved queue intact',async()=>{
 for(const minutes of [10,20,30,40,50]){
  const calls=[];const a=await app({initialPath:'',random:async(url,options,response)=>{calls.push([url,options]);return response(randomPage(url,[{...entry('chosen'),duration:61}]));}});
  a.playbackQueue.add([entry('saved')]);a.click('random-'+minutes);assert.equal(a.ids.get('player-dialog').open,true);assert.equal(a.fullscreenCalls.enter,1);await flush();
  const params=new URL(calls[0][0],'http://test').searchParams;assert.equal(params.get('path'),'');assert.equal(params.get('minutes'),String(minutes));
  assert.equal(a.playback.items.length,1);assert.equal(a.playback.items[0].name,'chosen.mp4');assert.equal(a.playbackQueue.items[0].name,'saved.mp4');
  assert.match(a.ids.get('random-summary').textContent,/1편/);a.end();await flush();assert.equal(a.ids.get('player-dialog').open,false);
 }
});
test('category random playback includes descendants and closes after its final item without changing saved order',async()=>{
 const items=[{path:'folder/deep/first.mp4',name:'first.mp4',size:12,duration:60},{...entry('second'),duration:60}];
 const a=await app({random:async(url,options,response)=>response(randomPage(url,items))});a.playbackQueue.add([entry('saved')]);a.click('random-10');await flush();
 assert.match(a.ids.get('random-scope').textContent,/folder/);assert.equal(a.playback.items[0].path,'folder/deep/first.mp4');
 a.end();await flush();assert.match(a.ids.get('player-title').textContent,/second/);a.end();await flush();
 assert.equal(a.ids.get('player-dialog').open,false);assert.equal(a.playbackQueue.items[0].name,'saved.mp4');assert.equal(a.context.scrollY,321);
});
test('wrong-category siblings, duplicate paths and over-budget server responses never start playback',async()=>{
 for(const items of [[{path:'folder-other/a.mp4',name:'a.mp4',duration:60}],[{...entry('a'),duration:60},{...entry('a'),duration:60}],[{...entry('a'),duration:600.0001}]]){
  const a=await app({random:async(url,options,response)=>response(randomPage(url,items))});a.click('random-10');await flush();
  assert.equal(a.playback.active,false);assert.equal(a.ids.get('retry-player').hidden,false);assert.equal(a.ids.get('video-player').src,'');a.closePlayer();
 }
});
test('close aborts a pending random response and never reopens the player when it arrives late',async()=>{
 let resolve,signal;const gate=new Promise(r=>resolve=r);
 const a=await app({random:async(url,options,response)=>{signal=options.signal;await gate;return response(randomPage(url,[{...entry('late'),duration:60}]));}});
 a.click('random-20');a.closePlayer();assert.equal(signal.aborted,true);resolve();await flush();
 assert.equal(a.playback.active,false);assert.equal(a.ids.get('player-dialog').open,false);assert.equal(a.ids.get('video-player').src,'');
});
test('navigation cancels a preparing scope-bound query and sends its matching cancellation request',async()=>{
 const calls=[];const a=await app({random:async(url,options,response)=>{calls.push([url,options]);return response(randomPage(url,[],{state:'preparing',videos:15}));}});
 a.click('random-30');await flush();await a.loadFolder('other');await flush();
 assert.equal(a.ids.get('player-dialog').open,false);assert.equal(a.playback.active,false);
 const deletion=calls.find(([,options])=>options.method==='DELETE');assert.ok(deletion);
 const p=new URL(deletion[0],'http://test').searchParams;assert.equal(p.get('path'),'folder');assert.equal(p.get('minutes'),'30');assert.equal(p.get('query'),'random-id');
});
test('an empty random result shows recovery and retry can build a new playable list',async()=>{
 let attempt=0;const a=await app({random:async(url,options,response)=>response(randomPage(url,options.method==='DELETE'||++attempt===1?[]:[{...entry('retry'),duration:60}],{message:'다른 시간으로 골라 주세요.'}))});
 a.click('random-40');await flush();assert.equal(a.playback.active,false);assert.equal(a.ids.get('retry-player').textContent,'다시 고르기');
 a.click('retry-player');await flush();assert.equal(a.playback.active,true);assert.match(a.ids.get('player-title').textContent,/retry/);a.closePlayer();
});
test('a selected random source replaced by a different video is blocked instead of exceeding the chosen budget',async()=>{
 const a=await app({random:async(url,options,response)=>response(randomPage(url,[{...entry('changed'),duration:60,version:'selected-old-version'}]))});
 a.click('random-10');await flush();assert.equal(a.ids.get('video-player').src,'');assert.match(a.ids.get('player-state').textContent,/파일이 바뀌었/);assert.equal(a.ids.get('retry-player').hidden,true);a.closePlayer();
});
test('refresh remains bound to its category across navigation and is consumed only by a completed selection',async()=>{
 const calls=[];const a=await app({random:async(url,options,response)=>{calls.push([url,options]);const path=new URL(url,'http://test').searchParams.get('path');return response(randomPage(url,[{path:path+'/clip.mp4',name:'clip.mp4',duration:60}]));}});
 await a.loadFolder('folder',{force:true});await a.loadFolder('other');await a.startRandom(10);a.closePlayer();await flush();
 const other=new URL(calls.find(([u,o])=>o.method!=='DELETE'&&new URL(u,'http://test').searchParams.get('path')==='other')[0],'http://test').searchParams;assert.equal(other.has('refresh'),false);
 await a.loadFolder('folder');await a.startRandom(10);a.closePlayer();await flush();
 const folderCalls=calls.filter(([u,o])=>o.method!=='DELETE'&&new URL(u,'http://test').searchParams.get('path')==='folder');assert.equal(new URL(folderCalls[0][0],'http://test').searchParams.get('refresh'),'true');
 await a.startRandom(10);a.closePlayer();await flush();
 const last=calls.filter(([u,o])=>o.method!=='DELETE'&&new URL(u,'http://test').searchParams.get('path')==='folder').at(-1);assert.equal(new URL(last[0],'http://test').searchParams.has('refresh'),false);
});
