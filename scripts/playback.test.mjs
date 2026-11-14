import test from 'node:test';
import assert from 'node:assert/strict';
import {PlaybackQueue,PlaybackSession,PlaybackFullscreen,MAX_QUEUE} from '../src/main/resources/static/js/playback.js';
const entry=(id)=>({path:`folder/${id}.mp4`,name:`${id}.mp4`,size:12});
function storage(){const map=new Map();return {getItem:key=>map.get(key),setItem:(key,value)=>map.set(key,value)};}
test('one queue survives reload, isolates media roots, deduplicates and preserves chosen order',()=>{
 const disk=storage(),q=new PlaybackQueue(disk,'root-a');
 assert.equal(q.add([entry('a'),entry('b'),entry('a')]),2);
 q.move(entry('b').path,-1);
 assert.deepEqual(new PlaybackQueue(disk,'root-a').items.map(e=>e.name),['b.mp4','a.mp4']);
 assert.equal(new PlaybackQueue(disk,'root-b').items.length,0);
 q.remove(entry('a').path);assert.equal(new PlaybackQueue(disk,'root-a').items.length,1);
 q.clear();assert.deepEqual(new PlaybackQueue(disk,'root-a').items,[]);
});
test('invalid saved data and denied/quota storage stay recoverable without erasing the original',()=>{
 const disk=storage(),key='moviesites:playback-queue:v1:r';disk.setItem(key,'{broken');
 const q=new PlaybackQueue(disk,'r');assert.equal(q.items.length,0);assert.ok(q.warning);assert.equal(disk.getItem(key),'{broken');
 const blocked=new PlaybackQueue({getItem(){throw Error();},setItem(){throw Error();}},'r');
 blocked.add([entry('a')]);assert.equal(blocked.items.length,1);assert.ok(blocked.warning);assert.equal(blocked.save(),false);
 const unavailable=new PlaybackQueue(undefined,'r');unavailable.add([entry('a')]);assert.ok(unavailable.warning);
});
test('queue bounds saved fields, rejects unsafe paths and caps total entries',()=>{
 const q=new PlaybackQueue(storage(),'r');
 q.add([{path:'../escape.mp4',name:'bad'}, {path:'/absolute.mp4',name:'bad'}, {path:'x\\y',name:'bad'}, {path:'x//y',name:'bad'}, {path:'x/./y',name:'bad'}, {path:'x\0y',name:'bad'}, ...Array.from({length:MAX_QUEUE+10},(_,i)=>entry(i))]);
 assert.equal(q.items.length,MAX_QUEUE);assert.equal(q.items[0].name,'0.mp4');
 q.move(q.items[0].path,-1);assert.equal(q.items[0].name,'0.mp4');
});
test('play all starts at first, next/end advances once, last end finishes and stale end is ignored',()=>{
 const selected=[];let finished=0;
 const s=new PlaybackSession((e,t)=>selected.push([e.name,t]),()=>finished++);
 assert.equal(s.start([]),false);s.start([entry('a'),entry('b')]);const first=s.token;
 assert.equal(s.index,0);assert.equal(s.hasPrevious,false);assert.equal(s.hasNext,true);
 s.ended(first);assert.equal(s.index,1);assert.equal(selected.at(-1)[0],'b.mp4');
 s.ended(first);assert.equal(s.index,1);assert.equal(finished,0);
 s.ended(s.token);assert.equal(finished,1);assert.equal(s.active,false);
 s.ended(s.token);assert.equal(finished,1);
});
test('manual close invalidates pending async work; skip/previous and new sessions replace old tokens',async()=>{
 let release;const gate=new Promise(resolve=>release=resolve),completed=[];
 const s=new PlaybackSession(async(e,token)=>{await gate;if(s.current(token))completed.push(e.name);},()=>{});
 s.start([entry('a'),entry('b')]);const first=s.token;s.next();const second=s.token;s.previous();
 assert.equal(s.current(first),false);assert.equal(s.current(second),false);s.close();release();await gate;await Promise.resolve();
 assert.deepEqual(completed,[]);assert.equal(s.active,false);
 s.start([entry('c')]);assert.equal(s.index,0);assert.equal(s.hasNext,false);s.ended(s.token);assert.equal(s.active,false);
});
test('editing saved queue during playback leaves the active snapshot stable and affects next play all',()=>{
 const q=new PlaybackQueue(storage(),'r');q.add([entry('a'),entry('b')]);
 const selected=[],s=new PlaybackSession(e=>selected.push(e.name),()=>{});s.start(q.items);
 q.clear();q.add([entry('c')]);s.ended(s.token);assert.equal(selected.at(-1),'b.mp4');
 s.start(q.items);assert.equal(selected.at(-1),'c.mp4');assert.equal(s.items.length,1);
});

