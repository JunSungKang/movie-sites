import {t, setLocale, localeTag} from './i18n.js';
// Non-overlapping intervals use exact seconds, not rounded display strings.
export const DURATION_GROUPS = [
  {id:'under-five',get label(){return t('5분 미만');},limit:300},
  {id:'under-ten',get label(){return t('5분 이상 · 10분 미만');},limit:600},
  {id:'under-twenty',get label(){return t('10분 이상 · 20분 미만');},limit:1200},
  {id:'twenty-plus',get label(){return t('20분 이상');},limit:Infinity},
  {id:'unknown',get label(){return t('길이를 확인하지 못한 영상');}}
];
export function durationGroup(seconds) {
  if (typeof seconds !== 'number' || !Number.isFinite(seconds) || seconds <= 0) return DURATION_GROUPS.at(-1);
  return DURATION_GROUPS.find(group=>seconds < group.limit);
}
export function durationGroups(videos) {
  return DURATION_GROUPS.map(group=>({...group,entries:videos.filter(entry=>durationGroup(entry.duration).id===group.id)})).filter(group=>group.entries.length);
}
export function topicLabels(entry) {
  const tags=Array.isArray(entry.tags)?entry.tags.filter(tag=>typeof tag==='string'&&tag.trim()&&tag.length<=40).slice(0,8):[];
  return {tags,recommended:entry.tagSource==='filename',empty:entry.tagSource==='metadata'?t('주제 표시 안 함'):t('주제 미지정')};
}
export function waitForPreparation(signal, milliseconds=1200) {
  return new Promise((resolve,reject)=>{
    const abort=()=>{clearTimeout(timer);const error=new Error('Listing cancelled');error.name='AbortError';reject(error);};
    const timer=setTimeout(()=>{signal?.removeEventListener('abort',abort);resolve();},milliseconds);
    if(signal?.aborted)abort();else signal?.addEventListener('abort',abort,{once:true});
  });
}
