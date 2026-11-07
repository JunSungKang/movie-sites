import {DirectoryCache,PageHistory} from './cache.js?v=39b9fda8f3bd565c';

const $=id=>document.getElementById(id);
const cache=new DirectoryCache();
const state={config:null,path:'',history:new PageHistory(),listing:null,generation:0,requests:new Map(),known:new Map(),metadata:new Map(),queue:[],workers:0,player:null,playerTimer:null};
let observer;
function icon(name){const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');svg.classList.add('icon');svg.setAttribute('aria-hidden','true');const use=document.createElementNS(svg.namespaceURI,'use');use.setAttribute('href','#i-'+name);svg.append(use);return svg;}
function node(tag,className,text){const el=document.createElement(tag);if(className)el.className=className;if(text!==undefined)el.textContent=text;return el;}
function button(label,handler,className='text-button'){const b=node('button',className,label);b.type='button';b.addEventListener('click',handler);return b;}
function title(name){return name.replace(/\.[^.]+$/,'');}
function size(bytes){if(!Number.isFinite(bytes)||bytes<0)return '';if(bytes<1024)return bytes+' B';for(const [threshold,label] of [[1024**4,'TB'],[1024**3,'GB'],[1024**2,'MB'],[1024,'KB']])if(bytes>=threshold)return (bytes/threshold).toLocaleString('ko-KR',{maximumFractionDigits:1})+' '+label;return '';}
function duration(seconds){if(!Number.isFinite(seconds))return '';const n=Math.floor(seconds);return n>=3600?`${Math.floor(n/3600)}:${String(Math.floor(n/60)%60).padStart(2,'0')}:${String(n%60).padStart(2,'0')}`:`${Math.floor(n/60)}:${String(n%60).padStart(2,'0')}`;}
function date(value){const d=new Date(value);return Number.isNaN(d.valueOf())?'':new Intl.DateTimeFormat('ko-KR',{year:'numeric',month:'2-digit',day:'2-digit'}).format(d)+' 수정';}
function folderName(path){return path.split('/').filter(Boolean).at(-1)||'나의 영상들';}
function cacheKey(path,cursor){return `${state.config?.rootId??''}\0${path}\0${cursor??''}`;}
function invalidateCurrent(){cache.invalidatePath(`${state.config?.rootId??''}\0${state.path}\0`);}
async function api(url,options={}){const response=await fetch(url,{cache:'no-store',...options});if(!response.ok){let data;try{data=await response.json();}catch{data={message:'요청을 처리하지 못했습니다.'};}const error=new Error(data.message);error.code=data.code;error.status=response.status;throw error;}return response.json();}
function toast(message){$('toast').textContent=message;$('toast').hidden=false;clearTimeout(toast.timer);toast.timer=setTimeout(()=>$('toast').hidden=true,4500);}
function notice(message,retry){$('notice').replaceChildren(node('div','',message));if(retry)$('notice').append(button('다시 불러오기',()=>loadFolder(state.path,{force:true})));$('notice').hidden=false;}
function renderLocation(){
  $('folder-title').textContent=folderName(state.path);document.title=`${folderName(state.path)} — MovieSites`;
  $('folder-description').textContent=state.path?'이 폴더에 담긴 장면들을 천천히 둘러보세요.':'오래 간직하고 싶은 장면들을 만나보세요.';
  const crumbs=$('breadcrumbs');crumbs.replaceChildren();crumbs.append(button('영상 서재',()=>navigate(''),'breadcrumb-button'));
  let current='';for(const part of state.path.split('/').filter(Boolean)){current=current?current+'/'+part:part;const target=current;crumbs.append(icon('chevron'),button(part,()=>navigate(target),'breadcrumb-button'));}
  const nav=$('folder-nav');nav.replaceChildren();$('home-button').setAttribute('aria-current',state.path?'false':'page');
  const paths=new Map();let parent='';for(const part of state.path.split('/').filter(Boolean)){parent=parent?parent+'/'+part:part;paths.set(parent,part);}
  for(const [path,name] of state.known)if(paths.size<14)paths.set(path,name);
  for(const [path,name] of paths){const b=button('',()=>navigate(path),'folder-nav-button');b.append(icon('folder'),node('span','',name));if(path===state.path)b.setAttribute('aria-current','page');nav.append(b);}
}
async function listing(path,cursor,force){
  const key=cacheKey(path,cursor);if(!force){const saved=cache.get(key);if(saved)return {data:saved,cached:true};}
  for(const [other,request] of state.requests)if(other!==key||force){request.controller.abort();state.requests.delete(other);}
  if(state.requests.has(key))return state.requests.get(key).promise;
  const controller=new AbortController();const request={controller,promise:null};
  request.promise=api('/api/browse?'+new URLSearchParams({path,...(cursor?{cursor}:{})}),{signal:controller.signal}).then(data=>{cache.put(key,data);return {data,cached:false};}).finally(()=>{if(state.requests.get(key)===request)state.requests.delete(key);});
  state.requests.set(key,request);return request.promise;
}
function navigate(path){if(path!==state.path){const url=new URL(location.href);if(path)url.searchParams.set('path',path);else url.searchParams.delete('path');history.pushState({path},'',url);}loadFolder(path);}
async function loadFolder(path,{force=false,keepPage=false}={}){
  const generation=++state.generation;state.queue=[];observer?.disconnect();state.path=path;
  if(!keepPage)state.history.reset();
  if(force){invalidateCurrent();state.metadata.clear();}
  $('notice').hidden=true;$('empty-state').hidden=true;$('folder-section').hidden=true;$('pagination').hidden=true;$('video-list').replaceChildren();$('loading-state').hidden=false;$('video-list').setAttribute('aria-busy','true');$('refresh-button').disabled=true;
  renderLocation();
  try{
    if(!state.config){state.config=await api('/api/config');cache.ttlMs=state.config.cacheTtlSeconds*1000;}
    const result=await listing(path,state.history.current,force);if(generation!==state.generation)return;
    state.listing=result.data;renderListing(result.data,generation);
    $('listing-status').textContent=result.cached?'최근에 불러온 목록입니다. 변경 사항은 새로고침으로 확인하세요.':'현재 폴더의 목록을 불러왔습니다.';
  }catch(error){if(generation!==state.generation||error.name==='AbortError')return;
    if(error.status===410){invalidateCurrent();notice('목록 조회 시간이 만료되었습니다. 새로고침하면 처음부터 다시 확인할 수 있습니다.',true);}
    else notice(error.message||'폴더를 불러오지 못했습니다.',true);
    $('listing-status').textContent='목록을 확인할 수 없습니다.';
  }finally{if(generation===state.generation){$('loading-state').hidden=true;$('video-list').setAttribute('aria-busy','false');$('refresh-button').disabled=false;}}
}
function renderListing(data,generation){
  const folders=data.entries.filter(e=>e.kind==='folder'),videos=data.entries.filter(e=>e.kind==='video');
  $('folder-list').replaceChildren();for(const entry of folders){state.known.delete(entry.path);state.known.set(entry.path,entry.name);const b=button('',()=>navigate(entry.path),'folder-chip');b.append(icon('folder'),node('span','',entry.name));$('folder-list').append(b);}
  while(state.known.size>30)state.known.delete(state.known.keys().next().value);
  $('folder-section').hidden=!folders.length;renderLocation();
  observer=new IntersectionObserver(items=>{for(const item of items){item.target._visible=item.isIntersecting;if(item.isIntersecting&&!item.target._queued){item.target._queued=true;state.queue.push({entry:item.target._entry,row:item.target,generation});pump();}}},{rootMargin:'200px'});
  for(const entry of videos){const row=renderVideo(entry);$('video-list').append(row);observer.observe(row);}
  $('empty-state').hidden=data.entries.length>0||Boolean(data.nextCursor);
  $('first-button').hidden=state.history.offset===0;$('previous-button').disabled=!state.history.canPrevious;$('next-button').disabled=!data.nextCursor;$('page-label').textContent=`${state.history.pageNumber}번째 목록`;$('pagination').hidden=state.history.pageNumber===1&&!data.nextCursor;
  if(data.warnings)notice('일부 항목은 읽을 수 없거나 연결된 링크여서 표시하지 않았습니다.',false);
  if(!data.entries.length&&data.nextCursor)notice('이 구간에는 표시할 영상이 없습니다. 다음 목록에서 계속 확인할 수 있습니다.',false);
}
function renderVideo(entry){
  const row=node('article','video-entry');row._entry=entry;
  const thumb=button('',()=>openPlayer(entry),'thumbnail-button');thumb.setAttribute('aria-label',title(entry.name)+' 재생');
  const placeholder=node('span','thumbnail-placeholder');placeholder.append(icon('film'));thumb.append(placeholder);
  const play=node('span','play-mark');play.append(icon('play'));thumb.append(play);const time=node('span','duration');time.hidden=true;thumb.append(time);
  const copy=node('div','entry-copy');const category=node('div','entry-category');category.append(icon('film'),node('span','',folderName(state.path)));const heading=node('h2','entry-title');heading.append(button(title(entry.name),()=>openPlayer(entry),'title-button'));
  const meta=node('div','entry-meta');meta.append(node('span','',date(entry.modified)),node('span','',size(entry.size)));copy.append(category,heading,node('p','entry-file',entry.name),meta);row.append(thumb,copy);
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
      if(!row._image){const image=node('img');image.alt='';image.loading='lazy';image.decoding='async';image.addEventListener('load',()=>row._thumb.querySelector('.thumbnail-placeholder')?.remove(),{once:true});image.src='/api/media/thumbnail?'+new URLSearchParams({path:entry.path,v:info.version});row._thumb.prepend(image);row._image=image;}
      state.metadata.delete(key);state.metadata.set(key,info);while(state.metadata.size>200)state.metadata.delete(state.metadata.keys().next().value);return;
    }
    if(info.state==='failed'||info.thumbnailState==='unavailable')return;
    info=null;await sleep(1500);
  }
}
async function openPlayer(entry){
  closePlayer(false);state.playerSerial=(state.playerSerial||0)+1;state.player={entry,token:String(state.playerSerial),version:null};const token=state.player.token;
  $('player-title').textContent=title(entry.name);$('player-folder').textContent=state.path?state.path.replaceAll('/',' › '):'나의 영상 서재';$('player-file').textContent=entry.name+' · '+size(entry.size);$('video-player').hidden=true;$('player-preparing').hidden=false;$('player-progress').hidden=false;$('retry-player').hidden=true;$('player-state').textContent='영상을 확인하고 있습니다.';
  if(!$('player-dialog').open)$('player-dialog').showModal();
  await pollPlayer(token,false);
}
async function pollPlayer(token,retry){
  if(state.player?.token!==token)return;
  try{
    const info=await api('/api/media/info?'+new URLSearchParams({path:state.player.entry.path,play:'true',...(retry?{retry:'true'}:{})}));
    if(state.player?.token!==token)return;
    if(info.state==='ready'){
      state.player.version=info.version;$('video-player').src='/api/media/stream?'+new URLSearchParams({path:state.player.entry.path,v:info.version,token:info.streamToken});$('video-player').hidden=false;$('player-preparing').hidden=true;return;
    }
    if(info.state==='failed'){$('player-state').textContent=info.message||'영상을 준비하지 못했습니다.';$('player-progress').hidden=true;$('retry-player').hidden=false;return;}
    $('player-state').textContent=info.state==='preparing'?'브라우저에서 재생할 영상을 준비하고 있습니다. 원본은 그대로 보존됩니다.':'영상 정보를 확인하고 있습니다.';
    state.playerTimer=setTimeout(()=>pollPlayer(token,false),1800);
  }catch(error){if(state.player?.token!==token)return;
    if(error.status===429){state.playerTimer=setTimeout(()=>pollPlayer(token,false),2500);return;}
    $('player-state').textContent=error.message||'영상을 불러오지 못했습니다.';$('player-progress').hidden=true;$('retry-player').hidden=false;
    if(error.status===404||error.status===409)invalidateCurrent();
  }
}
function closePlayer(closeDialog=true){clearTimeout(state.playerTimer);state.player=null;const video=$('video-player');video.pause();video.removeAttribute('src');video.load();if(closeDialog&&$('player-dialog').open)$('player-dialog').close();}
$('home-button').addEventListener('click',()=>navigate(''));
$('refresh-button').addEventListener('click',()=>loadFolder(state.path,{force:true}));
$('first-button').addEventListener('click',()=>loadFolder(state.path));
$('previous-button').addEventListener('click',()=>{if(state.history.canPrevious){state.history.previous();loadFolder(state.path,{keepPage:true});}});
$('next-button').addEventListener('click',()=>{if(state.listing?.nextCursor){state.history.next(state.listing.nextCursor);loadFolder(state.path,{keepPage:true});}});
$('close-player').addEventListener('click',()=>closePlayer());$('player-dialog').addEventListener('close',()=>closePlayer(false));
$('retry-player').addEventListener('click',()=>{if(state.player){$('retry-player').hidden=true;$('player-progress').hidden=false;$('player-state').textContent='다시 확인하고 있습니다.';pollPlayer(state.player.token,true);}});
$('video-player').addEventListener('error',()=>{if(state.player){$('video-player').hidden=true;$('player-preparing').hidden=false;$('player-state').textContent='재생 중 연결이 끊겼거나 영상이 변경되었습니다. 다시 확인해 주세요.';$('player-progress').hidden=true;$('retry-player').hidden=false;invalidateCurrent();}});
window.addEventListener('popstate',()=>loadFolder(new URL(location.href).searchParams.get('path')||''));
window.addEventListener('pagehide',()=>{closePlayer();for(const request of state.requests.values())request.controller.abort();});
try{state.config=await api('/api/config');cache.ttlMs=state.config.cacheTtlSeconds*1000;$('source-link').href=state.config.sourceUrl;await loadFolder(new URL(location.href).searchParams.get('path')||'');}
catch(error){$('loading-state').hidden=true;$('video-list').setAttribute('aria-busy','false');notice('서비스에 연결하지 못했습니다. 목록 새로고침으로 다시 시도해 주세요.',true);}