const settle=async()=>{for(let i=0;i<12;i++)await Promise.resolve();};
function fullscreenFixture() {
 const document=Object.assign(new EventTarget(),{fullscreenElement:null});
 const video=Object.assign(new EventTarget(),{readyState:0,webkitDisplayingFullscreen:false});
 const target={contains:element=>element===video};let exits=0;
 document.exitFullscreen=()=>{exits++;document.fullscreenElement=null;return Promise.resolve();};
 return {document,video,target,exits:()=>exits};
}
test('late fullscreen completion after close exits only the owned player, without reopening it',async()=>{
 const f=fullscreenFixture();let resolve;
 f.target.requestFullscreen=()=>new Promise(r=>resolve=r);
 const fullscreen=new PlaybackFullscreen(f.document,f.target,f.video);
 fullscreen.start();fullscreen.close();f.document.fullscreenElement=f.target;resolve();await settle();
 assert.equal(f.document.fullscreenElement,null);assert.equal(f.exits(),1);assert.equal(fullscreen.enabled,false);
});
test('native WebKit waits for playable media, exits before reset and never re-enters after closing',()=>{
 const f=fullscreenFixture(),calls=[];
 f.video.webkitEnterFullscreen=()=>{calls.push('enter');f.video.webkitDisplayingFullscreen=true;};
 f.video.webkitExitFullscreen=()=>{calls.push('exit');f.video.webkitDisplayingFullscreen=false;};
 const fullscreen=new PlaybackFullscreen(f.document,f.target,f.video);
 fullscreen.start();assert.deepEqual(calls,[]);f.video.readyState=4;fullscreen.playing();assert.equal(fullscreen.active,true);
 fullscreen.close();fullscreen.playing();assert.deepEqual(calls,['enter','exit']);assert.equal(fullscreen.active,false);
});
test('prefixed fullscreen is supported and closing does not exit another element',async()=>{
 const f=fullscreenFixture();let entered=0,exited=0;
 f.target.webkitRequestFullscreen=()=>{entered++;f.document.webkitFullscreenElement=f.target;};
 delete f.document.exitFullscreen;f.document.webkitExitFullscreen=()=>{exited++;f.document.webkitFullscreenElement=null;};
 const fullscreen=new PlaybackFullscreen(f.document,f.target,f.video);
 fullscreen.start();await settle();assert.equal(fullscreen.active,true);assert.equal(entered,1);
 fullscreen.close();await settle();assert.equal(exited,1);
 f.document.webkitFullscreenElement={};fullscreen.close();assert.equal(exited,1);
});
test('legacy void fullscreen calls that enter after close are exited on their late notification',async()=>{
 const f=fullscreenFixture();let exits=0;
 f.target.webkitRequestFullscreen=()=>{};
 delete f.document.exitFullscreen;f.document.webkitExitFullscreen=()=>{exits++;f.document.webkitFullscreenElement=null;};
 const fullscreen=new PlaybackFullscreen(f.document,f.target,f.video);
 fullscreen.start();await settle();fullscreen.close();f.document.webkitFullscreenElement=f.target;
 f.document.dispatchEvent(new Event('webkitfullscreenchange'));await settle();
 assert.equal(f.document.webkitFullscreenElement,null);assert.equal(exits,1);
 const native=fullscreenFixture();native.video.readyState=4;native.video.webkitEnterFullscreen=()=>{};
 native.video.webkitExitFullscreen=()=>{native.video.webkitDisplayingFullscreen=false;};
 const nativeFullscreen=new PlaybackFullscreen(native.document,native.target,native.video);
 nativeFullscreen.start();nativeFullscreen.close();native.video.webkitDisplayingFullscreen=true;
 native.video.dispatchEvent(new Event('webkitbeginfullscreen'));
 assert.equal(native.video.webkitDisplayingFullscreen,false);
});
test('legacy void exit notifications preserve a new playback request after immediate reopening',async()=>{
 const f=fullscreenFixture();let enters=0;
 f.target.webkitRequestFullscreen=()=>{enters++;f.document.webkitFullscreenElement=f.target;f.document.dispatchEvent(new Event('webkitfullscreenchange'));};
 delete f.document.exitFullscreen;f.document.webkitExitFullscreen=()=>{};
 const fullscreen=new PlaybackFullscreen(f.document,f.target,f.video);
 fullscreen.start();await settle();fullscreen.close();await settle();fullscreen.start();
 f.document.webkitFullscreenElement=null;f.document.dispatchEvent(new Event('webkitfullscreenchange'));await settle();
 assert.equal(fullscreen.expanded,true);assert.equal(fullscreen.active,true);assert.equal(enters,2);
});
test('failed exit does not turn a later explicit user exit into another fullscreen request',async()=>{
 const f=fullscreenFixture();let enters=0;
 f.target.requestFullscreen=()=>{enters++;f.document.fullscreenElement=f.target;f.document.dispatchEvent(new Event('fullscreenchange'));return Promise.resolve();};
 f.document.exitFullscreen=()=>Promise.reject(new Error('inactive document'));
 const fullscreen=new PlaybackFullscreen(f.document,f.target,f.video);
 fullscreen.start();await settle();fullscreen.close();await settle();fullscreen.start();
 f.document.fullscreenElement=null;f.document.dispatchEvent(new Event('fullscreenchange'));await settle();
 assert.equal(fullscreen.expanded,false);assert.equal(enters,1);
});
test('a delayed native exit keeps a reopened video pending until its metadata is available',()=>{
 const f=fullscreenFixture();let enters=0;f.video.readyState=4;
 f.video.webkitEnterFullscreen=()=>{enters++;f.video.webkitDisplayingFullscreen=true;f.video.dispatchEvent(new Event('webkitbeginfullscreen'));};
 f.video.webkitExitFullscreen=()=>{};
 const fullscreen=new PlaybackFullscreen(f.document,f.target,f.video);
 fullscreen.start();fullscreen.close();f.video.readyState=0;fullscreen.start();
 f.video.webkitDisplayingFullscreen=false;f.video.dispatchEvent(new Event('webkitendfullscreen'));
 assert.equal(fullscreen.expanded,true);f.video.readyState=4;fullscreen.playing();assert.equal(enters,2);
});
test('unsupported/rejected native fullscreen is non-fatal and user exit is not undone by playing events',()=>{
 const f=fullscreenFixture(),fullscreen=new PlaybackFullscreen(f.document,f.target,f.video);
 assert.equal(fullscreen.supported,false);fullscreen.start();fullscreen.close();
 let entered=0;f.video.readyState=4;f.video.webkitEnterFullscreen=()=>{entered++;throw new Error('user gesture required');};
 fullscreen.start();fullscreen.playing();assert.equal(entered,1);assert.equal(fullscreen.active,false);
 f.video.webkitEnterFullscreen=()=>{entered++;f.video.webkitDisplayingFullscreen=true;};fullscreen.request();
 f.video.webkitDisplayingFullscreen=false;f.video.dispatchEvent(new Event('webkitendfullscreen'));fullscreen.playing();assert.equal(entered,2);
});
