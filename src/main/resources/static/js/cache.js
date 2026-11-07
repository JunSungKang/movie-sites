export class DirectoryCache {
  constructor({ttlMs=120000,maxEntries=20,maxItems=2000,maxBytes=10*1024*1024,clock=Date.now}={}) {
    this.ttlMs=ttlMs;this.maxEntries=maxEntries;this.maxItems=maxItems;this.maxBytes=maxBytes;this.clock=clock;this.entries=new Map();
  }
  get(key){const item=this.entries.get(key);if(!item)return null;if(this.clock()>=item.expires){this.entries.delete(key);return null;}this.entries.delete(key);this.entries.set(key,item);return item.value;}
  put(key,value){const count=value.entries?.length??0,bytes=JSON.stringify(value).length*2;if(count>this.maxItems||bytes>this.maxBytes)return;
    this.entries.delete(key);this.entries.set(key,{value,expires:this.clock()+this.ttlMs,count,bytes});
    let items=0,total=0;for(const e of this.entries.values()){items+=e.count;total+=e.bytes;}
    while(this.entries.size>this.maxEntries||items>this.maxItems||total>this.maxBytes){const first=this.entries.keys().next().value;const old=this.entries.get(first);this.entries.delete(first);items-=old.count;total-=old.bytes;}
  }
  invalidatePath(prefix){for(const key of this.entries.keys())if(key.startsWith(prefix))this.entries.delete(key);}
  clear(){this.entries.clear();}
}
