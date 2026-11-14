import {applyTheme} from '../themes/runtime.js?v=8dad06ab9ac9f4d3';
import {PlaybackQueue,PlaybackSession,PlaybackFullscreen,MAX_QUEUE} from './playback.js?v=afae23a0f0fb290b';
import {DirectoryCache,PageHistory} from './cache.js?v=cb07f2467980d05a';
import {durationGroups,topicLabels,waitForPreparation} from './library.js?v=9b9ec7046b3559fb';

const $=id=>document.getElementById(id);
const cache=new DirectoryCache();
const state={config:null,path:'',history:new PageHistory(),listing:null,generation:0,requests:new Map(),metadata:new Map(),queue:[],workers:0,player:null,playerTimer:null};
let observer, playbackQueue;
const playback = new PlaybackSession(showPlayer, () => { closePlayer(); toast('모든 영상의 재생이 끝났습니다.'); });
const fullscreen = new PlaybackFullscreen(document, $('player-fullscreen'), $('video-player'), updateFullscreenButton);
function updateFullscreenButton() { $('player-fullscreen-button').hidden = !fullscreen.supported || fullscreen.active; $('player-dialog').dataset.expanded = String(fullscreen.expanded); }
updateFullscreenButton();
let selectionPosition;
function queueChanged() { renderQueue(); $('queue-warning').hidden=!playbackQueue.warning; if (playbackQueue.warning) { $('queue-warning').textContent=playbackQueue.warning; $('queue-warning').hidden=false; } }
function enqueue(entries) {
  const count=playbackQueue.add(entries); queueChanged();
  toast(count?`${count}개 영상을 담았습니다.`:playbackQueue.items.length>=MAX_QUEUE?`최대 ${MAX_QUEUE}개까지 담을 수 있습니다.`:'이미 대기열에 담긴 영상입니다.');
}
function renderQueue() {
  if(!playbackQueue)return;
  const items=playbackQueue.items;
  $('queue-count').textContent=items.length+'개'; $('queue-empty').hidden=items.length>0;
  $('queue-play').disabled=!items.length; $('queue-clear').disabled=!items.length;
  $('queue-description').textContent=playback.active?'재생 중 바꾼 순서는 다음 전체 재생부터 적용됩니다.':'보고 싶은 영상을 담고 순서대로 재생하세요.';
  const focusPath=document.activeElement?.dataset.queuePath, focusAction=document.activeElement?.dataset.queueAction;
  $('queue-list').replaceChildren();
  for(const [index,entry] of items.entries()) {
    const row=node('li','queue-entry'),copy=node('div','queue-copy');
    const current=playback.active&&playback.items[playback.index].path===entry.path;
    if(current)row.setAttribute('aria-current','true');
    copy.append(node('span','queue-title',`${index+1}. ${title(entry.name)}`),node('span','queue-folder',(current?'재생 중 · ':'')+entry.path.split('/').slice(0,-1).join(' / ')));
    const actions=node('div','queue-item-actions');
    for(const [label,action,handler,disabled] of [
      ['위로','up',()=>playbackQueue.move(entry.path,-1),index===0],
      ['아래로','down',()=>playbackQueue.move(entry.path,1),index===items.length-1],
      ['삭제','remove',()=>playbackQueue.remove(entry.path),false]]) {
      const b=button(label,()=>{handler();queueChanged();},'queue-button'); b.disabled=disabled; b.setAttribute('aria-label',title(entry.name)+' '+label); b.dataset.queuePath=entry.path;b.dataset.queueAction=action;actions.append(b);
    }
    row.append(copy,actions);$('queue-list').append(row);
  }
  if(focusPath) {
    const replacement=[...$('queue-list').querySelectorAll('button')].find(b=>b.dataset.queuePath===focusPath&&b.dataset.queueAction===focusAction&&!b.disabled);
    (replacement||$('queue-play')).focus({preventScroll:true});
  }
  document.querySelectorAll('.enqueue-button').forEach(b=>{ const added=items.some(e=>e.path===b.dataset.path); b.textContent=added?'대기열에 담김':'대기열에 담기';b.setAttribute('aria-pressed',String(added)); });
}
function startPlayback(entries) {
  if(!entries.length)return;
  if(!$('player-dialog').open)selectionPosition={x:scrollX,y:scrollY,focus:document.activeElement};
  if(playback.start(entries))fullscreen.start();
}
function icon(name){const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');svg.classList.add('icon');svg.setAttribute('aria-hidden','true');const use=document.createElementNS(svg.namespaceURI,'use');use.setAttribute('href','#i-'+name);svg.append(use);return svg;}
function node(tag,className,text){const el=document.createElement(tag);if(className)el.className=className;if(text!==undefined)el.textContent=text;return el;}
function button(label,handler,className='text-button'){const b=node('button',className,label);b.type='button';b.addEventListener('click',handler);return b;}
function title(name){return name.replace(/\.[^.]+$/,'');}
function size(bytes){if(!Number.isFinite(bytes)||bytes<0)return '';if(bytes<1024)return bytes+' B';for(const [threshold,label] of [[1024**4,'TB'],[1024**3,'GB'],[1024**2,'MB'],[1024,'KB']])if(bytes>=threshold)return (bytes/threshold).toLocaleString('ko-KR',{maximumFractionDigits:1})+' '+label;return '';}
function duration(seconds){if(!Number.isFinite(seconds))return '';const n=Math.floor(seconds);return n>=3600?`${Math.floor(n/3600)}:${String(Math.floor(n/60)%60).padStart(2,'0')}:${String(n%60).padStart(2,'0')}`:`${Math.floor(n/60)}:${String(n%60).padStart(2,'0')}`;}
function date(value){const d=new Date(value);return Number.isNaN(d.valueOf())?'':new Intl.DateTimeFormat('ko-KR',{year:'numeric',month:'2-digit',day:'2-digit'}).format(d)+' 수정';}
function folderName(path){return path.split('/').filter(Boolean).at(-1)||'라이브러리';}
let categoryLabels=new Map();
let themeCopy;
function folderLabel(name){return categoryLabels.get(name)||name;}
function cacheScope(){return `${state.config?.rootId??''}\0${state.config?.instanceId??''}\0`;}
function cacheKey(path,cursor){return `${cacheScope()}${path}\0${cursor??''}`;}
function invalidateCurrent(){cache.invalidatePath(`${cacheScope()}${state.path}\0`);}
async function api(url,options={}){const response=await fetch(url,{cache:'no-cache',...options});if(!response.ok){let data;try{data=await response.json();}catch{data={message:'요청을 처리하지 못했습니다.'};}const error=new Error(data.message);error.code=data.code;error.status=response.status;throw error;}const data=await response.json();const age=response.headers?.get('Cache-Control')?.match(/(?:^|,)\s*max-age=(\d+)/);if(url.startsWith('/api/browse?')&&age)data.cacheTtlMs=Number(age[1])*1000;return data;}
function toast(message){$('toast').textContent=message;$('toast').hidden=false;clearTimeout(toast.timer);toast.timer=setTimeout(()=>$('toast').hidden=true,4500);}
function notice(message,retry){$('notice').replaceChildren(node('div','',message));if(retry)$('notice').append(button('다시 불러오기',()=>loadFolder(state.path,{force:true})));$('notice').hidden=false;}
function renderLocation(){
  const parts=state.path.split('/').filter(Boolean),parent=parts.slice(0,-1).join('/');
  if(!$('theme-preview').hidden){const original=new URL(location.href);original.searchParams.delete('preview-theme');$('exit-preview').href=original.pathname+original.search;}
  $('folder-title').textContent=state.path?folderLabel(folderName(state.path)):themeCopy.rootTitle;document.title=`${state.path?folderLabel(folderName(state.path)):themeCopy.rootTitle} — MovieSites`;
  $('folder-description').textContent=state.path?themeCopy.nestedDescription:themeCopy.rootDescription;
  $('folders-heading').textContent=state.path?themeCopy.nestedFolders:themeCopy.rootFolders;
  $('location-caption').textContent=parts.length>1?parts[0]+themeCopy.collectionSuffix:themeCopy.rootCaption;
  $('screen-navigation').hidden=!parts.length;$('parent-button').hidden=!parent;
  if(parent){const destination=folderLabel(folderName(parent));$('return-label').textContent=destination+' 돌아가기';$('parent-button').setAttribute('aria-label',destination+' 화면으로 돌아가기');}
}
async function listing(path,cursor,force){
  const key=cacheKey(path,cursor);if(!force){const saved=cache.get(key);if(saved)return {data:saved,cached:true};}
  for(const [other,request] of state.requests)if(other!==key||force){request.controller.abort();state.requests.delete(other);}
  if(state.requests.has(key))return state.requests.get(key).promise;
  const controller=new AbortController();const request={controller,promise:null};
  request.promise=(async()=>{
    let query;
    while(true){
      const data=await api('/api/browse?'+new URLSearchParams({path,sort:'duration',...(cursor?{cursor}:query?{query}:force?{refresh:'true'}:{})}),{signal:controller.signal,cache:force?'reload':'no-cache'});
      if(data.state==='failed')throw new Error(data.message||'목록을 정리하지 못했습니다. 잠시 후 새로고침해 주세요.');
      if(data.state!=='preparing'){cache.put(key,data,data.cacheTtlMs??cache.ttlMs);return {data,cached:false};}
      query=data.query;
      if(typeof query!=='string'||!query)throw new Error('목록 준비 상태를 확인하지 못했습니다. 새로고침해 주세요.');
      if(state.path===path){$('loading-text').textContent='짧은 영상부터 볼 수 있도록 길이를 확인하고 있어요.';$('listing-status').textContent=`${data.analyzed||0}편 확인했어요. 처음에는 조금 걸릴 수 있어요.`;}
      await waitForPreparation(controller.signal);
    }
  })().finally(()=>{if(state.requests.get(key)===request)state.requests.delete(key);});
  state.requests.set(key,request);return request.promise;
}
function navigate(path){if(path!==state.path){const url=new URL(location.href);if(path)url.searchParams.set('path',path);else url.searchParams.delete('path');history.pushState({path},'',url);}window.scrollTo({top:0,behavior:'auto'});loadFolder(path);}
async function loadFolder(path,{force=false,keepPage=false}={}){
  const generation=++state.generation;$('queue-add-page').hidden=true;state.queue=[];observer?.disconnect();state.path=path;
  for(const [key,request] of state.requests){request.controller.abort();state.requests.delete(key);}
  if(!keepPage)state.history.reset();
  if(force){invalidateCurrent();state.metadata.clear();}
  $('notice').hidden=true;$('empty-state').hidden=true;$('folder-section').hidden=true;$('video-section').hidden=true;$('pagination').hidden=true;$('video-list').replaceChildren();$('loading-state').hidden=false;$('video-list').setAttribute('aria-busy','true');$('refresh-button').disabled=true;
  $('loading-text').textContent=themeCopy.loadingText||'영상 목록을 불러오고 있습니다.';
  renderLocation();
  try{
    if(!state.config){state.config=await api('/api/config');cache.ttlMs=state.config.cacheTtlSeconds*1000;}
    const result=await listing(path,state.history.current,force);if(generation!==state.generation)return;
    state.listing=result.data;renderListing(result.data,generation);
    $('listing-status').textContent=result.cached?'최근에 본 목록입니다. 새로고침하면 변경된 내용을 확인할 수 있습니다.':'짧은 영상부터 보여드려요. 길이별로 나누어 편하게 골라 보세요.';
  }catch(error){if(generation!==state.generation||error.name==='AbortError')return;
    if(error.status===410){invalidateCurrent();notice('목록 조회 시간이 만료되었습니다. 새로고침하면 처음부터 다시 확인할 수 있습니다.',true);}
    else notice(error.message||'폴더를 불러오지 못했습니다.',true);
    $('listing-status').textContent='목록을 확인할 수 없습니다.';
  }finally{if(generation===state.generation){$('loading-state').hidden=true;$('video-list').setAttribute('aria-busy','false');$('refresh-button').disabled=false;}}
}
function renderListing(data,generation){
  const folders=data.entries.filter(e=>e.kind==='folder'),videos=data.entries.filter(e=>e.kind==='video');
  $('folder-list').replaceChildren();for(const [index,entry] of folders.entries()){
    const label=folderLabel(entry.name),card=button('',()=>navigate(entry.path),'folder-card');card.setAttribute('aria-label',label+' 보기');
    card.dataset.tone=['mint','rose','sky','lilac'][index%4];
    const tab=node('span','folder-tab',themeCopy.folderTab);tab.setAttribute('aria-hidden','true');
    const face=node('span','folder-face'),portal=node('span','folder-portal');portal.setAttribute('aria-hidden','true');
    const fallback=node('span','cover-fallback');fallback.append(icon('folder'));portal.append(fallback);
    const image=node('img');image.alt='';image.loading='lazy';image.decoding='async';image.addEventListener('error',()=>image.remove(),{once:true});image.src='/api/media/folder-cover?'+new URLSearchParams({path:entry.path,...(entry.coverVersion?{v:entry.coverVersion}:{})});portal.append(image);
    const copy=node('span','folder-copy'),bottom=node('span','folder-bottom');bottom.append(node('span','',themeCopy.openLabel),node('span','folder-arrow','↗'));copy.append(node('span','folder-name',label),bottom);face.append(portal,copy);card.append(tab,face);$('folder-list').append(card);
  }
  $('folder-section').hidden=!folders.length;$('video-section').hidden=!videos.length;$('queue-add-page').hidden=!videos.length;
  observer=new IntersectionObserver(items=>{for(const item of items){item.target._visible=item.isIntersecting;if(item.isIntersecting&&!item.target._queued){item.target._queued=true;state.queue.push({entry:item.target._entry,row:item.target,generation});pump();}}},{rootMargin:'200px'});
  for(const group of durationGroups(videos)){
    const heading=node('h3','duration-divider',group.label);heading.dataset.durationGroup=group.id;$('video-list').append(heading);
    for(const entry of group.entries){const row=renderVideo(entry);$('video-list').append(row);observer.observe(row);}
  }
  renderQueue();
  $('empty-state').hidden=data.entries.length>0||Boolean(data.nextCursor);
  $('first-button').hidden=state.history.offset===0;$('previous-button').disabled=!state.history.canPrevious;$('next-button').disabled=!data.nextCursor;$('page-label').textContent=`${state.history.pageNumber}번째 목록`;$('pagination').hidden=state.history.pageNumber===1&&!data.nextCursor;
  if(data.warnings)notice('일부 항목이나 주제 정보를 읽지 못했습니다. 연결된 링크는 표시하지 않으며, 잘못된 주제 파일은 추천 태그로 대신합니다.',false);
  if(!data.entries.length&&data.nextCursor)notice('이 구간에는 표시할 영상이 없습니다. 다음 목록에서 계속 확인할 수 있습니다.',false);
}
function renderVideo(entry){
  const row=node('article','video-entry');row._entry=entry;
  const thumb=button('',()=>startPlayback([entry]),'thumbnail-button');thumb.setAttribute('aria-label',title(entry.name)+' 재생');
  const placeholder=node('span','thumbnail-placeholder');placeholder.append(icon('film'));thumb.append(placeholder);
  const play=node('span','play-mark');play.append(icon('play'));thumb.append(play);const time=node('span','duration');time.hidden=true;thumb.append(time);
  const copy=node('div','entry-copy');const category=node('div','entry-category');category.append(icon('film'),node('span','',state.path.split('/')[0]||themeCopy.rootCollection));const heading=node('h4','entry-title');heading.append(button(title(entry.name),()=>startPlayback([entry]),'title-button'));
  const labels=topicLabels(entry),tags=node('div','topic-tags');tags.setAttribute('aria-label',labels.recommended?'파일명 기반 추천 주제':'영상 주제');
  if(labels.recommended&&labels.tags.length)tags.append(node('span','topic-source','추천 주제'));
  for(const label of labels.tags)tags.append(node('span','topic-tag',label));
  if(!labels.tags.length)tags.append(node('span','topic-empty',labels.empty));
  if(typeof entry.duration==='number'&&entry.duration>0&&Number.isFinite(entry.duration)){time.textContent=duration(entry.duration);time.hidden=false;}
  const meta=node('div','entry-meta');meta.append(node('span','',date(entry.modified)),node('span','',size(entry.size)));copy.append(category,heading,tags,node('p','entry-file',entry.name),meta);const add=button('대기열에 담기',()=>enqueue([entry]),'queue-button enqueue-button');add.dataset.path=entry.path;add.setAttribute('aria-label',title(entry.name)+' 대기열에 담기');copy.append(add);row.append(thumb,copy);
  row._thumb=thumb;row._time=time;row._meta=meta;return row;
}
function pump(){while(state.workers<2&&state.queue.length){const item=state.queue.shift();if(item.generation!==state.generation||!item.row.isConnected)continue;state.workers++;enrich(item).catch(()=>{}).finally(()=>{state.workers--;pump();});}}
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function enrich({entry,row,generation}){
  const key=entry.path+'\0'+entry.modified+'\0'+entry.size;let info=state.metadata.get(key);
  for(let attempt=0;attempt<40;attempt++){
    if(generation!==state.generation||!row.isConnected)return;
    if(!info){try{info=await api('/api/media/info?'+new URLSearchParams({path:entry.path}));}catch(e){if(e.status===429){await sleep(1800);continue;}return;}}
    if(generation!==state.generation||!row.isConnected)return;
    if(info.duration!==null){row._time.textContent=duration(info.duration);row._time.hidden=!row._time.textContent;}
    if(info.width&&info.height&&!row._resolution){row._resolution=node('span','',`${info.width} × ${info.height}`);row._meta.append(row._resolution);}
    if(info.thumbnailState==='ready'){
      if(!row._image){const image=node('img');image.alt='';image.loading='lazy';image.decoding='async';image.addEventListener('load',()=>row._thumb.querySelector('.thumbnail-placeholder')?.remove(),{once:true});image.src='/api/media/thumbnail?'+new URLSearchParams({path:entry.path,v:info.thumbnailVersion||info.version});row._thumb.prepend(image);row._image=image;}
      if(!['queued','analyzing'].includes(info.state)){state.metadata.delete(key);state.metadata.set(key,info);while(state.metadata.size>200)state.metadata.delete(state.metadata.keys().next().value);return;}
    }
    if(info.state==='failed'||info.thumbnailState==='unavailable')return;
    info=null;await sleep(1500);
  }
}
function resetVideo() {
  clearTimeout(state.playerTimer); state.player?.controller.abort(); state.player=null;
  const video=$('video-player'); video.pause(); video.removeAttribute('src'); video.load();
}
async function showPlayer(entry,token) {
  resetVideo();state.player={entry,token,version:null,controller:new AbortController(),loaded:false};
  $('player-title').textContent=title(entry.name);$('player-folder').textContent=entry.path.split('/').slice(0,-1).join(' / ');$('player-file').textContent=entry.name+(entry.size?' · '+size(entry.size):'');$('video-player').hidden=true;$('player-preparing').hidden=false;$('player-progress').hidden=false;$('retry-player').hidden=true;$('player-resume').hidden=true;$('player-state').textContent='영상을 확인하고 있습니다.';
  $('player-position').textContent=playback.items.length>1?`${playback.index+1} / ${playback.items.length} · 순서대로 재생`:'영상 재생';
  $('player-previous').disabled=!playback.hasPrevious;$('player-next').disabled=!playback.hasNext;
  if(!$('player-dialog').open)$('player-dialog').showModal();
  renderQueue(); await pollPlayer(token,false);
}
async function resumePlayer(token) {
  if(!playback.current(token)||!state.player?.loaded)return;
  try { await $('video-player').play(); if(playback.current(token))$('player-resume').hidden=true; }
  catch(error) {
    if(!playback.current(token))return;
    if(error.name==='NotAllowedError'||error.name==='AbortError') {
      $('player-resume').hidden=false;$('player-position').textContent=`${playback.index+1} / ${playback.items.length} · 재생 시작 버튼을 눌러 주세요.`;
    } else playerFailure('영상을 재생하지 못했습니다. 다시 확인하거나 다음 영상을 선택해 주세요.');
  }
}
function playerFailure(message) {
  $('video-player').hidden=true;$('player-preparing').hidden=false;$('player-state').textContent=message;$('player-progress').hidden=true;$('retry-player').hidden=false;$('player-resume').hidden=true;
}
async function pollPlayer(token,retry) {
  if(!playback.current(token)||state.player?.token!==token)return;
  const player=state.player;
  try {
    const info=await api('/api/media/info?'+new URLSearchParams({path:player.entry.path,play:'true',...(retry?{retry:'true'}:{})}),{signal:player.controller.signal});
    if(!playback.current(token)||state.player!==player)return;
    if(info.state==='ready') {
      player.version=info.version;player.loaded=true;
      $('video-player').src='/api/media/stream?'+new URLSearchParams({path:player.entry.path,v:info.version,token:info.streamToken});$('video-player').hidden=false;$('player-preparing').hidden=true;
      await resumePlayer(token);return;
    }
    if(info.state==='failed'){playerFailure(info.message||'영상을 준비하지 못했습니다. 다시 확인하거나 다음 영상을 선택해 주세요.');return;}
    $('player-state').textContent=info.state==='preparing'?'브라우저에서 재생할 영상을 준비하고 있습니다.':'영상 정보를 확인하고 있습니다.';
    state.playerTimer=setTimeout(()=>pollPlayer(token,false),1800);
  } catch(error) {
    if(!playback.current(token)||state.player!==player||error.name==='AbortError')return;
    if(error.status===429){state.playerTimer=setTimeout(()=>pollPlayer(token,false),2500);return;}
    playerFailure((error.status===404?'영상 파일을 찾을 수 없습니다.':error.message||'영상을 불러오지 못했습니다.')+' 다시 확인하거나 다음 영상을 선택해 주세요.');
    if(error.status===404||error.status===409)invalidateCurrent();
  }
}
function closePlayer(closeDialog=true) {
  fullscreen.close();playback.close();resetVideo();
  if(closeDialog&&$('player-dialog').open)$('player-dialog').close();
  renderQueue();
  if(selectionPosition){selectionPosition.focus?.isConnected&&selectionPosition.focus.focus({preventScroll:true});window.scrollTo(selectionPosition.x,selectionPosition.y);selectionPosition=null;}
}
$('queue-play').addEventListener('click',()=>startPlayback(playbackQueue.items));
$('queue-clear').addEventListener('click',()=>{playbackQueue.clear();queueChanged();});
$('queue-add-page').addEventListener('click',()=>enqueue(state.listing.entries.filter(e=>e.kind==='video')));
$('player-previous').addEventListener('click',()=>playback.previous());
$('player-next').addEventListener('click',()=>playback.next());
$('player-resume').addEventListener('click',()=>{fullscreen.request();resumePlayer(playback.token);});
$('player-fullscreen-button').addEventListener('click',()=>fullscreen.request());
$('video-player').addEventListener('playing',()=>fullscreen.playing());
$('video-player').addEventListener('ended',()=>{if(state.player?.loaded&&$('video-player').ended)playback.ended(state.player.token);});
$('player-dialog').addEventListener('cancel',event=>{event.preventDefault();closePlayer();});
$('home-button').addEventListener('click',()=>navigate(''));
$('parent-button').addEventListener('click',()=>navigate(state.path.includes('/')?state.path.slice(0,state.path.lastIndexOf('/')):''));
$('refresh-button').addEventListener('click',()=>loadFolder(state.path,{force:true}));
$('first-button').addEventListener('click',()=>loadFolder(state.path));
$('previous-button').addEventListener('click',()=>{if(state.history.canPrevious){state.history.previous();loadFolder(state.path,{keepPage:true});}});
$('next-button').addEventListener('click',()=>{if(state.listing?.nextCursor){state.history.next(state.listing.nextCursor);loadFolder(state.path,{keepPage:true});}});
$('close-player').addEventListener('click',()=>closePlayer());$('player-dialog').addEventListener('close',()=>{if(!$('player-dialog').open)closePlayer(false);});
$('retry-player').addEventListener('click',()=>{if(state.player){$('retry-player').hidden=true;$('player-progress').hidden=false;$('player-state').textContent='다시 확인하고 있습니다.';pollPlayer(state.player.token,true);}});
$('video-player').addEventListener('error',()=>{if(state.player?.loaded&&$('video-player').error){playerFailure('재생 중 연결이 끊겼거나 영상이 변경되었습니다. 다시 확인하거나 다음 영상을 선택해 주세요.');invalidateCurrent();}});
window.addEventListener('popstate',()=>loadFolder(new URL(location.href).searchParams.get('path')||''));
window.addEventListener('pagehide',()=>{closePlayer();for(const request of state.requests.values())request.controller.abort();});
$('boot-retry').addEventListener('click',()=>location.reload());
try{state.config=await api('/api/config');themeCopy=await applyTheme(state.config);categoryLabels=new Map(Object.entries(state.config.folderLabels||{}));cache.ttlMs=state.config.cacheTtlSeconds*1000;let storage,session;try{storage=window.localStorage;}catch{}try{session=window.sessionStorage;}catch{}cache.useStorage(session,'moviesites:directory-cache:v2',cacheScope());playbackQueue=new PlaybackQueue(storage,state.config.rootId);queueChanged();$('source-link').href=state.config.sourceUrl;await loadFolder(new URL(location.href).searchParams.get('path')||'');}
catch(error){$('boot-message').textContent=error.message||'서비스에 연결하지 못했습니다. 다시 연결해 주세요.';$('boot-retry').hidden=false;}
