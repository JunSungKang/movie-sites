export class DirectoryCache {
  constructor({ttlMs=120000,maxEntries=20,maxItems=2000,maxBytes=10*1024*1024,clock=Date.now}={}) {
    this.ttlMs=ttlMs;this.maxEntries=maxEntries;this.maxItems=maxItems;this.maxBytes=maxBytes;this.clock=clock;this.entries=new Map();
  }
  get(key){const item=this.entries.get(key);if(!item)return null;if(this.clock()>=item.expires){this.entries.delete(key);return null;}this.entries.delete(key);this.entries.set(key,item);return item.value;}
  put(key,value,ttlMs=this.ttlMs){const count=value.entries?.length??0,bytes=JSON.stringify(value).length*2;this.entries.delete(key);if(count>this.maxItems||bytes>this.maxBytes||ttlMs<=0){this.persist();return;}
    this.entries.set(key,{value,expires:this.clock()+Math.min(this.ttlMs,ttlMs),count,bytes});
    let items=0,total=0;for(const e of this.entries.values()){items+=e.count;total+=e.bytes;}
    while(this.entries.size>this.maxEntries||items>this.maxItems||total>this.maxBytes){const first=this.entries.keys().next().value;const old=this.entries.get(first);this.entries.delete(first);items-=old.count;total-=old.bytes;}
    this.persist();
  }
  invalidatePath(prefix){for(const key of this.entries.keys())if(key.startsWith(prefix))this.entries.delete(key);this.persist();}
  clear(){this.entries.clear();this.persist();}
  useStorage(storage,key,scope=''){
    this.storageKey=key;this.scope=scope;this.storage=null;
    try{
      const raw=storage?.getItem(key);
      if(raw&&raw.length<=1500000){const saved=JSON.parse(raw);if(saved.version===2&&saved.scope===scope&&saved.savedAt<=this.clock()&&Array.isArray(saved.records)&&saved.records.length<=this.maxEntries){
        for(const item of saved.records){if(typeof item.key==='string'&&item.key.length<=8192&&item.expires>this.clock()&&Array.isArray(item.value?.entries)&&item.value.entries.length<=100)this.put(item.key,item.value,item.expires-this.clock());}
      }}
    }catch{}
    this.storage=storage;
  }
  persist(){
    if(!this.storage)return;
    try{
      const records=[];let bytes=0;
      for(const [key,item] of [...this.entries].reverse())if(item.expires>this.clock()&&bytes+item.bytes+key.length*2+256<2800000){records.unshift({key,value:item.value,expires:item.expires});bytes+=item.bytes+key.length*2+256;}
      this.storage.setItem(this.storageKey,JSON.stringify({version:2,scope:this.scope,savedAt:this.clock(),records}));
    }catch{} // Storage denial/quota leaves the bounded memory cache working.
  }
}

// A sliding cursor window bounds navigation metadata independently of response caching.
export class PageHistory {
  constructor(maxEntries=20){this.maxEntries=maxEntries;this.reset();}
  reset(){this.cursors=[null];this.index=0;this.offset=0;}
  get current(){return this.cursors[this.index];}
  get pageNumber(){return this.offset+this.index+1;}
  get canPrevious(){return this.index>0;}
  previous(){if(this.canPrevious)this.index--;}
  next(cursor){
    this.cursors.splice(this.index+1);this.cursors.push(cursor);this.index++;
    const excess=this.cursors.length-this.maxEntries;
    if(excess>0){this.cursors.splice(0,excess);this.offset+=excess;this.index-=excess;}
  }
}
