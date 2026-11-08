import test from 'node:test';
import assert from 'node:assert/strict';
import {PlaybackQueue,PlaybackSession,MAX_QUEUE} from '../src/main/resources/static/js/playback.js';
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
