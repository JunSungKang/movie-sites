import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {t,setLocale,localeTag} from '../src/main/resources/static/js/i18n.js';
import {durationGroups,topicLabels} from '../src/main/resources/static/js/library.js';
test('locale switches packaged copy, interpolates values literally and preserves operator tags',()=>{
 setLocale('en');assert.equal(localeTag(),'en-US');assert.equal(t('다시 확인'),'Try again');
 assert.equal(t`최대 ${10}분 랜덤 재생`,'Random play · up to 10 min');
 assert.equal(durationGroups([{duration:299}])[0].label,'Under 5 minutes');
 const tags=['식습관교육','<script>'];assert.deepEqual(topicLabels({tags}).tags,tags);
 setLocale('ko');assert.equal(localeTag(),'ko-KR');assert.equal(t`최대 ${20}분 랜덤 재생`,'최대 20분 랜덤 재생');
 assert.equal(durationGroups([{duration:300}])[0].label,'5분 이상 · 10분 미만');
 assert.throws(()=>setLocale('fr'));assert.equal(localeTag(),'ko-KR');
});
test('static English/Korean entry pages preserve exactly the same controls and no private data',()=>{
 const page=name=>readFileSync('src/main/resources/static/'+name,'utf8');
 const en=page('index.html'),ko=page('index.ko.html');
 assert.match(en,/<html lang="en"/);assert.match(ko,/<html lang="ko"/);assert.doesNotMatch(en,/[가-힣]/);
 const ids=s=>[...s.matchAll(/id="([^"]+)"/g)].map(m=>m[1]);assert.deepEqual(ids(en),ids(ko));
});
