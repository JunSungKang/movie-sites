import test from 'node:test';
import assert from 'node:assert/strict';
import {durationGroup,durationGroups,topicLabels,waitForPreparation} from '../src/main/resources/static/js/library.js';

test('duration dividers use exact non-overlapping thresholds and keep unknowns separate',()=>{
  const examples=[[0,'unknown'],[null,'unknown'],[undefined,'unknown'],['300','unknown'],[NaN,'unknown'],[Infinity,'unknown'],[-1,'unknown'],[0.5,'under-five'],[299.999,'under-five'],[300,'under-ten'],[599.999,'under-ten'],[600,'under-twenty'],[1199.999,'under-twenty'],[1200,'twenty-plus'],[7200,'twenty-plus']];
  for(const [seconds,id] of examples)assert.equal(durationGroup(seconds).id,id,String(seconds));
  const groups=durationGroups([{path:'short',duration:90},{path:'exact-five',duration:300},{path:'exact-ten',duration:600},{path:'long',duration:1200},{path:'broken',duration:null}]);
  assert.deepEqual(groups.map(g=>g.id),['under-five','under-ten','under-twenty','twenty-plus','unknown']);
  assert.deepEqual(groups.flatMap(g=>g.entries).map(e=>e.path),['short','exact-five','exact-ten','long','broken']);
  assert.equal(durationGroups([]).length,0);
});
test('topic presentation distinguishes recommendations, explicit empty overrides and literal labels',()=>{
  assert.deepEqual(topicLabels({tags:['식습관교육'],tagSource:'filename'}),{tags:['식습관교육'],recommended:true,empty:'주제 미지정'});
  assert.equal(topicLabels({tags:[],tagSource:'metadata'}).empty,'주제 표시 안 함');
  assert.equal(topicLabels({tags:['<literal>',''],tagSource:'metadata'}).recommended,false);
  assert.deepEqual(topicLabels({tags:['<literal>',''],tagSource:'metadata'}).tags,['<literal>']);
  assert.deepEqual(topicLabels({}).tags,[]);
});
test('leaving a collection aborts preparation waits, including an already aborted request',async()=>{
  const controller=new AbortController();const pending=waitForPreparation(controller.signal,10000);controller.abort();
  await assert.rejects(pending,{name:'AbortError'});
  await assert.rejects(waitForPreparation(controller.signal,10000),{name:'AbortError'});
  await waitForPreparation(new AbortController().signal,1);
});
